package com.epubaudioreader.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubaudioreader.core.domain.usecase.reader.GetChapterContentUseCase
import com.epubaudioreader.core.domain.usecase.reader.SaveProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val getChapterContentUseCase: GetChapterContentUseCase,
    private val saveProgressUseCase: SaveProgressUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBookId: Long = 0L
    private var currentChapterId: Long = 0L

    private val progressFlow = MutableStateFlow<Triple<Long, Long, Int>?>(null)

    init {
        progressFlow
            .filter { it != null }
            .debounce(1000L)
            .onEach { triple ->
                triple?.let { (bookId, chapterId, paragraphIndex) ->
                    saveProgressUseCase(bookId, chapterId, paragraphIndex)
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadChapter(bookId: Long, chapterId: Long) {
        currentBookId = bookId
        currentChapterId = chapterId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val chapter = getChapterContentUseCase(chapterId)
                if (chapter != null) {
                    val paragraphs = parseParagraphs(chapter.contentHtml)
                    _uiState.update {
                        it.copy(
                            chapterTitle = chapter.title,
                            paragraphs = paragraphs,
                            isLoading = false,
                            currentParagraphIndex = 0
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Capitulo nao encontrado"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Erro ao carregar capitulo"
                    )
                }
            }
        }
    }

    fun onParagraphVisible(index: Int) {
        _uiState.update { it.copy(currentParagraphIndex = index) }
        if (currentBookId != 0L && currentChapterId != 0L) {
            progressFlow.value = Triple(currentBookId, currentChapterId, index)
        }
    }

    private fun parseParagraphs(contentHtml: String): List<String> {
        if (contentHtml.isBlank()) return emptyList()
        // Simple HTML tag stripping for display
        return contentHtml
            .replace(Regex("<p[^>]*>"), "\n\n")
            .replace(Regex("</p>"), "")
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
