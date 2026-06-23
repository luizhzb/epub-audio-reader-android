package com.epubaudioreader.core.data.local.storage;

import android.content.Context;
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class EpubStorageManager_Factory implements Factory<EpubStorageManager> {
  private final Provider<Context> contextProvider;

  private final Provider<DispatcherProvider> dispatcherProvider;

  public EpubStorageManager_Factory(Provider<Context> contextProvider,
      Provider<DispatcherProvider> dispatcherProvider) {
    this.contextProvider = contextProvider;
    this.dispatcherProvider = dispatcherProvider;
  }

  @Override
  public EpubStorageManager get() {
    return newInstance(contextProvider.get(), dispatcherProvider.get());
  }

  public static EpubStorageManager_Factory create(Provider<Context> contextProvider,
      Provider<DispatcherProvider> dispatcherProvider) {
    return new EpubStorageManager_Factory(contextProvider, dispatcherProvider);
  }

  public static EpubStorageManager newInstance(Context context, DispatcherProvider dispatcher) {
    return new EpubStorageManager(context, dispatcher);
  }
}
