package com.epubaudioreader.domain.usecase

import com.epubaudioreader.domain.repository.ChapterRepository
import javax.inject.Inject

class SaveProgressUseCase @Inject constructor(
    private val chapterRepository: ChapterRepository
) {
    suspend operator fun invoke(bookId: Long, chapterId: Long, paragraphIndex: Int) {
        chapterRepository.saveProgress(bookId, chapterId, paragraphIndex)
    }
}
