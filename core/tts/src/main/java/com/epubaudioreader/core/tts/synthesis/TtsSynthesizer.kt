package com.epubaudioreader.core.tts.synthesis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.epubaudioreader.core.tts.engine.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestrador de síntese e reprodução de voz TTS.
 *
 * Integra o [TtsEngine] para síntese de texto em áudio PCM e utiliza
 * [AudioTrack] para reprodução do áudio no dispositivo.
 *
 * ### Correções aplicadas (AudioTrack MODE_STATIC):
 * 1. **Lock de sincronização**: Evita race condition entre [speak] e [stop].
 * 2. **Release antes de criar novo**: AudioTrack anterior é liberado ANTES da criação.
 * 3. **Write ANTES de play**: Obrigatório para MODE_STATIC — write() deve escrever TODOS os bytes.
 * 4. **Verificação de write() retorno**: Se written != pcmData.size, falha imediatamente.
 * 5. **Clamp [-1.0f, 1.0f]**: Antes da conversão float -> short, evita overflow/underflow.
 * 6. **Logs em cada etapa**: Facilita diagnóstico de problemas.
 * 7. **Thread-safety**: Todos os métodos que tocam [audioTrack] usam [lock].
 * 8. **Buffer size correto**: minBufferSize coerceAtLeast pcmData.size * 2 (short = 2 bytes).
 */
@Singleton
class TtsSynthesizer @Inject constructor(
    private val ttsEngine: TtsEngine
) {
    companion object {
        private const val TAG = "TtsSynthesizer"

        /** Fator de escala: float [-1.0f, 1.0f] -> short [-32767, 32767] */
        const val FLOAT_TO_SHORT_SCALE = 32767.0f
    }

    /** Instância ativa do AudioTrack. Acesso SEMPRE via [lock]. */
    private var audioTrack: AudioTrack? = null

    /** Lock para sincronização de acesso ao [audioTrack].
     *  Evita race condition entre speak() (corrotina) e stop()/playBuffer() (threads diversas). */
    private val lock = Object()

    /** Indica se há reprodução ativa no momento. Acesso thread-safe via [lock]. */
    val isPlaying: Boolean
        get() = synchronized(lock) {
            audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
        }

    /** Indica se o engine TTS está inicializado. */
    val isEngineReady: Boolean
        get() = ttsEngine.isInitialized

    /**
     * Sintetiza o [text] e reproduz o áudio resultante.
     *
     * @param text Texto a ser sintetizado e reproduzido.
     * @return Result indicando sucesso ou falha da operação.
     */
    suspend fun speak(text: String): Result<Unit> = withContext(Dispatchers.Default) {
        Log.d(TAG, "speak() iniciado — text=\"$text\"")
        try {
            if (!ttsEngine.isInitialized) {
                Log.e(TAG, "speak() falhou — Engine TTS não inicializado")
                return@withContext Result.failure(
                    IllegalStateException("Engine TTS não inicializado. Chame initialize() primeiro.")
                )
            }

            if (text.isBlank()) {
                Log.w(TAG, "speak() — texto em branco, retornando sucesso vazio")
                return@withContext Result.success(Unit)
            }

            // 1. Sintetizar texto em amostras float
            Log.d(TAG, "speak() — sintetizando texto...")
            val samples = ttsEngine.synthesize(text)
                ?: run {
                    Log.e(TAG, "speak() falhou — synthesize() retornou null")
                    return@withContext Result.failure(
                        IllegalStateException("Síntese falhou: engine retornou null")
                    )
                }

            if (samples.isEmpty()) {
                Log.w(TAG, "speak() — amostras vazias, retornando sucesso vazio")
                return@withContext Result.success(Unit)
            }
            Log.d(TAG, "speak() — sintetizado ${samples.size} amostras")

            // 2. Converter float[] -> short[] (PCM 16-bit) com clamp
            val pcmData = floatArrayToShortArray(samples)
            Log.d(TAG, "speak() — convertido para ${pcmData.size} shorts PCM (${pcmData.size * 2} bytes)")

            // 3. Parar áudio anterior e criar novo AudioTrack (tudo sob lock)
            val sampleRate = ttsEngine.sampleRate
            if (sampleRate <= 0) {
                Log.e(TAG, "speak() falhou — sampleRate inválido: $sampleRate")
                return@withContext Result.failure(
                    IllegalStateException("Sample rate inválido: $sampleRate")
                )
            }

            synchronized(lock) {
                // 3a. Libera AudioTrack anterior ANTES de criar novo
                Log.d(TAG, "speak() — liberando AudioTrack anterior (se houver)")
                releaseAudioTrackLocked()

                // 3b. Calcular buffer size mínimo
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufferSize < 0) {
                    Log.e(TAG, "speak() falhou — getMinBufferSize() retornou erro: $minBufferSize")
                    return@withContext Result.failure(
                        IllegalStateException("getMinBufferSize() falhou: $minBufferSize")
                    )
                }

                // Para MODE_STATIC, o buffer deve caber TODOS os dados (short = 2 bytes)
                val requiredSize = pcmData.size * 2
                val bufferSize = minBufferSize.coerceAtLeast(requiredSize)
                Log.d(TAG, "speak() — minBufferSize=$minBufferSize, requiredSize=$requiredSize, bufferSize=$bufferSize")

                // 3c. Criar AudioTrack
                Log.d(TAG, "speak() — criando AudioTrack(sampleRate=$sampleRate, bufferSize=$bufferSize, MODE_STATIC)")
                val track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    bufferSize,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                audioTrack = track
                Log.d(TAG, "speak() — AudioTrack criado com sucesso, state=${track.state}")

                // 4. Escrever TODOS os dados ANTES de play() — OBRIGATÓRIO para MODE_STATIC
                Log.d(TAG, "speak() — escrevendo ${pcmData.size} shorts no AudioTrack...")
                val written = track.write(pcmData, 0, pcmData.size)
                Log.d(TAG, "speak() — AudioTrack.write() retornou: $written (esperado: ${pcmData.size})")

                if (written < 0) {
                    Log.e(TAG, "speak() falhou — write() retornou erro: $written")
                    releaseAudioTrackLocked()
                    return@withContext Result.failure(
                        IllegalStateException("AudioTrack.write() falhou com código: $written")
                    )
                }
                if (written != pcmData.size) {
                    Log.e(TAG, "speak() falhou — write() não escreveu todos os bytes: $written / ${pcmData.size}")
                    releaseAudioTrackLocked()
                    return@withContext Result.failure(
                        IllegalStateException("AudioTrack.write() escreveu $written de ${pcmData.size} shorts — MODE_STATIC exige escrita completa")
                    )
                }

                // 5. Iniciar reprodução
                Log.d(TAG, "speak() — chamando play()...")
                track.play()
                Log.d(TAG, "speak() — play() iniciado com sucesso")
            }

            Log.d(TAG, "speak() — concluído com sucesso")
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "speak() — IllegalStateException: ${e.message}", e)
            synchronized(lock) { releaseAudioTrackLocked() }
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "speak() — Exception inesperada: ${e.message}", e)
            synchronized(lock) { releaseAudioTrackLocked() }
            Result.failure(e)
        }
    }

    /**
     * Sintetiza o texto e retorna as amostras de áudio sem reproduzir.
     * Útil para salvar em arquivo ou processamento posterior.
     *
     * @param text Texto a ser sintetizado.
     * @return FloatArray de amostras PCM [-1.0, 1.0] ou null se falhar.
     */
    suspend fun synthesizeToBuffer(text: String): FloatArray? = withContext(Dispatchers.Default) {
        try {
            if (!ttsEngine.isInitialized) {
                Log.w(TAG, "synthesizeToBuffer() — Engine não inicializado")
                return@withContext null
            }
            Log.d(TAG, "synthesizeToBuffer() — sintetizando \"$text\"")
            val result = ttsEngine.synthesize(text)
            Log.d(TAG, "synthesizeToBuffer() — retornou ${result?.size ?: 0} amostras")
            result
        } catch (e: Exception) {
            Log.e(TAG, "synthesizeToBuffer() — Exception: ${e.message}", e)
            null
        }
    }

    /**
     * Reproduz amostras PCM float previamente sintetizadas.
     * Thread-safe — executa sob [lock].
     *
     * @param samples Amostras PCM no intervalo [-1.0, 1.0].
     * @param sampleRate Taxa de amostragem em Hz.
     */
    fun playBuffer(samples: FloatArray, sampleRate: Int) {
        Log.d(TAG, "playBuffer() — ${samples.size} amostras, sampleRate=$sampleRate")
        try {
            if (samples.isEmpty()) {
                Log.w(TAG, "playBuffer() — amostras vazias, ignorando")
                return
            }
            if (sampleRate <= 0) {
                Log.e(TAG, "playBuffer() — sampleRate inválido: $sampleRate")
                return
            }

            // Converte e reproduz sob lock
            val pcmData = floatArrayToShortArray(samples)
            synchronized(lock) {
                releaseAudioTrackLocked()

                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufferSize < 0) {
                    Log.e(TAG, "playBuffer() — getMinBufferSize() falhou: $minBufferSize")
                    return
                }

                val requiredSize = pcmData.size * 2
                val bufferSize = minBufferSize.coerceAtLeast(requiredSize)

                val track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    bufferSize,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                audioTrack = track

                // Write ANTES de play (MODE_STATIC)
                val written = track.write(pcmData, 0, pcmData.size)
                if (written == pcmData.size) {
                    track.play()
                    Log.d(TAG, "playBuffer() — reprodução iniciada")
                } else {
                    Log.e(TAG, "playBuffer() — write falhou: $written / ${pcmData.size}")
                    releaseAudioTrackLocked()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "playBuffer() — Exception: ${e.message}", e)
            synchronized(lock) { releaseAudioTrackLocked() }
        }
    }

    /**
     * Para a reprodução atual e libera o AudioTrack.
     * Thread-safe — executa sob [lock].
     */
    fun stop() {
        Log.d(TAG, "stop() — chamado")
        synchronized(lock) {
            releaseAudioTrackLocked()
        }
        Log.d(TAG, "stop() — concluído")
    }

    /**
     * Libera o AudioTrack atual (stop + release + null).
     * **DEVE ser chamado dentro de um bloco `synchronized(lock)`**.
     */
    private fun releaseAudioTrackLocked() {
        val track = audioTrack
        if (track != null) {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    Log.d(TAG, "releaseAudioTrackLocked() — chamando stop()")
                    track.stop()
                }
            } catch (_: Exception) {
                // Ignora — o AudioTrack pode já estar em estado finalizado
            }
            try {
                Log.d(TAG, "releaseAudioTrackLocked() — chamando release()")
                track.release()
            } catch (_: Exception) {
                // Ignora — release em track já liberado é safe
            }
            audioTrack = null
            Log.d(TAG, "releaseAudioTrackLocked() — AudioTrack liberado")
        }
    }

    /**
     * Pausa a reprodução atual.
     * Nota: AudioTrack em MODE_STATIC não suporta pause/resume nativamente.
     *       Para pause real, use MODE_STREAM.
     */
    fun pause() {
        Log.d(TAG, "pause() — chamado")
        synchronized(lock) {
            try {
                audioTrack?.pause()
            } catch (e: Exception) {
                Log.w(TAG, "pause() — exceção ao pausar: ${e.message}")
            }
        }
    }

    /**
     * Libera todos os recursos do sintetizador e do engine.
     */
    fun release() {
        Log.d(TAG, "release() — chamado")
        synchronized(lock) {
            releaseAudioTrackLocked()
        }
        try {
            ttsEngine.release()
        } catch (e: Exception) {
            Log.e(TAG, "release() — exceção ao liberar engine: ${e.message}", e)
        }
        Log.d(TAG, "release() — concluído")
    }

    /**
     * Converte array de floats [-1.0, 1.0] para shorts PCM 16-bit.
     * Aplica **clamp** no intervalo [-1.0f, 1.0f] antes da conversão para evitar
     * overflow e garantir que valores fora da faixa sejam truncados de forma segura.
     */
    private fun floatArrayToShortArray(floats: FloatArray): ShortArray {
        return ShortArray(floats.size) { index ->
            val clamped = floats[index].coerceIn(-1.0f, 1.0f)
            (clamped * FLOAT_TO_SHORT_SCALE).toInt().toShort()
        }
    }
}
