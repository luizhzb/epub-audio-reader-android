package com.epubaudioreader.core.data.epub.model

data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: String? = null,
    val fallback: String? = null
)
