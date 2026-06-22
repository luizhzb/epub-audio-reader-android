package com.epubaudioreader.domain.model

data class Chapter(
    val id: Long = 0,
    val bookId: Long,
    val title: String,
    val orderIndex: Int,
    val contentHtml: String = "",
    val paragraphCount: Int = 0
)
