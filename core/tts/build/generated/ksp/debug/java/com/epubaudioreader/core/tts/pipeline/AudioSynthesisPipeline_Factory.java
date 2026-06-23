package com.epubaudioreader.core.tts.pipeline;

import com.epubaudioreader.core.common.dispatcher.DispatcherProvider;
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer;
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
public final class AudioSynthesisPipeline_Factory implements Factory<AudioSynthesisPipeline> {
  private final Provider<TtsSynthesizer> synthesizerProvider;

  private final Provider<DispatcherProvider> dispatcherProvider;

  public AudioSynthesisPipeline_Factory(Provider<TtsSynthesizer> synthesizerProvider,
      Provider<DispatcherProvider> dispatcherProvider) {
    this.synthesizerProvider = synthesizerProvider;
    this.dispatcherProvider = dispatcherProvider;
  }

  @Override
  public AudioSynthesisPipeline get() {
    return newInstance(synthesizerProvider.get(), dispatcherProvider.get());
  }

  public static AudioSynthesisPipeline_Factory create(Provider<TtsSynthesizer> synthesizerProvider,
      Provider<DispatcherProvider> dispatcherProvider) {
    return new AudioSynthesisPipeline_Factory(synthesizerProvider, dispatcherProvider);
  }

  public static AudioSynthesisPipeline newInstance(TtsSynthesizer synthesizer,
      DispatcherProvider dispatcher) {
    return new AudioSynthesisPipeline(synthesizer, dispatcher);
  }
}
