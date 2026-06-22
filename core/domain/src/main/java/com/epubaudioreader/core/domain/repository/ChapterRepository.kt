package com.epubaudioreader.core.domain.repository

import com.epubaudioreader.core.domain.model.Chapter
import com.epubaudioreader.core.domain.model.ChapterContent
import kotlinx.coroutines.flow.Flow

interface ChapterRepository {
    fun getChaptersByBook(bookId: Long): Flow<List<Chapter>>
    suspend fun getChapterById(chapterId: Long): Chapter?
    suspend fun getChapterContent(chapter: Chapter): ChapterContent
    suspend fun getNextChapter(bookId: Long, currentOrderIndex: Int): Chapter?
    suspend fun getPreviousChapter(bookId: Long, currentOrderIndex: Int): Chapter?
}
