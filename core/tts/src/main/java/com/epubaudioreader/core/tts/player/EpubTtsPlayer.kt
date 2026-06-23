package com.epubaudioreader.core.tts.player

import android.util.Log
import com.epubaudioreader.core.domain.model.ChapterContent
import com.epubaudioreader.core.tts.engine.TtsEngine
import com.epubaudioreader.core.tts.model.ModelAssetLoader
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class TtsPlaybackState(
    val isPlaying: Boolean = false,
    val currentChapterIndex: Int = 0,
    val currentParagraphIndex: Int = 0,
    val currentText: String = "",
    val isEngineReady: Boolean = false,
    val error: String? = null
)

@Singleton
class EpubTtsPlayer @Inject constructor(
    private val modelLoader: ModelAssetLoader,
    private val ttsEngine: TtsEngine,
    private val synthesizer: TtsSynthesizer
) {
    companion object {
        private const val TAG = "EpubTtsPlayer"
    }

    private val _state = MutableStateFlow(TtsPlaybackState())
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    private var chapters: List<ChapterContent> = emptyList()
    private var playbackJob: Job? = null

    fun setChapters(newChapters: List<ChapterContent>) {
        chapters = newChapters
    }

    suspend fun prepare(): Boolean {
        return try {
            val result = modelLoader.prepareModel()
            if (result) {
                synthesizer.initAudioTrack()
                synthesizer.startPlayback()
            }
            _state.update { it.copy(isEngineReady = result) }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao preparar: ${e.message}", e)
            _state.update { it.copy(error = e.message) }
            false
        }
    }

    fun play(chapterIndex: Int, startParagraph: Int = 0) {
        if (chapters.isEmpty()) {
            _state.value = _state.value.copy(error = "Nenhum capitulo carregado")
            return
        }
        if (!ttsEngine.isInitialized) {
            _state.value = _state.value.copy(error = "Engine nao inicializado. Toque Preparar primeiro.")
            return
        }

        stop()

        playbackJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                _state.update {
                    it.copy(isPlaying = true, currentChapterIndex = chapterIndex, error = null)
                }

                for (cIndex in chapterIndex until chapters.size) {
                    if (!isActive) break
                    val chapter = chapters[cIndex]
                    val startIdx = if (cIndex == chapterIndex) startParagraph else 0

                    for (pIndex in startIdx until chapter.paragraphs.size) {
                        if (!isActive) break

                        val text = chapter.paragraphs[pIndex]
                        _state.update {
                            it.copy(
                                currentChapterIndex = cIndex,
                                currentParagraphIndex = pIndex,
                                currentText = text
                            )
                        }

                        Log.d(TAG, "Paragrafo $pIndex: ${text.take(40)}...")

                        val result = synthesizer.speak(text)
                        result.onFailure { e ->
                            Log.e(TAG, "Erro: ${e.message}")
                            _state.update { it.copy(isPlaying = false, error = e.message) }
                            return@launch
                        }

                        // Aguardar playback terminar
                        var waitCount = 0
                        while (synthesizer.isPlaying && isActive && waitCount < 500) {
                            delay(100)
                            waitCount++
                        }
                    }
                }

                _state.update { it.copy(isPlaying = false) }
                Log.d(TAG, "Reproducao concluida")
            } catch (e: CancellationException) {
                Log.d(TAG, "Reproducao cancelada")
                _state.update { it.copy(isPlaying = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}", e)
                _state.update { it.copy(isPlaying = false, error = e.message) }
            }
        }
    }

    fun pause() {
        stop()
        _state.update { it.copy(isPlaying = false) }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        synthesizer.stop()
    }

    fun nextParagraph() {
        val s = _state.value
        val chapter = chapters.getOrNull(s.currentChapterIndex) ?: return
        if (s.currentParagraphIndex < chapter.paragraphs.size - 1) {
            play(s.currentChapterIndex, s.currentParagraphIndex + 1)
        } else if (s.currentChapterIndex < chapters.size - 1) {
            play(s.currentChapterIndex + 1, 0)
        }
    }

    fun previousParagraph() {
        val s = _state.value
        if (s.currentParagraphIndex > 0) {
            play(s.currentChapterIndex, s.currentParagraphIndex - 1)
        } else if (s.currentChapterIndex > 0) {
            val prevChapter = chapters[s.currentChapterIndex - 1]
            play(s.currentChapterIndex - 1, prevChapter.paragraphs.size - 1)
        }
    }

    fun release() {
        stop()
    }
}
