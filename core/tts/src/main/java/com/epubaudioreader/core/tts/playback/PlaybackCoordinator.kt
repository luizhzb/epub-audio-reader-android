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
 * Coordenador de playback para leitura de EPUB com TTS.
 *
 * Integra:
 * - SmartTextSegmenter: divide texto em segmentos
 * - AudioSynthesisPipeline: prefetch de segmentos
 * - TtsSynthesizer: sintese e playback via callback JNI
 */
@Singleton
class PlaybackCoordinator @Inject constructor(
    private val segmenter: SmartTextSegmenter,
    private val pipeline: AudioSynthesisPipeline,
    private val synthesizer: TtsSynthesizer
) {
    companion object {
        private const val TAG = "PlaybackCoordinator"
        private const val LOOKAHEAD = 3
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var segments: List<TextSegment> = emptyList()
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Inicia playback a partir de uma lista de paragrafos.
     */
    fun playParagraphs(paragraphs: List<String>, chapterIndex: Int = 0, startParagraph: Int = 0) {
        if (paragraphs.isEmpty()) {
            _state.value = _state.value.copy(error = "Nenhum texto para ler")
            return
        }

        stop()

        // Segmentar texto
        segments = segmenter.segment(paragraphs, chapterIndex = chapterIndex)
        if (segments.isEmpty()) {
            _state.value = _state.value.copy(error = "Nenhum segmento gerado")
            return
        }

        Log.i(TAG, "${segments.size} segmentos gerados de ${paragraphs.size} paragrafos")

        // Iniciar pipeline
        pipeline.start(segments)

        // Calcular indice de inicio baseado no paragrafo
        val startSegmentIndex = findSegmentIndexForParagraph(startParagraph)

        _state.value = PlaybackState(
            isPlaying = true,
            currentSegmentIndex = startSegmentIndex,
            totalSegments = segments.size,
            currentText = segments.getOrNull(startSegmentIndex)?.text ?: "",
            error = null
        )

        // Iniciar playback
        startPlaybackLoop(startSegmentIndex)
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        synthesizer.stop()
        _state.update { it.copy(isPlaying = false) }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        pipeline.cancelAll()
        synthesizer.stop()
        _state.value = PlaybackState()
    }

    fun seekToSegment(index: Int) {
        if (index < 0 || index >= segments.size) return

        playbackJob?.cancel()
        playbackJob = null
        synthesizer.stop()

        _state.update {
            it.copy(
                currentSegmentIndex = index,
                currentText = segments.getOrNull(index)?.text ?: ""
            )
        }

        startPlaybackLoop(index)
    }

    fun nextSegment() {
        val next = _state.value.currentSegmentIndex + 1
        if (next < segments.size) {
            seekToSegment(next)
        }
    }

    fun previousSegment() {
        val prev = _state.value.currentSegmentIndex - 1
        if (prev >= 0) {
            seekToSegment(prev)
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun startPlaybackLoop(startIndex: Int) {
        playbackJob = scope.launch {
            try {
                for (i in startIndex until segments.size) {
                    if (!isActive) break

                    val segment = segments[i]
                    _state.update {
                        it.copy(
                            currentSegmentIndex = i,
                            currentText = segment.text
                        )
                    }

                    // Prefetch proximos segmentos
                    pipeline.prefetchUpTo(i + 1, LOOKAHEAD)

                    Log.d(TAG, "Tocando segmento $i/${segments.size}: ${segment.text.take(40)}...")

                    // Tocar via TtsSynthesizer (callback JNI)
                    val result = synthesizer.speak(
                        text = segment.text,
                        onComplete = null // Nao precisamos de callback, controlamos pelo loop
                    )

                    result.onFailure { e ->
                        Log.e(TAG, "Erro no segmento $i: ${e.message}")
                        _state.update { it.copy(error = e.message) }
                        return@launch
                    }

                    // Aguardar playback terminar (polling)
                    var waitCount = 0
                    while (synthesizer.isPlaying && isActive && waitCount < 600) {
                        delay(100)
                        waitCount++
                    }
                }

                _state.update { it.copy(isPlaying = false) }
                Log.i(TAG, "Playback concluido")

            } catch (e: CancellationException) {
                Log.d(TAG, "Playback cancelado")
            } catch (e: Exception) {
                Log.e(TAG, "Erro no playback: ${e.message}", e)
                _state.update { it.copy(isPlaying = false, error = e.message) }
            }
        }
    }

    private fun findSegmentIndexForParagraph(paragraphIndex: Int): Int {
        // Encontra o primeiro segmento que contem o paragrafo
        return segments.indexOfFirst { it.paragraphIndex >= paragraphIndex }
            .coerceAtLeast(0)
            .coerceAtMost(segments.size - 1)
    }
}
