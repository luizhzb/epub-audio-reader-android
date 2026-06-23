package com.epubaudioreader.core.data.repository;

import com.epubaudioreader.core.common.dispatcher.DispatcherProvider;
import com.epubaudioreader.core.data.local.database.dao.ChapterDao;
import com.epubaudioreader.core.data.local.storage.EpubStorageManager;
import com.epubaudioreader.core.data.repository.mapper.ChapterMapper;
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
public final class ChapterRepositoryImpl_Factory implements Factory<ChapterRepositoryImpl> {
  private final Provider<ChapterDao> chapterDaoProvider;

  private final Provider<EpubStorageManager> storageManagerProvider;

  private final Provider<ChapterMapper> chapterMapperProvider;

  private final Provider<DispatcherProvider> dispatcherProvider;

  public ChapterRepositoryImpl_Factory(Provider<ChapterDao> chapterDaoProvider,
      Provider<EpubStorageManager> storageManagerProvider,
      Provider<ChapterMapper> chapterMapperProvider,
      Provider<DispatcherProvider> dispatcherProvider) {
    this.chapterDaoProvider = chapterDaoProvider;
    this.storageManagerProvider = storageManagerProvider;
    this.chapterMapperProvider = chapterMapperProvider;
    this.dispatcherProvider = dispatcherProvider;
  }

  @Override
  public ChapterRepositoryImpl get() {
    return newInstance(chapterDaoProvider.get(), storageManagerProvider.get(), chapterMapperProvider.get(), dispatcherProvider.get());
  }

  public static ChapterRepositoryImpl_Factory create(Provider<ChapterDao> chapterDaoProvider,
      Provider<EpubStorageManager> storageManagerProvider,
      Provider<ChapterMapper> chapterMapperProvider,
      Provider<DispatcherProvider> dispatcherProvider) {
    return new ChapterRepositoryImpl_Factory(chapterDaoProvider, storageManagerProvider, chapterMapperProvider, dispatcherProvider);
  }

  public static ChapterRepositoryImpl newInstance(ChapterDao chapterDao,
      EpubStorageManager storageManager, ChapterMapper chapterMapper,
      DispatcherProvider dispatcher) {
    return new ChapterRepositoryImpl(chapterDao, storageManager, chapterMapper, dispatcher);
  }
}
