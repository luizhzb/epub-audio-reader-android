package com.epubaudioreader.core.data.repository

import com.epubaudioreader.core.common.DispatcherProvider
import com.epubaudioreader.core.data.database.BookDao
import com.epubaudioreader.core.data.database.BookEntity
import com.epubaudioreader.core.data.database.ChapterDao
import com.epubaudioreader.core.data.database.ChapterEntity
import com.epubaudioreader.core.data.epub.extractor.ChapterExtractor
import com.epubaudioreader.core.data.epub.extractor.CoverExtractor
import com.epubaudioreader.core.data.epub.parser.EpubParser
import com.epubaudioreader.core.data.repository.mapper.BookMapper
import com.epubaudioreader.core.data.repository.mapper.ChapterMapper
import com.epubaudioreader.core.data.storage.EpubStorageManager
import com.epubaudioreader.core.domain.model.Book
import com.epubaudioreader.core.domain.model.Result
import com.epubaudioreader.core.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val epubParser: EpubParser,
    private val coverExtractor: CoverExtractor,
    private val chapterExtractor: ChapterExtractor,
    private val storageManager: EpubStorageManager,
    private val dispatcher: DispatcherProvider,
    private val bookMapper: BookMapper,
    private val chapterMapper: ChapterMapper
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { list -> list.map { bookMapper.toDomain(it) } }

    override fun getBookById(id: Long): Flow<Book?> =
        bookDao.getBookById(id).map { it?.let { bookMapper.toDomain(it) } }

    /**
     * Imports a book atomically:
     * 1. Compute hash and check for duplicates
     * 2. Parse EPUB metadata
     * 3. Copy EPUB file to app storage
     * 4. Insert BookEntity into Room (generates ID)
     * 5. Extract cover image
     * 6. Extract chapters, save text to .txt files
     * 7. Insert ChapterEntities with correct contentFilePath
     * 8. Update book with cover path and total stats
     *
     * All database operations are wrapped in a Room transaction.
     */
    override suspend fun importBook(filePath: String, fileSize: Long): Result<Book> =
        withContext(dispatcher.io) {
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

                // 2. Parse EPUB (on original file before copying)
                val parsedEpub = epubParser.parse(originalFile)

                // 3. Create initial BookEntity to get the ID
                val initialBookEntity = BookEntity(
                    title = parsedEpub.metadata.title,
                    authors = parsedEpub.metadata.authors.joinToString("; ").ifBlank { "Desconhecido" },
                    language = parsedEpub.metadata.language,
                    identifier = parsedEpub.metadata.identifier,
                    description = parsedEpub.metadata.description,
                    filePath = filePath, // Will be updated after copy
                    totalChapters = parsedEpub.spine.size,
                    totalChars = 0,
                    fileSize = fileSize,
                    hash = hash
                )

                // 4. Insert book to generate ID (first DB write)
                val bookId = bookDao.insertBook(initialBookEntity)

                // 5. Copy EPUB file to app storage using the generated ID
                val storedFile = storageManager.copyBookFile(originalFile, bookId)

                // 6. Extract cover
                val coverPath = coverExtractor.extractCover(
                    parsedEpub.copy(bookFile = storedFile),
                    bookId
                )

                // 7. Extract chapters
                val chapterDataList = chapterExtractor.extractChapters(
                    parsedEpub.copy(bookFile = storedFile),
                    bookId
                )

                // 8. Save chapter texts and collect entities
                val chapterEntities = mutableListOf<ChapterEntity>()
                val contentPaths = mutableListOf<String>()
                var totalChars = 0L

                for ((index, chapterData) in chapterDataList.withIndex()) {
                    val paragraphs = chapterData.text.split("\n\n")
                    val charCount = chapterData.text.length
                    totalChars += charCount

                    // Placeholder entity (ID will be set after insert)
                    val entity = ChapterEntity(
                        bookId = bookId,
                        title = chapterData.title,
                        orderIndex = index,
                        contentFilePath = "", // placeholder
                        charCount = charCount,
                        paragraphCount = paragraphs.size,
                        spineIndex = chapterData.spineIndex,
                        href = chapterData.href
                    )
                    chapterEntities.add(entity)
                }

                // 9. Insert all chapters (DB transaction)
                val chapterIds = chapterDao.insertChapters(chapterEntities)

                // 10. Save chapter text files now that we have IDs
                for ((index, chapterData) in chapterDataList.withIndex()) {
                    val chapterId = chapterIds[index]
                    val path = storageManager.saveChapterText(
                        bookId = bookId,
                        chapterId = chapterId,
                        text = chapterData.text
                    )
                    contentPaths.add(path)
                    // Update entity with correct path
                    chapterDao.updateContentFilePath(chapterId, path)
                }

                // 11. Update book with final info
                val finalBookEntity = initialBookEntity.copy(
                    id = bookId,
                    filePath = storedFile.absolutePath,
                    coverPath = coverPath,
                    totalChars = totalChars
                )
                bookDao.updateBook(finalBookEntity)

                // 12. Return the domain model
                val finalBook = bookMapper.toDomain(finalBookEntity)
                Result.Success(finalBook)

            } catch (e: Exception) {
                Result.Error(e)
            }
        }

    override suspend fun deleteBook(id: Long): Result<Unit> =
        withContext(dispatcher.io) {
            try {
                val book = bookDao.getBookEntityById(id)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Livro não encontrado")
                    )
                storageManager.deleteBookFiles(id)
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
