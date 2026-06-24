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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.epubaudioreader.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    bookId: Long,
    chapterId: Long,
    onBackClick: () -> Unit,
    onNavigateToNextChapter: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Lifecycle-aware collection of navigation events (BUG-READ-016)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navigationEvents.collect { event ->
                when (event) {
                    is ReaderViewModel.ReaderNavigationEvent.AdvanceToNextChapter -> {
                        onNavigateToNextChapter?.invoke(event.currentChapterId)
                    }
                }
            }
        }
    }

    LaunchedEffect(bookId, chapterId) {
        viewModel.loadChapter(bookId, chapterId)
    }

    // Stop TTS when leaving the screen (BUG-READ-004)
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onLeaveScreen()
        }
    }

    ReaderContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onToggleTts = { viewModel.toggleTts() },
        onParagraphVisible = { viewModel.onParagraphVisible(it) },
        onDismissTtsError = { viewModel.dismissTtsError() },
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
    onDismissTtsError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val firstVisibleItemIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    // Track visible paragraph for scroll-based highlighting (BUG-READ-002)
    LaunchedEffect(firstVisibleItemIndex) {
        if (firstVisibleItemIndex >= 0 && uiState.paragraphs.isNotEmpty()) {
            onParagraphVisible(firstVisibleItemIndex)
        }
    }

    // Auto-scroll to the paragraph being read by TTS (BUG-READ-001)
    LaunchedEffect(uiState.currentParagraphIndex, uiState.isTtsPlaying) {
        if (uiState.currentParagraphIndex >= 0 && uiState.isTtsPlaying) {
            listState.animateScrollToItem(uiState.currentParagraphIndex)
        }
    }

    // Show TTS error with dismiss action (BUG-READ-007)
    LaunchedEffect(uiState.ttsError) {
        uiState.ttsError?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "OK",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) {
                onDismissTtsError()
            }
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
                    onClick = { if (!uiState.isTtsPreparing) onToggleTts() }
                ) {
                    if (uiState.isTtsPreparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        // Show Pause when playing, PlayArrow when paused/stopped (BUG-READ-005)
                        Icon(
                            imageVector = if (uiState.isTtsPlaying) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = if (uiState.isTtsPlaying) {
                                "Pause reading"
                            } else {
                                "Start reading"
                            }
                        )
                    }
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
                            text = uiState.error ?: "Unknown error",
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
                            text = "No content available",
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
                            // Stable keys using paragraph index (BUG-READ-018)
                            key = { index, paragraph -> "${index}_$paragraph" }
                        ) { index, paragraph ->
                            // Highlight TTS paragraph even when paused (BUG-READ-006)
                            // Use ttsParagraphIndex when TTS is active, visibleParagraphIndex during manual scroll
                            val isCurrentParagraph = if (uiState.isTtsPlaying || uiState.ttsParagraphIndex >= 0) {
                                index == uiState.currentParagraphIndex
                            } else {
                                index == uiState.visibleParagraphIndex
                            }

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
                chapterTitle = "Chapter I",
                paragraphs = listOf(
                    "Once upon a time there was a very interesting book about audio and reading.",
                    "This is the second paragraph of the chapter, showing how the text looks on screen.",
                    "TTS allows you to listen to the content while following along visually."
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
                chapterTitle = "Chapter I",
                paragraphs = listOf(
                    "First paragraph being read aloud by TTS.",
                    "Second paragraph has not been reached yet.",
                    "Third paragraph awaiting reading."
                ),
                isLoading = false,
                isTtsPlaying = true,
                currentParagraphIndex = 0,
                ttsParagraphIndex = 0
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ReaderContentTtsPausedPreview() {
    MaterialTheme {
        ReaderContent(
            uiState = ReaderUiState(
                chapterTitle = "Chapter I",
                paragraphs = listOf(
                    "First paragraph was being read but TTS is now paused.",
                    "Second paragraph has not been reached yet.",
                    "Third paragraph awaiting reading."
                ),
                isLoading = false,
                isTtsPlaying = false,
                currentParagraphIndex = 0,
                ttsParagraphIndex = 0
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {}
        )
    }
}

// BUG-READ-020: Preview with error state
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ReaderContentErrorPreview() {
    MaterialTheme {
        ReaderContent(
            uiState = ReaderUiState(
                chapterTitle = "",
                paragraphs = emptyList(),
                isLoading = false,
                error = "Failed to load chapter: Network connection lost"
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ReaderContentLoadingPreview() {
    MaterialTheme {
        ReaderContent(
            uiState = ReaderUiState(
                isLoading = true
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {}
        )
    }
}
