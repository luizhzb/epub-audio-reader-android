package com.epubaudioreader.ui.screens.ttstest

data class TtsTestUiState(
    val modelStatus: ModelStatus = ModelStatus.NOT_LOADED,
    val copyProgress: Float = 0f,
    val text: String = "Hello, this is a test of voice synthesis.",
    val isPlaying: Boolean = false,
    val error: String? = null
)

enum class ModelStatus {
    NOT_LOADED,
    COPYING,
    LOADING,
    READY,
    ERROR
}
