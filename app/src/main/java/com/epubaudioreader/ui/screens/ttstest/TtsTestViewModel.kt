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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel da tela de teste TTS com correcoes de crash (AUDITORIA 2025-06-25).
 *
 * CORRECOES:
 * - Captura Result de initAudioTrack() e startPlayback() (nao lanca excecao)
 * - Logs TTS_TRACE detalhados em cada ponto critico
 * - Try/catch completo em speak()
 */
@HiltViewModel
class TtsTestViewModel @Inject constructor(
    private val modelLoader: ModelAssetLoader,
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
            modelLoader.state.collect { state ->
                val status = when (state) {
                    is ModelLoadState.NotLoaded -> ModelStatus.NOT_LOADED
                    is ModelLoadState.Copying -> ModelStatus.COPYING
                    is ModelLoadState.Loading -> ModelStatus.LOADING
                    is ModelLoadState.Ready -> {
                        Log.i(TAG, "[TTS_TRACE] Modelo pronto, inicializando AudioTrack...")
                        val initResult = synthesizer.initAudioTrack()
                        if (initResult.isSuccess) {
                            val startResult = synthesizer.startPlayback()
                            if (startResult.isFailure) {
                                Log.e(TAG, "[TTS_TRACE] startPlayback() falhou: ${startResult.exceptionOrNull()?.message}")
                                _uiState.value = _uiState.value.copy(
                                    error = "Erro ao iniciar audio: ${startResult.exceptionOrNull()?.message}"
                                )
                            } else {
                                Log.i(TAG, "[TTS_TRACE] AudioTrack inicializado com sucesso")
                            }
                        } else {
                            Log.e(TAG, "[TTS_TRACE] initAudioTrack() falhou: ${initResult.exceptionOrNull()?.message}")
                            _uiState.value = _uiState.value.copy(
                                error = "Erro ao inicializar audio: ${initResult.exceptionOrNull()?.message}"
                            )
                        }
                        ModelStatus.READY
                    }
                    is ModelLoadState.Error -> ModelStatus.ERROR
                }
                _uiState.value = _uiState.value.copy(
                    modelStatus = status,
                    copyProgress = if (state is ModelLoadState.Copying) state.percent / 100f else _uiState.value.copyProgress,
                    error = if (state is ModelLoadState.Error) state.message else _uiState.value.error
                )
            }
        }

        viewModelScope.launch {
            modelLoader.checkExistingModel()
        }
    }

    fun prepareModel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            modelLoader.prepareModel()
        }
    }

    fun onTextChange(text: String) {
        _uiState.value = _uiState.value.copy(text = text)
    }

    fun speak() {
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            val text = _uiState.value.text
            if (text.isBlank()) return@launch

            Log.i(TAG, "[TTS_TRACE] speak() INICIO: '${text.take(60)}...'")

            if (synthesizer.isPlaying) {
                Log.d(TAG, "[TTS_TRACE] Parando sintese atual")
                synthesizer.stop()
                _uiState.value = _uiState.value.copy(isPlaying = false)
                return@launch
            }

            _uiState.value = _uiState.value.copy(isPlaying = true, error = null)

            try {
                if (!ttsEngine.isInitialized) {
                    Log.i(TAG, "[TTS_TRACE] Engine nao inicializado, preparando modelo...")
                    val prepared = modelLoader.prepareModel()
                    if (!prepared) {
                        Log.e(TAG, "[TTS_TRACE] prepareModel() retornou false")
                        _uiState.value = _uiState.value.copy(
                            isPlaying = false,
                            error = "Falha ao preparar modelo TTS"
                        )
                        return@launch
                    }
                    Log.i(TAG, "[TTS_TRACE] Modelo preparado, inicializando AudioTrack...")
                    val initResult = synthesizer.initAudioTrack()
                    if (initResult.isFailure) {
                        _uiState.value = _uiState.value.copy(
                            isPlaying = false,
                            error = "Falha ao inicializar audio: ${initResult.exceptionOrNull()?.message}"
                        )
                        return@launch
                    }
                }

                Log.i(TAG, "[TTS_TRACE] Chamando synthesizer.speak()...")
                val result = synthesizer.speak(text, onComplete = {
                    viewModelScope.launch {
                        Log.i(TAG, "[TTS_TRACE] speak() onComplete chamado")
                        _uiState.value = _uiState.value.copy(isPlaying = false)
                    }
                })

                result.onSuccess {
                    Log.i(TAG, "[TTS_TRACE] speak() concluido com sucesso")
                }
                result.onFailure { e ->
                    Log.e(TAG, "[TTS_TRACE] speak() falhou: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isPlaying = false,
                        error = e.message ?: "Erro na sintese"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "[TTS_TRACE] ERRO em speak(): ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    error = e.message ?: "Erro desconhecido"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        speakJob?.cancel()
        synthesizer.stop()
    }
}

/** Estados do modelo TTS */
enum class ModelStatus {
    NOT_LOADED, COPYING, LOADING, READY, ERROR
}

/** Estado da UI da tela de teste TTS */
data class TtsTestUiState(
    val text: String = "Ola! Este e um teste de sintese de voz.",
    val isPlaying: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.NOT_LOADED,
    val copyProgress: Float = 0f,
    val error: String? = null
)