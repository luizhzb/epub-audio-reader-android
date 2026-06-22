package com.epubaudioreader.core.domain.usecase.library

import com.epubaudioreader.core.domain.model.Book
import com.epubaudioreader.core.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBooksUseCase @Inject constructor(private val bookRepository: BookRepository) {
    operator fun invoke(): Flow<List<Book>> = bookRepository.getAllBooks()
}
