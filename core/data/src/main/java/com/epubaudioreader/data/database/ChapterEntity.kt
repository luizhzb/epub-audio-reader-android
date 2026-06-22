package com.epubaudioreader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val title: String,
    val orderIndex: Int,
    val contentHtml: String = "",
    val paragraphCount: Int = 0
)
