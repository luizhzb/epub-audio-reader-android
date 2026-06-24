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
 * ViewModel da tela de leitura com correcoes de crash (AUDITORIA 2025-06-25).
 *
 * CORRECOES:
 * 1. prepareTts() captura Result de initAudioTrack() e startPlayback()
 * 2. Nao chama initAudioTrack() durante sintese ativa (protege contra race condition)
 * 3. Logs TTS_TRACE detalhados em cada ponto do pipeline
 * 4. toggleTts() com try/catch completo
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

        // Observe playback state
        viewModelScope.launch {
            playbackCoordinator.state.collect { playbackState ->
                Log.d(TAG, "[TTS_TRACE] PlaybackState: isPlaying=${playbackState.isPlaying}, " +
                        "segment=${playbackState.currentSegmentIndex}/${playbackState.totalSegments}, " +
                        "error=${playbackState.error}")

                val isLastSegment = playbackState.currentSegmentIndex >= 0 &&
                        playbackState.totalSegments > 0 &&
                        playbackState.currentSegmentIndex >= playbackState.totalSegments - 1

                val wasPlaying = _uiState.value.isTtsPlaying
                if (wasPlaying && !playbackState.isPlaying && isLastSegment && paragraphs.isNotEmpty()) {
                    Log.i(TAG, "[TTS_TRACE] TTS terminou ultimo segmento, avancando capitulo")
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

                if (playbackState.isPlaying && playbackState.currentSegmentIndex >= 0 &&
                    currentBookId != 0L && currentChapterId != 0L
                ) {
                    progressFlow.value = Triple(
                        currentBookId, currentChapterId, playbackState.currentSegmentIndex
                    )
                }
            }
        }

        // Observe TTS model loading state
        viewModelScope.launch {
            modelLoader.state.collect { state ->
                val modelStatus = when (state) {
                    is ModelLoadState.NotLoaded -> ModelStatus.NOT_LOADED
                    is ModelLoadState.Copying -> ModelStatus.COPYING
                    is ModelLoadState.Loading -> ModelStatus.LOADING
                    is ModelLoadState.Ready -> ModelStatus.READY
                    is ModelLoadState.Error -> ModelStatus.ERROR
                }

                val isPrepared = state is ModelLoadState.Ready
                val isPreparing = state is ModelLoadState.Loading || state is ModelLoadState.Copying

                Log.d(TAG, "[TTS_TRACE] ModelLoadState: ${state.javaClass.simpleName} -> " +
                        "prepared=$isPrepared")

                _uiState.update {
                    it.copy(
                        modelStatus = modelStatus,
                        isTtsPrepared = isPrepared,
                        isTtsPreparing = isPreparing,
                        modelCopyProgress = if (state is ModelLoadState.Copying) {
                            state.percent / 100f
                        } else it.modelCopyProgress,
                        ttsError = if (state is ModelLoadState.Error) state.message else it.ttsError
                    )
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (_uiState.value.isTtsPlaying) {
            Log.i(TAG, "[TTS_TRACE] Lifecycle onStop, pausando TTS")
            playbackCoordinator.pause()
        }
    }

    fun loadChapter(bookId: Long, chapterId: Long) {
        if (currentBookId == bookId && currentChapterId == chapterId && paragraphs.isNotEmpty()) {
            return
        }
        if (isLoadingChapter) return

        playbackCoordinator.stop()
        currentBookId = bookId
        currentChapterId = chapterId
        paragraphs = emptyList()
        isLoadingChapter = true

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, currentParagraphIndex = 0,
                    visibleParagraphIndex = 0, ttsParagraphIndex = -1)
            }
            try {
                val result = getChapterContentUseCase(chapterId)
                if (result != null) {
                    Log.i(TAG, "[TTS_TRACE] Capitulo carregado: '${result.title}', ${result.paragraphs.size} paragrafos")
                    paragraphs = result.paragraphs
                    _uiState.update {
                        it.copy(chapterTitle = result.title, paragraphs = result.paragraphs,
                            isLoading = false, currentParagraphIndex = 0,
                            visibleParagraphIndex = 0, ttsParagraphIndex = -1)
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Chapter not found") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[TTS_TRACE] Erro carregando capitulo: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Error loading chapter") }
            } finally {
                isLoadingChapter = false
            }
        }
    }

    fun onParagraphVisible(index: Int) {
        _uiState.update { it.copy(visibleParagraphIndex = index) }
    }

    fun prepareModelExplicitly() {
        viewModelScope.launch {
            Log.i(TAG, "[TTS_TRACE] Preparacao explicita do modelo solicitada pelo usuario")
            _uiState.update { it.copy(isTtsPreparing = true, ttsError = null) }
            val prepared = prepareTts()
            if (!prepared) {
                Log.e(TAG, "[TTS_TRACE] Preparacao explicita falhou")
                if (_uiState.value.ttsError == null) {
                    _uiState.update {
                        it.copy(ttsError = "Falha ao carregar modelo. Toque em 'Carregar Modelo' para tentar novamente.")
                    }
                }
            }
            _uiState.update { it.copy(isTtsPreparing = false) }
        }
    }

    /**
     * CORRECAO: toggleTts() com try/catch completo ao redor de playParagraphs.
     */
    fun toggleTts() {
        val currentParagraphs = _uiState.value.paragraphs
        if (currentParagraphs.isEmpty()) {
            Log.w(TAG, "[TTS_TRACE] toggleTts sem paragrafos")
            return
        }

        if (_uiState.value.isTtsPlaying) {
            Log.i(TAG, "[TTS_TRACE] Pausando TTS")
            playbackCoordinator.pause()
            return
        }

        viewModelScope.launch {
            Log.i(TAG, "[TTS_TRACE] toggleTts: prepared=${_uiState.value.isTtsPrepared}, " +
                    "modelStatus=${_uiState.value.modelStatus}")

            // Verificar erro previo no modelo
            if (_uiState.value.modelStatus == ModelStatus.ERROR) {
                _uiState.update {
                    it.copy(ttsError = "Modelo com erro. Toque em 'Carregar Modelo' para tentar novamente.")
                }
                return@launch
            }

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
                    Log.e(TAG, "[TTS_TRACE] Preparacao TTS falhou")
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
            Log.i(TAG, "[TTS_TRACE] Chamando playParagraphs indice=${_uiState.value.currentParagraphIndex}")

            // CORRECAO: try/catch completo ao redor de playParagraphs
            try {
                playbackCoordinator.playParagraphs(
                    paragraphs = currentParagraphs,
                    startIndex = _uiState.value.currentParagraphIndex.coerceIn(0, currentParagraphs.size - 1)
                )
                Log.i(TAG, "[TTS_TRACE] playParagraphs retornou")
            } catch (e: Exception) {
                Log.e(TAG, "[TTS_TRACE] ERRO em playParagraphs: ${e.message}", e)
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

    /**
     * CORRECAO: prepareTts() captura Result de initAudioTrack() e startPlayback().
     * Nao chama initAudioTrack() durante sintese ativa.
     */
    private suspend fun prepareTts(): Boolean {
        return try {
            if (modelLoader.state.value is ModelLoadState.Ready) {
                if (synthesizer.isAudioTrackInitialized()) {
                    Log.i(TAG, "[TTS_TRACE] Modelo pronto e AudioTrack ja inicializado")
                    return true
                }
                Log.i(TAG, "[TTS_TRACE] Modelo pronto, AudioTrack nao inicializado. Inicializando...")

                // CORRECAO: Capturar Result de initAudioTrack()
                val initResult = synthesizer.initAudioTrack()
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()
                    Log.e(TAG, "[TTS_TRACE] initAudioTrack() falhou: ${error?.message}")
                    _uiState.update { it.copy(ttsError = "Erro ao inicializar audio: ${error?.message}") }
                    return false
                }
                Log.i(TAG, "[TTS_TRACE] initAudioTrack() sucesso")
                return true
            }

            Log.i(TAG, "[TTS_TRACE] Preparando modelo TTS...")
            val success = modelLoader.prepareModel()
            if (success) {
                Log.i(TAG, "[TTS_TRACE] Modelo preparado. Inicializando AudioTrack...")

                // CORRECAO: Capturar Result de initAudioTrack()
                val initResult = synthesizer.initAudioTrack()
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()
                    Log.e(TAG, "[TTS_TRACE] initAudioTrack() apos prepareModel falhou: ${error?.message}")
                    _uiState.update { it.copy(ttsError = "Erro ao inicializar audio: ${error?.message}") }
                    return false
                }
                Log.i(TAG, "[TTS_TRACE] initAudioTrack() apos prepareModel sucesso")
            } else {
                Log.e(TAG, "[TTS_TRACE] modelLoader.prepareModel() retornou false")
                if (_uiState.value.ttsError == null) {
                    _uiState.update { it.copy(ttsError = "Falha ao preparar modelo TTS. Verifique os assets.") }
                }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TRACE] Erro preparando TTS: ${e.message}", e)
            _uiState.update { it.copy(ttsError = e.message ?: "Erro ao preparar TTS") }
            false
        }
    }

    fun stopTts() {
        Log.i(TAG, "[TTS_TRACE] stopTts()")
        playbackCoordinator.stop()
    }

    fun dismissTtsError() {
        _uiState.update { it.copy(ttsError = null) }
    }

    fun onLeaveScreen() {
        Log.i(TAG, "[TTS_TRACE] onLeaveScreen()")
        playbackCoordinator.stop()
    }

    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "[TTS_TRACE] onCleared()")
        playbackCoordinator.stop()
    }
}
