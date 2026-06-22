package com.epubaudioreader.core.domain.usecase.reader

import com.epubaudioreader.core.domain.model.ChapterContent
import com.epubaudioreader.core.domain.repository.ChapterRepository
import javax.inject.Inject

class GetChapterContentUseCase @Inject constructor(private val chapterRepository: ChapterRepository) {
    suspend operator fun invoke(chapterId: Long): ChapterContent? {
        val chapter = chapterRepository.getChapterById(chapterId) ?: return null
        return chapterRepository.getChapterContent(chapter)
    }
}
