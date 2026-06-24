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
 * Coordenador central de playback de TTS com correcoes de crash.
 *
 * CORRECOES (AUDITORIA 2025-06-25):
 * 1. segmenter.segment() AGORA tem try/catch completo - evita deadlock do Mutex
 * 2. Logs TTS_TRACE detalhados em cada ponto do pipeline
 * 3. Protecao contra chamada com engine nao inicializado
 * 4. Verificacao de segmentos vazios apos segmentacao
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
     * CORRECAO CRITICA: try/catch em torno de segmenter.segment() para evitar
     * deadlock do Mutex. Se a segmentacao falhar, o mutex eh liberado e o erro
     * eh reportado ao usuario.
     */
    fun playParagraphs(paragraphs: List<String>, startIndex: Int = 0) {
        Log.i(TAG, "[TTS_TRACE] playParagraphs: ${paragraphs.size} paragrafos, indice=$startIndex")

        scope.launch {
            mutex.withLock {
                stopInternal()
            }

            if (paragraphs.isEmpty()) {
                Log.w(TAG, "[TTS_TRACE] Nenhum texto para ler")
                _state.value = PlaybackState(error = "Nenhum texto para ler")
                return@launch
            }

            // Verificar se sintetizador esta inicializado
            if (!synthesizer.isAudioTrackInitialized()) {
                Log.e(TAG, "[TTS_TRACE] AudioTrack nao inicializado. Modelo TTS nao esta pronto.")
                _state.value = PlaybackState(
                    error = "Modelo de voz nao carregado. Carregue o modelo primeiro."
                )
                return@launch
            }

            // === CORRECAO CRITICA: try/catch ao redor de segmenter.segment() ===
            val currentSegments = try {
                mutex.withLock {
                    Log.d(TAG, "[TTS_TRACE] Iniciando segmentacao de ${paragraphs.size} paragrafos...")
                    segments = segmenter.segment(paragraphs)
                    Log.d(TAG, "[TTS_TRACE] Segmentacao concluida: ${segments.size} segmentos")
                    segments
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "[TTS_TRACE] Segmentacao cancelada pelo usuario")
                _state.value = PlaybackState(error = "Leitura cancelada")
                return@launch
            } catch (e: Exception) {
                Log.e(TAG, "[TTS_TRACE] ERRO NA SEGMENTACAO: ${e.message}", e)
                _state.value = PlaybackState(
                    error = "Erro ao dividir texto em segmentos: ${e.message}"
                )
                return@launch
            }

            if (currentSegments.isEmpty()) {
                Log.w(TAG, "[TTS_TRACE] Nenhum segmento valido gerado")
                _state.value = PlaybackState(error = "Nenhum segmento valido para leitura")
                return@launch
            }

            Log.i(TAG, "[TTS_TRACE] ${currentSegments.size} segmentos criados a partir de ${paragraphs.size} paragrafos")

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
                    Log.d(TAG, "[TTS_TRACE] Aguardando primeiro segmento ($startIndex)...")
                    val waitCount = AtomicInteger(0)
                    while (isActive &&
                        pipeline.getAudio(startIndex) == null &&
                        waitCount.get() < MAX_WAIT_CYCLES
                    ) {
                        delay(WAIT_DELAY_MS)
                        waitCount.incrementAndGet()
                    }

                    if (!isActive) {
                        Log.d(TAG, "[TTS_TRACE] Playback cancelado durante preparacao")
                        return@launch
                    }

                    if (pipeline.getAudio(startIndex) == null) {
                        Log.w(TAG, "[TTS_TRACE] Timeout aguardando primeiro segmento")
                        _state.update {
                            it.copy(
                                isPreparing = false,
                                isPlaying = false,
                                error = "Timeout ao preparar primeiro segmento"
                            )
                        }
                        return@launch
                    }

                    Log.i(TAG, "[TTS_TRACE] Primeiro segmento pronto. Iniciando playback.")
                    _state.update { it.copy(isPreparing = false, isPlaying = true) }

                    for (i in startIndex until currentSegments.size) {
                        if (!isActive) {
                            Log.d(TAG, "[TTS_TRACE] Playback interrompido no segmento $i")
                            break
                        }

                        yield()

                        val segment = currentSegments[i]
                        Log.d(TAG, "[TTS_TRACE] Segmento $i/${currentSegments.size}: '${segment.text.take(60)}...'")
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
                    Log.i(TAG, "[TTS_TRACE] Playback concluido - ${currentSegments.size} segmentos")

                } catch (e: CancellationException) {
                    Log.d(TAG, "[TTS_TRACE] Playback cancelado pelo usuario")
                    _state.update { it.copy(isPlaying = false, isPreparing = false) }
                } catch (e: Exception) {
                    Log.e(TAG, "[TTS_TRACE] Erro fatal no playback: ${e.message}", e)
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
     */
    private suspend fun playSegment(index: Int) {
        Log.d(TAG, "[TTS_TRACE] playSegment($index) INICIO")

        try {
            // Tenta tocar do cache primeiro
            Log.d(TAG, "[TTS_TRACE] Aguardando audio segmento $index...")
            val cached = pipeline.awaitAudio(index, timeoutMs = WAIT_DELAY_MS * MAX_WAIT_CYCLES)

            if (cached != null && cached.samples.isNotEmpty()) {
                Log.d(TAG, "[TTS_TRACE] Tocando segmento $index do cache (${cached.samples.size} samples, sr=${cached.sampleRate})")
                val result = synthesizer.playSamples(cached.samples, cached.sampleRate)
                result.onFailure { e ->
                    Log.e(TAG, "[TTS_TRACE] Erro ao tocar segmento $index do cache: ${e.message}")
                    _state.update { it.copy(error = "Erro de audio: ${e.message}") }
                }
                result.onSuccess {
                    Log.d(TAG, "[TTS_TRACE] Segmento $index do cache concluido")
                }

                val durationMs = (cached.samples.size.toFloat() / cached.sampleRate * 1000).toLong()
                Log.d(TAG, "[TTS_TRACE] Aguardando ${durationMs}ms de playback do segmento $index")
                delay(durationMs)
                Log.d(TAG, "[TTS_TRACE] playSegment($index) FIM (cache)")
                return
            }

            Log.d(TAG, "[TTS_TRACE] Cache miss segmento $index, usando sintese direta")

            // Fallback: sintese direta via speak
            val currentSegments = mutex.withLock { segments }
            val segment = currentSegments.getOrNull(index) ?: return

            // Verificar AudioTrack antes do fallback
            if (!synthesizer.isAudioTrackInitialized()) {
                Log.e(TAG, "[TTS_TRACE] AudioTrack nao inicializado no fallback do segmento $index")
                _state.update { it.copy(error = "Audio nao inicializado") }
                return
            }

            Log.d(TAG, "[TTS_TRACE] Tocando segmento $index por geracao direta: '${segment.text.take(50)}...'")
            val result = synthesizer.speak(segment.text, onComplete = null)
            result.onFailure { e ->
                Log.e(TAG, "[TTS_TRACE] Erro no segmento $index: ${e.message}")
                _state.update { it.copy(error = "Erro na sintese: ${e.message}") }
            }
            result.onSuccess {
                Log.d(TAG, "[TTS_TRACE] Segmento $index por geracao direta concluido")
            }

            Log.d(TAG, "[TTS_TRACE] playSegment($index) FIM (direto)")
        } catch (e: CancellationException) {
            Log.d(TAG, "[TTS_TRACE] playSegment($index) cancelado")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TRACE] Erro fatal em playSegment($index): ${e.message}", e)
            _state.update { it.copy(error = "Erro ao reproduzir segmento: ${e.message}") }
        }
    }

    fun pause() {
        Log.i(TAG, "[TTS_TRACE] pause()")
        playbackJob?.cancel()
        scope.launch {
            mutex.withLock {
                playbackJob = null
            }
            synthesizer.stop()
            _state.update { it.copy(isPlaying = false, isPreparing = false) }
            Log.d(TAG, "[TTS_TRACE] Playback pausado")
        }
    }

    fun stop() {
        Log.i(TAG, "[TTS_TRACE] stop()")
        scope.launch {
            mutex.withLock {
                stopInternal()
            }
            Log.d(TAG, "[TTS_TRACE] Playback parado e estado resetado")
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
        Log.i(TAG, "[TTS_TRACE] release()")
        stop()
        scope.coroutineContext.cancelChildren()
        scope.cancel()
        Log.d(TAG, "[TTS_TRACE] PlaybackCoordinator liberado")
    }
}
