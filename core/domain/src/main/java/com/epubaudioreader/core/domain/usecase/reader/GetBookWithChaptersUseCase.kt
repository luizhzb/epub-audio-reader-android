package com.epubaudioreader.core.domain.usecase.reader

import com.epubaudioreader.core.domain.model.Book
import com.epubaudioreader.core.domain.model.Chapter
import com.epubaudioreader.core.domain.repository.BookRepository
import com.epubaudioreader.core.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetBookWithChaptersUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository
) {
    operator fun invoke(bookId: Long): Flow<Pair<Book?, List<Chapter>>> = combine(
        bookRepository.getBookById(bookId),
        chapterRepository.getChaptersByBook(bookId)
    ) { book, chapters -> book to chapters }
}
