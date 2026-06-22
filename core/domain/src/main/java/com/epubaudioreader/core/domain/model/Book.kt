package com.epubaudioreader.core.domain.model

data class Book(
    val id: Long = 0,
    val title: String,
    val authors: String,
    val language: String,
    val identifier: String,
    val description: String? = null,
    val coverPath: String? = null,
    val filePath: String,
    val totalChapters: Int = 0,
    val totalChars: Long = 0,
    val fileSize: Long = 0,
    val hash: String = "",
    val lastReadChapterId: Long? = null,
    val lastReadPosition: Int? = null,
    val lastReadAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
)
