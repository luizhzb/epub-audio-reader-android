package com.epubaudioreader.core.domain.repository

import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun getBookById(id: Long): Flow<Book?>
    suspend fun importBook(filePath: String, fileSize: Long): Result<Book>
    suspend fun deleteBook(id: Long): Result<Unit>
    suspend fun updateLastRead(bookId: Long, chapterId: Long?, position: Int?)
}
