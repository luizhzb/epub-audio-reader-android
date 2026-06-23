package com.epubaudioreader.ui.screens.reader

data class ReaderUiState(
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val chapterContent: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isTtsPlaying: Boolean = false,
    val currentParagraphIndex: Int = 0
)
