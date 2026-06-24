package com.epubaudioreader.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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

/**
 * ReaderScreen com melhorias de UX para diagnostico TTS.
 * 
 * Melhorias:
 * 1. ModelStatusCard: mostra status do modelo com botao "Carregar Modelo"
 * 2. FAB melhorado: desabilitado visualmente quando modelo nao pronto
 * 3. Snackbar com acao "Tentar" quando modelo em erro
 * 4. Card de status some automaticamente quando TTS comeca a tocar
 */
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

    DisposableEffect(Unit) {
        onDispose { viewModel.onLeaveScreen() }
    }

    ReaderContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onToggleTts = { viewModel.toggleTts() },
        onParagraphVisible = { viewModel.onParagraphVisible(it) },
        onDismissTtsError = { viewModel.dismissTtsError() },
        onPrepareModel = { viewModel.prepareModelExplicitly() },
        modifier = modifier
    )
}

// ====================================================================================
// READER CONTENT - UI PRINCIPAL
// ====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderContent(
    uiState: ReaderUiState,
    onBackClick: () -> Unit,
    onToggleTts: () -> Unit,
    onParagraphVisible: (Int) -> Unit,
    onDismissTtsError: () -> Unit = {},
    onPrepareModel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val firstVisibleItemIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    // Track visible paragraph for scroll-based highlighting
    LaunchedEffect(firstVisibleItemIndex) {
        if (firstVisibleItemIndex >= 0 && uiState.paragraphs.isNotEmpty()) {
            onParagraphVisible(firstVisibleItemIndex)
        }
    }

    // Auto-scroll to the paragraph being read by TTS
    LaunchedEffect(uiState.currentParagraphIndex, uiState.isTtsPlaying) {
        if (uiState.currentParagraphIndex >= 0 && uiState.isTtsPlaying) {
            listState.animateScrollToItem(uiState.currentParagraphIndex)
        }
    }

    // MELHORIA: Snackbar com acao "Tentar" quando modelo em erro
    LaunchedEffect(uiState.ttsError) {
        uiState.ttsError?.let { message ->
            val actionLabel = if (uiState.modelStatus == ModelStatus.ERROR ||
                uiState.modelStatus == ModelStatus.NOT_LOADED
            ) "Tentar" else "OK"

            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed && actionLabel == "Tentar") {
                onPrepareModel()
            }
            onDismissTtsError()
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
            // MELHORIA: FAB so aparece quando ha conteudo
            if (uiState.paragraphs.isNotEmpty()) {
                TtsFloatingActionButton(
                    isTtsPlaying = uiState.isTtsPlaying,
                    isTtsPreparing = uiState.isTtsPreparing,
                    modelStatus = uiState.modelStatus,
                    onToggleTts = onToggleTts
                )
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
                        // MELHORIA: Card de status do modelo no topo
                        item(key = "model_status_card") {
                            AnimatedVisibility(
                                visible = !uiState.isTtsPlaying &&
                                        uiState.modelStatus != ModelStatus.READY,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                ModelStatusCard(
                                    modelStatus = uiState.modelStatus,
                                    copyProgress = uiState.modelCopyProgress,
                                    onPrepareClick = onPrepareModel
                                )
                            }
                        }

                        itemsIndexed(
                            items = uiState.paragraphs,
                            key = { index, paragraph -> "${index}_$paragraph" }
                        ) { index, paragraph ->
                            val isCurrentParagraph = if (uiState.isTtsPlaying ||
                                uiState.ttsParagraphIndex >= 0
                            ) {
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

// ====================================================================================
// COMPONENTE: ModelStatusCard
// ====================================================================================

/**
 * Card que exibe o status atual do modelo TTS.
 * 
 * Estados:
 * - NOT_LOADED: Mostra mensagem de erro + botao "Carregar Modelo"
 * - COPYING:    Mostra LinearProgressIndicator com percentual
 * - LOADING:    Mostra CircularProgressIndicator
 * - READY:      Card some (nao visivel)
 * - ERROR:      Mostra mensagem de erro + botao "Tentar Novamente"
 */
@Composable
private fun ModelStatusCard(
    modelStatus: ModelStatus,
    copyProgress: Float,
    onPrepareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusColor) = when (modelStatus) {
        ModelStatus.NOT_LOADED -> "Modelo de voz nao carregado" to MaterialTheme.colorScheme.error
        ModelStatus.COPYING -> "Copiando modelo..." to MaterialTheme.colorScheme.primary
        ModelStatus.LOADING -> "Inicializando modelo..." to MaterialTheme.colorScheme.tertiary
        ModelStatus.READY -> "Modelo pronto" to MaterialTheme.colorScheme.primary
        ModelStatus.ERROR -> "Erro no modelo de voz" to MaterialTheme.colorScheme.error
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icone de status
            Icon(
                imageVector = when (modelStatus) {
                    ModelStatus.NOT_LOADED -> Icons.Default.Download
                    ModelStatus.COPYING, ModelStatus.LOADING -> Icons.Default.Sync
                    ModelStatus.READY -> Icons.Default.CheckCircle
                    ModelStatus.ERROR -> Icons.Default.Error
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(36.dp)
            )

            // Texto de status
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleMedium,
                color = statusColor
            )

            // Progresso durante copia
            if (modelStatus == ModelStatus.COPYING) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { copyProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${(copyProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Indicador durante loading
            if (modelStatus == ModelStatus.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Isso pode levar alguns segundos...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Botao de acao para NOT_LOADED e ERROR
            if (modelStatus == ModelStatus.NOT_LOADED || modelStatus == ModelStatus.ERROR) {
                Button(
                    onClick = onPrepareClick,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Icon(
                        imageVector = if (modelStatus == ModelStatus.ERROR) {
                            Icons.Default.Sync
                        } else {
                            Icons.Default.Download
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (modelStatus == ModelStatus.ERROR) {
                            "Tentar Novamente"
                        } else {
                            "Carregar Modelo"
                        }
                    )
                }
            }

            // Dica para o usuario
            if (modelStatus == ModelStatus.NOT_LOADED) {
                Text(
                    text = "O modelo de voz sera copiado dos assets do app (100% offline).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ====================================================================================
// COMPONENTE: TtsFloatingActionButton (FAB melhorado)
// ====================================================================================

/**
 * FAB de controle TTS com estado visual do modelo.
 * 
 * Comportamento:
 * - Quando modelo nao pronto: FAB desabilitado visualmente (cor atenuada)
 * - Quando preparando: Mostra CircularProgressIndicator
 * - Quando pronto e parado: Mostra icone Play
 * - Quando pronto e tocando: Mostra icone Pause
 */
@Composable
private fun TtsFloatingActionButton(
    isTtsPlaying: Boolean,
    isTtsPreparing: Boolean,
    modelStatus: ModelStatus,
    onToggleTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = !isTtsPreparing && modelStatus == ModelStatus.READY

    FloatingActionButton(
        onClick = { if (isEnabled) onToggleTts() },
        modifier = modifier,
        containerColor = if (isEnabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        if (isTtsPreparing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = if (isTtsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isTtsPlaying) "Pausar leitura" else "Iniciar leitura",
                modifier = Modifier.size(24.dp),
                tint = if (isEnabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                }
            )
        }
    }
}

// ====================================================================================
// PREVIEWS
// ====================================================================================

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ReaderContentModelNotLoadedPreview() {
    MaterialTheme {
        ReaderContent(
            uiState = ReaderUiState(
                chapterTitle = "Chapter I",
                paragraphs = listOf(
                    "First paragraph of the chapter.",
                    "Second paragraph here.",
                    "Third paragraph waiting."
                ),
                isLoading = false,
                modelStatus = ModelStatus.NOT_LOADED
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {},
            onPrepareModel = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ReaderContentModelErrorPreview() {
    MaterialTheme {
        ReaderContent(
            uiState = ReaderUiState(
                chapterTitle = "Chapter I",
                paragraphs = listOf(
                    "First paragraph of the chapter.",
                    "Second paragraph here."
                ),
                isLoading = false,
                modelStatus = ModelStatus.ERROR,
                ttsError = "Falha ao carregar modelo TTS"
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {},
            onPrepareModel = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ReaderContentModelCopyingPreview() {
    MaterialTheme {
        ReaderContent(
            uiState = ReaderUiState(
                chapterTitle = "Chapter I",
                paragraphs = listOf(
                    "First paragraph of the chapter.",
                    "Second paragraph here."
                ),
                isLoading = false,
                modelStatus = ModelStatus.COPYING,
                modelCopyProgress = 0.65f
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {},
            onPrepareModel = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ReaderContentModelReadyPreview() {
    MaterialTheme {
        ReaderContent(
            uiState = ReaderUiState(
                chapterTitle = "Chapter I",
                paragraphs = listOf(
                    "First paragraph ready for TTS.",
                    "Second paragraph here.",
                    "Third paragraph waiting."
                ),
                isLoading = false,
                modelStatus = ModelStatus.READY,
                isTtsPrepared = true
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {},
            onPrepareModel = {}
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
                modelStatus = ModelStatus.READY,
                isTtsPlaying = true,
                isTtsPrepared = true,
                currentParagraphIndex = 0,
                ttsParagraphIndex = 0
            ),
            onBackClick = {},
            onToggleTts = {},
            onParagraphVisible = {},
            onPrepareModel = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelStatusCardNotLoadedPreview() {
    MaterialTheme {
        ModelStatusCard(
            modelStatus = ModelStatus.NOT_LOADED,
            copyProgress = 0f,
            onPrepareClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelStatusCardCopyingPreview() {
    MaterialTheme {
        ModelStatusCard(
            modelStatus = ModelStatus.COPYING,
            copyProgress = 0.42f,
            onPrepareClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelStatusCardErrorPreview() {
    MaterialTheme {
        ModelStatusCard(
            modelStatus = ModelStatus.ERROR,
            copyProgress = 0f,
            onPrepareClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelStatusCardLoadingPreview() {
    MaterialTheme {
        ModelStatusCard(
            modelStatus = ModelStatus.LOADING,
            copyProgress = 0f,
            onPrepareClick = {}
        )
    }
}
