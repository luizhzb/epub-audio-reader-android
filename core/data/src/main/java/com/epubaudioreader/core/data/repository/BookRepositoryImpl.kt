package com.epubaudioreader.core.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.data.epub.extractor.ChapterExtractor
import com.epubaudioreader.core.data.epub.extractor.CoverExtractor
import com.epubaudioreader.core.data.epub.parser.EpubParser
import com.epubaudioreader.core.data.local.database.AppDatabase
import com.epubaudioreader.core.data.local.database.dao.BookDao
import com.epubaudioreader.core.data.local.database.dao.ChapterDao
import com.epubaudioreader.core.data.local.database.entity.BookEntity
import com.epubaudioreader.core.data.local.database.entity.ChapterEntity
import com.epubaudioreader.core.data.local.storage.EpubStorageManager
import com.epubaudioreader.core.data.repository.mapper.BookMapper
import com.epubaudioreader.core.domain.model.Book
import com.epubaudioreader.core.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val epubParser: EpubParser,
    private val coverExtractor: CoverExtractor,
    private val chapterExtractor: ChapterExtractor,
    private val storageManager: EpubStorageManager,
    private val dispatcher: DispatcherProvider,
    private val bookMapper: BookMapper,
) : BookRepository {

    companion object {
        private const val TAG = "BookRepositoryImpl"
    }

    override fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { list -> list.map { bookMapper.toDomain(it) } }

    override fun getBookById(id: Long): Flow<Book?> =
        bookDao.getBookById(id).map { it?.let { bookMapper.toDomain(it) } }

    /**
     * Imports a book with proper separation of I/O and DB operations:
     *
     * Phase 1 - Validation & Setup:
     *   1. Compute hash and check for duplicates
     *   2. Parse EPUB metadata
     *   3. Insert initial BookEntity to generate ID
     *
     * Phase 2 - Filesystem I/O (ALL outside Room transaction):
     *   4. Copy EPUB file to app storage
     *   5. Extract cover image
     *   6. Extract and save ALL chapter texts to filesystem
     *
     * Phase 3 - Database (inside Room transaction, NO I/O):
     *   7. Build ChapterEntities with correct file paths
     *   8. Insert chapters and update book inside transaction
     *
     * Phase 4 - Rollback on failure:
     *   9. If any step fails, delete created files and DB row
     */
    override suspend fun importBook(filePath: String, fileSize: Long): Result<Book> =
        withContext(dispatcher.io) {
            var bookId: Long = -1
            val createdFiles = mutableListOf<File>()

            try {
                val originalFile = File(filePath)
                if (!originalFile.exists()) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Arquivo não encontrado: $filePath")
                    )
                }

                // Phase 1: Validation & Setup
                val hash = storageManager.computeFileHash(originalFile)
                val existingByHash = bookDao.findBookByHash(hash)
                if (existingByHash != null) {
                    return@withContext Result.Error(
                        IllegalStateException("Este EPUB já foi importado")
                    )
                }

                val parsedEpub = epubParser.parse(originalFile)

                // BUG-EPUB-006: Validate spine is not empty before inserting
                if (parsedEpub.spine.isEmpty()) {
                    Log.e(TAG, "EPUB has empty spine - no readable chapters: $filePath")
                    return@withContext Result.Error(
                        IllegalStateException("O EPUB não possui capítulos legíveis (spine vazio)")
                    )
                }

                // Also validate that at least one linear spine item has a corresponding manifest entry
                val resolvableSpineItems = parsedEpub.spine.count { spineItem ->
                    spineItem.linear && parsedEpub.manifest[spineItem.idref] != null
                }
                if (resolvableSpineItems == 0) {
                    Log.e(TAG, "EPUB spine has no resolvable manifest items: $filePath")
                    return@withContext Result.Error(
                        IllegalStateException("O EPUB não possui capítulos resolvíveis no manifesto")
                    )
                }

                val initialBookEntity = BookEntity(
                    title = parsedEpub.metadata.title,
                    authors = parsedEpub.metadata.authors.joinToString("; ").ifBlank { "Desconhecido" },
                    language = parsedEpub.metadata.language,
                    identifier = parsedEpub.metadata.identifier,
                    description = parsedEpub.metadata.description,
                    filePath = filePath,
                    totalChapters = parsedEpub.spine.size,
                    totalChars = 0L,
                    fileSize = fileSize,
                    hash = hash
                )
                bookId = bookDao.insertBook(initialBookEntity)
                if (bookId == -1L) {
                    return@withContext Result.Error(
                        IllegalStateException("Livro com mesmo hash já existe (insert ignorado)")
                    )
                }

                // Phase 2: Filesystem I/O (ALL outside transaction)
                val storedFile = storageManager.copyBookFile(originalFile, bookId)
                createdFiles += storedFile

                val parsedEpubWithStoredFile = parsedEpub.copy(bookFile = storedFile)

                // BUG-EPUB-004: Open ZipFile once and pass to extractors to avoid multiple opens
                val (coverPath, chapterDataList) = ZipFile(storedFile).use { zip ->
                    val cover = coverExtractor.extractCover(
                        parsedEpubWithStoredFile, bookId, zip
                    )
                    val chapters = chapterExtractor.extractChapters(
                        parsedEpubWithStoredFile, bookId, zip
                    )
                    cover to chapters
                }
                coverPath?.let { createdFiles += File(it) }

                // Save ALL chapter texts to filesystem BEFORE any DB transaction
                val chapterPaths = mutableListOf<String>()
                var totalChars = 0L

                for ((index, chapterData) in chapterDataList.withIndex()) {
                    val charCount = chapterData.text.length
                    totalChars += charCount.toLong()

                    // Save chapter text to filesystem (I/O outside transaction)
                    val path = storageManager.saveChapterText(
                        bookId = bookId,
                        chapterId = index.toLong(),
                        text = chapterData.text
                    )
                    createdFiles += File(path)
                    chapterPaths += path
                }

                // Phase 3: Database transaction (NO filesystem I/O here)
                database.withTransaction {
                    val chapterEntities = chapterDataList.mapIndexed { index, chapterData ->
                        val paragraphs = chapterData.text.split("\n\n")
                        ChapterEntity(
                            bookId = bookId,
                            title = chapterData.title,
                            orderIndex = index,
                            contentFilePath = chapterPaths.getOrElse(index) { "" },
                            charCount = chapterData.text.length,
                            paragraphCount = paragraphs.size,
                            spineIndex = chapterData.spineIndex,
                            href = chapterData.href
                        )
                    }

                    chapterDao.insertChapters(chapterEntities)

                    val finalBookEntity = initialBookEntity.copy(
                        id = bookId,
                        filePath = storedFile.absolutePath,
                        coverImagePath = coverPath,
                        totalChars = totalChars
                    )
                    bookDao.updateBook(finalBookEntity)
                }

                // Phase 4: Return result
                val finalBookEntity = bookDao.getBookEntityById(bookId)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Falha ao recuperar livro após importação")
                    )
                Result.Success(bookMapper.toDomain(finalBookEntity))

            } catch (e: Exception) {
                // BUG-EPUB-008: Log rollback errors
                Log.e(TAG, "Book import failed for $filePath, rolling back", e)
                rollbackImport(bookId, createdFiles)
                Result.Error(e)
            }
        }

    private suspend fun rollbackImport(bookId: Long, createdFiles: List<File>) {
        try {
            createdFiles.forEach { it.delete() }
            if (bookId > 0) {
                storageManager.deleteBookFiles(bookId)
                bookDao.getBookEntityById(bookId)?.let { bookDao.deleteBook(it) }
            }
        } catch (e: Exception) {
            // Best-effort rollback; log if needed
            Log.e(TAG, "Error during import rollback for book $bookId", e)
        }
    }

    /**
     * Deletes a book atomically: filesystem files first, then DB row.
     * If file deletion fails, the DB row is preserved to avoid orphaned records.
     */
    override suspend fun deleteBook(id: Long): Result<Unit> =
        withContext(dispatcher.io) {
            try {
                val book = bookDao.getBookEntityById(id)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Livro não encontrado")
                    )

                // Delete filesystem files FIRST
                try {
                    storageManager.deleteBookFiles(id)
                } catch (e: Exception) {
                    Log.e(TAG, "Falha ao deletar arquivos do livro $id", e)
                    return@withContext Result.Error(
                        IllegalStateException("Falha ao deletar arquivos do livro. O registro foi preservado.")
                    )
                }

                // Only delete DB row after successful file deletion
                bookDao.deleteBook(book)
                Result.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete book $id", e)
                Result.Error(e)
            }
        }

    override suspend fun updateLastRead(bookId: Long, chapterId: Long?, position: Int?) {
        bookDao.updateLastRead(bookId, chapterId, position, System.currentTimeMillis())
    }
}
