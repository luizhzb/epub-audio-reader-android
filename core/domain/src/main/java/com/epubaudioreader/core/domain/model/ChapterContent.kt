package com.epubaudioreader.core.domain.model

data class ChapterContent(
    val paragraphs: List<String>,
    val totalChars: Int,
    val totalParagraphs: Int
)
