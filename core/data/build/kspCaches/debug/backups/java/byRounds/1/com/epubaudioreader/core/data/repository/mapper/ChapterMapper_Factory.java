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
public final class ChapterMapper_Factory implements Factory<ChapterMapper> {
  @Override
  public ChapterMapper get() {
    return newInstance();
  }

  public static ChapterMapper_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ChapterMapper newInstance() {
    return new ChapterMapper();
  }

  private static final class InstanceHolder {
    private static final ChapterMapper_Factory INSTANCE = new ChapterMapper_Factory();
  }
}
