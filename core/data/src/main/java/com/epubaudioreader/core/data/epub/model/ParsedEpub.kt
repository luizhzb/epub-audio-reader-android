package com.epubaudioreader.core.data.epub.model

import java.io.File

data class ParsedEpub(
    val metadata: ParsedMetadata,
    val manifest: Map<String, ManifestItem>,
    val spine: List<SpineItem>,
    val toc: List<TocEntry>,
    val guide: List<GuideReference> = emptyList(),
    val opfDir: String,
    val bookFile: File
)
