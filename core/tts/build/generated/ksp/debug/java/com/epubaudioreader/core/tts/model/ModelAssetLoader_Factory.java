package com.epubaudioreader.core.tts.model;

import android.content.Context;
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider;
import com.epubaudioreader.core.tts.engine.TtsEngine;
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
public final class ModelAssetLoader_Factory implements Factory<ModelAssetLoader> {
  private final Provider<Context> contextProvider;

  private final Provider<TtsEngine> ttsEngineProvider;

  private final Provider<DispatcherProvider> dispatcherProvider;

  public ModelAssetLoader_Factory(Provider<Context> contextProvider,
      Provider<TtsEngine> ttsEngineProvider, Provider<DispatcherProvider> dispatcherProvider) {
    this.contextProvider = contextProvider;
    this.ttsEngineProvider = ttsEngineProvider;
    this.dispatcherProvider = dispatcherProvider;
  }

  @Override
  public ModelAssetLoader get() {
    return newInstance(contextProvider.get(), ttsEngineProvider.get(), dispatcherProvider.get());
  }

  public static ModelAssetLoader_Factory create(Provider<Context> contextProvider,
      Provider<TtsEngine> ttsEngineProvider, Provider<DispatcherProvider> dispatcherProvider) {
    return new ModelAssetLoader_Factory(contextProvider, ttsEngineProvider, dispatcherProvider);
  }

  public static ModelAssetLoader newInstance(Context context, TtsEngine ttsEngine,
      DispatcherProvider dispatcher) {
    return new ModelAssetLoader(context, ttsEngine, dispatcher);
  }
}
