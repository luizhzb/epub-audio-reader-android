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
 * Coordenador central de playback de TTS com melhorias de robustez.
 * 
 * Melhorias:
 * 1. Verifica se AudioTrack esta inicializado antes de iniciar playback
 * 2. Try/catch em playSegment para evitar crash nativo
 * 3. Mensagens de erro amigaveis em portugues
 * 4. Verificacao de estado do sintetizador no fallback
 * 5. Protecao contra chamada com engine nao inicializado
 */
@Singleton
class PlaybackCoordinator @Inject constructor(
    private val segmenter: SmartTextSegmenter,
    private val synthesizer: TtsSynthesizer,
    private val pipeline: AudioSynthesisPipeline
) {
    companion object {
        private const val TAG = "PlaybackCoordinator"
        private const val MAX_WAIT_CYCLES = 75
        private const val WAIT_DELAY_MS = 200L
        private const val PREFETCH_AHEAD = 3
        private const val SEGMENT_GAP_MS = 300L
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null
    private var segments: List<TextSegment> = emptyList()

    private val mutex = Mutex()

    /**
     * Inicia playback de paragrafos com verificacao de inicializacao.
     * 
     * Melhorias:
     * - Verifica se AudioTrack esta inicializado antes de comecar
     * - Retorna erro amigavel se sintetizador nao esta pronto
     * - Mensagens de log detalhadas para diagnostico
     */
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

            // MELHORIA: Verificar se sintetizador esta inicializado
            if (!synthesizer.isAudioTrackInitialized()) {
                Log.e(TAG, "AudioTrack nao inicializado. Modelo TTS nao esta pronto.")
                _state.value = PlaybackState(
                    error = "Modelo de voz nao carregado. Carregue o modelo primeiro."
                )
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

                        yield()

                        val segment = currentSegments[i]
                        _state.update {
                            it.copy(currentSegmentIndex = i, currentText = segment.text)
                        }

                        pipeline.prefetchUpTo(i, PREFETCH_AHEAD)
                        playSegment(i)

                        if (isActive && i < currentSegments.size - 1) {
                            delay(SEGMENT_GAP_MS)
                        }
                    }

                    _state.update { it.copy(isPlaying = false, currentText = "") }
                    Log.i(TAG, "Playback concluido - ${currentSegments.size} segmentos")

                } catch (e: CancellationException) {
                    Log.d(TAG, "Playback cancelado pelo usuario")
                    _state.update { it.copy(isPlaying = false, isPreparing = false) }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro fatal no playback: ${e.message}", e)
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            isPreparing = false,
                            error = "Erro na reproducao: ${e.message}"
                        )
                    }
                }
            }

            mutex.withLock {
                playbackJob = job
            }
        }
    }

    /**
     * Reproduz um segmento individual com protecao contra falhas.
     * 
     * Melhorias:
     * - Try/catch completo para evitar crash nativo
     * - Verifica AudioTrack antes do fallback de sintese direta
     * - Mensagens de erro detalhadas por segmento
     */
    private suspend fun playSegment(index: Int) {
        Log.d(TAG, "playSegment($index)")

        try {
            // Tenta tocar do cache primeiro
            val cached = pipeline.awaitAudio(index, timeoutMs = WAIT_DELAY_MS * MAX_WAIT_CYCLES)
            if (cached != null && cached.samples.isNotEmpty()) {
                Log.d(TAG, "Tocando segmento $index do cache (${cached.samples.size} samples)")
                val result = synthesizer.playSamples(cached.samples, cached.sampleRate)
                result.onFailure { e ->
                    Log.e(TAG, "Erro ao tocar segmento $index do cache: ${e.message}")
                    _state.update { it.copy(error = "Erro de audio: ${e.message}") }
                }

                val durationMs = (cached.samples.size.toFloat() / cached.sampleRate * 1000).toLong()
                Log.d(TAG, "Aguardando ${durationMs}ms de playback do segmento $index")
                delay(durationMs)
                return
            }

            // Fallback: sintese direta via speak
            val currentSegments = mutex.withLock { segments }
            val segment = currentSegments.getOrNull(index) ?: return

            // MELHORIA: Verificar AudioTrack antes do fallback
            if (!synthesizer.isAudioTrackInitialized()) {
                Log.e(TAG, "AudioTrack nao inicializado no fallback do segmento $index")
                _state.update { it.copy(error = "Audio nao inicializado") }
                return
            }

            Log.d(TAG, "Tocando segmento $index por geracao direta: '${segment.text.take(50)}...'")
            val result = synthesizer.speak(segment.text, onComplete = null)
            result.onFailure { e ->
                Log.e(TAG, "Erro no segmento $index: ${e.message}")
                _state.update { it.copy(error = "Erro na sintese: ${e.message}") }
            }
        } catch (e: CancellationException) {
            // Re-lanca para que o loop principal capture
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal em playSegment($index): ${e.message}", e)
            _state.update { it.copy(error = "Erro ao reproduzir segmento: ${e.message}") }
        }
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

    fun release() {
        stop()
        scope.coroutineContext.cancelChildren()
        scope.cancel()
        Log.d(TAG, "PlaybackCoordinator liberado")
    }
}
