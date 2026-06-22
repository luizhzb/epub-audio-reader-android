package com.epubaudioreader.ui.screens.bookdetail

import com.epubaudioreader.domain.model.Book
import com.epubaudioreader.domain.model.Chapter

data class BookDetailUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
