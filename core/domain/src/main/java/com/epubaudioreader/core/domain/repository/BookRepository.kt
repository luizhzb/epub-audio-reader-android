package com.epubaudioreader.core.domain.repository

import com.epubaudioreader.core.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun getBookById(bookId: Long): Flow<Book?>
    suspend fun insertBook(book: Book): Long
    suspend fun updateLastRead(bookId: Long, chapterId: Long?, position: Int?, timestamp: Long)
    suspend fun deleteBook(book: Book)
    suspend fun getBookByIdSync(bookId: Long): Book?
    suspend fun findBookByFilePath(filePath: String): Long?
}
