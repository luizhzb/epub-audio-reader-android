package com.epubaudioreader.core.tts.synthesis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.epubaudioreader.core.tts.engine.TtsEngine
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsSynthesizer @Inject constructor(
    private val ttsEngine: TtsEngine
) {
    companion object {
        private const val TAG = "TtsSynthesizer"
        private const val SAMPLE_RATE_FALLBACK = 22050
    }

    private var audioTrack: AudioTrack? = null
    private val lock = Object()

    val isPlaying: Boolean
        get() = synchronized(lock) {
            audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
        }

    private fun floatToShortArray(floats: FloatArray): ShortArray {
        return ShortArray(floats.size) { i ->
            val clamped = floats[i].coerceIn(-1.0f, 1.0f)
            (clamped * 32767).toInt().toShort()
        }
    }

    suspend fun speak(text: String, onComplete: (() -> Unit)? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!ttsEngine.isInitialized) {
                    return@withContext Result.failure(IllegalStateException("TTS engine nao inicializado"))
                }
                if (text.isBlank()) {
                    return@withContext Result.success(Unit)
                }

                stopInternal()

                Log.d(TAG, "Sintetizando texto: ${text.take(30)}...")
                val samples = ttsEngine.synthesize(text)

                if (samples == null || samples.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Sintese falhou ou retornou vazio"))
                }

                Log.d(TAG, "Audio sintetizado: ${samples.size} samples")

                // Converter para PCM 16bit
                val pcmData = floatToShortArray(samples)
                val sampleRate = if (ttsEngine.sampleRate > 0) ttsEngine.sampleRate else SAMPLE_RATE_FALLBACK

                playPcm16(pcmData, sampleRate, onComplete)
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Erro em speak: ${e.message}", e)
                Result.failure(e)
            }
        }

    private suspend fun playPcm16(pcmData: ShortArray, sampleRate: Int, onComplete: (() -> Unit)?) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuffer <= 0) {
            Log.e(TAG, "getMinBufferSize retornou valor invalido: $minBuffer")
            throw IllegalStateException("Formato de audio nao suportado pelo dispositivo")
        }

        val bufferSize = minBuffer.coerceAtLeast(pcmData.size * 2)
        Log.d(TAG, "AudioTrack bufferSize=$bufferSize, sampleRate=$sampleRate")

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

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack nao foi inicializado! state=${track.state}")
            track.release()
            throw IllegalStateException("AudioTrack: falha na inicializacao (state=${track.state})")
        }

        synchronized(lock) {
            audioTrack = track
        }

        val written = track.write(pcmData, 0, pcmData.size)
        if (written < 0) {
            Log.e(TAG, "AudioTrack write falhou: $written")
            stopInternal()
            throw IllegalStateException("AudioTrack write falhou: $written")
        }

        Log.d(TAG, "AudioTrack write OK: $written bytes")

        try {
            track.play()
            Log.d(TAG, "AudioTrack play iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack play falhou: ${e.message}")
            stopInternal()
            throw e
        }

        // Aguardar playback terminar usando coroutine do caller
        val playbackDurationMs = (pcmData.size * 1000L) / sampleRate
        Log.d(TAG, "Aguardando playback: ~${playbackDurationMs}ms")
        delay(playbackDurationMs + 200)

        // Verificar se ainda esta tocando
        var waitCount = 0
        while (isPlaying && waitCount < 50) {
            delay(100)
            waitCount++
        }

        Log.d(TAG, "Playback finalizado")
        onComplete?.invoke()
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        synchronized(lock) {
            val track = audioTrack
            audioTrack = null
            if (track != null) {
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                } catch (_: Exception) {}
                try {
                    track.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun release() {
        stopInternal()
    }
}
