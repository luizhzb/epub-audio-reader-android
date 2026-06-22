package com.epubaudioreader.core.domain.usecase.library

import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.domain.repository.BookRepository
import javax.inject.Inject

class DeleteBookUseCase @Inject constructor(private val bookRepository: BookRepository) {
    suspend operator fun invoke(bookId: Long): Result<Unit> {
        return bookRepository.deleteBook(bookId)
    }
}
