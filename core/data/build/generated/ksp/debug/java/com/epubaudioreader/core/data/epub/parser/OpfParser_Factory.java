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
public final class OpfParser_Factory implements Factory<OpfParser> {
  @Override
  public OpfParser get() {
    return newInstance();
  }

  public static OpfParser_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static OpfParser newInstance() {
    return new OpfParser();
  }

  private static final class InstanceHolder {
    private static final OpfParser_Factory INSTANCE = new OpfParser_Factory();
  }
}
