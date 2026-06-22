package com.epubaudioreader.di

import com.epubaudioreader.data.repository.BookRepositoryImpl
import com.epubaudioreader.data.repository.ChapterRepositoryImpl
import com.epubaudioreader.domain.repository.BookRepository
import com.epubaudioreader.domain.repository.ChapterRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    abstract fun bindChapterRepository(impl: ChapterRepositoryImpl): ChapterRepository
}
