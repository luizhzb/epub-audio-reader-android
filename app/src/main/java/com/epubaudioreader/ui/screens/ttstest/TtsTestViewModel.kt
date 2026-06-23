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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TtsTestViewModel"

@HiltViewModel
class TtsTestViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val ttsEngine: TtsEngine,
    private val synthesizer: TtsSynthesizer
) : ViewModel() {

    private val _uiState = MutableStateFlow(TtsTestUiState())
    val uiState: StateFlow<TtsTestUiState> = _uiState.asStateFlow()

    /** Guarda o Job de speak() ativo para permitir cancelamento se o usuario clicar multiplas vezes */
    private var speakJob: Job? = null

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

    /**
     * Inicia a sintese de voz do texto atual.
     *
     * Correcoes aplicadas:
     * 1. Cancela job anterior se o usuario clicar multiplas vezes (evita leaks e estados conflitantes)
     * 2. Trata o Result retornado por synthesizer.speak() - em caso de falha, mostra erro na UI
     * 3. Se ttsEngine.initialize() retornar false, mostra erro e aborta
     * 4. Em qualquer erro (catch ou onFailure), seta isPlaying = false e popula a mensagem de erro
     * 5. Garante que isPlaying = false no finally para nao deixar a UI travada
     */
    fun speak() {
        // Cancela job anterior se existir - evita multiplas sinteses simultaneas
        speakJob?.cancel()

        speakJob = viewModelScope.launch {
            val text = _uiState.value.text
            if (text.isBlank()) {
                Log.w(TAG, "speak() chamado com texto em branco - ignorando")
                return@launch
            }

            // Se ja esta tocando, parar primeiro antes de iniciar nova sintese
            if (_uiState.value.isPlaying) {
                Log.d(TAG, "Ja estava tocando, parando sintese anterior")
                synthesizer.stop()
            }

            _uiState.update { it.copy(isPlaying = true, error = null) }
            Log.d(TAG, "Iniciando sintese para texto: ${text.take(50)}...")

            try {
                // Inicializa engine se necessario
                if (!ttsEngine.isInitialized) {
                    val modelDir = modelManager.modelDir.absolutePath
                    val ok = ttsEngine.initialize(modelDir)
                    if (!ok) {
                        Log.e(TAG, "Falha ao inicializar TTS engine")
                        _uiState.update {
                            it.copy(isPlaying = false, error = "Falha ao inicializar TTS")
                        }
                        return@launch
                    }
                    Log.d(TAG, "TTS engine inicializado com sucesso")
                }

                // Executa sintese e trata o Result retornado
                val result = synthesizer.speak(text)
                result.onSuccess {
                    Log.d(TAG, "Sintese concluida com sucesso")
                }.onFailure { e ->
                    Log.e(TAG, "Erro na sintese: ${e.message}", e)
                    _uiState.update {
                        it.copy(isPlaying = false, error = e.message ?: "Erro na sintese de voz")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excecao durante sintese: ${e.message}", e)
                _uiState.update {
                    it.copy(isPlaying = false, error = e.message ?: "Erro desconhecido")
                }
            } finally {
                // So atualiza isPlaying se nao houve erro (em erro ja foi atualizado acima)
                // Isso evita sobrescrever o estado de erro
                val currentError = _uiState.value.error
                if (currentError == null) {
                    _uiState.update { it.copy(isPlaying = false) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared - liberando recursos")
        speakJob?.cancel()
        synthesizer.stop()
    }
}
