package com.epubaudioreader.core.data.epub.parser;

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
public final class NcxParser_Factory implements Factory<NcxParser> {
  @Override
  public NcxParser get() {
    return newInstance();
  }

  public static NcxParser_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static NcxParser newInstance() {
    return new NcxParser();
  }

  private static final class InstanceHolder {
    private static final NcxParser_Factory INSTANCE = new NcxParser_Factory();
  }
}
