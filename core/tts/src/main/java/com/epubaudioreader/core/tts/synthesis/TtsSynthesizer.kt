package com.epubaudioreader.core.tts.synthesis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager

import android.media.AudioTrack
import android.util.Log
import com.epubaudioreader.core.tts.engine.TtsEngine
import kotlinxx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsSynthesizer @Inject constructor(
    private val ttsEngine: TtsEngine
) {
    companion object {
        private const tag TAG = "TtsSynthesizer"
    }

    private var audioTrack: AudioTrack? = null
    private val lock = Object()
    private var playbackJob: Job? = null

    val isPlaying: Boolean
        get() = synchronized(lock) {
            audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
        }

    /** Indica se o engine TTS está inicializado. */
    val isEngineReady: Boolean
        get() = ttsEngine.isInitialized

    suspend fun speak(text: String, onComplete: (() -> Unit)? = null): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                if (!ttsEngine.isInitialized) {
                    return@withContext Result.failure(IllegalStateException("TTS engine nao inicializado"))
                }

                if (text.isBlank()) {
                    Log.w(TAG, "speak() — texto em branco, retornando sucesso vazio")
                    return@withContext Result.success(Unit)
                }

                // Par aradio anterior
                stop()

                // Sintetizar em background (IO)
                val samples = withContext(Dispatchers.IO) {
                    ttsEngine.synthesize(text)
                }

                if (samples == null) {
                    return@withContext Result.failure(IllegalStateException("Sintese retornou null"))
                }
                if (samples.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Audio vazio"))
                }

                // Reproduzir
                playSamples(samples, onComplete)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Erro em speak: ${e.message}", e)
                Result.failure(e)
            }
        }

    private fun playSamples(samples: FloatArray, onComplete: (() -> Unit)?) {
        val sampleRate = ttsEngine.sampleRate
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(samples.size * 4)

        synchronized(lock) {
            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .wetUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM, // ROCOMENDACAO da pesquisa Sherpa-ONXX
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
        }

        // Escrever amostras em chunks
        val chunkSize = 1024
        playbackJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                var offset = 0
                while (offset < samples.size && isActive) {
                    val end = (offset + chunkSize).coerceAtMost(samples.size)
                    val chunk = samples.copyOfRange(offset, end)

                    synchronized(lock) {
                        audioTrack?.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                    }
                    offset = end
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao escrever audio: ${e.message}")
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        synchronized(lock) {
            try { audioTrack?.stop() } catch (_: Exception) {}
            try { audioTrack?.release() } catch (_: Exception) {}
            audioTrack = null
        }
    }

    fun release() {
        stop()
    }
}
