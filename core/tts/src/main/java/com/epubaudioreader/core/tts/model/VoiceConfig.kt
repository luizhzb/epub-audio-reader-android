package com.epubaudioreader.core.tts.model

/**
 * Configuração de uma voz TTS, incluindo URLs dos arquivos do modelo
 * e parâmetros de síntese.
 */
data class VoiceConfig(
    val modelUrl: String,
    val configUrl: String,
    val tokensUrl: String,
    val lexiconUrl: String? = null,
    val dictDirUrl: String? = null,
    val dataDirUrl: String? = null,
    val speakerId: Int = 0,
    val speed: Float = 1.0f,
    val sampleRate: Int = 22050,
    val numThreads: Int = 4,
    val provider: String = "cpu"
)

/**
 * Configurações de voz pré-definidas para diferentes idiomas e modelos.
 */
object DefaultVoiceConfigs {

    /**
     * Voz pt-BR Faber (masculina, qualidade medium) via Piper.
     * Modelo disponível no HuggingFace: rhasspy/piper-voices
     */
    val PT_BR_FABER = VoiceConfig(
        modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/pt/pt_BR-faber-medium/pt_BR-faber-medium.onnx",
        configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/pt/pt_BR-faber-medium/pt_BR-faber-medium.onnx.json",
        tokensUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/pt/pt_BR-faber-medium/tokens.txt",
        sampleRate = 22050
    )

    /**
     * Voz pt-BR Faber (masculina, qualidade low) - menor e mais rápida para download.
     */
    val PT_BR_FABER_LOW = VoiceConfig(
        modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/pt/pt_BR-faber-low/pt_BR-faber-low.onnx",
        configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/pt/pt_BR-faber-low/pt_BR-faber-low.onnx.json",
        tokensUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/pt/pt_BR-faber-low/tokens.txt",
        sampleRate = 22050
    )

    /**
     * Pacote tar.bz2 completo do modelo pt-BR via Sherpa-ONNX releases.
     * Contém todos os arquivos necessários (model.onnx, tokens.txt, lexicon, dict, espeak-ng-data).
     */
    val PT_BR_SHERPA_TARBZ2 = VoiceConfig(
        modelUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pt_BR-faber-medium.tar.bz2",
        configUrl = "",
        tokensUrl = "",
        sampleRate = 22050
    )

    /**
     * Voz en-US (fallback / teste) - qualidade low, menor tamanho.
     */
    val EN_US_AMY_LOW = VoiceConfig(
        modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US-amy-low/en_US-amy-low.onnx",
        configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US-amy-low/en_US-amy-low.onnx.json",
        tokensUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US-amy-low/tokens.txt",
        sampleRate = 22050
    )

    /** Lista de todas as vozes disponíveis. */
    val ALL = listOf(PT_BR_FABER, PT_BR_FABER_LOW, PT_BR_SHERPA_TARBZ2, EN_US_AMY_LOW)

    /** Voz padrão: pt-BR Faber low (menor, download rápido). */
    val DEFAULT = PT_BR_FABER_LOW
}
