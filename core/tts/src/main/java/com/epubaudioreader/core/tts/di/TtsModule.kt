package com.epubaudioreader.core.tts.di

import com.epubaudioreader.core.tts.engine.SherpaOnnxTtsEngine
import com.epubaudioreader.core.tts.engine.TtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Modulo de DI para o componente TTS.
 *
 * ## Estrategia de injecao:
 * A maioria das classes TTS usa `@Inject constructor` e eh concreta,
 * entao o Hilt gera as fabricas automaticamente (nenhum `@Binds` necessario).
 *
 * O unico `@Binds` obrigatorio eh para a interface [TtsEngine], pois
 * varios componentes dependem da ABSTRACAO e nao da IMPLEMENTACAO:
 *
 * - [TtsSynthesizer] injeta `TtsEngine` (interface)
 * - [ModelAssetLoader] injeta `TtsEngine` (interface)
 * - [EpubTtsPlayer] injeta `TtsEngine` (interface)
 *
 * ## Classes com @Inject constructor (Hilt gera automaticamente):
 * | Classe                | Escopo     | Dependencias injetadas |
 * |-----------------------|------------|------------------------|
 * | SmartTextSegmenter    | @Singleton | nenhuma |
 * | AudioSynthesisPipeline| @Singleton | TtsSynthesizer |
 * | PlaybackCoordinator   | @Singleton | SmartTextSegmenter, AudioSynthesisPipeline, TtsSynthesizer |
 * | TtsSynthesizer        | @Singleton | TtsEngine (interface -> SherpaOnnxTtsEngine) |
 * | ModelAssetLoader      | @Singleton | @ApplicationContext, TtsEngine (interface) |
 * | SherpaOnnxTtsEngine   | @Singleton | @ApplicationContext |
 * | EpubTtsPlayer         | @Singleton | ModelAssetLoader, TtsEngine, TtsSynthesizer |
 *
 * ## Grafo de dependencias (sem ciclos):
 * ```
 * ReaderViewModel -> PlaybackCoordinator
 *   -> SmartTextSegmenter
 *   -> AudioSynthesisPipeline -> TtsSynthesizer -> TtsEngine <- SherpaOnnxTtsEngine
 *   -> TtsSynthesizer -> TtsEngine <- SherpaOnnxTtsEngine
 * ModelAssetLoader -> TtsEngine <- SherpaOnnxTtsEngine
 * EpubTtsPlayer -> ModelAssetLoader -> TtsEngine <- SherpaOnnxTtsEngine
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {

    /**
     * Bind obrigatorio: TtsEngine (interface) -> SherpaOnnxTtsEngine (impl).
     *
     * Sem este bind, Hilt nao consegue resolver `TtsEngine` quando ele eh
     * injetado por outras classes (TtsSynthesizer, ModelAssetLoader, EpubTtsPlayer).
     */
    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: SherpaOnnxTtsEngine): TtsEngine
}
