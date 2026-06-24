package com.epubaudioreader.core.tts.synthesis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.tts.engine.TtsEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsSynthesizer @Inject constructor(
    private val ttsEngine: TtsEngine
) {
    companion object {
        private const val TAG = "TtsSynthesizer"
        private const val GENERATION_TIMEOUT_MS = 30000L
    }

    private var track: AudioTrack? = null
    @Volatile
    private var stopped: Boolean = true
    private val lock = Object()

    /**
     * Mutex que serializa chamadas ao motor Sherpa-ONNX.
     *
     * OfflineTts nao e thread-safe: gerar varios segmentos em paralelo
     * (como o pipeline faz no prefetch) corrompe o estado nativo e pode
     * travar ou retornar audio vazio. Apenas uma geracao por vez.
     */
    private val generationMutex = Mutex()

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
            track?.let { old ->
                Log.d(TAG, "Liberando AudioTrack anterior")
                try { old.stop() } catch (_: Exception) {}
                try { old.release() } catch (_: Exception) {}
            }
            track = AudioTrack(
                attr, format, bufLength,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            Log.i(TAG, "AudioTrack criado (state=${track?.state})")
        }
    }

    fun startPlayback() {
        synchronized(lock) {
            val t = track ?: throw IllegalStateException("AudioTrack nao inicializado")
            if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                t.play()
            }
        }
    }

    /**
     * Toca samples PCM float previamente sintetizados no AudioTrack.
     * Retorna imediatamente apos iniciar o playback (nao bloqueia).
     */
    fun playSamples(samples: FloatArray, sampleRate: Int): kotlin.Result<Unit> {
        return try {
            if (samples.isEmpty()) {
                Log.w(TAG, "playSamples recebeu samples vazio")
                return kotlin.Result.success(Unit)
            }

            synchronized(lock) {
                // Recria AudioTrack se necessario (sampleRate mudou ou foi liberado)
                val needsRecreate = track?.sampleRate != sampleRate ||
                        track?.state == AudioTrack.STATE_UNINITIALIZED

                if (needsRecreate) {
                    Log.d(TAG, "Recriando AudioTrack sampleRate=$sampleRate")
                    try { track?.release() } catch (_: Exception) {}

                    val bufLength = AudioTrack.getMinBufferSize(
                        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
                    )
                    val attr = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA).build()
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setSampleRate(sampleRate).build()
                    track = AudioTrack(attr, format, bufLength, AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE)
                }

                val t = track ?: return kotlin.Result.failure(
                    IllegalStateException("AudioTrack nulo apos inicializacao")
                )

                Log.d(TAG, "playSamples: ${samples.size} samples, playState=${t.playState}")
                t.play()
                stopped = false
                val written = t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                Log.d(TAG, "playSamples: escritos $written floats")
                if (written < 0) {
                    throw IllegalStateException("AudioTrack.write retornou erro $written")
                }
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
     */
    private fun callback(samples: FloatArray): Int {
        return try {
            synchronized(lock) {
                if (stopped) return 0
                val t = track ?: return 0.also {
                    Log.e(TAG, "AudioTrack nulo no callback JNI")
                    stopped = true
                }
                val written = t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    Log.e(TAG, "AudioTrack.write erro no callback: $written")
                    stopped = true
                    0
                } else {
                    1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack write error no callback JNI: ${e.message}", e)
            stopped = true
            0
        }
    }

    /**
     * Sintetiza e reproduz o texto via AudioTrack com callback.
     * Usa delay() em vez de Thread.sleep() para nao bloquear a thread de IO.
     */
    suspend fun speak(text: String, sid: Int = 0, speed: Float = 1.0f, onComplete: (() -> Unit)? = null): kotlin.Result<Unit> {
        return try {
            if (text.isBlank()) return kotlin.Result.success(Unit)

            val tts = ttsEngine.getTts()
                ?: return kotlin.Result.failure(IllegalStateException("TTS nao inicializado"))

            // Timeout no mutex para evitar deadlock
            val acquired = withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                generationMutex.tryLock()
            }
            if (acquired != true) {
                Log.w(TAG, "speak: generationMutex nao disponivel (outra geracao em andamento)")
                return kotlin.Result.failure(IllegalStateException("TTS ocupado. Aguarde o segmento atual terminar."))
            }

            try {
                Log.d(TAG, "speak: '${text.take(60)}...'")

                synchronized(lock) {
                    track?.flush()
                    stopped = false
                }

                tts.generateWithCallback(
                    text = text,
                    sid = sid,
                    speed = speed,
                    callback = ::callback
                )

                Log.d(TAG, "speak: geracao concluida")
                onComplete?.invoke()
                kotlin.Result.success(Unit)
            } finally {
                generationMutex.unlock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro em speak: ${e.message}", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Sintetiza o texto para memoria, retornando os samples PCM float.
     * Usa FloatArray diretamente para evitar OOM com boxing.
     */
    suspend fun synthesizeToMemory(
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

            // Timeout no mutex para evitar deadlock
            val acquired = withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                generationMutex.tryLock()
            }
            if (acquired != true) {
                return Result.Error(IllegalStateException("TTS ocupado. Aguarde o segmento atual terminar."))
            }

            try {
                Log.d(TAG, "synthesizeToMemory: '${text.take(60)}...'")

                var samples = FloatArray(0)
                var totalSize = 0

                val memoryCallback: (FloatArray) -> Int = { chunk ->
                    try {
                        if (chunk.isNotEmpty()) {
                            val newSize = totalSize + chunk.size
                            if (newSize > samples.size) {
                                val newCapacity = (newSize * 1.5).toInt().coerceAtLeast(newSize + 4096)
                                samples = samples.copyOf(newCapacity)
                            }
                            chunk.copyInto(samples, destinationOffset = totalSize)
                            totalSize += chunk.size
                        }
                        1
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro acumulando samples: ${e.message}", e)
                        0
                    }
                }

                tts.generateWithCallback(
                    text = text,
                    sid = sid,
                    speed = speed,
                    callback = memoryCallback
                )

                val finalSamples = if (totalSize < samples.size) samples.copyOf(totalSize) else samples
                val result = SynthesizedAudio(finalSamples, tts.sampleRate())
                Log.d(TAG, "synthesizeToMemory: ${result.samples.size} samples")
                Result.Success(result)
            } finally {
                generationMutex.unlock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro em synthesizeToMemory: ${e.message}", e)
            Result.Error(e)
        }
    }

    fun stop() {
        synchronized(lock) {
            stopped = true
            try { track?.pause() } catch (_: Exception) {}
            try { track?.flush() } catch (_: Exception) {}
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
