package com.epubaudioreader.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, ChapterEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
}
