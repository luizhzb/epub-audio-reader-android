package com.epubaudioreader.core.data.epub.extractor;

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
public final class TextExtractor_Factory implements Factory<TextExtractor> {
  @Override
  public TextExtractor get() {
    return newInstance();
  }

  public static TextExtractor_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static TextExtractor newInstance() {
    return new TextExtractor();
  }

  private static final class InstanceHolder {
    private static final TextExtractor_Factory INSTANCE = new TextExtractor_Factory();
  }
}
