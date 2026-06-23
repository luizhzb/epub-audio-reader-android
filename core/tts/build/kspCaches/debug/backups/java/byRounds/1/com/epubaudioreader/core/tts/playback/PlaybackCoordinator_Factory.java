package com.epubaudioreader.core.tts.playback;

import com.epubaudioreader.core.tts.pipeline.AudioSynthesisPipeline;
import com.epubaudioreader.core.tts.segmentation.SmartTextSegmenter;
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
public final class PlaybackCoordinator_Factory implements Factory<PlaybackCoordinator> {
  private final Provider<SmartTextSegmenter> segmenterProvider;

  private final Provider<TtsSynthesizer> synthesizerProvider;

  private final Provider<AudioSynthesisPipeline> pipelineProvider;

  public PlaybackCoordinator_Factory(Provider<SmartTextSegmenter> segmenterProvider,
      Provider<TtsSynthesizer> synthesizerProvider,
      Provider<AudioSynthesisPipeline> pipelineProvider) {
    this.segmenterProvider = segmenterProvider;
    this.synthesizerProvider = synthesizerProvider;
    this.pipelineProvider = pipelineProvider;
  }

  @Override
  public PlaybackCoordinator get() {
    return newInstance(segmenterProvider.get(), synthesizerProvider.get(), pipelineProvider.get());
  }

  public static PlaybackCoordinator_Factory create(Provider<SmartTextSegmenter> segmenterProvider,
      Provider<TtsSynthesizer> synthesizerProvider,
      Provider<AudioSynthesisPipeline> pipelineProvider) {
    return new PlaybackCoordinator_Factory(segmenterProvider, synthesizerProvider, pipelineProvider);
  }

  public static PlaybackCoordinator newInstance(SmartTextSegmenter segmenter,
      TtsSynthesizer synthesizer, AudioSynthesisPipeline pipeline) {
    return new PlaybackCoordinator(segmenter, synthesizer, pipeline);
  }
}
