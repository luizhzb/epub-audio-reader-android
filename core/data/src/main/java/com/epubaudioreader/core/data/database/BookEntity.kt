package com.epubaudioreader.core.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["hash"], unique = true)
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
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
