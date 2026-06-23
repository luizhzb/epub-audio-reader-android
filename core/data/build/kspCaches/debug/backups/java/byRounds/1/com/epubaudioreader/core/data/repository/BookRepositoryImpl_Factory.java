package com.epubaudioreader.core.data.repository;

import com.epubaudioreader.core.common.dispatcher.DispatcherProvider;
import com.epubaudioreader.core.data.epub.extractor.ChapterExtractor;
import com.epubaudioreader.core.data.epub.extractor.CoverExtractor;
import com.epubaudioreader.core.data.epub.parser.EpubParser;
import com.epubaudioreader.core.data.local.database.AppDatabase;
import com.epubaudioreader.core.data.local.database.dao.BookDao;
import com.epubaudioreader.core.data.local.database.dao.ChapterDao;
import com.epubaudioreader.core.data.local.storage.EpubStorageManager;
import com.epubaudioreader.core.data.repository.mapper.BookMapper;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class BookRepositoryImpl_Factory implements Factory<BookRepositoryImpl> {
  private final Provider<AppDatabase> databaseProvider;

  private final Provider<BookDao> bookDaoProvider;

  private final Provider<ChapterDao> chapterDaoProvider;

  private final Provider<EpubParser> epubParserProvider;

  private final Provider<CoverExtractor> coverExtractorProvider;

  private final Provider<ChapterExtractor> chapterExtractorProvider;

  private final Provider<EpubStorageManager> storageManagerProvider;

  private final Provider<DispatcherProvider> dispatcherProvider;

  private final Provider<BookMapper> bookMapperProvider;

  public BookRepositoryImpl_Factory(Provider<AppDatabase> databaseProvider,
      Provider<BookDao> bookDaoProvider, Provider<ChapterDao> chapterDaoProvider,
      Provider<EpubParser> epubParserProvider, Provider<CoverExtractor> coverExtractorProvider,
      Provider<ChapterExtractor> chapterExtractorProvider,
      Provider<EpubStorageManager> storageManagerProvider,
      Provider<DispatcherProvider> dispatcherProvider, Provider<BookMapper> bookMapperProvider) {
    this.databaseProvider = databaseProvider;
    this.bookDaoProvider = bookDaoProvider;
    this.chapterDaoProvider = chapterDaoProvider;
    this.epubParserProvider = epubParserProvider;
    this.coverExtractorProvider = coverExtractorProvider;
    this.chapterExtractorProvider = chapterExtractorProvider;
    this.storageManagerProvider = storageManagerProvider;
    this.dispatcherProvider = dispatcherProvider;
    this.bookMapperProvider = bookMapperProvider;
  }

  @Override
  public BookRepositoryImpl get() {
    return newInstance(databaseProvider.get(), bookDaoProvider.get(), chapterDaoProvider.get(), epubParserProvider.get(), coverExtractorProvider.get(), chapterExtractorProvider.get(), storageManagerProvider.get(), dispatcherProvider.get(), bookMapperProvider.get());
  }

  public static BookRepositoryImpl_Factory create(Provider<AppDatabase> databaseProvider,
      Provider<BookDao> bookDaoProvider, Provider<ChapterDao> chapterDaoProvider,
      Provider<EpubParser> epubParserProvider, Provider<CoverExtractor> coverExtractorProvider,
      Provider<ChapterExtractor> chapterExtractorProvider,
      Provider<EpubStorageManager> storageManagerProvider,
      Provider<DispatcherProvider> dispatcherProvider, Provider<BookMapper> bookMapperProvider) {
    return new BookRepositoryImpl_Factory(databaseProvider, bookDaoProvider, chapterDaoProvider, epubParserProvider, coverExtractorProvider, chapterExtractorProvider, storageManagerProvider, dispatcherProvider, bookMapperProvider);
  }

  public static BookRepositoryImpl newInstance(AppDatabase database, BookDao bookDao,
      ChapterDao chapterDao, EpubParser epubParser, CoverExtractor coverExtractor,
      ChapterExtractor chapterExtractor, EpubStorageManager storageManager,
      DispatcherProvider dispatcher, BookMapper bookMapper) {
    return new BookRepositoryImpl(database, bookDao, chapterDao, epubParser, coverExtractor, chapterExtractor, storageManager, dispatcher, bookMapper);
  }
}
