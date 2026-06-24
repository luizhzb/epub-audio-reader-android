package com.epubaudioreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.epubaudioreader.ui.navigation.AppNavigation
import com.epubaudioreader.ui.screens.model.TtsModelViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/**
 * MainActivity com melhorias de UX para carregamento do modelo TTS.
 * 
 * Melhorias:
 * - Pre-carrega modelo TTS automaticamente ao abrir o app
 * - Mostra SplashScreen com progresso ate modelo estar pronto
 * - Permite navegacao pela biblioteca enquanto modelo carrega
 * - Mostra erro amigavel com botao "Tentar Novamente" se falhar
 * 
 * A SplashScreen e um overlay que some automaticamente quando o modelo
 * fica pronto ou apos um timeout de seguranca.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val ttsModelViewModel: TtsModelViewModel = hiltViewModel()
    val ttsState by ttsModelViewModel.uiState.collectAsStateWithLifecycle()
    var showSplash by remember { mutableStateOf(true) }

    // Inicia pre-carregamento do modelo ao abrir o app
    LaunchedEffect(Unit) {
        ttsModelViewModel.prepareModelIfNeeded()
    }

    // Esconder splash quando modelo estiver pronto ou em erro
    LaunchedEffect(ttsState.modelStatus) {
        when (ttsState.modelStatus) {
            TtsModelViewModel.ModelStatus.READY,
            TtsModelViewModel.ModelStatus.ERROR -> {
                // Pequeno delay para transicao suave
                delay(500)
                showSplash = false
            }
            else -> { /* Aguardando */ }
        }
    }

    // Timeout de seguranca: esconder splash apos 30s mesmo se nao pronto
    LaunchedEffect(Unit) {
        delay(30000)
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // App principal (sempre carregado em background)
        AppNavigation()

        // SplashScreen overlay (some com animacao quando pronto)
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SplashScreen(
                modelStatus = ttsState.modelStatus,
                copyProgress = ttsState.copyProgress,
                error = ttsState.error,
                onRetry = { ttsModelViewModel.retry() }
            )
        }
    }
}

/**
 * Tela de splash/carregamento exibida durante inicializacao do modelo TTS.
 * 
 * Estados:
 * - NOT_LOADED/LOADING: Mostra spinner com mensagem
 * - COPYING: Mostra progress bar linear com percentual
 * - READY: Transicao para app (via AnimatedVisibility)
 * - ERROR: Mostra mensagem de erro + botao "Tentar Novamente"
 */
@Composable
private fun SplashScreen(
    modelStatus: TtsModelViewModel.ModelStatus,
    copyProgress: Float,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Titulo do app
            Text(
                text = "EPUB Audio Reader",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Leitor de eBooks com voz sintetica offline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            when {
                // Estado de erro
                modelStatus == TtsModelViewModel.ModelStatus.ERROR && error != null -> {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onRetry) {
                        Text("Tentar Novamente")
                    }
                }

                // Estado de copia (mostra progresso)
                modelStatus == TtsModelViewModel.ModelStatus.COPYING -> {
                    CircularProgressIndicator(
                        progress = { copyProgress },
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = "Copiando modelo de voz...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LinearProgressIndicator(
                        progress = { copyProgress },
                        modifier = Modifier.fillMaxSize(0.6f)
                    )
                    Text(
                        text = "${(copyProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Isso so acontece na primeira vez",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Estados de loading e not_loaded
                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = when (modelStatus) {
                            TtsModelViewModel.ModelStatus.NOT_LOADED -> "Verificando modelo..."
                            TtsModelViewModel.ModelStatus.LOADING -> "Inicializando sintetizador de voz..."
                            TtsModelViewModel.ModelStatus.READY -> "Pronto!"
                            else -> "Carregando..."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (modelStatus == TtsModelViewModel.ModelStatus.LOADING) {
                        Text(
                            text = "Isso pode levar alguns segundos no primeiro uso",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Dica para o usuario
            Text(
                text = "Voce pode navegar pela biblioteca enquanto o modelo carrega",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
