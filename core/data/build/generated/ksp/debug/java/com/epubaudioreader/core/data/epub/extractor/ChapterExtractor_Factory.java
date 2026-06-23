package com.epubaudioreader.core.data.epub.extractor;

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
public final class ChapterExtractor_Factory implements Factory<ChapterExtractor> {
  private final Provider<TextExtractor> textExtractorProvider;

  public ChapterExtractor_Factory(Provider<TextExtractor> textExtractorProvider) {
    this.textExtractorProvider = textExtractorProvider;
  }

  @Override
  public ChapterExtractor get() {
    return newInstance(textExtractorProvider.get());
  }

  public static ChapterExtractor_Factory create(Provider<TextExtractor> textExtractorProvider) {
    return new ChapterExtractor_Factory(textExtractorProvider);
  }

  public static ChapterExtractor newInstance(TextExtractor textExtractor) {
    return new ChapterExtractor(textExtractor);
  }
}
