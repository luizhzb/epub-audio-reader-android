package com.epubaudioreader.core.data.repository

import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import com.epubaudioreader.core.data.local.database.dao.ChapterDao
import com.epubaudioreader.core.data.repository.mapper.ChapterMapper
import com.epubaudioreader.core.data.local.storage.EpubStorageManager
import com.epubaudioreader.core.domain.model.Chapter
import com.epubaudioreader.core.domain.model.ChapterContent
import com.epubaudioreader.core.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ChapterRepositoryImpl @Inject constructor(
    private val chapterDao: ChapterDao,
    private val storageManager: EpubStorageManager,
    private val chapterMapper: ChapterMapper,
    private val dispatcher: DispatcherProvider
) : ChapterRepository {

    override fun getChaptersByBook(bookId: Long): Flow<List<Chapter>> =
        chapterDao.getChaptersByBook(bookId).map { list ->
            list.map { chapterMapper.toDomain(it) }
        }

    override suspend fun getChapterById(chapterId: Long): Chapter? =
        chapterDao.getChapterById(chapterId)?.let { chapterMapper.toDomain(it) }

    override suspend fun getChapterContent(chapter: Chapter): ChapterContent =
        withContext(dispatcher.io) {
            val text = storageManager.readChapterText(chapter.contentFilePath)
            val paragraphs = text.split("\n\n").filter { it.isNotBlank() }
            ChapterContent(
                title = chapter.title,
                paragraphs = paragraphs,
                totalChars = text.length,
                totalParagraphs = paragraphs.size
            )
        }

    override suspend fun getNextChapter(bookId: Long, currentOrderIndex: Int): Chapter? =
        chapterDao.getChapterByOrder(bookId, currentOrderIndex + 1)?.let {
            chapterMapper.toDomain(it)
        }

    override suspend fun getPreviousChapter(bookId: Long, currentOrderIndex: Int): Chapter? =
        if (currentOrderIndex > 0) {
            chapterDao.getChapterByOrder(bookId, currentOrderIndex - 1)?.let {
                chapterMapper.toDomain(it)
            }
        } else null
}
