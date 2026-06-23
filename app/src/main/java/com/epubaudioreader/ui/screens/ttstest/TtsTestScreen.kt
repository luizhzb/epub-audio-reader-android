package com.epubaudioreader.ui.screens.ttstest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsTestScreen(
    viewModel: TtsTestViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Observa erros e mostra Snackbar com acao de dismiss
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "OK"
            )
            if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) {
                viewModel.dismissError()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Teste TTS",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusCard(uiState = uiState)

            when (uiState.modelStatus) {
                ModelStatus.NOT_LOADED -> {
                    DownloadSection(
                        onPrepareClick = { viewModel.prepareModel() }
                    )
                }

                ModelStatus.COPYING -> {
                    CopyingSection(progress = uiState.copyProgress)
                }

                ModelStatus.LOADING -> {
                    InitializingSection()
                }

                ModelStatus.READY -> {
                    TestSection(
                        text = uiState.text,
                        isPlaying = uiState.isPlaying,
                        onTextChange = { viewModel.onTextChange(it) },
                        onSpeakClick = { viewModel.speak() }
                    )
                }

                ModelStatus.ERROR -> {
                    ErrorSection(
                        onRetryClick = { viewModel.prepareModel() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    uiState: TtsTestUiState,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusColor) = when (uiState.modelStatus) {
        ModelStatus.NOT_LOADED -> "Modelo nao preparado" to MaterialTheme.colorScheme.error
        ModelStatus.COPYING -> "Copiando modelo..." to MaterialTheme.colorScheme.primary
        ModelStatus.LOADING -> "Inicializando..." to MaterialTheme.colorScheme.tertiary
        ModelStatus.READY -> "Modelo pronto" to MaterialTheme.colorScheme.primary
        ModelStatus.ERROR -> "Erro" to MaterialTheme.colorScheme.error
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SettingsVoice,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleMedium,
                color = statusColor
            )
            if (uiState.modelStatus == ModelStatus.READY) {
                Text(
                    text = "O modelo TTS esta pronto para uso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (uiState.isPlaying) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "Sintetizando audio...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (uiState.modelStatus == ModelStatus.NOT_LOADED) {
                Text(
                    text = "O modelo de voz sera copiado dos assets do app (100% offline).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DownloadSection(
    onPrepareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = onPrepareClick,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = "Preparar Modelo", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CopyingSection(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(0.8f),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Copiando modelo de voz...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InitializingSection(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = "Inicializando motor TTS...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Secao de teste TTS.
 *
 * Correcoes aplicadas:
 * - Botao alterna entre "Testar Voz" (parado) e "Parar" (tocando)
 * - Botao habilitado quando texto nao esta em branco (independente de isPlaying)
 * - Clique em "Parar" chama viewModel.speak() que detecta synthesizer.isPlaying e para
 * - Indicador de progresso circular quando isPlaying = true
 */
@Composable
private fun TestSection(
    text: String,
    isPlaying: Boolean,
    onTextChange: (String) -> Unit,
    onSpeakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Texto para sintetizar") },
            placeholder = { Text("Digite o texto aqui...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            shape = MaterialTheme.shapes.medium,
            enabled = !isPlaying
        )

        Button(
            onClick = onSpeakClick,
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = "Parar", style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = "Testar Voz", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ErrorSection(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Ocorreu um erro",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Button(
            onClick = onRetryClick,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text(text = "Tentar Novamente", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private object ButtonDefaults {
    val IconSpacing = 8.dp
}
