package com.epubaudioreader.ui.screens.reader

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel da tela de leitura com melhorias de diagnostico TTS.
 * 
 * Melhorias implementadas:
 * 1. Mapeia ModelLoadState para ModelStatus com progresso de copia
 * 2. Verifica erro previo no modelo antes de iniciar TTS
 * 3. Garante mensagem de erro amigavel quando preparacao falha
 * 4. Metodo publico prepareModelExplicitly() para botao "Carregar Modelo"
 * 5. Mensagens de erro claras em portugues para o usuario
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val getChapterContentUseCase: GetChapterContentUseCase,
    private val saveProgressUseCase: SaveProgressUseCase,
    private val playbackCoordinator: PlaybackCoordinator,
    private val modelLoader: ModelAssetLoader,
    private val synthesizer: TtsSynthesizer
) : ViewModel(), DefaultLifecycleObserver {

    companion object {
        private const val TAG = "ReaderViewModel"
    }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBookId: Long = 0L
    private var currentChapterId: Long = 0L
    private var paragraphs: List<String> = emptyList()
    private var isLoadingChapter: Boolean = false
    private val progressFlow = MutableStateFlow<Triple<Long, Long, Int>?>(null)

    // Channel for one-time navigation events
    private val _navigationEvents = Channel<ReaderNavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    sealed class ReaderNavigationEvent {
        data class AdvanceToNextChapter(val currentChapterId: Long) : ReaderNavigationEvent()
    }

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

        // === MELHORIA: Observe playback state ===
        viewModelScope.launch {
            playbackCoordinator.state.collect { playbackState ->
                Log.d(TAG, "PlaybackState: isPlaying=${playbackState.isPlaying}, " +
                        "isPreparing=${playbackState.isPreparing}, " +
                        "segment=${playbackState.currentSegmentIndex}/${playbackState.totalSegments}, " +
                        "error=${playbackState.error}")

                val isLastSegment = playbackState.currentSegmentIndex >= 0 &&
                        playbackState.totalSegments > 0 &&
                        playbackState.currentSegmentIndex >= playbackState.totalSegments - 1

                val wasPlaying = _uiState.value.isTtsPlaying
                if (wasPlaying && !playbackState.isPlaying && isLastSegment && paragraphs.isNotEmpty()) {
                    Log.i(TAG, "TTS finished last segment, advancing to next chapter")
                    _navigationEvents.send(
                        ReaderNavigationEvent.AdvanceToNextChapter(currentChapterId)
                    )
                }

                _uiState.update {
                    it.copy(
                        isTtsPlaying = playbackState.isPlaying,
                        isTtsPreparing = playbackState.isPreparing,
                        ttsParagraphIndex = if (playbackState.currentSegmentIndex >= 0) {
                            playbackState.currentSegmentIndex
                        } else it.ttsParagraphIndex,
                        currentParagraphIndex = playbackState.currentSegmentIndex,
                        ttsError = playbackState.error ?: it.ttsError
                    )
                }

                // Save progress based on TTS position
                if (playbackState.isPlaying && playbackState.currentSegmentIndex >= 0 &&
                    currentBookId != 0L && currentChapterId != 0L
                ) {
                    progressFlow.value = Triple(
                        currentBookId, currentChapterId, playbackState.currentSegmentIndex
                    )
                }
            }
        }

        // === MELHORIA: Observe TTS model loading state com ModelStatus ===
        viewModelScope.launch {
            modelLoader.state.collect { state ->
                // Mapeia ModelLoadState para ModelStatus amigavel a UI
                val modelStatus = when (state) {
                    is ModelLoadState.NotLoaded -> ModelStatus.NOT_LOADED
                    is ModelLoadState.Copying -> ModelStatus.COPYING
                    is ModelLoadState.Loading -> ModelStatus.LOADING
                    is ModelLoadState.Ready -> ModelStatus.READY
                    is ModelLoadState.Error -> ModelStatus.ERROR
                }

                val isPrepared = state is ModelLoadState.Ready
                val isPreparing = state is ModelLoadState.Loading || state is ModelLoadState.Copying

                Log.d(TAG, "ModelLoadState: ${state.javaClass.simpleName} -> " +
                        "ModelStatus=$modelStatus, prepared=$isPrepared, preparing=$isPreparing")

                _uiState.update {
                    it.copy(
                        modelStatus = modelStatus,
                        isTtsPrepared = isPrepared,
                        isTtsPreparing = isPreparing,
                        modelCopyProgress = if (state is ModelLoadState.Copying) {
                            state.percent / 100f
                        } else it.modelCopyProgress,
                        ttsError = if (state is ModelLoadState.Error) {
                            state.message
                        } else it.ttsError
                    )
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (_uiState.value.isTtsPlaying) {
            Log.i(TAG, "Lifecycle stopping, pausing TTS")
            playbackCoordinator.pause()
        }
    }

    fun loadChapter(bookId: Long, chapterId: Long) {
        if (currentBookId == bookId && currentChapterId == chapterId && paragraphs.isNotEmpty()) {
            Log.d(TAG, "Chapter already loaded, skipping reload")
            return
        }
        if (isLoadingChapter) {
            Log.d(TAG, "Already loading a chapter, skipping duplicate call")
            return
        }

        playbackCoordinator.stop()
        currentBookId = bookId
        currentChapterId = chapterId
        paragraphs = emptyList()
        isLoadingChapter = true

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    currentParagraphIndex = 0,
                    visibleParagraphIndex = 0,
                    ttsParagraphIndex = -1
                )
            }
            try {
                val result = getChapterContentUseCase(chapterId)
                if (result != null) {
                    Log.i(TAG, "Chapter loaded: '${result.title}', ${result.paragraphs.size} paragraphs")
                    paragraphs = result.paragraphs
                    _uiState.update {
                        it.copy(
                            chapterTitle = result.title,
                            paragraphs = result.paragraphs,
                            isLoading = false,
                            currentParagraphIndex = 0,
                            visibleParagraphIndex = 0,
                            ttsParagraphIndex = -1
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Chapter not found")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chapter: ${e.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Error loading chapter")
                }
            } finally {
                isLoadingChapter = false
            }
        }
    }

    fun onParagraphVisible(index: Int) {
        _uiState.update { it.copy(visibleParagraphIndex = index) }
    }

    // === MELHORIA: Metodo publico para botao "Carregar Modelo" ===
    fun prepareModelExplicitly() {
        viewModelScope.launch {
            Log.i(TAG, "Preparacao explicita do modelo solicitada pelo usuario")
            _uiState.update { it.copy(isTtsPreparing = true, ttsError = null) }
            val prepared = prepareTts()
            if (!prepared) {
                Log.e(TAG, "Preparacao explicita falhou")
                // Garante mensagem de erro amigavel
                if (_uiState.value.ttsError == null) {
                    _uiState.update {
                        it.copy(ttsError = "Falha ao carregar modelo. Toque em 'Carregar Modelo' para tentar novamente.")
                    }
                }
            }
            _uiState.update { it.copy(isTtsPreparing = false) }
        }
    }

    // === MELHORIA: ToggleTTS com verificacoes de erro ===
    fun toggleTts() {
        val currentParagraphs = _uiState.value.paragraphs
        if (currentParagraphs.isEmpty()) {
            Log.w(TAG, "toggleTts with no paragraphs")
            return
        }

        if (_uiState.value.isTtsPlaying) {
            Log.i(TAG, "Pausing TTS")
            playbackCoordinator.pause()
            return
        }

        viewModelScope.launch {
            Log.i(TAG, "Starting TTS, prepared=${_uiState.value.isTtsPrepared}, " +
                    "modelStatus=${_uiState.value.modelStatus}")

            // MELHORIA: Verificar se ha erro previo no modelo
            if (_uiState.value.modelStatus == ModelStatus.ERROR) {
                _uiState.update {
                    it.copy(ttsError = "Modelo com erro. Toque em 'Carregar Modelo' para tentar novamente.")
                }
                return@launch
            }

            // MELHORIA: Verificar se modelo esta em estado invalido
            if (_uiState.value.modelStatus == ModelStatus.NOT_LOADED) {
                _uiState.update {
                    it.copy(ttsError = "Modelo nao carregado. Toque em 'Carregar Modelo' primeiro.")
                }
                return@launch
            }

            if (!_uiState.value.isTtsPrepared) {
                _uiState.update { it.copy(isTtsPreparing = true, ttsError = null) }
                val prepared = prepareTts()
                if (!prepared) {
                    Log.e(TAG, "TTS preparation failed")
                    // MELHORIA: Garante mensagem de erro amigavel
                    if (_uiState.value.ttsError == null) {
                        _uiState.update {
                            it.copy(ttsError = "Falha ao preparar o modelo TTS. Toque em 'Carregar Modelo'.")
                        }
                    }
                    _uiState.update { it.copy(isTtsPreparing = false) }
                    return@launch
                }
            }

            _uiState.update { it.copy(isTtsPreparing = false, ttsError = null) }
            Log.i(TAG, "Calling playParagraphs index=${_uiState.value.currentParagraphIndex}")

            try {
                playbackCoordinator.playParagraphs(
                    paragraphs = currentParagraphs,
                    startIndex = _uiState.value.currentParagraphIndex.coerceIn(
                        0, currentParagraphs.size - 1
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error starting playback: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isTtsPlaying = false,
                        isTtsPreparing = false,
                        ttsError = e.message ?: "Erro ao iniciar reproducao"
                    )
                }
            }
        }
    }

    private suspend fun prepareTts(): Boolean {
        return try {
            if (modelLoader.state.value is ModelLoadState.Ready) {
                if (synthesizer.isAudioTrackInitialized()) {
                    Log.i(TAG, "Model ready and AudioTrack initialized")
                    return true
                }
                Log.i(TAG, "Model ready, but AudioTrack not initialized. Initializing...")
                try {
                    synthesizer.initAudioTrack()
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing AudioTrack: ${e.message}", e)
                    _uiState.update {
                        it.copy(ttsError = "Erro ao inicializar audio: ${e.message}")
                    }
                    return false
                }
            }

            Log.i(TAG, "Preparing TTS model...")
            val success = modelLoader.prepareModel()
            if (success) {
                Log.i(TAG, "Model prepared. Initializing AudioTrack...")
                try {
                    synthesizer.initAudioTrack()
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing AudioTrack: ${e.message}", e)
                    _uiState.update {
                        it.copy(ttsError = "Erro ao inicializar audio: ${e.message}")
                    }
                    return false
                }
            } else {
                Log.e(TAG, "modelLoader.prepareModel() returned false")
                if (_uiState.value.ttsError == null) {
                    _uiState.update {
                        it.copy(ttsError = "Falha ao preparar modelo TTS. Verifique os assets.")
                    }
                }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing TTS: ${e.message}", e)
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

    fun onLeaveScreen() {
        Log.i(TAG, "Leaving reader screen, stopping TTS")
        playbackCoordinator.stop()
    }

    override fun onCleared() {
        super.onCleared()
        playbackCoordinator.stop()
    }
}
