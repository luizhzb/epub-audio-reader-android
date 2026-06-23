package com.epubaudioreader.core.tts.segmentation

data class TextSegment(
    val id: Int,
    val text: String,
    val type: SegmentType,
    val chapterIndex: Int,
    val paragraphIndex: Int
)
