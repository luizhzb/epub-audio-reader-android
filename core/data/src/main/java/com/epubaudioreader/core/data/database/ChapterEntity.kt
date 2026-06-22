package com.epubaudioreader.core.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId", "orderIndex"], unique = true),
        Index(value = ["bookId", "spineIndex"])
    ]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
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
