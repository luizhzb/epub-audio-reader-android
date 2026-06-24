package com.epubaudioreader.ui.screens.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.epubaudioreader.R
import com.epubaudioreader.core.domain.model.Book
import com.epubaudioreader.core.domain.model.ImportProgress
import com.epubaudioreader.ui.components.BookCard
import com.epubaudioreader.ui.components.EmptyState
import com.epubaudioreader.ui.components.ImportFab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookClick: (Long) -> Unit,
    onTtsTestClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    // Observa erros e exibe Snackbar antes de limpar (BUG-UI-001)
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    LibraryContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onImportClick = { pickDocumentLauncher.launch(arrayOf("application/epub+zip")) },
        onBookClick = onBookClick,
        onDeleteBook = { viewModel.confirmDelete(it) },
        onTtsTestClick = onTtsTestClick,
        onDeleteConfirm = { viewModel.executeDelete() },
        onDeleteDismiss = { viewModel.dismissDelete() },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryContent(
    uiState: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    onImportClick: () -> Unit,
    onBookClick: (Long) -> Unit,
    onDeleteBook: (Book) -> Unit,
    onTtsTestClick: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.library_title))
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onTtsTestClick) {
                        Icon(
                            imageVector = Icons.Default.SettingsVoice,
                            contentDescription = stringResource(R.string.tts_test_title),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            ImportFab(
                onClick = onImportClick,
                isLoading = uiState.isImporting
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.books.isEmpty() && !uiState.isLoading) {
                EmptyState(
                    message = stringResource(R.string.empty_library),
                    onImportClick = onImportClick
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.books,
                        key = { it.id }
                    ) { book ->
                        BookCard(
                            book = book,
                            onClick = { onBookClick(book.id) },
                            onLongClick = { onDeleteBook(book) }
                        )
                    }
                }
            }

            // Loading overlay durante importacao (BUG-UI-014)
            if (uiState.isImporting) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (uiState.bookToDelete != null) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(text = stringResource(R.string.delete_book)) },
            text = { Text(text = stringResource(R.string.confirm_delete)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(text = stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(text = stringResource(R.string.no))
                }
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LibraryContentEmptyPreview() {
    MaterialTheme {
        LibraryContent(
            uiState = LibraryUiState(),
            snackbarHostState = SnackbarHostState(),
            onImportClick = {},
            onBookClick = {},
            onDeleteBook = {},
            onTtsTestClick = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LibraryContentWithBooksPreview() {
    MaterialTheme {
        LibraryContent(
            uiState = LibraryUiState(
                books = listOf(
                    Book(
                        id = 1,
                        title = "Dom Casmurro",
                        authors = "Machado de Assis",
                        filePath = "",
                        totalChapters = 10,
                        totalChars = 50000,
                        fileSize = 1024,
                        hash = ""
                    ),
                    Book(
                        id = 2,
                        title = "Memorias Postumas de Bras Cubas",
                        authors = "Machado de Assis",
                        filePath = "",
                        totalChapters = 20,
                        totalChars = 80000,
                        fileSize = 2048,
                        hash = ""
                    )
                )
            ),
            snackbarHostState = SnackbarHostState(),
            onImportClick = {},
            onBookClick = {},
            onDeleteBook = {},
            onTtsTestClick = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {}
        )
    }
}
