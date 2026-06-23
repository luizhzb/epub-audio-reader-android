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
public final class NavDocParser_Factory implements Factory<NavDocParser> {
  @Override
  public NavDocParser get() {
    return newInstance();
  }

  public static NavDocParser_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static NavDocParser newInstance() {
    return new NavDocParser();
  }

  private static final class InstanceHolder {
    private static final NavDocParser_Factory INSTANCE = new NavDocParser_Factory();
  }
}
