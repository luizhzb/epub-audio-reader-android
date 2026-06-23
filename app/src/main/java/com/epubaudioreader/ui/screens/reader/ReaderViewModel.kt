package com.epubaudioreader.ui.screens.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.domain.usecase.reader.GetChapterContentUseCase
import com.epubaudioreader.core.domain.usecase.reader.SaveProgressUseCase
import com.epubaudioreader.core.tts.model.ModelAssetLoader
import com.epubaudioreader.core.tts.model.ModelLoadState
import com.epubaudioreader.core.tts.player.EpubTtsPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val getChapterContentUseCase: GetChapterContentUseCase,
    private val saveProgressUseCase: SaveProgressUseCase,
    private val modelLoader: ModelAssetLoader,
    private val ttsPlayer: EpubTtsPlayer
) : ViewModel() {

    companion object {
        private const val TAG = "ReaderViewModel"
    }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBookId: Long = 0L
    private var currentChapterId: Long = 0L
    private val progressFlow = MutableStateFlow<Triple<Long, Long, Int>?>(null)

    init {
        progressFlow
            .filter { it != null }
            .debounce(1000L)
            .onEach { triple ->
                triple?.let { (bookId, chapterId, paragraphIndex) ->
                    saveProgressUseCase(bookId, chapterId, paragraphIndex)
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            ttsPlayer.state.collect { ttsState ->
                _uiState.update {
                    it.copy(
                        isTtsPlaying = ttsState.isPlaying,
                        isTtsPrepared = ttsState.isEngineReady,
                        ttsError = ttsState.error
                    )
                }
            }
        }
    }

    fun loadChapter(bookId: Long, chapterId: Long) {
        currentBookId = bookId
        currentChapterId = chapterId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = getChapterContentUseCase(chapterId)
                if (result != null) {
                    _uiState.update {
                        it.copy(
                            chapterTitle = result.title,
                            paragraphs = result.paragraphs,
                            isLoading = false,
                            currentParagraphIndex = 0
                        )
                    }
                    val chapters = result.paragraphs.map { para ->
                        com.epubaudioreader.core.domain.model.ChapterContent(
                            title = result.title,
                            paragraphs = result.paragraphs,
                            totalChars = para.length,
                            totalParagraphs = result.paragraphs.size
                        )
                    }
                    ttsPlayer.setChapters(chapters)
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Capitulo nao encontrado")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Erro ao carregar")
                }
            }
        }
    }

    fun onParagraphVisible(index: Int) {
        _uiState.update { it.copy(currentParagraphIndex = index) }
        if (currentBookId != 0L && currentChapterId != 0L) {
            progressFlow.value = Triple(currentBookId, currentChapterId, index)
        }
    }

    fun toggleTts() {
        viewModelScope.launch {
            try {
                if (ttsPlayer.state.value.isPlaying) {
                    ttsPlayer.pause()
                } else {
                    if (!ttsPlayer.state.value.isEngineReady) {
                        _uiState.update { it.copy(isLoading = true) }
                        ttsPlayer.prepare()
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    val chapterIdx = 0
                    val paraIdx = _uiState.value.currentParagraphIndex
                    ttsPlayer.play(chapterIdx, paraIdx)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro TTS: ${e.message}", e)
                _uiState.update { it.copy(ttsError = e.message) }
            }
        }
    }

    fun stopTts() {
        ttsPlayer.stop()
    }

    override fun onCleared() {
        super.onCleared()
        ttsPlayer.release()
    }
}
