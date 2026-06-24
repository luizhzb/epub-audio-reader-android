package com.epubaudioreader.ui.screens.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.tts.model.ModelAssetLoader
import com.epubaudioreader.core.tts.model.ModelLoadState
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel compartilhado para gerenciamento do modelo TTS em todo o app.
 *
 * CORRECOES (AUDITORIA 2025-06-25):
 * - initializeAudioTrack() captura Result de initAudioTrack() e startPlayback()
 * - Logs TTS_TRACE detalhados
 */
@HiltViewModel
class TtsModelViewModel @Inject constructor(
    private val modelLoader: ModelAssetLoader,
    private val synthesizer: TtsSynthesizer
) : ViewModel() {

    companion object {
        private const val TAG = "TtsModelViewModel"
    }

    data class TtsModelUiState(
        val modelStatus: ModelStatus = ModelStatus.NOT_LOADED,
        val copyProgress: Float = 0f,
        val error: String? = null
    )

    enum class ModelStatus {
        NOT_LOADED, COPYING, LOADING, READY, ERROR
    }

    private val _uiState = MutableStateFlow(TtsModelUiState())
    val uiState: StateFlow<TtsModelUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelLoader.state.collect { state ->
                val status = when (state) {
                    is ModelLoadState.NotLoaded -> ModelStatus.NOT_LOADED
                    is ModelLoadState.Copying -> ModelStatus.COPYING
                    is ModelLoadState.Loading -> ModelStatus.LOADING
                    is ModelLoadState.Ready -> {
                        initializeAudioTrack()
                        ModelStatus.READY
                    }
                    is ModelLoadState.Error -> ModelStatus.ERROR
                }

                _uiState.update {
                    it.copy(
                        modelStatus = status,
                        copyProgress = if (state is ModelLoadState.Copying) {
                            state.percent / 100f
                        } else it.copyProgress,
                        error = if (state is ModelLoadState.Error) state.message else null
                    )
                }

                Log.d(TAG, "[TTS_TRACE] State: ${state.javaClass.simpleName} -> $status")
            }
        }

        viewModelScope.launch {
            modelLoader.checkExistingModel()
        }
    }

    /**
     * CORRECAO: Captura Result de initAudioTrack() e startPlayback().
     * Nao lanca excecao - retorna erro via StateFlow.
     */
    private fun initializeAudioTrack() {
        Log.i(TAG, "[TTS_TRACE] initializeAudioTrack() INICIO")

        val initResult = synthesizer.initAudioTrack()
        if (initResult.isFailure) {
            val error = initResult.exceptionOrNull()
            Log.e(TAG, "[TTS_TRACE] initAudioTrack() falhou: ${error?.message}")
            _uiState.update {
                it.copy(
                    modelStatus = ModelStatus.ERROR,
                    error = "Erro ao inicializar audio: ${error?.message}"
                )
            }
            return
        }
        Log.i(TAG, "[TTS_TRACE] initAudioTrack() sucesso")

        val startResult = synthesizer.startPlayback()
        if (startResult.isFailure) {
            val error = startResult.exceptionOrNull()
            Log.e(TAG, "[TTS_TRACE] startPlayback() falhou: ${error?.message}")
            _uiState.update {
                it.copy(
                    modelStatus = ModelStatus.ERROR,
                    error = "Erro ao iniciar playback: ${error?.message}"
                )
            }
            return
        }
        Log.i(TAG, "[TTS_TRACE] startPlayback() sucesso")
        Log.i(TAG, "[TTS_TRACE] AudioTrack inicializado com sucesso")
    }

    fun prepareModelIfNeeded() {
        viewModelScope.launch {
            when (_uiState.value.modelStatus) {
                ModelStatus.READY -> {
                    Log.d(TAG, "[TTS_TRACE] Modelo ja pronto, ignorando")
                    return@launch
                }
                ModelStatus.LOADING, ModelStatus.COPYING -> {
                    Log.d(TAG, "[TTS_TRACE] Modelo carregando, ignorando")
                    return@launch
                }
                else -> {
                    Log.i(TAG, "[TTS_TRACE] Iniciando pre-carregamento do modelo TTS...")
                    modelLoader.prepareModel()
                }
            }
        }
    }

    fun retry() {
        Log.i(TAG, "[TTS_TRACE] Retry solicitado")
        _uiState.update { it.copy(error = null) }
        prepareModelIfNeeded()
    }

    override fun onCleared() {
        super.onCleared()
    }
}