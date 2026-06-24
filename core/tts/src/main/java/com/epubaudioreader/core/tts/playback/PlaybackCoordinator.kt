package com.epubaudioreader.core.tts.playback

import android.util.Log
import com.epubaudioreader.core.tts.pipeline.AudioSynthesisPipeline
import com.epubaudioreader.core.tts.pipeline.SegmentStatus
import com.epubaudioreader.core.tts.segmentation.SmartTextSegmenter
import com.epubaudioreader.core.tts.segmentation.TextSegment
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
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
 * Integra segmentacao de texto, pipeline de sintese e reproducao,
 * fornecendo um StateFlow unificado para a UI observar.
 *
 * O playback roda em [Dispatchers.IO] porque as operacoes de AudioTrack
 * (especialmente write em modo bloqueante) nao podem travar a main thread.
 */
@Singleton
class PlaybackCoordinator @Inject constructor(
    private val segmenter: SmartTextSegmenter,
    private val synthesizer: TtsSynthesizer,
    private val pipeline: AudioSynthesisPipeline
) {
    companion object {
        private const val TAG = "PlaybackCoordinator"
        private const val MAX_WAIT_CYCLES = 75  // 15s no total
        private const val WAIT_DELAY_MS = 200L
        private const val PREFETCH_AHEAD = 3
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null
    private var segments: List<TextSegment> = emptyList()

    fun playParagraphs(paragraphs: List<String>, startIndex: Int = 0) {
        Log.i(TAG, "playParagraphs: ${paragraphs.size} paragrafos, indice=$startIndex")
        stop()

        if (paragraphs.isEmpty()) {
            _state.value = PlaybackState(error = "Nenhum texto para ler")
            return
        }

        segments = segmenter.segment(paragraphs)
        Log.i(TAG, "${segments.size} segmentos criados a partir de ${paragraphs.size} paragrafos")
        if (segments.isEmpty()) {
            _state.value = PlaybackState(error = "Nenhum segmento valido para leitura")
            return
        }

        pipeline.start(segments)
        // Prefetch inicial dos primeiros segmentos
        pipeline.prefetchUpTo(startIndex, PREFETCH_AHEAD)

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
                var waitCount = 0
                while (isActive &&
                    pipeline.getAudio(startIndex) == null &&
                    waitCount < MAX_WAIT_CYCLES
                ) {
                    delay(WAIT_DELAY_MS)
                    waitCount++
                }

                if (!isActive) {
                    Log.d(TAG, "Playback cancelado durante preparacao")
                    return@launch
                }

                if (pipeline.getAudio(startIndex) == null) {
                    Log.w(TAG, "Timeout aguardando primeiro segmento")
                    _state.update {
                        it.copy(
                            isPreparing = false,
                            isPlaying = false,
                            error = "Timeout ao preparar primeiro segmento"
                        )
                    }
                    return@launch
                }

                _state.update { it.copy(isPreparing = false, isPlaying = true) }

                for (i in startIndex until segments.size) {
                    if (!isActive) break

                    val segment = segments[i]
                    _state.update {
                        it.copy(currentSegmentIndex = i, currentText = segment.text)
                    }

                    pipeline.prefetchUpTo(i, PREFETCH_AHEAD)
                    playSegment(i)
                }

                _state.update { it.copy(isPlaying = false, currentText = "") }
                Log.i(TAG, "Playback concluido — ${segments.size} segmentos")

            } catch (e: CancellationException) {
                Log.d(TAG, "Playback cancelado pelo usuario")
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

    fun playFromSegment(index: Int) {
        if (segments.isEmpty()) {
            Log.w(TAG, "Nenhum segmento carregado para tocar a partir do indice $index")
            return
        }
        if (index < 0 || index >= segments.size) {
            Log.w(TAG, "Indice $index fora dos limites [0, ${segments.size})")
            return
        }

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
                var waitCount = 0
                while (isActive &&
                    pipeline.getAudio(index) == null &&
                    waitCount < MAX_WAIT_CYCLES
                ) {
                    delay(WAIT_DELAY_MS)
                    waitCount++
                }

                if (!isActive) return@launch

                if (pipeline.getAudio(index) == null) {
                    _state.update {
                        it.copy(
                            isPreparing = false,
                            isPlaying = false,
                            error = "Timeout ao preparar segmento $index"
                        )
                    }
                    return@launch
                }

                _state.update { it.copy(isPreparing = false, isPlaying = true) }

                for (i in index until segments.size) {
                    if (!isActive) break

                    val segment = segments[i]
                    _state.update {
                        it.copy(currentSegmentIndex = i, currentText = segment.text)
                    }

                    pipeline.prefetchUpTo(i, PREFETCH_AHEAD)
                    playSegment(i)
                }

                _state.update { it.copy(isPlaying = false, currentText = "") }
                Log.i(TAG, "Playback a partir do segmento $index concluido")

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

    private suspend fun playSegment(index: Int) {
        Log.d(TAG, "playSegment($index)")
        val cached = pipeline.awaitAudio(index, timeoutMs = WAIT_DELAY_MS * MAX_WAIT_CYCLES)

        if (cached != null && cached.samples.isNotEmpty()) {
            Log.d(TAG, "Tocando segmento $index do cache (${cached.samples.size} samples)")
            val result = synthesizer.playSamples(cached.samples, cached.sampleRate)
            result.onFailure { e ->
                Log.e(TAG, "Erro ao tocar segmento $index do cache: ${e.message}")
                _state.update { it.copy(error = e.message) }
            }
        } else {
            val segment = segments.getOrNull(index) ?: return
            Log.d(TAG, "Tocando segmento $index por geracao direta")
            val result = synthesizer.speak(segment.text, onComplete = null)
            result.onFailure { e ->
                Log.e(TAG, "Erro no segmento $index: ${e.message}")
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        synthesizer.stop()
        _state.update { it.copy(isPlaying = false, isPreparing = false) }
        Log.d(TAG, "Playback pausado")
    }

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
     * Libera os recursos do playback atual.
     * Como PlaybackCoordinator e um singleton, o scope NAO e cancelado aqui;
     * apenas os jobs ativos sao cancelados para permitir reuso.
     */
    fun release() {
        stop()
        scope.coroutineContext.cancelChildren()
        Log.d(TAG, "PlaybackCoordinator liberado")
    }
}
