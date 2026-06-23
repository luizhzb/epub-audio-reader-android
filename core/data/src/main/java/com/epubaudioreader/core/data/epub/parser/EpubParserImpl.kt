package com.epubaudioreader.core.data.epub.parser

import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import com.epubaudioreader.core.data.epub.model.ParsedEpub
import com.epubaudioreader.core.data.epub.model.ParsedOpf
import com.epubaudioreader.core.data.epub.model.TocEntry
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

class EpubParserImpl @Inject constructor(
    private val containerParser: ContainerParser,
    private val opfParser: OpfParser,
    private val ncxParser: NcxParser,
    private val navDocParser: NavDocParser,
    private val dispatcher: DispatcherProvider
) : EpubParser {

    override suspend fun parse(file: File): ParsedEpub = withContext(dispatcher.io) {
        ZipFile(file).use { zip ->
            val opfPath = containerParser.parse(zip)
            val opfDir = opfPath.substringBeforeLast('/', "")
            val parsedOpf = opfParser.parse(zip, opfPath)

            val toc = parseToc(zip, parsedOpf, opfDir)

            ParsedEpub(
                metadata = parsedOpf.metadata,
                manifest = parsedOpf.manifest,
                spine = parsedOpf.spine,
                toc = toc,
                opfDir = opfDir,
                bookFile = file
            )
        }
    }

    private fun parseToc(zip: ZipFile, opf: ParsedOpf, opfDir: String): List<TocEntry> {
        val navItem = opf.manifest.values.find { it.properties == "nav" }
        if (navItem != null) {
            val navPath = resolvePath(opfDir, navItem.href)
            return try {
                navDocParser.parse(zip, navPath)
            } catch (_: Exception) {
                emptyList()
            }
        }

        val ncxItem = opf.manifest.values.find {
            it.mediaType == "application/x-dtbncx+xml"
        }
        if (ncxItem != null) {
            val ncxPath = resolvePath(opfDir, ncxItem.href)
            return try {
                ncxParser.parse(zip, ncxPath)
            } catch (_: Exception) {
                emptyList()
            }
        }

        return emptyList()
    }

    private fun resolvePath(opfDir: String, href: String): String {
        return if (opfDir.isEmpty()) href else "$opfDir/$href"
    }
}
