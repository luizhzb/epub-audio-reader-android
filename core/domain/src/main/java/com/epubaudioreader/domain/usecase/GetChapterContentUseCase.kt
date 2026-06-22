package com.epubaudioreader.domain.usecase

import com.epubaudioreader.domain.repository.ChapterRepository
import javax.inject.Inject

class GetChapterContentUseCase @Inject constructor(
    private val chapterRepository: ChapterRepository
) {
    suspend operator fun invoke(chapterId: Long): com.epubaudioreader.domain.model.Chapter? {
        return chapterRepository.getChapter(chapterId)
    }
}
