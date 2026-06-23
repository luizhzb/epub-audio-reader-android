package com.epubaudioreader.core.tts.di

import com.epubaudioreader.core.tts.engine.SherpaOnnxTtsEngine
import com.epubaudioreader.core.tts.engine.TtsEngine
import com.epubaudioreader.core.tts.player.EpubTtsPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: SherpaOnnxTtsEngine): TtsEngine
    // EpubTtsPlayer uses @Inject constructor, no binding needed
}
