package com.epubaudioreader.core.data.epub.model

data class SpineItem(
    val idref: String,
    val linear: Boolean = true,
    val id: String? = null
)
