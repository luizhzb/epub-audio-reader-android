package com.epubaudioreader.core.data.repository.mapper

import com.epubaudioreader.core.data.local.database.entity.BookEntity
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
        coverImagePath = entity.coverImagePath,
        filePath = entity.filePath,
        importDate = entity.importDate,
        lastReadDate = entity.lastReadDate,
        totalChapters = entity.totalChapters,
        totalChars = entity.totalChars,
        fileSize = entity.fileSize,
        hash = entity.hash,
        lastReadChapterId = entity.lastReadChapterId,
        lastReadPosition = entity.lastReadPosition
    )

    /**
     * Converts domain model to entity.
     * Preserves the original importDate to prevent accidental overwrites during updates.
     */
    fun toEntity(domain: Book): BookEntity = BookEntity(
        id = domain.id,
        title = domain.title,
        authors = domain.authors,
        language = domain.language,
        identifier = domain.identifier,
        description = domain.description,
        coverImagePath = domain.coverImagePath,
        filePath = domain.filePath,
        importDate = domain.importDate,
        lastReadDate = domain.lastReadDate,
        totalChapters = domain.totalChapters,
        totalChars = domain.totalChars,
        fileSize = domain.fileSize,
        hash = domain.hash,
        lastReadChapterId = domain.lastReadChapterId,
        lastReadPosition = domain.lastReadPosition
    )
}
