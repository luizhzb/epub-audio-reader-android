package com.epubaudioreader.core.domain.model

data class Chapter(
    val id: Long = 0,
    val bookId: Long,
    val title: String,
    val orderIndex: Int,
    val contentFilePath: String,
    val charCount: Int = 0,
    val paragraphCount: Int = 0,
    val spineIndex: Int = 0,
    val href: String = ""
)
