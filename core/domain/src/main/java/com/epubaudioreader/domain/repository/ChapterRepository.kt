package com.epubaudioreader.domain.repository

import com.epubaudioreader.domain.model.Chapter
import kotlinx.coroutines.flow.Flow

interface ChapterRepository {
    fun getChaptersForBook(bookId: Long): Flow<List<Chapter>>
    suspend fun getChapter(chapterId: Long): Chapter?
    suspend fun saveProgress(bookId: Long, chapterId: Long, paragraphIndex: Int)
}
