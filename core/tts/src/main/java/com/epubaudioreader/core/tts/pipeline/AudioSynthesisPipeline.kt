package com.epubaudioreader.core.tts.pipeline

import android.util.Log
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.tts.segmentation.TextSegment
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pipeline de sintese de audio para TTS.
 *
 * Responsabilidades:
 * 1. Recebe lista de [TextSegment]s e inicia o pipeline
 * 2. Sintetiza segmentos em background via [TtsSynthesizer]
 * 3. Mantem cache LRU em memoria (max [CACHE_SIZE] segmentos)
 * 4. Faz prefetch dos proximos segmentos durante o playback
 * 5. Expoe estado observavel via [StateFlow]
 *
 * BUG-005: Prefetch limitado por Semaphore(2) para evitar explosao de corrotinas.
 * BUG-008: awaitAudio usa CompletableDeferred em vez de busy-wait polling.
 * BUG-018: Campo jobs removido (codigo morto).
 */
@Singleton
class AudioSynthesisPipeline @Inject constructor(
    private val synthesizer: TtsSynthesizer,
    private val dispatcher: DispatcherProvider
) {
    companion object {
        private const val TAG = "AudioSynthesisPipeline"
        private const val CACHE_SIZE = 10
        private const val LOOKAHEAD = 3
        private const val MAX_CONCURRENT_SYNTHESIS = 2
    }

    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private val cache = object : LinkedHashMap<Int, AudioSegment>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, AudioSegment>?): Boolean {
            val shouldRemove = size > CACHE_SIZE
            if (shouldRemove && eldest != null) {
                Log.v(TAG, "LRU evict: segmento ${eldest.key}")
            }
            return shouldRemove
        }
    }
    private val cacheMutex = Mutex()

    /** BUG-008: Mapa de deferreds para notificacao assincrona quando segmentos ficam prontos */
    private val pendingDeferreds = mutableMapOf<Int, CompletableDeferred<AudioSegment?>>()
    private val deferredsMutex = Mutex()

    /** BUG-005: Semaphore que limita sinteses concorrentes */
    private val synthesisSemaphore = Semaphore(MAX_CONCURRENT_SYNTHESIS)

    private var allSegments: List<TextSegment> = emptyList()
    private val scope = CoroutineScope(dispatcher.io + SupervisorJob())

    fun start(segments: List<TextSegment>) {
        cancelAll()
        allSegments = segments

        _state.value = PipelineState(
            isRunning = true,
            totalSegments = segments.size,
            readyCount = 0,
            currentIndex = 0
        )

        Log.i(TAG, "Pipeline iniciado com ${segments.size} segmentos")
    }

    fun prefetchUpTo(index: Int, lookahead: Int = LOOKAHEAD) {
        if (!_state.value.isRunning || allSegments.isEmpty()) return
        if (index < 0 || index >= allSegments.size) return

        val endIndex = (index + lookahead).coerceAtMost(allSegments.size)

        for (i in index until endIndex) {
            scope.launch {
                // BUG-005: Limitar numero de sinteses concorrentes
                synthesisSemaphore.withPermit {
                    val shouldSkip = cacheMutex.withLock {
                        val existing = cache[i]
                        when {
                            existing == null -> {
                                cache[i] = AudioSegment(
                                    segmentId = i,
                                    samples = FloatArray(0),
                                    sampleRate = 0,
                                    status = SegmentStatus.SYNTHESIZING
                                )
                                false
                            }
                            existing.status == SegmentStatus.READY -> true
                            existing.status == SegmentStatus.SYNTHESIZING -> true
                            else -> {
                                cache[i] = AudioSegment(
                                    segmentId = i,
                                    samples = FloatArray(0),
                                    sampleRate = 0,
                                    status = SegmentStatus.SYNTHESIZING
                                )
                                false
                            }
                        }
                    }

                    if (shouldSkip) return@withPermit

                    synthesizeSegment(i)
                }
            }
        }
    }

    suspend fun getAudio(segmentId: Int): AudioSegment? {
        if (segmentId < 0 || segmentId >= allSegments.size) return null
        return cacheMutex.withLock {
            val segment = cache[segmentId]
            if (segment?.status == SegmentStatus.READY) segment else null
        }
    }

    /**
     * Aguarda um segmento ficar pronto para playback.
     * BUG-008: Usa CompletableDeferred para notificacao assincrona em vez de polling.
     */
    suspend fun awaitAudio(segmentId: Int, timeoutMs: Long = 30000): AudioSegment? {
        if (segmentId < 0 || segmentId >= allSegments.size) return null

        // Verificar se ja esta pronto
        cacheMutex.withLock {
            val segment = cache[segmentId]
            if (segment?.status == SegmentStatus.READY) return segment
        }

        // Criar ou obter deferred para este segmento
        val deferred = deferredsMutex.withLock {
            pendingDeferreds.getOrPut(segmentId) { CompletableDeferred() }
        }

        return try {
            // Aguardar notificacao assincrona com timeout
            val result = withTimeoutOrNull(timeoutMs) {
                deferred.await()
            }
            if (result == null) {
                Log.w(TAG, "Timeout aguardando segmento $segmentId (${timeoutMs}ms)")
            }
            result
        } catch (e: CancellationException) {
            Log.v(TAG, "awaitAudio cancelado para segmento $segmentId")
            null
        }
    }

    fun advanceTo(newIndex: Int) {
        _state.update { it.copy(currentIndex = newIndex) }
        prefetchUpTo(newIndex, LOOKAHEAD)
    }

    fun cancelAll() {
        scope.coroutineContext.cancelChildren()
        _state.update { it.copy(isRunning = false) }
        Log.i(TAG, "Pipeline cancelado. ${cache.size} segmentos em cache.")
    }

    /**
     * Libera os jobs ativos e limpa o cache.
     * Como este pipeline e um singleton, o scope NAO e cancelado aqui;
     * apenas os filhos sao cancelados. O scope e reutilizado.
     */
    fun release() {
        cancelAll()
        cache.clear()
        allSegments = emptyList()
        _state.value = PipelineState()
        Log.i(TAG, "Pipeline liberado")
    }

    private suspend fun synthesizeSegment(index: Int) {
        try {
            val segment = allSegments.getOrNull(index) ?: return
            Log.d(TAG, "Sintetizando segmento $index: ${segment.text.take(50)}...")

            val result = synthesizer.synthesizeToMemory(segment.text)

            when (result) {
                is Result.Success -> {
                    val audio = result.data
                    cacheMutex.withLock {
                        cache[index] = AudioSegment(
                            segmentId = index,
                            samples = audio.samples,
                            sampleRate = audio.sampleRate,
                            status = SegmentStatus.READY
                        )
                    }

                    val readyCount = cacheMutex.withLock {
                        cache.count { entry -> entry.value.status == SegmentStatus.READY }
                    }
                    _state.update { it.copy(readyCount = readyCount) }

                    Log.d(TAG, "Segmento $index pronto ($readyCount/${allSegments.size})")

                    // BUG-008: Notificar deferreds pendentes que este segmento ficou pronto
                    val deferred = deferredsMutex.withLock {
                        pendingDeferreds.remove(index)
                    }
                    val cachedSegment = cacheMutex.withLock { cache[index] }
                    deferred?.complete(cachedSegment)
                }
                is Result.Error -> {
                    throw result.exception
                }
            }
        } catch (e: CancellationException) {
            cacheMutex.withLock {
                if (cache[index]?.status == SegmentStatus.SYNTHESIZING) {
                    cache[index] = AudioSegment(
                        segmentId = index,
                        samples = FloatArray(0),
                        sampleRate = 0,
                        status = SegmentStatus.PENDING
                    )
                }
            }
            // BUG-008: Completar deferred com null em caso de cancelamento
            val deferred = deferredsMutex.withLock {
                pendingDeferreds.remove(index)
            }
            deferred?.complete(null)
            Log.v(TAG, "Sintese do segmento $index cancelada")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sintetizar segmento $index: ${e.message}", e)
            cacheMutex.withLock {
                cache[index] = AudioSegment(
                    segmentId = index,
                    samples = FloatArray(0),
                    sampleRate = 0,
                    status = SegmentStatus.ERROR
                )
            }
            // BUG-008: Completar deferred com null em caso de erro
            val deferred = deferredsMutex.withLock {
                pendingDeferreds.remove(index)
            }
            deferred?.complete(null)
            _state.update { it.copy(error = "Segmento $index: ${e.message}") }
        }
    }
}
