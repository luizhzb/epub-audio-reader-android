package com.epubaudioreader.core.domain.model

data class Chapter(
    val id: Long = 0,
    val bookId: Long,
    val title: String,
    val orderIndex: Int,
    val contentFilePath: String,
    val charCount: Int,
    val paragraphCount: Int,
    val spineIndex: Int,
    val href: String
)
