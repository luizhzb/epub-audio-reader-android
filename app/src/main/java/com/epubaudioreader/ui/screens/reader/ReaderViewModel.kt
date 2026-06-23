package com.epubaudioreader.ui.screens.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.domain.usecase.reader.GetChapterContentUseCase
import com.epubaudioreader.core.domain.usecase.reader.SaveProgressUseCase
import com.epubaudioreader.core.tts.playback.PlaybackCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
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
    private val playbackCoordinator: PlaybackCoordinator
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

        // Observar estado do playback
        viewModelScope.launch {
            playbackCoordinator.state.collect { playbackState ->
                _uiState.update {
                    it.copy(
                        isTtsPlaying = playbackState.isPlaying,
                        currentParagraphIndex = playbackState.currentSegmentIndex,
                        ttsError = playbackState.error
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

    /**
     * Toggle TTS playback.
     */
    fun toggleTts() {
        val paragraphs = _uiState.value.paragraphs
        if (paragraphs.isEmpty()) return

        if (_uiState.value.isTtsPlaying) {
            playbackCoordinator.pause()
        } else {
            playbackCoordinator.playParagraphs(
                paragraphs = paragraphs,
                startParagraph = _uiState.value.currentParagraphIndex
            )
        }
    }

    fun stopTts() {
        playbackCoordinator.stop()
    }

    fun nextTtsSegment() {
        playbackCoordinator.nextSegment()
    }

    fun previousTtsSegment() {
        playbackCoordinator.previousSegment()
    }

    override fun onCleared() {
        super.onCleared()
        playbackCoordinator.release()
    }
}
