package com.epubaudioreader.core.domain.model

data class Book(
    val id: Long = 0,
    val title: String,
    val authors: String,
    val language: String = "pt-BR",
    val identifier: String = "",
    val description: String? = null,
    val coverImagePath: String? = null,
    val filePath: String,
    val importDate: Long = System.currentTimeMillis(),
    val lastReadDate: Long? = null,
    val totalChapters: Int = 0,
    val totalChars: Long = 0,
    val fileSize: Long = 0,
    val hash: String = "",
    val lastReadChapterId: Long? = null,
    val lastReadPosition: Int? = null
)
