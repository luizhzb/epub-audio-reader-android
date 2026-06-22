package com.epubaudioreader.core.domain.repository

import com.epubaudioreader.core.domain.model.Chapter
import kotlinx.coroutines.flow.Flow

interface ChapterRepository {
    fun getChaptersByBook(bookId: Long): Flow<List<Chapter>>
    suspend fun getChapterById(chapterId: Long): Chapter?
    suspend fun insertChapters(chapters: List<Chapter>)
    suspend fun getChapterByOrder(bookId: Long, orderIndex: Int): Chapter?
    suspend fun getContentFilePath(chapterId: Long): String?
    suspend fun deleteChaptersByBook(bookId: Long)
}
