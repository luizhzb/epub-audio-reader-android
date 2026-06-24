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
 * Responsabilidades:
 * - Pre-carregar o modelo automaticamente na inicializacao do app
 * - Manter estado do modelo acessivel a todas as telas (Library, Reader, TtsTest)
 * - Inicializar AudioTrack automaticamente quando modelo fica pronto
 * - Fornecer retry automatico em caso de erro
 * 
 * Este ViewModel deve ser usado como hiltViewModel() na MainActivity para
 * iniciar o pre-carregamento logo que o app abre.
 * 
 * Uso:
 * ```
 * val ttsModelViewModel: TtsModelViewModel = hiltViewModel()
 * val ttsState by ttsModelViewModel.uiState.collectAsStateWithLifecycle()
 * ```
 */
@HiltViewModel
class TtsModelViewModel @Inject constructor(
    private val modelLoader: ModelAssetLoader,
    private val synthesizer: TtsSynthesizer
) : ViewModel() {

    companion object {
        private const val TAG = "TtsModelViewModel"
    }

    /**
     * Estado da UI para o modelo TTS.
     * Usado principalmente na SplashScreen e para observacao global.
     */
    data class TtsModelUiState(
        val modelStatus: ModelStatus = ModelStatus.NOT_LOADED,
        val copyProgress: Float = 0f,
        val error: String? = null
    )

    /**
     * Status do modelo TTS mapeado de ModelLoadState.
     */
    enum class ModelStatus {
        NOT_LOADED,   // Modelo nao carregado
        COPYING,      // Copiando espeak-ng-data
        LOADING,      // Inicializando engine Sherpa-ONNX
        READY,        // Modelo pronto + AudioTrack inicializado
        ERROR         // Falha no carregamento
    }

    private val _uiState = MutableStateFlow(TtsModelUiState())
    val uiState: StateFlow<TtsModelUiState> = _uiState.asStateFlow()

    init {
        // Observar mudancas no estado do modelo
        viewModelScope.launch {
            modelLoader.state.collect { state ->
                val status = when (state) {
                    is ModelLoadState.NotLoaded -> ModelStatus.NOT_LOADED
                    is ModelLoadState.Copying -> ModelStatus.COPYING
                    is ModelLoadState.Loading -> ModelStatus.LOADING
                    is ModelLoadState.Ready -> {
                        // Inicializar AudioTrack automaticamente quando pronto
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

                Log.d(TAG, "State changed: ${state.javaClass.simpleName} -> $status")
            }
        }

        // Verificar se modelo ja existe (para estado inicial correto)
        viewModelScope.launch {
            modelLoader.checkExistingModel()
        }
    }

    /**
     * Inicializa o AudioTrack quando o modelo fica pronto.
     * Centraliza a inicializacao para evitar duplicacao.
     */
    private fun initializeAudioTrack() {
        try {
            synthesizer.initAudioTrack()
            synthesizer.startPlayback()
            Log.i(TAG, "AudioTrack initialized and started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack: ${e.message}", e)
            _uiState.update {
                it.copy(
                    modelStatus = ModelStatus.ERROR,
                    error = "Erro ao inicializar audio: ${e.message}"
                )
            }
        }
    }

    /**
     * Prepara o modelo se ainda nao estiver pronto.
     * Chamado automaticamente na MainActivity e pode ser chamado
     * manualmente para retry.
     * 
     * Seguro chamar multiplas vezes - ignora se ja esta carregando.
     */
    fun prepareModelIfNeeded() {
        viewModelScope.launch {
            when (_uiState.value.modelStatus) {
                ModelStatus.READY -> {
                    Log.d(TAG, "Modelo ja esta pronto, ignorando")
                    return@launch
                }
                ModelStatus.LOADING, ModelStatus.COPYING -> {
                    Log.d(TAG, "Modelo ja esta carregando, ignorando")
                    return@launch
                }
                else -> {
                    Log.i(TAG, "Iniciando pre-carregamento do modelo TTS...")
                    modelLoader.prepareModel()
                }
            }
        }
    }

    /**
     * Forca recarregamento do modelo (para retry apos erro).
     * Limpa o erro e tenta carregar novamente.
     */
    fun retry() {
        Log.i(TAG, "Retry solicitado pelo usuario")
        _uiState.update { it.copy(error = null) }
        prepareModelIfNeeded()
    }

    override fun onCleared() {
        super.onCleared()
        // NAO liberar engine/synthesizer - sao singletons compartilhados
        // Apenas cancelamos observacoes
    }
}
