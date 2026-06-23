package com.epubaudioreader.core.tts.playback

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentSegmentIndex: Int = 0,
    val totalSegments: Int = 0,
    val currentText: String = "",
    val error: String? = null
)
