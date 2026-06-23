package com.epubaudioreader.core.tts.synthesis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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
 * Exemplo de uso:
 * ```
 * val synthesizer = TtsSynthesizer(ttsEngine)
 * 
 * // Inicializar engine
 * ttsEngine.initialize(modelDir)
 * 
 * // Sintetizar e falar
 * synthesizer.speak("Olá, mundo!")
 * 
 * // Parar reprodução
 * synthesizer.stop()
 * ```
 */
@Singleton
class TtsSynthesizer @Inject constructor(
    private val ttsEngine: TtsEngine
) {
    companion object {
        /** Multiplicador para conversão float [-1.0, 1.0] -> short [-32768, 32767] */
        const val FLOAT_TO_SHORT_SCALE = 32767.0f

        /** Tamanho mínimo do buffer de áudio em bytes */
        const val MIN_BUFFER_MULTIPLIER = 2
    }

    private var audioTrack: AudioTrack? = null

    /** Indica se há reprodução ativa no momento. */
    val isPlaying: Boolean
        get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

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
        try {
            if (!ttsEngine.isInitialized) {
                return@withContext Result.failure(
                    IllegalStateException("Engine TTS não inicializado. Chame initialize() primeiro.")
                )
            }

            if (text.isBlank()) {
                return@withContext Result.success(Unit)
            }

            // Para reprodução anterior se houver
            stopInternal()

            val samples = ttsEngine.synthesize(text)
                ?: return@withContext Result.failure(
                    IllegalStateException("Síntese falhou: engine retornou null")
                )

            if (samples.isEmpty()) {
                return@withContext Result.success(Unit)
            }

            playPcm(samples, ttsEngine.sampleRate)
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
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
                return@withContext null
            }
            ttsEngine.synthesize(text)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Reproduz amostras PCM float previamente sintetizadas.
     *
     * @param samples Amostras PCM no intervalo [-1.0, 1.0].
     * @param sampleRate Taxa de amostragem em Hz.
     */
    fun playBuffer(samples: FloatArray, sampleRate: Int) {
        try {
            stopInternal()

            if (samples.isEmpty()) return

            playPcm(samples, sampleRate)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Configura e inicia a reprodução PCM via AudioTrack.
     *
     * @param samples Amostras float [-1.0, 1.0].
     * @param sampleRate Taxa de amostragem do áudio.
     */
    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        try {
            // Converte float [-1.0, 1.0] para short PCM 16-bit
            val pcmData = floatArrayToShortArray(samples)

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val bufferSize = minBufferSize.coerceAtLeast(pcmData.size * 2 * MIN_BUFFER_MULTIPLIER)

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

            // Libera AudioTrack anterior
            audioTrack?.release()
            audioTrack = track

            // Escreve dados e inicia reprodução
            val written = track.write(pcmData, 0, pcmData.size)
            if (written > 0) {
                track.play()
            } else {
                track.release()
                audioTrack = null
                throw IllegalStateException("Falha ao escrever dados no AudioTrack: $written")
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            throw IllegalStateException("Parâmetros de áudio inválidos: ${e.message}")
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Converte array de floats [-1.0, 1.0] para shorts PCM 16-bit.
     */
    private fun floatArrayToShortArray(floats: FloatArray): ShortArray {
        return ShortArray(floats.size) { index ->
            val value = (floats[index] * FLOAT_TO_SHORT_SCALE).toInt()
            value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Para a reprodução atual e libera o AudioTrack.
     */
    fun stop() {
        stopInternal()
    }

    /**
     * Para reprodução e libera recursos de áudio.
     */
    private fun stopInternal() {
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioTrack = null
        }
    }

    /**
     * Pausa a reprodução atual.
     * Nota: AudioTrack em MODE_STATIC não suporta pause/resume nativamente.
     *       Para pause real, use MODE_STREAM.
     */
    fun pause() {
        try {
            audioTrack?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Libera todos os recursos do sintetizador e do engine.
     */
    fun release() {
        stopInternal()
        ttsEngine.release()
    }
}
