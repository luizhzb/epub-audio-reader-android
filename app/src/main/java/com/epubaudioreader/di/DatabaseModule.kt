package com.epubaudioreader.di

import android.content.Context
import androidx.room.Room
import com.epubaudioreader.data.database.AppDatabase
import com.epubaudioreader.data.database.BookDao
import com.epubaudioreader.data.database.ChapterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "epub_reader.db")
            .build()
    }

    @Provides
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()

    @Provides
    fun provideChapterDao(db: AppDatabase): ChapterDao = db.chapterDao()
}
