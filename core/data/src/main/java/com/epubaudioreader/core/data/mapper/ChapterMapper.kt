package com.epubaudioreader.core.data.mapper

import com.epubaudioreader.core.data.local.database.entity.ChapterEntity
import com.epubaudioreader.core.domain.model.Chapter

fun ChapterEntity.toDomain(): Chapter = Chapter(
    id = id,
    bookId = bookId,
    title = title,
    orderIndex = orderIndex,
    contentFilePath = contentFilePath,
    charCount = charCount,
    paragraphCount = paragraphCount,
    spineIndex = spineIndex,
    href = href
)

fun Chapter.toEntity(): ChapterEntity = ChapterEntity(
    id = id,
    bookId = bookId,
    title = title,
    orderIndex = orderIndex,
    contentFilePath = contentFilePath,
    charCount = charCount,
    paragraphCount = paragraphCount,
    spineIndex = spineIndex,
    href = href
)
