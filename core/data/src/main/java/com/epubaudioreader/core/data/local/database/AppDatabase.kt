package com.epubaudioreader.core.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.epubaudioreader.core.data.local.database.dao.BookDao
import com.epubaudioreader.core.data.local.database.dao.ChapterDao
import com.epubaudioreader.core.data.local.database.entity.BookEntity
import com.epubaudioreader.core.data.local.database.entity.ChapterEntity

@Database(
    entities = [BookEntity::class, ChapterEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
}
