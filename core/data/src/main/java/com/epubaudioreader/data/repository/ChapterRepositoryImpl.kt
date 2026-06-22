package com.epubaudioreader.data.repository

import com.epubaudioreader.data.database.ChapterDao
import com.epubaudioreader.data.database.ChapterEntity
import com.epubaudioreader.domain.model.Chapter
import com.epubaudioreader.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChapterRepositoryImpl @Inject constructor(
    private val chapterDao: ChapterDao
) : ChapterRepository {

    override fun getChaptersForBook(bookId: Long): Flow<List<Chapter>> =
        chapterDao.getChaptersForBook(bookId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getChapter(chapterId: Long): Chapter? =
        chapterDao.getChapterById(chapterId)?.toDomain()

    override suspend fun saveProgress(bookId: Long, chapterId: Long, paragraphIndex: Int) {
        chapterDao.updateParagraphIndex(chapterId, paragraphIndex)
    }

    private fun ChapterEntity.toDomain(): Chapter = Chapter(
        id = id,
        bookId = bookId,
        title = title,
        orderIndex = orderIndex,
        contentHtml = contentHtml,
        paragraphCount = paragraphCount
    )
}
