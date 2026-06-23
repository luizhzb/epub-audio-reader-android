package com.epubaudioreader.core.tts.synthesis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.tts.engine.TtsEngine
import com.k2fsa.sherpa.onnx.GenerationConfig
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsSynthesizer @Inject constructor(
    private val ttsEngine: TtsEngine
) {
    companion object {
        private const val TAG = "TtsSynthesizer"
    }

    private var track: AudioTrack? = null
    private var stopped: Boolean = true
    private val lock = Object()

    val isPlaying: Boolean
        get() = synchronized(lock) { !stopped }

    fun isAudioTrackInitialized(): Boolean = synchronized(lock) { track != null }

    fun initAudioTrack() {
        val tts = ttsEngine.getTts() ?: throw IllegalStateException("TTS nao inicializado")
        val sampleRate = tts.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i(TAG, "AudioTrack sampleRate=$sampleRate bufLength=$bufLength")

        if (bufLength <= 0) {
            throw IllegalStateException("Formato PCM_FLOAT nao suportado pelo dispositivo (bufLength=$bufLength)")
        }

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        synchronized(lock) {
            track = AudioTrack(
                attr, format, bufLength,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        }
    }

    fun startPlayback() {
        synchronized(lock) {
            track?.play()
        }
    }

    /**
     * Toca samples PCM float previamente sintetizados no AudioTrack.
     * Usado pelo PlaybackCoordinator quando o pipeline ja tem o audio em cache.
     */
    fun playSamples(samples: FloatArray, sampleRate: Int): kotlin.Result<Unit> {
        return try {
            if (samples.isEmpty()) return kotlin.Result.success(Unit)

            val bufLength = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            if (bufLength <= 0) {
                return kotlin.Result.failure(
                    IllegalStateException("Formato PCM_FLOAT nao suportado (bufLength=$bufLength)")
                )
            }

            synchronized(lock) {
                track?.pause()
                track?.flush()

                // Recriar AudioTrack se sampleRate mudou
                if (track?.sampleRate != sampleRate) {
                    try { track?.release() } catch (_: Exception) {}
                    val attr = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setSampleRate(sampleRate)
                        .build()
                    track = AudioTrack(
                        attr, format, bufLength,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                }

                track?.play()
                stopped = false
                track?.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track?.stop()
            }
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro em playSamples: ${e.message}", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Callback chamado pelo JNI do Sherpa-ONNX durante a geracao.
     * Retorna 1 para continuar, 0 para parar.
     *
     * IMPORTANTE: Este callback e invocado por thread nativa do JNI.
     * Qualquer excecao nao tratada causa crash fatal (signal 6/11).
     * SEMPRE usar try/catch aqui.
     */
    private fun callback(samples: FloatArray): Int {
        return try {
            synchronized(lock) {
                if (!stopped) {
                    track?.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    1
                } else {
                    track?.stop()
                    0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack write error no callback JNI: ${e.message}", e)
            stopped = true
            0
        }
    }

    fun speak(text: String, sid: Int = 0, speed: Float = 1.0f, onComplete: (() -> Unit)? = null): kotlin.Result<Unit> {
        return try {
            val tts = ttsEngine.getTts() ?: return kotlin.Result.failure(IllegalStateException("TTS nao inicializado"))
            if (text.isBlank()) return kotlin.Result.success(Unit)

            // Resetar AudioTrack entre geracoes (como no oficial)
            synchronized(lock) {
                track?.pause()
                track?.flush()
                track?.play()
                stopped = false
            }

            // Gerar com callback (thread de background)
            val genConfig = GenerationConfig(sid = sid, speed = speed)
            tts.generateWithConfigAndCallback(
                text = text,
                config = genConfig,
                callback = this::callback
            )

            onComplete?.invoke()
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro em speak: ${e.message}", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Sintetiza o texto para memória, retornando os samples PCM float.
     * Útil para prefetch no pipeline de síntese.
     */
    fun synthesizeToMemory(
        text: String,
        sid: Int = 0,
        speed: Float = 1.0f
    ): Result<SynthesizedAudio> {
        return try {
            val tts = ttsEngine.getTts()
                ?: return Result.Error(IllegalStateException("TTS nao inicializado"))
            if (text.isBlank()) {
                return Result.Success(SynthesizedAudio(FloatArray(0), tts.sampleRate()))
            }

            val samples = mutableListOf<Float>()
            val memoryCallback: (FloatArray) -> Int = { chunk ->
                try {
                    samples.addAll(chunk.toList())
                    1
                } catch (e: Exception) {
                    Log.e(TAG, "Erro acumulando samples: ${e.message}", e)
                    0
                }
            }

            val genConfig = GenerationConfig(sid = sid, speed = speed)
            tts.generateWithConfigAndCallback(
                text = text,
                config = genConfig,
                callback = memoryCallback
            )

            Result.Success(SynthesizedAudio(samples.toFloatArray(), tts.sampleRate()))
        } catch (e: Exception) {
            Log.e(TAG, "Erro em synthesizeToMemory: ${e.message}", e)
            Result.Error(e)
        }
    }

    fun stop() {
        synchronized(lock) {
            stopped = true
            try { track?.stop() } catch (_: Exception) {}
        }
    }

    fun release() {
        synchronized(lock) {
            stopped = true
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            track = null
        }
    }
}
