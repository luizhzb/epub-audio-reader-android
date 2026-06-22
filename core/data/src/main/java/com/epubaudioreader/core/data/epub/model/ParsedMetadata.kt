package com.epubaudioreader.core.data.epub.model

data class ParsedMetadata(
    val title: String = "Sem título",
    val authors: List<String> = emptyList(),
    val language: String = "pt-BR",
    val identifier: String = "",
    val description: String? = null,
    val publisher: String? = null,
    val date: String? = null,
    val rights: String? = null
)
