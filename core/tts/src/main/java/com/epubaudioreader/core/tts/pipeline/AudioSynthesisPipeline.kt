package com.epubaudioreader.core.tts.pipeline

import android.util.Log
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.tts.segmentation.TextSegment
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private var allSegments: List<TextSegment> = emptyList()
    private var jobs: MutableList<Job> = mutableListOf()
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

                if (shouldSkip) return@launch

                synthesizeSegment(i)
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

    suspend fun awaitAudio(segmentId: Int, timeoutMs: Long = 30000): AudioSegment? {
        if (segmentId < 0 || segmentId >= allSegments.size) return null
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            cacheMutex.withLock {
                val segment = cache[segmentId]
                if (segment?.status == SegmentStatus.READY) return segment
            }
            delay(100)
        }
        Log.w(TAG, "Timeout aguardando segmento $segmentId (${timeoutMs}ms)")
        return null
    }

    fun advanceTo(newIndex: Int) {
        _state.update { it.copy(currentIndex = newIndex) }
        prefetchUpTo(newIndex, LOOKAHEAD)
    }

    fun cancelAll() {
        scope.coroutineContext.cancelChildren()
        jobs.clear()
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
            _state.update { it.copy(error = "Segmento $index: ${e.message}") }
        }
    }
}
