package com.epubaudioreader.core.data.epub.model

data class ParsedOpf(
    val metadata: ParsedMetadata,
    val manifest: Map<String, ManifestItem>,
    val spine: List<SpineItem>,
    val guide: List<GuideReference>,
    val opfDir: String,
    val version: String = "2.0"
)
