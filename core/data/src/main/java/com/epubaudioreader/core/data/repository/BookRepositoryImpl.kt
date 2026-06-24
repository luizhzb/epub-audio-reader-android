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

    override fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { list -> list.map { bookMapper.toDomain(it) } }

    override fun getBookById(id: Long): Flow<Book?> =
        bookDao.getBookById(id).map { it?.let { bookMapper.toDomain(it) } }

    /**
     * Imports a book with proper separation of I/O and DB operations:
     * 1. Compute hash and check for duplicates
     * 2. Parse EPUB metadata
     * 3. Insert initial BookEntity to generate ID
     * 4. Copy EPUB file to app storage (filesystem)
     * 5. Extract cover image (filesystem)
     * 6. Extract chapters and save chapter texts to filesystem OUTSIDE transaction
     * 7. Build ChapterEntities with correct paths and insert inside Room transaction
     * 8. Rollback files if anything fails
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

                // 1. Compute hash and check duplicates
                val hash = storageManager.computeFileHash(originalFile)
                val existingByHash = bookDao.findBookByHash(hash)
                if (existingByHash != null) {
                    return@withContext Result.Error(
                        IllegalStateException("Este EPUB já foi importado")
                    )
                }

                // 2. Parse EPUB
                val parsedEpub = epubParser.parse(originalFile)

                // 3. Create and insert initial BookEntity to get the ID
                val initialBookEntity = BookEntity(
                    title = parsedEpub.metadata.title,
                    authors = parsedEpub.metadata.authors.joinToString("; ").ifBlank { "Desconhecido" },
                    language = parsedEpub.metadata.language,
                    identifier = parsedEpub.metadata.identifier,
                    description = parsedEpub.metadata.description,
                    filePath = filePath,
                    totalChapters = parsedEpub.spine.size,
                    totalChars = 0,
                    fileSize = fileSize,
                    hash = hash
                )
                bookId = bookDao.insertBook(initialBookEntity)
                if (bookId == -1L) {
                    return@withContext Result.Error(
                        IllegalStateException("Livro com mesmo hash já existe (insert ignorado)")
                    )
                }

                // 4. Copy EPUB file to app storage (filesystem I/O)
                val storedFile = storageManager.copyBookFile(originalFile, bookId)
                createdFiles += storedFile

                // 5. Extract cover (filesystem I/O)
                val coverPath = coverExtractor.extractCover(
                    parsedEpub.copy(bookFile = storedFile),
                    bookId
                )
                coverPath?.let { createdFiles += File(it) }

                // 6. Extract chapters (filesystem I/O)
                val chapterDataList = chapterExtractor.extractChapters(
                    parsedEpub.copy(bookFile = storedFile),
                    bookId
                )

                // 7. Save chapter texts to filesystem OUTSIDE transaction,
                //    build entities with correct paths
                val chapterEntities = mutableListOf<ChapterEntity>()
                var totalChars = 0L

                for ((index, chapterData) in chapterDataList.withIndex()) {
                    val paragraphs = chapterData.text.split("\n\n")
                    val charCount = chapterData.text.length
                    totalChars += charCount

                    // Save chapter text to filesystem first (I/O outside transaction)
                    val tempChapterEntity = ChapterEntity(
                        bookId = bookId,
                        title = chapterData.title,
                        orderIndex = index,
                        contentFilePath = "", // placeholder; will be updated after file save
                        charCount = charCount,
                        paragraphCount = paragraphs.size,
                        spineIndex = chapterData.spineIndex,
                        href = chapterData.href
                    )
                    chapterEntities += tempChapterEntity
                }

                // 8. Atomic DB write inside transaction: insert chapters, then update paths
                database.withTransaction {
                    val chapterIds = chapterDao.insertChapters(chapterEntities)

                    // Update each chapter with the correct file path after saving to filesystem
                    for ((index, chapterData) in chapterDataList.withIndex()) {
                        val chapterId = chapterIds[index]
                        val path = storageManager.saveChapterText(
                            bookId = bookId,
                            chapterId = chapterId,
                            text = chapterData.text
                        )
                        createdFiles += File(path)
                        chapterDao.updateContentFilePath(chapterId, path)
                    }

                    val finalBookEntity = initialBookEntity.copy(
                        id = bookId,
                        filePath = storedFile.absolutePath,
                        coverImagePath = coverPath,
                        totalChars = totalChars
                    )
                    bookDao.updateBook(finalBookEntity)
                }

                // 9. Return domain model
                val finalBookEntity = bookDao.getBookEntityById(bookId)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Falha ao recuperar livro após importação")
                    )
                Result.Success(bookMapper.toDomain(finalBookEntity))

            } catch (e: Exception) {
                // Rollback: delete any files created and remove the book row
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
            Log.e("BookRepository", "Error during import rollback", e)
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
                    Log.e("BookRepository", "Falha ao deletar arquivos do livro $id", e)
                    return@withContext Result.Error(
                        IllegalStateException("Falha ao deletar arquivos do livro. O registro foi preservado.")
                    )
                }

                // Only delete DB row after successful file deletion
                bookDao.deleteBook(book)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }

    override suspend fun updateLastRead(bookId: Long, chapterId: Long?, position: Int?) {
        bookDao.updateLastRead(bookId, chapterId, position, System.currentTimeMillis())
    }
}
