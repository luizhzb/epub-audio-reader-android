package com.epubaudioreader.core.tts.playback

import android.util.Log
import com.epubaudioreader.core.tts.pipeline.AudioSegment
import com.epubaudioreader.core.tts.pipeline.AudioSynthesisPipeline
import com.epubaudioreader.core.tts.segmentation.SmartTextSegmenter
import com.epubaudioreader.core.tts.segmentation.TextSegment
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordenador central de playback de TTS.
 *
 * Integra segmentação de texto, pipeline de síntese e reprodução,
 * fornecendo um StateFlow unificado para a UI observar.
 *
 * Arquitetura:
 * - SupervisorJob: falha em uma corrotina não cancela as outras
 * - Dispatchers.Main: todos os callbacks de estado são no thread principal
 * - Playback sequencial: toca segmento por segmento com prefetch
 */
@Singleton
class PlaybackCoordinator @Inject constructor(
    private val segmenter: SmartTextSegmenter,
    private val synthesizer: TtsSynthesizer,
    private val pipeline: AudioSynthesisPipeline
) {
    companion object {
        private const val TAG = "PlaybackCoordinator"
        private const val MAX_WAIT_CYCLES = 50
        private const val WAIT_DELAY_MS = 200L
        private const val PREFETCH_AHEAD = 3
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackJob: Job? = null
    private var segments: List<TextSegment> = emptyList()

    /**
     * Inicia playback a partir de uma lista de parágrafos.
     *
     * Fluxo:
     * 1. Segmenta o texto em [TextSegment]s
     * 2. Inicia pipeline de síntese em background com cache
     * 3. Aguarda primeiro segmento ficar pronto
     * 4. Toca segmento por segmento sequencialmente com prefetch
     */
    fun playParagraphs(paragraphs: List<String>, startIndex: Int = 0) {
        stop()

        if (paragraphs.isEmpty()) {
            _state.value = PlaybackState(error = "Nenhum texto para ler")
            return
        }

        // 1. Segmentar texto
        segments = segmenter.segment(paragraphs)
        Log.i(TAG, "${segments.size} segmentos criados a partir de ${paragraphs.size} parágrafos")

        // 2. Iniciar pipeline de síntese em background
        pipeline.start(segments)

        // 3. Iniciar playback
        playbackJob = scope.launch {
            _state.update {
                it.copy(
                    isPreparing = true,
                    totalSegments = segments.size,
                    currentSegmentIndex = startIndex,
                    error = null
                )
            }

            try {
                // Aguardar primeiro segmento ficar pronto (com timeout)
                var waitCount = 0
                while (isActive &&
                    pipeline.getAudio(startIndex)?.status != AudioSegment.SegmentStatus.READY &&
                    waitCount < MAX_WAIT_CYCLES
                ) {
                    delay(WAIT_DELAY_MS)
                    waitCount++
                }

                if (!isActive) return@launch

                _state.update { it.copy(isPreparing = false, isPlaying = true) }

                // Tocar cada segmento sequencialmente
                for (i in startIndex until segments.size) {
                    if (!isActive) break

                    val segment = segments[i]
                    _state.update {
                        it.copy(currentSegmentIndex = i, currentText = segment.text)
                    }

                    // Prefetch próximos segmentos
                    pipeline.prefetchUpTo(i, PREFETCH_AHEAD)

                    // Tocar segmento atual (bloqueante até conclusão)
                    val result = synthesizer.speak(segment.text, onComplete = null)
                    result.onFailure { e ->
                        Log.e(TAG, "Erro no segmento $i: ${e.message}")
                        _state.update { it.copy(error = e.message) }
                    }
                }

                _state.update { it.copy(isPlaying = false, currentText = "") }
                Log.i(TAG, "Playback concluído — ${segments.size} segmentos")

            } catch (e: CancellationException) {
                Log.d(TAG, "Playback cancelado pelo usuário")
                _state.update { it.copy(isPlaying = false, isPreparing = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Erro fatal no playback: ${e.message}", e)
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isPreparing = false,
                        error = e.message
                    )
                }
            }
        }
    }

    /**
     * Retoma playback a partir de um índice de segmento específico.
     * Útil para navegação (ex: tocar a partir do parágrafo visível).
     */
    fun playFromSegment(index: Int) {
        if (segments.isEmpty()) {
            Log.w(TAG, "Nenhum segmento carregado para tocar a partir do índice $index")
            return
        }
        if (index < 0 || index >= segments.size) {
            Log.w(TAG, "Índice $index fora dos limites [0, ${segments.size})")
            return
        }

        // Cancelar job atual e reiniciar a partir do índice
        playbackJob?.cancel()
        playbackJob = scope.launch {
            _state.update {
                it.copy(
                    isPreparing = true,
                    currentSegmentIndex = index,
                    error = null
                )
            }

            try {
                // Aguardar segmento ficar pronto
                var waitCount = 0
                while (isActive &&
                    pipeline.getAudio(index)?.status != AudioSegment.SegmentStatus.READY &&
                    waitCount < MAX_WAIT_CYCLES
                ) {
                    delay(WAIT_DELAY_MS)
                    waitCount++
                }

                if (!isActive) return@launch

                _state.update { it.copy(isPreparing = false, isPlaying = true) }

                // Tocar a partir do índice
                for (i in index until segments.size) {
                    if (!isActive) break

                    val segment = segments[i]
                    _state.update {
                        it.copy(currentSegmentIndex = i, currentText = segment.text)
                    }

                    // Prefetch próximos segmentos
                    pipeline.prefetchUpTo(i, PREFETCH_AHEAD)

                    val result = synthesizer.speak(segment.text, onComplete = null)
                    result.onFailure { e ->
                        Log.e(TAG, "Erro no segmento $i: ${e.message}")
                        _state.update { it.copy(error = e.message) }
                    }
                }

                _state.update { it.copy(isPlaying = false, currentText = "") }
                Log.i(TAG, "Playback a partir do segmento $index concluído")

            } catch (e: CancellationException) {
                Log.d(TAG, "Playback cancelado")
                _state.update { it.copy(isPlaying = false, isPreparing = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no playback: ${e.message}", e)
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isPreparing = false,
                        error = e.message
                    )
                }
            }
        }
    }

    /**
     * Pausa o playback imediatamente.
     * Cancela o job de playback e para o sintetizador.
     */
    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        synthesizer.stop()
        _state.update { it.copy(isPlaying = false, isPreparing = false) }
        Log.d(TAG, "Playback pausado")
    }

    /**
     * Para o playback completamente e limpa o estado.
     * Cancela jobs, para sintetizador e limpa o pipeline.
     */
    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        synthesizer.stop()
        pipeline.cancelAll()
        _state.update {
            it.copy(
                isPlaying = false,
                isPreparing = false,
                currentSegmentIndex = 0,
                currentText = "",
                error = null
            )
        }
        Log.d(TAG, "Playback parado e estado resetado")
    }

    /**
     * Libera todos os recursos.
     * Deve ser chamado quando o ViewModel é limpo (onCleared).
     */
    fun release() {
        stop()
        scope.cancel()
        Log.d(TAG, "PlaybackCoordinator liberado")
    }
}
