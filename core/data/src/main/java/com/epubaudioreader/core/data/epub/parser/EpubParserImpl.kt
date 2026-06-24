package com.epubaudioreader.core.data.epub.parser

import android.util.Log
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import com.epubaudioreader.core.data.epub.model.ParsedEpub
import com.epubaudioreader.core.data.epub.model.ParsedOpf
import com.epubaudioreader.core.data.epub.model.TocEntry
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Main EPUB parser that orchestrates container, OPF, and TOC parsing.
 * Opens ZipFile only once (BUG-EPUB-004) and passes reference to sub-parsers.
 * Includes proper error logging (BUG-EPUB-008).
 * Maps TOC entries to correct spine indices (BUG-EPUB-003).
 */
class EpubParserImpl @Inject constructor(
    private val containerParser: ContainerParser,
    private val opfParser: OpfParser,
    private val ncxParser: NcxParser,
    private val navDocParser: NavDocParser,
    private val dispatcher: DispatcherProvider
) : EpubParser {

    companion object {
        private const val TAG = "EpubParserImpl"
    }

    override suspend fun parse(file: File): ParsedEpub = withContext(dispatcher.io) {
        // BUG-EPUB-004: Open ZipFile only once in the main method
        ZipFile(file).use { zip ->
            val opfPath = containerParser.parse(zip)
            val opfDir = opfPath.substringBeforeLast('/', "")
            val parsedOpf = opfParser.parse(zip, opfPath)

            // BUG-EPUB-003: Build href -> spineIndex map for correct TOC alignment
            val hrefToSpineIndex = buildHrefToSpineIndex(parsedOpf)

            val toc = parseToc(zip, parsedOpf, opfDir, hrefToSpineIndex)

            Log.i(TAG, "Parsed EPUB: title='${parsedOpf.metadata.title}', " +
                    "spine=${parsedOpf.spine.size}, chapters=${parsedOpf.spine.count { it.linear }}, " +
                    "manifest=${parsedOpf.manifest.size}, toc=${toc.size}")

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

    /**
     * Builds a map of manifest href -> spine index for correct TOC alignment. (BUG-EPUB-003)
     */
    private fun buildHrefToSpineIndex(opf: ParsedOpf): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for ((index, spineItem) in opf.spine.withIndex()) {
            val manifestItem = opf.manifest[spineItem.idref]
            if (manifestItem != null) {
                map[manifestItem.href] = index
            }
        }
        return map
    }

    private fun parseToc(
        zip: ZipFile,
        opf: ParsedOpf,
        opfDir: String,
        hrefToSpineIndex: Map<String, Int>
    ): List<TocEntry> {
        // Try EPUB 3 nav document first
        val navItem = opf.manifest.values.find { it.properties == "nav" }
        if (navItem != null) {
            val navPath = resolvePath(opfDir, navItem.href)
            return try {
                navDocParser.parse(zip, navPath, hrefToSpineIndex)
            } catch (e: Exception) {
                // BUG-EPUB-008: Log errors instead of silently swallowing
                Log.e(TAG, "Failed to parse nav document at $navPath", e)
                emptyList()
            }
        }

        // Fallback to EPUB 2 NCX
        val ncxItem = opf.manifest.values.find {
            it.mediaType == "application/x-dtbncx+xml"
        }
        if (ncxItem != null) {
            val ncxPath = resolvePath(opfDir, ncxItem.href)
            return try {
                ncxParser.parse(zip, ncxPath, hrefToSpineIndex)
            } catch (e: Exception) {
                // BUG-EPUB-008: Log errors instead of silently swallowing
                Log.e(TAG, "Failed to parse NCX at $ncxPath", e)
                emptyList()
            }
        }

        Log.w(TAG, "No TOC found in EPUB")
        return emptyList()
    }

    private fun resolvePath(opfDir: String, href: String): String {
        return if (opfDir.isEmpty()) href else "$opfDir/$href"
    }
}
