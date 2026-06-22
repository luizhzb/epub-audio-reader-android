package com.epubaudioreader.core.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.epubaudioreader.core.data.local.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastReadDate, importDate) DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun getBookById(bookId: Long): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: BookEntity): Long

    @Query(
        "UPDATE books SET lastReadDate = :timestamp, lastReadChapterId = :chapterId, " +
            "lastReadPosition = :position WHERE id = :bookId"
    )
    suspend fun updateLastRead(bookId: Long, chapterId: Long?, position: Int?, timestamp: Long)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookEntityById(bookId: Long): BookEntity?

    @Query("SELECT id FROM books WHERE filePath = :filePath LIMIT 1")
    suspend fun findBookByFilePath(filePath: String): Long?

    @Query("SELECT id FROM books WHERE hash = :hash LIMIT 1")
    suspend fun findBookByHash(hash: String): Long?

    @Update
    suspend fun updateBook(book: BookEntity)
}
