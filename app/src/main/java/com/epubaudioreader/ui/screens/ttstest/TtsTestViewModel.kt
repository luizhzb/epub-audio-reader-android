package com.epubaudioreader.ui.screens.ttstest

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.tts.engine.TtsEngine
import com.epubaudioreader.core.tts.model.ModelManager
import com.epubaudioreader.core.tts.model.ModelState
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    companion object {
        private const val TAG = "TtsTestViewModel"
    }

    private val _uiState = MutableStateFlow(TtsTestUiState())
    val uiState: StateFlow<TtsTestUiState> = _uiState.asStateFlow()

    private var speakJob: Job? = null

    init {
        viewModelScope.launch {
            observeModelState()
        }
        viewModelScope.launch {
            delay(300)
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
                    _uiState.update { it.copy(modelStatus = ModelStatus.INITIALIZING) }
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
            val success = modelManager.ensureModelReady()
            if (!success) {
                Log.w(TAG, "ensureModelReady() retornou false")
            }
        }
    }

    fun onTextChange(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    fun speak() {
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            val text = _uiState.value.text
            if (text.isBlank()) return@launch

            if (synthesizer.isPlaying) {
                synthesizer.stop()
                _uiState.update { it.copy(isPlaying = false) }
                return@launch
            }

            _uiState.update { it.copy(isPlaying = true, error = null) }

            try {
                if (!ttsEngine.isInitialized) {
                    val modelDir = modelManager.modelDir.absolutePath
                    val ok = ttsEngine.initialize(modelDir)
                    if (!ok) {
                        _uiState.update {
                            it.copy(isPlaying = false, error = "Falha ao inicializar TTS")
                        }
                        return@launch
                    }
                }

                val result = synthesizer.speak(text, onComplete = {
                    viewModelScope.launch {
                        _uiState.update { it.copy(isPlaying = false) }
                    }
                })

                result.onFailure { e ->
                    _uiState.update {
                        it.copy(isPlaying = false, error = e.message ?: "Erro na sintese")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro em speak: ${e.message}", e)
                _uiState.update {
                    it.copy(isPlaying = false, error = e.message ?: "Erro desconhecido")
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        speakJob?.cancel()
        synthesizer.stop()
    }
}
