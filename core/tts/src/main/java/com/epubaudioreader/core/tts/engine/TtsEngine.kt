package com.epubaudioreader.core.tts.engine

import com.k2fsa.sherpa.onnx.OfflineTts

interface TtsEngine {
    val isInitialized: Boolean
    val sampleRate: Int
    fun initialize(assetManager: android.content.res.AssetManager): Boolean
    fun getTts(): OfflineTts?
    fun release()
}
