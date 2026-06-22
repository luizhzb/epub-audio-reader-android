package com.epubaudioreader.domain.usecase

import com.epubaudioreader.domain.model.Book
import com.epubaudioreader.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBooksUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    operator fun invoke(): Flow<List<Book>> = bookRepository.getAllBooks()
}
