package com.epubaudioreader.core.data.di;

import com.epubaudioreader.core.data.local.database.AppDatabase;
import com.epubaudioreader.core.data.local.database.dao.BookDao;
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
public final class DatabaseModule_ProvideBookDaoFactory implements Factory<BookDao> {
  private final Provider<AppDatabase> databaseProvider;

  public DatabaseModule_ProvideBookDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public BookDao get() {
    return provideBookDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideBookDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new DatabaseModule_ProvideBookDaoFactory(databaseProvider);
  }

  public static BookDao provideBookDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideBookDao(database));
  }
}
