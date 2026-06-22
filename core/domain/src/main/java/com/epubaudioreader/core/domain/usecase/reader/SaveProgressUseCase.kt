package com.epubaudioreader.core.domain.usecase.reader

import com.epubaudioreader.core.domain.repository.BookRepository
import javax.inject.Inject

class SaveProgressUseCase @Inject constructor(private val bookRepository: BookRepository) {
    suspend operator fun invoke(bookId: Long, chapterId: Long, position: Int) {
        bookRepository.updateLastRead(bookId, chapterId, position)
    }
}
