package com.epubaudioreader.ui.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.epubaudioreader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    bookId: Long,
    chapterId: Long,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(bookId, chapterId) {
        viewModel.loadChapter(bookId, chapterId)
    }

    ReaderContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onToggleTts = { viewModel.toggleTts() },
        onParagraphVisible = { viewModel.onParagraphVisible(it) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderContent(
    uiState: ReaderUiState,
    onBackClick: () -> Unit,
    onToggleTts: () -> Unit,
    onParagraphVisible: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val firstVisibleItemIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    LaunchedEffect(firstVisibleItemIndex) {
        if (firstVisibleItemIndex >= 0 && uiState.paragraphs.isNotEmpty()) {
            onParagraphVisible(firstVisibleItemIndex)
        }
    }

    LaunchedEffect(uiState.ttsError) {
        uiState.ttsError?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.chapterTitle.ifBlank { stringResource(R.string.chapters_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (uiState.paragraphs.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onToggleTts
                ) {
                    Icon(
                        imageVector = if (uiState.isTtsPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (uiState.isTtsPlaying) "Parar leitura" else "Iniciar leitura"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error ?: "Erro desconhecido",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
                uiState.paragraphs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum conteudo disponivel",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.paragraphs,
                            key = { index, _ -> index }
                        ) { index, paragraph ->
                            val isCurrentParagraph = index == uiState.currentParagraphIndex && uiState.isTtsPlaying
                            Text(
                                text = paragraph,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCurrentParagraph) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ReaderContentPreview() {
    MaterialTheme {
        ReaderContent(
            uiState = ReaderUiState(
                chapterTitle = "Capitulo I",
                paragraphs = listOf(
                    "Era uma vez um livro muito interessante sobre audio e leitura.",
                    "Este e o segundo paragrafo do capitulo, mostrando como o texto fica na tela.",
                    "O TTS permite ouvir o conteudo enquanto acompanha a leitura visual."
                ),
                isLoading = false
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ReaderContentTtsPlayingPreview() {
    MaterialTheme {
        ReaderContent(
            uiState = ReaderUiState(
                chapterTitle = "Capitulo I",
                paragraphs = listOf(
                    "Primeiro paragrafo sendo lido em voz alta pelo TTS.",
                    "Segundo paragrafo ainda nao foi alcancado.",
                    "Terceiro paragrafo aguardando a leitura."
                ),
                isLoading = false,
                isTtsPlaying = true,
                currentParagraphIndex = 0
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {}
        )
    }
}
