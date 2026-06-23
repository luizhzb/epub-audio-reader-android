package com.epubaudioreader.core.data.repository.mapper

import com.epubaudioreader.core.data.local.database.entity.BookEntity
import com.epubaudioreader.core.domain.model.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class BookMapperTest {

    private val mapper = BookMapper()

    @Test
    fun `toDomain maps all fields correctly`() {
        val entity = BookEntity(
            id = 1L,
            title = "Dom Casmurro",
            authors = "Machado de Assis",
            language = "pt-BR",
            identifier = "id123",
            description = "Um classico",
            coverImagePath = "/covers/1.jpg",
            filePath = "/books/1/book.epub",
            importDate = 1000L,
            lastReadDate = 2000L,
            totalChapters = 10,
            totalChars = 50000L,
            fileSize = 1024L,
            hash = "abc",
            lastReadChapterId = 5L,
            lastReadPosition = 42
        )

        val domain = mapper.toDomain(entity)

        assertEquals(entity.id, domain.id)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.authors, domain.authors)
        assertEquals(entity.language, domain.language)
        assertEquals(entity.identifier, domain.identifier)
        assertEquals(entity.description, domain.description)
        assertEquals(entity.coverImagePath, domain.coverImagePath)
        assertEquals(entity.filePath, domain.filePath)
        assertEquals(entity.importDate, domain.importDate)
        assertEquals(entity.lastReadDate, domain.lastReadDate)
        assertEquals(entity.totalChapters, domain.totalChapters)
        assertEquals(entity.totalChars, domain.totalChars)
        assertEquals(entity.fileSize, domain.fileSize)
        assertEquals(entity.hash, domain.hash)
        assertEquals(entity.lastReadChapterId, domain.lastReadChapterId)
        assertEquals(entity.lastReadPosition, domain.lastReadPosition)
    }

    @Test
    fun `toEntity maps all fields correctly and resets id when zero`() {
        val domain = Book(
            id = 0L,
            title = "Memorias Postumas",
            authors = "Machado de Assis",
            filePath = "/books/2/book.epub",
            totalChapters = 5,
            totalChars = 30000L,
            fileSize = 512L,
            hash = "def"
        )

        val entity = mapper.toEntity(domain)

        assertEquals(0L, entity.id)
        assertEquals(domain.title, entity.title)
        assertEquals(domain.authors, entity.authors)
        assertEquals(domain.filePath, entity.filePath)
        assertEquals(domain.totalChapters, entity.totalChapters)
        assertEquals(domain.totalChars, entity.totalChars)
        assertEquals(domain.fileSize, entity.fileSize)
        assertEquals(domain.hash, entity.hash)
    }

    @Test
    fun `toEntity preserves non-zero id`() {
        val domain = Book(
            id = 7L,
            title = "Iracema",
            authors = "Jose de Alencar",
            filePath = "/books/3/book.epub",
            totalChapters = 1,
            totalChars = 100L,
            fileSize = 10L,
            hash = "ghi"
        )

        val entity = mapper.toEntity(domain)

        assertEquals(7L, entity.id)
    }
}
