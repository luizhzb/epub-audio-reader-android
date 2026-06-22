package com.epubaudioreader.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    fun getChaptersByBook(bookId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :chapterId LIMIT 1")
    suspend fun getChapterById(chapterId: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND orderIndex = :orderIndex LIMIT 1")
    suspend fun getChapterByOrder(bookId: Long, orderIndex: Int): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND spineIndex = :spineIndex LIMIT 1")
    suspend fun getChapterBySpineIndex(bookId: Long, spineIndex: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChapters(chapters: List<ChapterEntity>): List<Long>

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("UPDATE chapters SET contentFilePath = :path WHERE id = :chapterId")
    suspend fun updateContentFilePath(chapterId: Long, path: String)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersByBook(bookId: Long)

    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId")
    suspend fun countChaptersByBook(bookId: Long): Int

    @Query("SELECT SUM(charCount) FROM chapters WHERE bookId = :bookId")
    suspend fun getTotalCharsByBook(bookId: Long): Long?
}
