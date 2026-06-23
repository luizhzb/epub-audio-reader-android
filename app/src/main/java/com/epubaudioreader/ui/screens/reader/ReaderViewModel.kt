package com.epubaudioreader.ui.screens.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.domain.usecase.reader.GetChapterContentUseCase
import com.epubaudioreader.core.domain.usecase.reader.SaveProgressUseCase
import com.epubaudioreader.core.tts.model.ModelAssetLoader
import com.epubaudioreader.core.tts.model.ModelLoadState
import com.epubaudioreader.core.tts.playback.PlaybackCoordinator
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
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
    private val playbackCoordinator: PlaybackCoordinator,
    private val modelLoader: ModelAssetLoader,
    private val synthesizer: TtsSynthesizer
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

        // Observar estado de preparacao do modelo TTS
        viewModelScope.launch {
            modelLoader.state.collect { state ->
                val isPrepared = state is ModelLoadState.Ready
                val isPreparing = state is ModelLoadState.Loading || state is ModelLoadState.Copying
                _uiState.update {
                    it.copy(
                        isTtsPrepared = isPrepared,
                        isTtsPreparing = isPreparing,
                        ttsError = if (state is ModelLoadState.Error) state.message else it.ttsError
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
        val paragraphs = _uiState.value.paragraphs
        if (paragraphs.isEmpty()) return

        if (_uiState.value.isTtsPlaying) {
            playbackCoordinator.pause()
            return
        }

        viewModelScope.launch {
            if (!_uiState.value.isTtsPrepared) {
                _uiState.update { it.copy(isTtsPreparing = true, ttsError = null) }
                val prepared = prepareTts()
                if (!prepared) {
                    _uiState.update { it.copy(isTtsPreparing = false) }
                    return@launch
                }
            }

            _uiState.update { it.copy(isTtsPreparing = false, ttsError = null) }
            playbackCoordinator.playParagraphs(
                paragraphs = paragraphs,
                startIndex = _uiState.value.currentParagraphIndex
            )
        }
    }

    private suspend fun prepareTts(): Boolean {
        return try {
            if (modelLoader.state.value is ModelLoadState.Ready) {
                // Ja pronto; garante AudioTrack inicializado
                if (synthesizer.isAudioTrackInitialized()) {
                    return true
                }
            }

            val success = modelLoader.prepareModel()
            if (success) {
                try {
                    synthesizer.initAudioTrack()
                    synthesizer.startPlayback()
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao iniciar AudioTrack: ${e.message}", e)
                    _uiState.update { it.copy(ttsError = "Erro ao iniciar audio: ${e.message}") }
                    return false
                }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao preparar TTS: ${e.message}", e)
            _uiState.update { it.copy(ttsError = e.message ?: "Erro ao preparar TTS") }
            false
        }
    }

    fun stopTts() {
        playbackCoordinator.stop()
    }

    fun dismissTtsError() {
        _uiState.update { it.copy(ttsError = null) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackCoordinator.release()
        // NAO liberar o engine nem o synthesizer aqui: eles sao singletons
        // compartilhados com outras telas (ex: TtsTestScreen).
    }
}
