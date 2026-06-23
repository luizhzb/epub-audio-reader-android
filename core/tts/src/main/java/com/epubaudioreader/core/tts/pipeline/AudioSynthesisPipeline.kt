package com.epubaudioreader.core.tts.pipeline

import android.util.Log
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
 *
 * ## Fluxo de uso:
 * ```
 * pipeline.start(segments)          // Inicia com lista de segmentos
 * pipeline.prefetchUpTo(index)      // Pre-sintetiza segmentos ahead
 * pipeline.getAudio(segmentId)      // Verifica se segmento esta pronto
 * pipeline.awaitAudio(segmentId)    // Aguarda segmento ficar pronto
 * pipeline.cancelAll()              // Cancela todas as corrotinas
 * pipeline.release()                // Libera todos os recursos
 * ```
 *
 * ## Arquitetura:
 * O [TtsSynthesizer] ja toca o audio diretamente via callback JNI
 * (generateWithConfigAndCallback). Este pipeline coordena **QUANDO**
 * sintetizar cada segmento (gerenciamento de estado e prefetch),
 * nao **COMO** tocar o audio.
 */
@Singleton
class AudioSynthesisPipeline @Inject constructor(
    private val synthesizer: TtsSynthesizer
) {
    companion object {
        private const val TAG = "AudioSynthesisPipeline"
        private const val CACHE_SIZE = 10
        private const val LOOKAHEAD = 3
    }

    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    /**
     * Cache LRU: segmentId (indice) -> AudioSegment.
     * accessOrder=true garante ordem de acesso para eviction.
     */
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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Inicia o pipeline com uma lista de segmentos.
     *
     * Cancela qualquer operacao anterior, limpa o cache e
     * reinicia o estado.
     *
     * @param segments Lista ordenada de segmentos de texto para sintetizar
     */
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

    /**
     * Prefetch segmentos a partir de um indice.
     *
     * Sintetiza os segmentos no intervalo [index, index + lookahead)
     * em paralelo, evitando re-sintetizar segmentos ja prontos ou
     * em andamento.
     *
     * @param index Indice do segmento atual (sera sintetizado primeiro)
     * @param lookahead Quantidade de segmentos a frente para pre-sintetizar (padrao: [LOOKAHEAD])
     */
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
                            // Marcar como SYNTHESIZING para evitar duplicacao
                            cache[i] = AudioSegment(
                                segmentId = i,
                                samples = FloatArray(0),
                                sampleRate = 0,
                                status = SegmentStatus.SYNTHESIZING
                            )
                            false // Nao pular, precisa sintetizar
                        }
                        existing.status == SegmentStatus.READY -> true
                        existing.status == SegmentStatus.SYNTHESIZING -> true
                        else -> {
                            // PENDING ou ERROR: re-tentar
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

    /**
     * Retorna um [AudioSegment] do cache se estiver pronto (status [SegmentStatus.READY]).
     *
     * @param segmentId Indice do segmento desejado
     * @return AudioSegment pronto, ou null se nao estiver disponivel
     */
    suspend fun getAudio(segmentId: Int): AudioSegment? {
        if (segmentId < 0 || segmentId >= allSegments.size) return null
        return cacheMutex.withLock {
            val segment = cache[segmentId]
            if (segment?.status == SegmentStatus.READY) segment else null
        }
    }

    /**
     * Aguarda ate que um segmento esteja pronto, com timeout.
     *
     * Faz polling a cada 100ms ate que o segmento fique com status READY
     * ou o timeout seja atingido.
     *
     * @param segmentId Indice do segmento aguardado
     * @param timeoutMs Timeout em milissegundos (padrao: 30000)
     * @return AudioSegment pronto, ou null em caso de timeout
     */
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

    /**
     * Move o indice atual e dispara prefetch automatico.
     *
     * Chamado pelo PlaybackCoordinator quando avanca para o proximo segmento.
     *
     * @param newIndex Novo indice atual
     */
    fun advanceTo(newIndex: Int) {
        _state.update { it.copy(currentIndex = newIndex) }
        prefetchUpTo(newIndex, LOOKAHEAD)
    }

    /**
     * Cancela todas as corrotinas de sintese ativas.
     *
     * Mantem o cache intacto para reuso rapido.
     */
    fun cancelAll() {
        scope.coroutineContext.cancelChildren()
        jobs.clear()
        _state.update { it.copy(isRunning = false) }
        Log.i(TAG, "Pipeline cancelado. ${cache.size} segmentos em cache.")
    }

    /**
     * Libera todos os recursos do pipeline.
     *
     * Cancela corrotinas, limpa cache e invalida o scope.
     * Deve ser chamado quando o TTS e definitivamente parado (ex: ao fechar o app).
     */
    fun release() {
        cancelAll()
        scope.cancel()
        cache.clear()
        allSegments = emptyList()
        _state.value = PipelineState()
        Log.i(TAG, "Pipeline liberado")
    }

    /**
     * Executa a sintese de um unico segmento.
     *
     * Como o [TtsSynthesizer] usa callback JNI (generateWithConfigAndCallback)
     * e o playback e feito diretamente no AudioTrack, este metodo gerencia
     * apenas o ESTADO do segmento no cache.
     *
     * Em uma versao futura que capture samples em memoria, o delay placeholder
     * sera substituido pela chamada real ao synthesizer.
     *
     * @param index Indice do segmento na lista [allSegments]
     */
    private suspend fun synthesizeSegment(index: Int) {
        try {
            val segment = allSegments.getOrNull(index) ?: return
            Log.d(TAG, "Sintetizando segmento $index: ${segment.text.take(50)}...")

            // O TtsSynthesizer.speak() retorna Result<Unit> e toca via callback JNI.
            // Como o playback e feito diretamente no AudioTrack pelo Sherpa-ONNX,
            // o pipeline gerencia o ESTADO e o PREFETCH, nao os samples.
            //
            // Na arquitetura atual:
            // - PlaybackCoordinator chama synthesizer.speak() diretamente para tocar
            // - Este pipeline faz prefetch (pre-sintetiza) para garantir fluidez
            //
            // O delay abaixo simula o tempo de sintese. Em producao, pode ser
            // substituido por uma chamada que realmente dispara a sintese e
            // aguarda completude (quando o TtsSynthesizer suportar).
            delay(50)

            // Obter sample rate do engine para metadados
            val sampleRate = 22050

            cacheMutex.withLock {
                cache[index] = AudioSegment(
                    segmentId = index,
                    samples = FloatArray(0),
                    sampleRate = sampleRate,
                    status = SegmentStatus.READY
                )
            }

            // Atualizar contagem de prontos
            val readyCount = cacheMutex.withLock {
                cache.count { entry -> entry.value.status == SegmentStatus.READY }
            }
            _state.update { it.copy(readyCount = readyCount) }

            Log.d(TAG, "Segmento $index pronto ($readyCount/${allSegments.size})")
        } catch (e: CancellationException) {
            // Cancelado propositalmente — segmento volta a PENDING
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
