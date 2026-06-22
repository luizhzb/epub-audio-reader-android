package com.epubaudioreader.core.data.repository.mapper

import com.epubaudioreader.core.data.database.ChapterEntity
import com.epubaudioreader.core.domain.model.Chapter
import javax.inject.Inject

class ChapterMapper @Inject constructor() {

    fun toDomain(entity: ChapterEntity): Chapter = Chapter(
        id = entity.id,
        bookId = entity.bookId,
        title = entity.title,
        orderIndex = entity.orderIndex,
        contentFilePath = entity.contentFilePath,
        charCount = entity.charCount,
        paragraphCount = entity.paragraphCount,
        spineIndex = entity.spineIndex,
        href = entity.href
    )

    fun toEntity(domain: Chapter): ChapterEntity = ChapterEntity(
        id = if (domain.id == 0L) 0 else domain.id,
        bookId = domain.bookId,
        title = domain.title,
        orderIndex = domain.orderIndex,
        contentFilePath = domain.contentFilePath,
        charCount = domain.charCount,
        paragraphCount = domain.paragraphCount,
        spineIndex = domain.spineIndex,
        href = domain.href
    )
}
