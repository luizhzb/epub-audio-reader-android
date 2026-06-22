package com.epubaudioreader.domain.usecase

import com.epubaudioreader.domain.model.ImportProgress
import com.epubaudioreader.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ImportBookUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(uri: String): Flow<ImportProgress> = bookRepository.importBook(uri)
}
