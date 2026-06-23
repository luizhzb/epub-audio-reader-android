package com.epubaudioreader.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.domain.usecase.reader.GetChapterContentUseCase
import com.epubaudioreader.core.domain.usecase.reader.SaveProgressUseCase
import com.epubaudioreader.core.tts.engine.TtsEngine
import com.epubaudioreader.core.tts.model.ModelManager
import com.epubaudioreader.core.tts.model.ModelState
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    private val modelManager: ModelManager,
    private val ttsEngine: TtsEngine,
    private val synthesizer: TtsSynthesizer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBookId: Long = 0L
    private var currentChapterId: Long = 0L

    private val progressFlow = MutableStateFlow<Triple<Long, Long, Int>?>(null)

    private var ttsJob: Job? = null

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
            observeModelState()
            modelManager.checkExistingModel()
        }
    }

    private suspend fun observeModelState() {
        modelManager.state.collect { state ->
            when (state) {
                is ModelState.NotDownloaded -> {
                    _uiState.update { it.copy(isModelReady = false) }
                }
                is ModelState.Copying -> {
                    _uiState.update {
                        it.copy(isTtsPreparing = true, copyProgress = state.percent / 100f)
                    }
                }
                is ModelState.Ready -> {
                    val initialized = ttsEngine.initialize(state.modelDir)
                    _uiState.update {
                        it.copy(
                            isModelReady = initialized,
                            isTtsPreparing = false,
                            ttsError = if (!initialized) "Falha ao inicializar engine TTS" else null
                        )
                    }
                }
                is ModelState.Error -> {
                    _uiState.update {
                        it.copy(
                            isTtsPreparing = false,
                            isModelReady = false,
                            ttsError = state.message
                        )
                    }
                }
                is ModelState.Initializing -> {
                    _uiState.update { it.copy(isTtsPreparing = true) }
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
                        it.copy(
                            isLoading = false,
                            error = "Capitulo nao encontrado"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Erro ao carregar capitulo"
                    )
                }
            }
        }
    }

    fun onParagraphVisible(index: Int) {
        _uiState.update { it.copy(currentParagraphIndex = index) }
        if (currentBookId != 0L && currentChapterId != 0L) {
            progressFlow.value = Triple(currentBookId, currentChapterId, index)
        }

        // Se TTS estiver tocando e o paragrafo visivel mudou, reinicia a partir do novo paragrafo
        if (_uiState.value.isTtsPlaying && index != _uiState.value.currentSpeakingParagraph) {
            startTtsFromCurrentParagraph()
        }
    }

    fun toggleTts() {
        if (_uiState.value.isTtsPlaying) {
            stopTts()
        } else {
            startTtsFromCurrentParagraph()
        }
    }

    private fun startTtsFromCurrentParagraph() {
        ttsJob?.cancel()
        ttsJob = viewModelScope.launch {
            // Garante que o modelo esteja pronto
            if (!_uiState.value.isModelReady) {
                _uiState.update { it.copy(isTtsPreparing = true) }
                val ready = modelManager.ensureModelReady()
                if (!ready) {
                    _uiState.update {
                        it.copy(
                            isTtsPreparing = false,
                            ttsError = "Modelo TTS não está disponível"
                        )
                    }
                    return@launch
                }
            }

            // Inicializa o engine se necessario
            if (!ttsEngine.isInitialized) {
                val modelDir = modelManager.modelDir.absolutePath
                val ok = ttsEngine.initialize(modelDir)
                _uiState.update {
                    it.copy(
                        isModelReady = ok,
                        isTtsPreparing = false,
                        ttsError = if (!ok) "Falha ao inicializar engine TTS" else null
                    )
                }
                if (!ok) return@launch
            }

            _uiState.update { it.copy(isTtsPlaying = true, isTtsPreparing = false) }

            val paragraphs = _uiState.value.paragraphs
            var startIndex = _uiState.value.currentParagraphIndex.coerceIn(
                0,
                paragraphs.size - 1
            )

            // Reproduz paragrafo por paragrafo
            while (isActive && startIndex < paragraphs.size) {
                _uiState.update { it.copy(currentSpeakingParagraph = startIndex) }

                val result = synthesizer.speak(paragraphs[startIndex])
                result.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isTtsPlaying = false,
                            ttsError = error.message
                        )
                    }
                    return@launch
                }

                startIndex++
            }

            // Fim da reproducao natural
            _uiState.update {
                it.copy(
                    isTtsPlaying = false,
                    currentSpeakingParagraph = -1
                )
            }
        }
    }

    private fun stopTts() {
        ttsJob?.cancel()
        ttsJob = null
        synthesizer.stop()
        _uiState.update {
            it.copy(
                isTtsPlaying = false,
                currentSpeakingParagraph = -1
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsJob?.cancel()
        synthesizer.stop()
    }
}
