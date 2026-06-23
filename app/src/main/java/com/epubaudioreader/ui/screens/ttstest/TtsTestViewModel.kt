package com.epubaudioreader.ui.screens.ttstest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.tts.engine.TtsEngine
import com.epubaudioreader.core.tts.model.ModelManager
import com.epubaudioreader.core.tts.model.ModelState
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TtsTestViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val ttsEngine: TtsEngine,
    private val synthesizer: TtsSynthesizer
) : ViewModel() {

    private val _uiState = MutableStateFlow(TtsTestUiState())
    val uiState: StateFlow<TtsTestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeModelState()
            modelManager.checkExistingModel()
        }
    }

    private suspend fun observeModelState() {
        modelManager.state.collect { state ->
            when (state) {
                is ModelState.NotDownloaded -> {
                    _uiState.update { it.copy(modelStatus = ModelStatus.NOT_DOWNLOADED) }
                }
                is ModelState.Copying -> {
                    _uiState.update {
                        it.copy(
                            modelStatus = ModelStatus.COPYING,
                            copyProgress = state.percent / 100f
                        )
                    }
                }
                is ModelState.Initializing -> {
                    _uiState.update { it.copy(modelStatus = ModelStatus.INITIALIZING) }
                }
                is ModelState.Ready -> {
                    // Inicializar engine TTS
                    val initialized = ttsEngine.initialize(state.modelDir)
                    _uiState.update {
                        it.copy(
                            modelStatus = if (initialized) ModelStatus.READY else ModelStatus.ERROR,
                            error = if (!initialized) "Falha ao inicializar TTS" else null
                        )
                    }
                }
                is ModelState.Error -> {
                    _uiState.update {
                        it.copy(modelStatus = ModelStatus.ERROR, error = state.message)
                    }
                }
            }
        }
    }

    fun prepareModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            modelManager.ensureModelReady()
        }
    }

    fun onTextChange(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    fun speak() {
        viewModelScope.launch {
            val text = _uiState.value.text
            if (text.isBlank()) return@launch

            _uiState.update { it.copy(isPlaying = true, error = null) }
            try {
                if (!ttsEngine.isInitialized) {
                    val modelDir = modelManager.modelDir.absolutePath
                    ttsEngine.initialize(modelDir)
                }
                synthesizer.speak(text)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isPlaying = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        synthesizer.stop()
    }
}
