package com.epubaudioreader.core.tts.player
import android.util.Log
import com.epubaudioreader.core.domain.model.ChapterContent
import com.epubaudioreader.core.tts.engine.TtsEngine
import com.epubaudioreader.core.tts.model.ModelManager
import com.epubaudioreader.core.tts.synthesis.TtsSynthesizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estado da reprodução TTS do EPUB.
 *
 * @param isPlaying Indica se o TTS esta reproduzindo.
 * @param currentChapterIndex Indice do capítulo atual.
 * @param currentParagraphIndex Indice do parágrafo atual.
 * @param curRentText Texto atual sendo reproduzido.
 * @param isEngineReady Indica se o engine TTS esta inicializado.
 * @param error Mensagem de erro, se houver.
 */
data class TtsPlaybackState(
    val isPlaying: Boolean = false,
    val currentChapterIndex: Int = 0,
    val currentParagraphIndex: Int = 0,
    val currentText: String = "",
    val isEngineReady: Boolean = false,
    val error: String? = null
)

/**
 * Player de TTS para leitura de EPUB por parágrafo.
 *
 * Gerencia a reprodução de capítulos por parágrafo, com controles de
 * navegacao (próximo/anterior) e estado de reprodução.
 *
 * os capítulos são passados como lista de [ChapterContent], que já contém
 * os parágrafos parseados.
 *
 * @param modelManager Gerenciador do modelo TTS.
 * @param ttsEngine Engine de síntese TTS.
 * @param synthesizer Sintetizador para reprodução.
 */
@Singleton
class EpubTtsPlayer @Inject constructor(
    private val modelManager: ModelManager,
    private val ttsEngine: TtsEngine,
    private val synthesizer: TtsSynthesizer
) {
    companion object {
        private const val TAG = "EpubTtsPlayer"
    }

    private val _state = MutableStateFlow(TtsPlaybackState())
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    // Lista de capítulos com parágrafos
    private var chapters: List<ChapterContent> = emptyList()
    private var currentParagraphs: List<String> = emptyList()

    // Job de reprodução atual
    private var playbackJob: Job? = null

    // Scope para corrotinas de reprodução
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Define os capítulos a serem lidos.
     * Antes de chamar play(), o player usa esta lista para navegar entre capítulos.
     *
     * @param newChapters Lista de [ChapterContent] com parágrafos.
     */
    fun setChapters(newChapters: List<ChapterContent>) {
        chapters = newChapters
    }

    /**
     * Prepara o engine TTS inicializando o modelo.
     * Deve ser chamado antes de iniciar a reprodução.
     *
     * @return true se o engine foi inicializado com sucesso.
     */
    fun prepare(): Boolean {
        if (ttsEngine.isInitialized) {
            _state.value = _state.value.copy(isEngineReady = true)
            Log.d(TAG, "Engine ja esta inicializado")
            return true
        }

        return runBlocking {
            try {
                modelManager.ensureModelReady()
                val modelDir = modelManager.modelDir.absolutePath
                val result = ttsEngine.initialize(modelDir)

                if (result) {
                    _state.value = _state.value.copy(isEngineReady = true)
                    Log.d(TAG, "Engine TTS inicializado com sucesso. sampleRate=${ttsEngine.sampleRate}")
                } else {
                    _state.value = _state.value.copy(
                        error = "Falha ao inicializar engine TTS"
                    )
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao preparar engine TSS: ${e.message}", e)
                _state.value = _state.value.copy(
                    error = "Erro ao preparar engine: ${e.message}"
                )
                false
            }
        }
    }

    /**
     * Inicia a reprodução a partir do capítulo e (possivelmente) parágrafo indicado.
     *
     * @param chapterIndex Indice do capítulo a reproduzir.
     * @param startParagraph Indice do parágrafo inicial dentro do capítulo.
     */
    fun play(chapterIndex: Int, startParagraph: Int = 0) {
        if (chapters.isEmpty()) {
            _state.value = _state.value.copy(error = "Nenhum capítulo carregado")
            return
        }

        if (!ttsEngine.isInitialized) {
            _state.value = _state.value.copy(
                error = "Engine TTS não inicializado. Chame prepare() primeiro."
            )
            return
        }

        // Cancela reprodução anterior se houver
        stop()

        playbackJob = scope.launch {
            try {
                _state.value = _state.value.copy(
                    isPlaying = true,
                    currentChapterIndex = chapterIndex,
                    error = null
                )

                // Toca por cada capítulo a partir do atual
                for (cIndex in chapterIndex until chapters.size) {
                    if (!isActive) break

                    val chapter = chapters[cIndex]
                    currentParagraphs = chapter.paragraphs

                    val startIdx = if (cIndex == chapterIndex) startParagraph else 0

                    Log.d(TAG, "Capitulo $cIndex: ${chapter.paragraphs.size} parágrafos. Iniciando do parágrafo $startIdx")

                    for (pIndex in startIdx until currentParagraphs.size) {
                        if (!isActive) break

                        val text = currentParagraphs[pIndex]

                        // Pula para o
                        _state.value = _state.value.copy(
                            currentChapterIndex = cIndex,
                            currentParagraphIndex = pIndex,
                            currentText = text
                        )

                        Log.d(TAG,  "Reproduzindo parágrafo $pIndex: ${text.take(50)}...")

                        // Syntetizar e reproduzir
                        val result = synthesizer.speak(text)

                        result.onFailure { e ->
                            Log.e(TAG, "Erro ao sintetizar parágrafo $pIndex: ${e.message}", e)
                            _state.value = _state.value.copy(
                                isPlaying = false,
                                error = e.message
                            )
                            return@launch
                        }

                        // Aguardar a reproducæo terminar (com timeout)
                        var waitCount = 0
                        while (synthesizer.isPlaying && isActive && waitCount < 500) {
                            delay(200)
                            waitCount++
                        }

                        if (waitCount >= 500) {
                            Log.w(TAG,  "Timeout aguardando reprodução do parágrafo $pIndex")
                        }
                    }
                }

                // Reprodução concluída
                _state.value = _state.value.copy(isPlaying = false)
                Log.d(TAG,  "Reprodução concluída - todos os capítulos foram leidos")

            } catch (e: CancellationException) {
                // Reproducǭo cancelada pelo usuario
                Log.d(TAG, "Reproducɶo cancelada em capítulo ${_state.value.currentChapterIndex}, parágrafo ${_state.value.currentParagraphIndex}")
                _state.value = _state.value.copy(isPlaying = false)

            } catch (e: Exception) {
                Log.e(TAG, "Erro na reprodução: ${e.message}", e)
                _state.value = _state.value.copy(
                    isPlaying = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Pausa a reprodução atual.
     * Mantem o estado interno para retomar a reprodução do mesmo ponto.
     */
    fun pause() {
        stop()
        _state.value = _state.value.copy(isPlaying = false)
        Log.d(TAG, "TTS: reprodução pausada")
    }

    /**
     * Para a reprodução atual e limpa recursos do AudioTrack.
     */
    fun stop() {
        playbackJob?.cancel()
        synthesizer.stop()
    }

    /**
     * Avança para o próximo parágrafo.
     * Se ja estiver no final do capítulo, avança para o primeiro parágrafo do próximo capítulo.
     */
    fun nextParagraph() {
        val current = _state.value

        // Verifica se a lista de parágrafos da capítulo atual está atualizada
        val currentChapterParagraphs = if (current.currentChapterIndex < chapters.size) {
            chapters[current.currentChapterIndex].paragraphs
        } else {
            emptyList()
        }

        if (current.currentParagraphIndex < currentChapterParagraphs.size - 1) {
            // Proximo parágrafo do mesmo capítulo
            play(current.currentChapterIndex, current.currentParagraphIndex + 1)
        } else if (current.currentChapterIndex < chapters.size - 1) {
            // Proximo capítulo - inicia do primeiro parágrafo
            play(current.currentChapterIndex + 1, 0)
        } else {
            Log.d(TAG, "Ja estamos no final do ultimo capítulo - reprodução concluída")
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    /**
     * Retorna para o
     * Se ja estiver no inicio do capítulo, volta para o
     */
    fun previousParagraph() {
        val current = _state.value

        if (current.currentParagraphIndex > 0) {
            // Paragrafo anterior do mesmo capítulo
            play(current.currentChapterIndex, current.currentParagraphIndex - 1)
        } else if (current.currentChapterIndex > 0) {
            // Volta para o ultimo parágrafo do capítulo anterior
            val prevChapter = chapters[current.currentChapterIndex - 1]
            play(current.currentChapterIndex - 1, prevChapter.paragraphs.size - 1)
        } else {
            Log.d(TAG, "Ja estamos no inicio da leitura - nemhem para retornar")
        }
    }

    /**
     * Limpa todos o recursos liberados pelo player.
     * Deve ser chamado quando o usuario sair da tela de leitura.
     */
    fun release() {
        Log.d(TAG, "Release EpubTtsPlayer")
        stop()
        synthesizer.release()
        scope.cancel()
    }
}
