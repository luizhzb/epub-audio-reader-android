package com.epubaudioreader.core.domain.usecase.library

import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.domain.model.Book
import com.epubaudioreader.core.domain.repository.BookRepository
import javax.inject.Inject

class ImportBookUseCase @Inject constructor(private val bookRepository: BookRepository) {
    suspend operator fun invoke(filePath: String, fileSize: Long): Result<Book> {
        return bookRepository.importBook(filePath, fileSize)
    }
}
