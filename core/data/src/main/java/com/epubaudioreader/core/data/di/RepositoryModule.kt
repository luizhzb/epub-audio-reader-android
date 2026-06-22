package com.epubaudioreader.core.data.di

import com.epubaudioreader.core.data.repository.BookRepositoryImpl
import com.epubaudioreader.core.data.repository.ChapterRepositoryImpl
import com.epubaudioreader.core.domain.repository.BookRepository
import com.epubaudioreader.core.domain.repository.ChapterRepository
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
