package com.epubaudioreader.core.data.repository

import com.epubaudioreader.core.data.local.database.dao.BookDao
import com.epubaudioreader.core.data.mapper.toDomain
import com.epubaudioreader.core.data.mapper.toEntity
import com.epubaudioreader.core.domain.model.Book
import com.epubaudioreader.core.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { entities -> entities.map { it.toDomain() } }

    override fun getBookById(bookId: Long): Flow<Book?> =
        bookDao.getBookById(bookId).map { it?.toDomain() }

    override suspend fun insertBook(book: Book): Long =
        bookDao.insertBook(book.toEntity())

    override suspend fun updateLastRead(
        bookId: Long,
        chapterId: Long?,
        position: Int?,
        timestamp: Long
    ) = bookDao.updateLastRead(bookId, chapterId, position, timestamp)

    override suspend fun deleteBook(book: Book) =
        bookDao.deleteBook(book.toEntity())

    override suspend fun getBookByIdSync(bookId: Long): Book? =
        bookDao.getBookEntityById(bookId)?.toDomain()

    override suspend fun findBookByFilePath(filePath: String): Long? =
        bookDao.findBookByFilePath(filePath)
}
