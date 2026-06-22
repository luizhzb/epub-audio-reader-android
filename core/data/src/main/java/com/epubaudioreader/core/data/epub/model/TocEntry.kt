package com.epubaudioreader.core.data.epub.model

data class TocEntry(
    val title: String,
    val href: String,
    val spineIndex: Int = 0,
    val depth: Int = 0,
    val linear: Boolean = true
)
