package com.epubaudioreader.core.tts.di

import com.epubaudioreader.core.tts.engine.SherpaOnnxTtsEngine
import com.epubaudioreader.core.tts.engine.TtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para injeção de dependências do módulo TTS.
 * Provê a implementação [SherpaOnnxTtsEngine] para a interface [TtsEngine].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {

    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: SherpaOnnxTtsEngine): TtsEngine
}
