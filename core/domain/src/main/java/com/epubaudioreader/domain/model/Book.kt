package com.epubaudioreader.domain.model

data class Book(
    val id: Long = 0,
    val title: String,
    val author: String = "",
    val coverImagePath: String? = null,
    val totalChapters: Int = 0,
    val filePath: String = "",
    val addedDate: Long = System.currentTimeMillis()
)
