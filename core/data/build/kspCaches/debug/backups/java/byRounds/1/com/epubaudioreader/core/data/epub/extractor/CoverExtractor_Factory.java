package com.epubaudioreader.core.data.epub.extractor;

import com.epubaudioreader.core.data.local.storage.EpubStorageManager;
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
public final class CoverExtractor_Factory implements Factory<CoverExtractor> {
  private final Provider<EpubStorageManager> storageManagerProvider;

  public CoverExtractor_Factory(Provider<EpubStorageManager> storageManagerProvider) {
    this.storageManagerProvider = storageManagerProvider;
  }

  @Override
  public CoverExtractor get() {
    return newInstance(storageManagerProvider.get());
  }

  public static CoverExtractor_Factory create(Provider<EpubStorageManager> storageManagerProvider) {
    return new CoverExtractor_Factory(storageManagerProvider);
  }

  public static CoverExtractor newInstance(EpubStorageManager storageManager) {
    return new CoverExtractor(storageManager);
  }
}
