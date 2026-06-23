package com.epubaudioreader.core.data.repository.mapper

import com.epubaudioreader.core.data.local.database.entity.ChapterEntity
import com.epubaudioreader.core.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterMapperTest {

    private val mapper = ChapterMapper()

    @Test
    fun `toDomain maps all fields correctly`() {
        val entity = ChapterEntity(
            id = 1L,
            bookId = 2L,
            title = "Capitulo 1",
            orderIndex = 0,
            contentFilePath = "/chapters/2/1.txt",
            charCount = 1000,
            paragraphCount = 5,
            spineIndex = 0,
            href = "chapter1.xhtml"
        )

        val domain = mapper.toDomain(entity)

        assertEquals(entity.id, domain.id)
        assertEquals(entity.bookId, domain.bookId)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.orderIndex, domain.orderIndex)
        assertEquals(entity.contentFilePath, domain.contentFilePath)
        assertEquals(entity.charCount, domain.charCount)
        assertEquals(entity.paragraphCount, domain.paragraphCount)
        assertEquals(entity.spineIndex, domain.spineIndex)
        assertEquals(entity.href, domain.href)
    }

    @Test
    fun `toEntity resets id when zero`() {
        val domain = Chapter(
            id = 0L,
            bookId = 2L,
            title = "Capitulo 2",
            orderIndex = 1,
            contentFilePath = "/chapters/2/2.txt",
            charCount = 2000,
            paragraphCount = 10,
            spineIndex = 1,
            href = "chapter2.xhtml"
        )

        val entity = mapper.toEntity(domain)

        assertEquals(0L, entity.id)
        assertEquals(domain.bookId, entity.bookId)
        assertEquals(domain.title, entity.title)
    }

    @Test
    fun `toEntity preserves non-zero id`() {
        val domain = Chapter(
            id = 9L,
            bookId = 2L,
            title = "Capitulo 3",
            orderIndex = 2,
            contentFilePath = "/chapters/2/3.txt",
            charCount = 3000,
            paragraphCount = 15,
            spineIndex = 2,
            href = "chapter3.xhtml"
        )

        val entity = mapper.toEntity(domain)

        assertEquals(9L, entity.id)
    }
}
