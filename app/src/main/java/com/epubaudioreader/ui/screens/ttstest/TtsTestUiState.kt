package com.epubaudioreader.ui.screens.ttstest

data class TtsTestUiState(
    val modelStatus: ModelStatus = ModelStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val text: String = "Ola, este eh um teste.",
    val isPlaying: Boolean = false,
    val error: String? = null
)

enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    INITIALIZING,
    ERROR
}
