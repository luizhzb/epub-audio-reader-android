package com.epubaudioreader.ui.screens.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.domain.usecase.reader.GetChapterContentUseCase
import com.epubaudioreader.core.domain.usecase.reader.SaveProgressUseCase
import com.epubaudioreader.core.tts.playback.PlaybackCoordinator
import com.epubaudioreader.core.tts.playback.PlaybackState
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
    private val coordinator: PlaybackCoordinator
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
        // Debounced auto-save de progresso de leitura
        progressFlow
            .filter { it != null }
            .debounce(1000L)
            .onEach { triple ->
                triple?.let { (bookId, chapterId, paragraphIndex) ->
                    saveProgressUseCase(bookId, chapterId, paragraphIndex)
                }
            }
            .launchIn(viewModelScope)

        // Observa estado do PlaybackCoordinator e replica na UI
        viewModelScope.launch {
            coordinator.state.collect { playbackState ->
                _uiState.update {
                    it.copy(
                        isTtsPlaying = playbackState.isPlaying,
                        isTtsPrepared = !playbackState.isPreparing,
                        ttsError = playbackState.error
                    )
                }
            }
        }
    }

    fun loadChapter(bookId: Long, chapterId: Long) {
        // Parar TTS ao mudar de capitulo
        playbackCoordinator.stop()

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
                        it.copy(isLoading = false, error = "Capítulo não encontrado")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar capítulo: ${e.message}", e)
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
     * Toggle TTS playback via PlaybackCoordinator.
     * Se estiver tocando → pausa.
     * Se estiver parado → inicia a partir do parágrafo atual.
     */
    fun toggleTts() {
        viewModelScope.launch {
            try {
                val paragraphs = _uiState.value.paragraphs
                if (paragraphs.isEmpty()) {
                    _uiState.update { it.copy(ttsError = "Nenhum texto para leitura") }
                    return@launch
                }

                if (coordinator.state.value.isPlaying) {
                    coordinator.pause()
                } else {
                    _uiState.update { it.copy(isLoading = true) }
                    val startIndex = _uiState.value.currentParagraphIndex
                    coordinator.playParagraphs(paragraphs, startIndex)
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro TTS toggle: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, ttsError = e.message) }
            }
        }
    }

    fun stopTts() {
        coordinator.stop()
    }

    override fun onCleared() {
        super.onCleared()
        coordinator.release()
    }
}
