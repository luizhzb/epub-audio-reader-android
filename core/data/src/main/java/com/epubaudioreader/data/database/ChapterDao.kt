package com.epubaudioreader.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    fun getChaptersForBook(bookId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getChapterById(chapterId: Long): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Query("UPDATE chapters SET paragraphCount = :paragraphIndex WHERE id = :chapterId")
    suspend fun updateParagraphIndex(chapterId: Long, paragraphIndex: Int)
}
