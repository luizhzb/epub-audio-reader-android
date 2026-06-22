package com.epubaudioreader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String = "",
    val coverImagePath: String? = null,
    val totalChapters: Int = 0,
    val filePath: String = "",
    val addedDate: Long = System.currentTimeMillis()
)
