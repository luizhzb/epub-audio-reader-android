package com.epubaudioreader.ui.screens.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.domain.model.ImportProgress
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import com.epubaudioreader.core.domain.usecase.library.DeleteBookUseCase
import com.epubaudioreader.core.domain.usecase.library.GetBooksUseCase
import com.epubaudioreader.core.domain.usecase.library.ImportBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcher: DispatcherProvider,
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
            _uiState.update { it.copy(importProgress = ImportProgress.Importing("Importando...", 0)) }
            var tempFile: File? = null
            try {
                tempFile = copyUriToTempFile(uri)
                val fileSize = tempFile.length()
                when (val result = importBookUseCase(tempFile.absolutePath, fileSize)) {
                    is com.epubaudioreader.core.common.result.Result.Success -> {
                        _uiState.update { it.copy(importProgress = ImportProgress.Success(result.data.id)) }
                    }
                    is com.epubaudioreader.core.common.result.Result.Error -> {
                        _uiState.update { it.copy(importProgress = ImportProgress.Error(result.message)) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importProgress = ImportProgress.Error(e.message ?: "Erro ao importar")) }
            } finally {
                tempFile?.delete()
            }
        }
    }

    private suspend fun copyUriToTempFile(uri: Uri): File = withContext(dispatcher.io) {
        val tempFile = File.createTempFile("epub_import_", ".epub", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Nao foi possivel abrir o arquivo selecionado")
        tempFile
    }

    fun confirmDelete(book: com.epubaudioreader.core.domain.model.Book) {
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
