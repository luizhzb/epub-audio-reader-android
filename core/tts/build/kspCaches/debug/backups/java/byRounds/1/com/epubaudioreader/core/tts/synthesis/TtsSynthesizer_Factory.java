package com.epubaudioreader.core.tts.synthesis;

import com.epubaudioreader.core.tts.engine.TtsEngine;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class TtsSynthesizer_Factory implements Factory<TtsSynthesizer> {
  private final Provider<TtsEngine> ttsEngineProvider;

  public TtsSynthesizer_Factory(Provider<TtsEngine> ttsEngineProvider) {
    this.ttsEngineProvider = ttsEngineProvider;
  }

  @Override
  public TtsSynthesizer get() {
    return newInstance(ttsEngineProvider.get());
  }

  public static TtsSynthesizer_Factory create(Provider<TtsEngine> ttsEngineProvider) {
    return new TtsSynthesizer_Factory(ttsEngineProvider);
  }

  public static TtsSynthesizer newInstance(TtsEngine ttsEngine) {
    return new TtsSynthesizer(ttsEngine);
  }
}
