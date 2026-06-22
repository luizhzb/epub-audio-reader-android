package com.epubaudioreader.core.data.repository

import com.epubaudioreader.core.data.local.database.dao.ChapterDao
import com.epubaudioreader.core.data.mapper.toDomain
import com.epubaudioreader.core.data.mapper.toEntity
import com.epubaudioreader.core.domain.model.Chapter
import com.epubaudioreader.core.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterRepositoryImpl @Inject constructor(
    private val chapterDao: ChapterDao
) : ChapterRepository {

    override fun getChaptersByBook(bookId: Long): Flow<List<Chapter>> =
        chapterDao.getChaptersByBook(bookId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getChapterById(chapterId: Long): Chapter? =
        chapterDao.getChapterById(chapterId)?.toDomain()

    override suspend fun insertChapters(chapters: List<Chapter>) =
        chapterDao.insertChapters(chapters.map { it.toEntity() })

    override suspend fun getChapterByOrder(bookId: Long, orderIndex: Int): Chapter? =
        chapterDao.getChapterByOrder(bookId, orderIndex)?.toDomain()

    override suspend fun getContentFilePath(chapterId: Long): String? =
        chapterDao.getContentFilePath(chapterId)

    override suspend fun deleteChaptersByBook(bookId: Long) =
        chapterDao.deleteChaptersByBook(bookId)
}
