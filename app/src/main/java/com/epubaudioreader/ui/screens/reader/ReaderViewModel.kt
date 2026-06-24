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

    // Channel for one-time navigation events (e.g., advance to next chapter)
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
                Log.d(TAG, "PlaybackState: isPlaying=${playbackState.isPlaying}, " +
                        "isPreparing=${playbackState.isPreparing}, " +
                        "segment=${playbackState.currentSegmentIndex}/${playbackState.totalSegments}, " +
                        "error=${playbackState.error}")

                // Check if TTS finished the last paragraph of the chapter
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
                        // Only update ttsParagraphIndex from TTS callbacks, not from scroll
                        ttsParagraphIndex = if (playbackState.currentSegmentIndex >= 0) {
                            playbackState.currentSegmentIndex
                        } else it.ttsParagraphIndex,
                        currentParagraphIndex = playbackState.currentSegmentIndex,
                        ttsError = playbackState.error ?: it.ttsError
                    )
                }

                // Save progress based on TTS position, not scroll position (BUG-READ-009)
                if (playbackState.isPlaying && playbackState.currentSegmentIndex >= 0 &&
                    currentBookId != 0L && currentChapterId != 0L
                ) {
                    progressFlow.value = Triple(
                        currentBookId,
                        currentChapterId,
                        playbackState.currentSegmentIndex
                    )
                }
            }
        }

        // Observe TTS model loading state
        viewModelScope.launch {
            modelLoader.state.collect { state ->
                val isPrepared = state is ModelLoadState.Ready
                val isPreparing = state is ModelLoadState.Loading || state is ModelLoadState.Copying
                Log.d(TAG, "ModelLoadState: ${state.javaClass.simpleName}, prepared=$isPrepared, preparing=$isPreparing")
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

    override fun onStop(owner: LifecycleOwner) {
        // Pause TTS when the lifecycle owner stops (fragment/activity pause)
        if (_uiState.value.isTtsPlaying) {
            Log.i(TAG, "Lifecycle stopping, pausing TTS")
            playbackCoordinator.pause()
        }
    }

    fun loadChapter(bookId: Long, chapterId: Long) {
        // Prevent reloading the same chapter on rotation (BUG-READ-003)
        if (currentBookId == bookId && currentChapterId == chapterId && paragraphs.isNotEmpty()) {
            Log.d(TAG, "Chapter already loaded, skipping reload")
            return
        }

        // Prevent duplicate load calls (BUG-READ-012)
        if (isLoadingChapter) {
            Log.d(TAG, "Already loading a chapter, skipping duplicate call")
            return
        }

        // Stop TTS when changing chapters
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
        // Only update visibleParagraphIndex for scroll tracking, never currentParagraphIndex (BUG-READ-002)
        _uiState.update { it.copy(visibleParagraphIndex = index) }
    }

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
            Log.i(TAG, "Starting TTS, prepared=${_uiState.value.isTtsPrepared}")
            if (!_uiState.value.isTtsPrepared) {
                _uiState.update { it.copy(isTtsPreparing = true, ttsError = null) }
                val prepared = prepareTts()
                if (!prepared) {
                    Log.e(TAG, "TTS preparation failed")
                    _uiState.update { it.copy(isTtsPreparing = false) }
                    return@launch
                }
            }

            _uiState.update { it.copy(isTtsPreparing = false, ttsError = null) }
            Log.i(TAG, "Calling playParagraphs index=${_uiState.value.currentParagraphIndex}")

            // Wrap playParagraphs in try/catch (BUG-READ-013)
            try {
                playbackCoordinator.playParagraphs(
                    paragraphs = currentParagraphs,
                    startIndex = _uiState.value.currentParagraphIndex.coerceIn(
                        0,
                        currentParagraphs.size - 1
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error starting playback: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isTtsPlaying = false,
                        isTtsPreparing = false,
                        ttsError = e.message ?: "Error starting playback"
                    )
                }
            }
        }
    }

    private suspend fun prepareTts(): Boolean {
        return try {
            if (modelLoader.state.value is ModelLoadState.Ready) {
                // Already ready; ensure AudioTrack is initialized
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
                    _uiState.update { it.copy(ttsError = "Error initializing audio: ${e.message}") }
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
                    _uiState.update { it.copy(ttsError = "Error initializing audio: ${e.message}") }
                    return false
                }
            } else {
                Log.e(TAG, "modelLoader.prepareModel() returned false")
                _uiState.update { it.copy(ttsError = "Failed to prepare TTS model") }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing TTS: ${e.message}", e)
            _uiState.update { it.copy(ttsError = e.message ?: "Error preparing TTS") }
            false
        }
    }

    fun stopTts() {
        playbackCoordinator.stop()
    }

    fun dismissTtsError() {
        _uiState.update { it.copy(ttsError = null) }
    }

    /** Call this when the user leaves the reader screen (BUG-READ-004) */
    fun onLeaveScreen() {
        Log.i(TAG, "Leaving reader screen, stopping TTS")
        playbackCoordinator.stop()
    }

    override fun onCleared() {
        super.onCleared()
        // Only stop playback; do NOT release the singleton coordinator (BUG-READ-011)
        playbackCoordinator.stop()
        // Do NOT release the engine or synthesizer here: they are singletons
        // shared with other screens (e.g., TtsTestScreen).
    }
}
