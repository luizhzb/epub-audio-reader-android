package com.epubaudioreader.ui.screens.ttstest

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.tts.engine.TtsEngine
import com.epubaudioreader.core.tts.model.ModelAssetLoader
import com.epubaudioreader.core.tts.model.ModelLoadState
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TtsTestViewModel @Inject constructor(
    private val modelLoader: ModelAssetLoader,
    private val ttsEngine: TtsEngine,
    private val synthesizer: TtsSynthesizer
) : ViewModel() {

    companion object {
        private const val TAG = "TtsTestViewModel"
    }

    val uiState = kotlinx.coroutines.flow.MutableStateFlow(TtsTestUiState())

    private var speakJob: Job? = null

    init {
        // Observar estado do modelo
        viewModelScope.launch {
            modelLoader.state.collect { state ->
                val status = when (state) {
                    is ModelLoadState.NotLoaded -> ModelStatus.NOT_LOADED
                    is ModelLoadState.Copying -> ModelStatus.COPYING
                    is ModelLoadState.Loading -> ModelStatus.LOADING
                    is ModelLoadState.Ready -> {
                        // Inicializar AudioTrack quando TTS fica pronto
                        try {
                            synthesizer.initAudioTrack()
                            synthesizer.startPlayback()
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao iniciar AudioTrack: ${e.message}", e)
                        }
                        ModelStatus.READY
                    }
                    is ModelLoadState.Error -> ModelStatus.ERROR
                }
                uiState.value = uiState.value.copy(
                    modelStatus = status,
                    copyProgress = if (state is ModelLoadState.Copying) state.percent / 100f else uiState.value.copyProgress,
                    error = if (state is ModelLoadState.Error) state.message else uiState.value.error
                )
            }
        }

        viewModelScope.launch {
            modelLoader.checkExistingModel()
        }
    }

    fun prepareModel() {
        viewModelScope.launch {
            uiState.value = uiState.value.copy(error = null)
            modelLoader.prepareModel()
        }
    }

    fun onTextChange(text: String) {
        uiState.value = uiState.value.copy(text = text)
    }

    fun speak() {
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            val text = uiState.value.text
            if (text.isBlank()) return@launch

            if (synthesizer.isPlaying) {
                synthesizer.stop()
                uiState.value = uiState.value.copy(isPlaying = false)
                return@launch
            }

            uiState.value = uiState.value.copy(isPlaying = true, error = null)

            try {
                if (!ttsEngine.isInitialized) {
                    val prepared = modelLoader.prepareModel()
                    if (!prepared) {
                        uiState.value = uiState.value.copy(
                            isPlaying = false,
                            error = "Falha ao preparar modelo TTS"
                        )
                        return@launch
                    }
                }

                val result = synthesizer.speak(text, onComplete = {
                    viewModelScope.launch {
                        uiState.value = uiState.value.copy(isPlaying = false)
                    }
                })

                result.onFailure { e ->
                    uiState.value = uiState.value.copy(
                        isPlaying = false,
                        error = e.message ?: "Erro na sintese"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro em speak: ${e.message}", e)
                uiState.value = uiState.value.copy(
                    isPlaying = false,
                    error = e.message ?: "Erro desconhecido"
                )
            }
        }
    }

    fun dismissError() {
        uiState.value = uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        speakJob?.cancel()
        // NAO liberar o engine/synthesizer aqui: eles sao singletons compartilhados
        // com outras telas (ex: ReaderScreen). Apenas cancelamos a fala atual.
        synthesizer.stop()
    }
}
