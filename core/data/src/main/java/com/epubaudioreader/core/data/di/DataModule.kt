package com.epubaudioreader.core.data.di

import android.content.Context
import androidx.room.Room
import com.epubaudioreader.core.common.DefaultDispatcherProvider
import com.epubaudioreader.core.common.DispatcherProvider
import com.epubaudioreader.core.data.database.AppDatabase
import com.epubaudioreader.core.data.database.BookDao
import com.epubaudioreader.core.data.database.ChapterDao
import com.epubaudioreader.core.data.epub.parser.ContainerParser
import com.epubaudioreader.core.data.epub.parser.EpubParser
import com.epubaudioreader.core.data.epub.parser.EpubParserImpl
import com.epubaudioreader.core.data.epub.parser.NcxParser
import com.epubaudioreader.core.data.epub.parser.NavDocParser
import com.epubaudioreader.core.data.epub.parser.OpfParser
import com.epubaudioreader.core.data.repository.BookRepositoryImpl
import com.epubaudioreader.core.data.repository.ChapterRepositoryImpl
import com.epubaudioreader.core.domain.repository.BookRepository
import com.epubaudioreader.core.domain.repository.ChapterRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModuleBinds {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindChapterRepository(impl: ChapterRepositoryImpl): ChapterRepository

    @Binds
    @Singleton
    abstract fun bindEpubParser(impl: EpubParserImpl): EpubParser

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}

@Module
@InstallIn(SingletonComponent::class)
object DataModuleProviders {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "epub_reader.db"
        ).build()
    }

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao = database.bookDao()

    @Provides
    fun provideChapterDao(database: AppDatabase): ChapterDao = database.chapterDao()

    @Provides
    fun provideContainerParser(): ContainerParser = ContainerParser()

    @Provides
    fun provideOpfParser(): OpfParser = OpfParser()

    @Provides
    fun provideNcxParser(): NcxParser = NcxParser()

    @Provides
    fun provideNavDocParser(): NavDocParser = NavDocParser()
}
