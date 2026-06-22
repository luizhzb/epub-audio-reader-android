package com.epubaudioreader.core.data.repository.mapper

import com.epubaudioreader.core.data.database.BookEntity
import com.epubaudioreader.core.domain.model.Book
import javax.inject.Inject

class BookMapper @Inject constructor() {

    fun toDomain(entity: BookEntity): Book = Book(
        id = entity.id,
        title = entity.title,
        authors = entity.authors,
        language = entity.language,
        identifier = entity.identifier,
        description = entity.description,
        coverPath = entity.coverPath,
        filePath = entity.filePath,
        totalChapters = entity.totalChapters,
        totalChars = entity.totalChars,
        fileSize = entity.fileSize,
        hash = entity.hash,
        lastReadChapterId = entity.lastReadChapterId,
        lastReadPosition = entity.lastReadPosition,
        lastReadAt = entity.lastReadAt,
        addedAt = entity.addedAt
    )

    fun toEntity(domain: Book): BookEntity = BookEntity(
        id = if (domain.id == 0L) 0 else domain.id,
        title = domain.title,
        authors = domain.authors,
        language = domain.language,
        identifier = domain.identifier,
        description = domain.description,
        coverPath = domain.coverPath,
        filePath = domain.filePath,
        totalChapters = domain.totalChapters,
        totalChars = domain.totalChars,
        fileSize = domain.fileSize,
        hash = domain.hash,
        lastReadChapterId = domain.lastReadChapterId,
        lastReadPosition = domain.lastReadPosition,
        lastReadAt = domain.lastReadAt,
        addedAt = domain.addedAt
    )
}
