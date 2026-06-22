package com.epubaudioreader.core.data.mapper

import com.epubaudioreader.core.data.local.database.entity.BookEntity
import com.epubaudioreader.core.domain.model.Book

fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    authors = authors,
    language = language,
    identifier = identifier,
    description = description,
    coverImagePath = coverImagePath,
    filePath = filePath,
    importDate = importDate,
    lastReadDate = lastReadDate,
    totalChapters = totalChapters,
    totalChars = totalChars,
    fileSize = fileSize,
    hash = hash,
    lastReadChapterId = lastReadChapterId,
    lastReadPosition = lastReadPosition
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    authors = authors,
    language = language,
    identifier = identifier,
    description = description,
    coverImagePath = coverImagePath,
    filePath = filePath,
    importDate = importDate,
    lastReadDate = lastReadDate,
    totalChapters = totalChapters,
    totalChars = totalChars,
    fileSize = fileSize,
    hash = hash,
    lastReadChapterId = lastReadChapterId,
    lastReadPosition = lastReadPosition
)
