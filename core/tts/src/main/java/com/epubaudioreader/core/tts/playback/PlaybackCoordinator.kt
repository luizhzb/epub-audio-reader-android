package com.epubaudioreader.core.tts.playback

import android.util.Log
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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger
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
 *
 * Todas as operacoes de estado mutavel sao protegidas por um Mutex (BUG-004 fix)
 * para garantir thread-safety como @Singleton.
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
        private const val SEGMENT_GAP_MS = 300L  // Pausa entre segmentos
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null
    private var segments: List<TextSegment> = emptyList()

    /** Mutex que protege todas as operacoes de estado mutavel (BUG-004 fix) */
    private val mutex = Mutex()

    fun playParagraphs(paragraphs: List<String>, startIndex: Int = 0) {
        Log.i(TAG, "playParagraphs: ${paragraphs.size} paragrafos, indice=$startIndex")

        scope.launch {
            mutex.withLock {
                stopInternal()
            }

            if (paragraphs.isEmpty()) {
                _state.value = PlaybackState(error = "Nenhum texto para ler")
                return@launch
            }

            val currentSegments = mutex.withLock {
                segments = segmenter.segment(paragraphs)
                segments
            }

            Log.i(TAG, "${currentSegments.size} segmentos criados a partir de ${paragraphs.size} paragrafos")
            if (currentSegments.isEmpty()) {
                _state.value = PlaybackState(error = "Nenhum segmento valido para leitura")
                return@launch
            }

            pipeline.start(currentSegments)
            pipeline.prefetchUpTo(startIndex, PREFETCH_AHEAD)

            val job = scope.launch {
                _state.update {
                    it.copy(
                        isPreparing = true,
                        totalSegments = currentSegments.size,
                        currentSegmentIndex = startIndex,
                        error = null
                    )
                }

                try {
                    // Aguarda o primeiro segmento ficar pronto
                    val waitCount = AtomicInteger(0)
                    while (isActive &&
                        pipeline.getAudio(startIndex) == null &&
                        waitCount.get() < MAX_WAIT_CYCLES
                    ) {
                        delay(WAIT_DELAY_MS)
                        waitCount.incrementAndGet()
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

                    for (i in startIndex until currentSegments.size) {
                        if (!isActive) break

                        yield() // Da chance para corrotinas de cancelamento

                        val segment = currentSegments[i]
                        _state.update {
                            it.copy(currentSegmentIndex = i, currentText = segment.text)
                        }

                        pipeline.prefetchUpTo(i, PREFETCH_AHEAD)
                        playSegment(i)

                        // Pausa entre segmentos para o usuario processar
                        if (isActive && i < currentSegments.size - 1) {
                            delay(SEGMENT_GAP_MS)
                        }
                    }

                    _state.update { it.copy(isPlaying = false, currentText = "") }
                    Log.i(TAG, "Playback concluido — ${currentSegments.size} segmentos")

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

            mutex.withLock {
                playbackJob = job
            }
        }
    }

    private suspend fun playSegment(index: Int) {
        Log.d(TAG, "playSegment($index)")

        // Tenta tocar do cache primeiro
        val cached = pipeline.awaitAudio(index, timeoutMs = WAIT_DELAY_MS * MAX_WAIT_CYCLES)
        if (cached != null && cached.samples.isNotEmpty()) {
            Log.d(TAG, "Tocando segmento $index do cache (${cached.samples.size} samples)")
            val result = synthesizer.playSamples(cached.samples, cached.sampleRate)
            result.onFailure { e ->
                Log.e(TAG, "Erro ao tocar segmento $index do cache: ${e.message}")
                _state.update { it.copy(error = e.message) }
            }

            // Aguarda a duracao estimada do audio
            val durationMs = (cached.samples.size.toFloat() / cached.sampleRate * 1000).toLong()
            Log.d(TAG, "Aguardando $durationStr ms de playback do segmento $index")
            delay(durationMs)
            return
        }

        // Fallback: sintese direta via speak
        val currentSegments = mutex.withLock { segments }
        val segment = currentSegments.getOrNull(index) ?: return
        Log.d(TAG, "Tocando segmento $index por geracao direta: '${segment.text.take(50)}...'")

        val result = synthesizer.speak(segment.text, onComplete = null)
        result.onFailure { e ->
            Log.e(TAG, "Erro no segmento $index: ${e.message}")
            _state.update { it.copy(error = e.message) }
        }
    }

    private val FloatArray.durationMs: Long
        get() = (size.toFloat() / 22050 * 1000).toLong()

    private fun durationStr(samples: Int, sampleRate: Int): Long {
        return (samples.toFloat() / sampleRate * 1000).toLong()
    }

    fun pause() {
        playbackJob?.cancel()
        scope.launch {
            mutex.withLock {
                playbackJob = null
            }
            synthesizer.stop()
            _state.update { it.copy(isPlaying = false, isPreparing = false) }
            Log.d(TAG, "Playback pausado")
        }
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                stopInternal()
            }
            Log.d(TAG, "Playback parado e estado resetado")
        }
    }

    /** Operacao stop interna - DEVE ser chamada dentro de mutex.withLock (BUG-007 fix) */
    private fun stopInternal() {
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
    }

    /**
     * Libera os recursos do playback atual.
     * BUG-012: Cancela o scope para evitar memory leak.
     */
    fun release() {
        stop()
        scope.coroutineContext.cancelChildren()
        scope.cancel()
        Log.d(TAG, "PlaybackCoordinator liberado")
    }
}
