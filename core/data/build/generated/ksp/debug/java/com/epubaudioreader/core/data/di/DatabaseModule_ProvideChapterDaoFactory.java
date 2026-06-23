package com.epubaudioreader.core.data.di;

import com.epubaudioreader.core.data.local.database.AppDatabase;
import com.epubaudioreader.core.data.local.database.dao.ChapterDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideChapterDaoFactory implements Factory<ChapterDao> {
  private final Provider<AppDatabase> databaseProvider;

  public DatabaseModule_ProvideChapterDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ChapterDao get() {
    return provideChapterDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideChapterDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new DatabaseModule_ProvideChapterDaoFactory(databaseProvider);
  }

  public static ChapterDao provideChapterDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideChapterDao(database));
  }
}
