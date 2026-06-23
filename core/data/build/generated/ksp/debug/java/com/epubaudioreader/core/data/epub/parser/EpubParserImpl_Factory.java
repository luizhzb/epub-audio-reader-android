package com.epubaudioreader.core.data.epub.parser;

import com.epubaudioreader.core.common.dispatcher.DispatcherProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class EpubParserImpl_Factory implements Factory<EpubParserImpl> {
  private final Provider<ContainerParser> containerParserProvider;

  private final Provider<OpfParser> opfParserProvider;

  private final Provider<NcxParser> ncxParserProvider;

  private final Provider<NavDocParser> navDocParserProvider;

  private final Provider<DispatcherProvider> dispatcherProvider;

  public EpubParserImpl_Factory(Provider<ContainerParser> containerParserProvider,
      Provider<OpfParser> opfParserProvider, Provider<NcxParser> ncxParserProvider,
      Provider<NavDocParser> navDocParserProvider,
      Provider<DispatcherProvider> dispatcherProvider) {
    this.containerParserProvider = containerParserProvider;
    this.opfParserProvider = opfParserProvider;
    this.ncxParserProvider = ncxParserProvider;
    this.navDocParserProvider = navDocParserProvider;
    this.dispatcherProvider = dispatcherProvider;
  }

  @Override
  public EpubParserImpl get() {
    return newInstance(containerParserProvider.get(), opfParserProvider.get(), ncxParserProvider.get(), navDocParserProvider.get(), dispatcherProvider.get());
  }

  public static EpubParserImpl_Factory create(Provider<ContainerParser> containerParserProvider,
      Provider<OpfParser> opfParserProvider, Provider<NcxParser> ncxParserProvider,
      Provider<NavDocParser> navDocParserProvider,
      Provider<DispatcherProvider> dispatcherProvider) {
    return new EpubParserImpl_Factory(containerParserProvider, opfParserProvider, ncxParserProvider, navDocParserProvider, dispatcherProvider);
  }

  public static EpubParserImpl newInstance(ContainerParser containerParser, OpfParser opfParser,
      NcxParser ncxParser, NavDocParser navDocParser, DispatcherProvider dispatcher) {
    return new EpubParserImpl(containerParser, opfParser, ncxParser, navDocParser, dispatcher);
  }
}
