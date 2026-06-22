package com.epubaudioreader.domain.usecase

import com.epubaudioreader.domain.repository.BookRepository
import javax.inject.Inject

class DeleteBookUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(bookId: Long) = bookRepository.deleteBook(bookId)
}
