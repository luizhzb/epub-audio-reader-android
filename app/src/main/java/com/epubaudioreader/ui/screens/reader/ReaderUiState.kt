package com.epubaudioreader.ui.screens.reader

/**
 * UI State do ReaderScreen com melhorias de diagnostico TTS.
 * 
 * Melhorias:
 * - Adicionado modelStatus: reflete o estado atual do modelo TTS na UI
 * - Adicionado modelCopyProgress: percentual durante copia do modelo
 * 
 * Estados do modelo:
 * - NOT_LOADED: Modelo nunca foi carregado (FAB desabilitado, mostra card)
 * - COPYING:    Copiando espeak-ng-data (mostra progress bar)
 * - LOADING:    Inicializando engine Sherpa-ONNX (mostra spinner)
 * - READY:      Modelo pronto para uso (FAB habilitado)
 * - ERROR:      Falha no modelo (mostra erro + botao retry)
 */
data class ReaderUiState(
    val chapterTitle: String = "",
    val paragraphs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentParagraphIndex: Int = 0,
    val visibleParagraphIndex: Int = 0,
    val ttsParagraphIndex: Int = -1,
    val isTtsPlaying: Boolean = false,
    val isTtsPrepared: Boolean = false,
    val isTtsPreparing: Boolean = false,
    val ttsError: String? = null,
    // NOVO: Status do modelo TTS para exibicao visual
    val modelStatus: ModelStatus = ModelStatus.NOT_LOADED,
    // NOVO: Progresso de copia (0.0 a 1.0)
    val modelCopyProgress: Float = 0f
)

/**
 * Enum que representa o status do modelo TTS na interface.
 * Mapeia diretamente de ModelLoadState para valores amigaveis a UI.
 */
enum class ModelStatus {
    NOT_LOADED,   // Modelo nao carregado - mostrar botao "Carregar Modelo"
    COPYING,      // Copiando arquivos - mostrar LinearProgressIndicator
    LOADING,      // Inicializando engine - mostrar CircularProgressIndicator
    READY,        // Pronto - FAB habilitado
    ERROR         // Erro - mostrar mensagem + botao retry
}
