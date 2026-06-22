package com.epubaudioreader.data.repository

import com.epubaudioreader.data.database.BookDao
import com.epubaudioreader.data.database.BookEntity
import com.epubaudioreader.domain.model.Book
import com.epubaudioreader.domain.model.ImportProgress
import com.epubaudioreader.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> = bookDao.getAllBooks().map { entities ->
        entities.map { it.toDomain() }
    }

    override fun getBookById(bookId: Long): Flow<Book?> = bookDao.getBookById(bookId).map { it?.toDomain() }

    override suspend fun importBook(uri: String): Flow<ImportProgress> = flow {
        emit(ImportProgress.Scanning)
        kotlinx.coroutines.delay(300)
        emit(ImportProgress.Parsing(50))
        kotlinx.coroutines.delay(300)
        emit(ImportProgress.Saving(100))
        val entity = BookEntity(
            title = "Livro Importado",
            author = "Autor Desconhecido",
            filePath = uri,
            totalChapters = 0
        )
        val id = bookDao.insertBook(entity)
        emit(ImportProgress.Success(id))
    }

    override suspend fun deleteBook(bookId: Long) {
        bookDao.deleteBook(bookId)
    }

    private fun BookEntity.toDomain(): Book = Book(
        id = id,
        title = title,
        author = author,
        coverImagePath = coverImagePath,
        totalChapters = totalChapters,
        filePath = filePath,
        addedDate = addedDate
    )
}
