package com.epubaudioreader.core.domain.usecase.reader

import com.epubaudioreader.core.domain.model.ChapterContent
import com.epubaudioreader.core.domain.repository.ChapterRepository
import javax.inject.Inject

class GetChapterContentUseCase @Inject constructor(private val chapterRepository: ChapterRepository) {
    suspend operator fun invoke(chapterId: Long): ChapterContent? {
        return try {
            val chapter = chapterRepository.getChapterById(chapterId) ?: return null
            chapterRepository.getChapterContent(chapter)
        } catch (e: Exception) {
            System.err.println("[GetChapterContent] Error loading content for chapter $chapterId: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
