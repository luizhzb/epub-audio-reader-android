package com.epubaudioreader.core.data.repository.mapper;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class BookMapper_Factory implements Factory<BookMapper> {
  @Override
  public BookMapper get() {
    return newInstance();
  }

  public static BookMapper_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static BookMapper newInstance() {
    return new BookMapper();
  }

  private static final class InstanceHolder {
    private static final BookMapper_Factory INSTANCE = new BookMapper_Factory();
  }
}
