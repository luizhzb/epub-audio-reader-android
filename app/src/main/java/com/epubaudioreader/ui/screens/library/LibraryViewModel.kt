package com.epubaudioreader.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.domain.model.ImportProgress
import com.epubaudioreader.domain.usecase.DeleteBookUseCase
import com.epubaudioreader.domain.usecase.GetBooksUseCase
import com.epubaudioreader.domain.usecase.ImportBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val importBookUseCase: ImportBookUseCase,
    private val deleteBookUseCase: DeleteBookUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        getBooksUseCase()
            .onEach { books ->
                _uiState.update { it.copy(books = books, isLoading = false) }
            }
            .catch { error ->
                _uiState.update { it.copy(error = error.message, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(importProgress = ImportProgress.Scanning) }
            try {
                importBookUseCase(uri.toString())
                    .collect { progress ->
                        _uiState.update { it.copy(importProgress = progress) }
                        if (progress is ImportProgress.Success || progress is ImportProgress.Error) {
                            kotlinx.coroutines.delay(2000)
                            _uiState.update { it.copy(importProgress = ImportProgress.Idle) }
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(importProgress = ImportProgress.Error(e.message ?: "Erro desconhecido"))
                }
                kotlinx.coroutines.delay(2000)
                _uiState.update { it.copy(importProgress = ImportProgress.Idle) }
            }
        }
    }

    fun confirmDelete(book: com.epubaudioreader.domain.model.Book) {
        _uiState.update { it.copy(bookToDelete = book) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(bookToDelete = null) }
    }

    fun executeDelete() {
        val book = _uiState.value.bookToDelete ?: return
        viewModelScope.launch {
            try {
                deleteBookUseCase(book.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(bookToDelete = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
