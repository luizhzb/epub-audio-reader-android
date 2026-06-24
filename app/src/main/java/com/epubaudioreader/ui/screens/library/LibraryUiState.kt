package com.epubaudioreader.ui.screens.library

import com.epubaudioreader.core.domain.model.Book
import com.epubaudioreader.core.domain.model.ImportProgress

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importProgress: ImportProgress = ImportProgress.Idle,
    val error: String? = null,
    val bookToDelete: Book? = null
)
