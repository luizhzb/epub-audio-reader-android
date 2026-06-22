package com.epubaudioreader.domain.usecase

import com.epubaudioreader.domain.model.BookWithChapters
import com.epubaudioreader.domain.repository.BookRepository
import com.epubaudioreader.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetBookWithChaptersUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository
) {
    operator fun invoke(bookId: Long): Flow<BookWithChapters> = combine(
        bookRepository.getBookById(bookId),
        chapterRepository.getChaptersForBook(bookId)
    ) { book, chapters ->
        BookWithChapters(book = book ?: throw IllegalStateException("Book not found"), chapters = chapters)
    }
}
