package com.epubaudioreader.core.data.epub.parser

import com.epubaudioreader.core.data.epub.model.ParsedEpub
import java.io.File

interface EpubParser {
    suspend fun parse(file: File): ParsedEpub
}
