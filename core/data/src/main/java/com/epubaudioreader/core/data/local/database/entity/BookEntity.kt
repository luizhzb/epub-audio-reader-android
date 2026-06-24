package com.epubaudioreader.core.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["title"]),
        Index(value = ["lastReadDate"]),
        Index(value = ["hash"], unique = true),
        Index(value = ["filePath"])
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val authors: String,
    val language: String,
    val identifier: String,
    val description: String? = null,
    val coverImagePath: String? = null,
    @ColumnInfo(name = "filePath") val filePath: String,
    val importDate: Long = System.currentTimeMillis(),
    val lastReadDate: Long? = null,
    val totalChapters: Int,
    val totalChars: Long,
    val fileSize: Long,
    val hash: String,
    val lastReadChapterId: Long? = null,
    val lastReadPosition: Int? = null
)
