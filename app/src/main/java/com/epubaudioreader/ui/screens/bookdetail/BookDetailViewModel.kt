package com.epubaudioreader.ui.screens.bookdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.domain.usecase.GetBookWithChaptersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val getBookWithChaptersUseCase: GetBookWithChaptersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    fun loadBook(bookId: Long) {
        getBookWithChaptersUseCase(bookId)
            .onEach { result ->
                _uiState.update {
                    it.copy(
                        book = result.book,
                        chapters = result.chapters,
                        isLoading = false
                    )
                }
            }
            .catch { error ->
                _uiState.update {
                    it.copy(error = error.message, isLoading = false)
                }
            }
            .launchIn(viewModelScope)
    }
}
