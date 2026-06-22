package com.epubaudioreader.domain.repository

import com.epubaudioreader.domain.model.Book
import com.epubaudioreader.domain.model.ImportProgress
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun importBook(uri: String): Flow<ImportProgress>
    suspend fun deleteBook(bookId: Long)
    fun getBookById(bookId: Long): Flow<Book?>
}
