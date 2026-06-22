package com.epubaudioreader.core.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookEntityById(id: Long): BookEntity?

    @Query("SELECT id FROM books WHERE filePath = :filePath LIMIT 1")
    suspend fun findBookByFilePath(filePath: String): Long?

    @Query("SELECT id FROM books WHERE `hash` = :hash LIMIT 1")
    suspend fun findBookByHash(hash: String): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET coverPath = :coverPath WHERE id = :bookId")
    suspend fun updateCoverPath(bookId: Long, coverPath: String)

    @Query("UPDATE books SET lastReadChapterId = :chapterId, lastReadPosition = :position, lastReadAt = :timestamp WHERE id = :bookId")
    suspend fun updateLastRead(bookId: Long, chapterId: Long?, position: Int?, timestamp: Long)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Transaction
    suspend fun insertBookWithChapters(book: BookEntity, chapters: List<ChapterEntity>): Pair<Long, List<Long>> {
        val bookId = insertBook(book)
        val chapterIds = mutableListOf<Long>()
        for (chapter in chapters) {
            val id = insertChapter(chapter.copy(bookId = bookId))
            chapterIds.add(id)
        }
        return bookId to chapterIds
    }

    // Delegated to ChapterDao via Room's @Dao aggregation
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChapters(chapters: List<ChapterEntity>): List<Long>
}
