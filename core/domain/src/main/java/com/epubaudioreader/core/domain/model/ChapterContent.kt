package com.epubaudioreader.core.domain.model

data class ChapterContent(
    val title: String,
    val paragraphs: List<String>,
    val totalChars: Int,
    val totalParagraphs: Int
)
