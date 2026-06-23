package com.epubaudioreader.ui.screens.reader

data class ReaderUiState(
    val chapterTitle: String = "",
    val paragraphs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentParagraphIndex: Int = 0,
    val isTtsPlaying: Boolean = false,
    val isTtsPrepared: Boolean = false,
    val isTtsPreparing: Boolean = false,
    val ttsError: String? = null
)
