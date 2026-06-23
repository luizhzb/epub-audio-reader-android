package com.epubaudioreader.core.tts.segmentation;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class SmartTextSegmenter_Factory implements Factory<SmartTextSegmenter> {
  @Override
  public SmartTextSegmenter get() {
    return newInstance();
  }

  public static SmartTextSegmenter_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SmartTextSegmenter newInstance() {
    return new SmartTextSegmenter();
  }

  private static final class InstanceHolder {
    private static final SmartTextSegmenter_Factory INSTANCE = new SmartTextSegmenter_Factory();
  }
}
