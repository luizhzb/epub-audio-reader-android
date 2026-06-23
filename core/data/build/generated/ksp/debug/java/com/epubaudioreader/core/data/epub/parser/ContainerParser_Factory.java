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
public final class ContainerParser_Factory implements Factory<ContainerParser> {
  @Override
  public ContainerParser get() {
    return newInstance();
  }

  public static ContainerParser_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ContainerParser newInstance() {
    return new ContainerParser();
  }

  private static final class InstanceHolder {
    private static final ContainerParser_Factory INSTANCE = new ContainerParser_Factory();
  }
}
