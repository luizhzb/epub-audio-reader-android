package com.epubaudioreader.core.data.epub.extractor

import com.epubaudioreader.core.data.epub.model.ManifestItem
import com.epubaudioreader.core.data.epub.model.ParsedEpub
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * For each item in the spine, resolves the href via manifest,
 * reads the XHTML from the ZIP, extracts text via TextExtractor,
 * and returns the clean text along with metadata.
 */
class ChapterExtractor @Inject constructor(
    private val textExtractor: TextExtractor
) {

    /**
     * Extracts chapters from the parsed EPUB.
     * Returns list of tuples: (title, text, href, spineIndex)
     */
    fun extractChapters(parsedEpub: ParsedEpub, bookId: Long): List<ChapterData> {
        val chapters = mutableListOf<ChapterData>()
        val tocMap = buildTocMap(parsedEpub)

        ZipFile(parsedEpub.bookFile).use { zip ->
            for ((spineIndex, spineItem) in parsedEpub.spine.withIndex()) {
                if (!spineItem.linear) {
                    // Skip non-linear spine items but still count index
                    continue
                }

                val manifestItem = parsedEpub.manifest[spineItem.idref]
                if (manifestItem == null) {
                    chapters.add(
                        ChapterData(
                            title = tocMap[spineIndex]?.title ?: "Capítulo ${chapters.size + 1}",
                            text = "",
                            href = "",
                            spineIndex = spineIndex
                        )
                    )
                    continue
                }

                val contentPath = resolvePath(parsedEpub.opfDir, manifestItem.href)
                val xhtmlContent = try {
                    readZipEntry(zip, contentPath)
                } catch (_: Exception) {
                    ""
                }

                val cleanText = if (xhtmlContent.isNotBlank()) {
                    textExtractor.extract(xhtmlContent)
                } else {
                    ""
                }

                val tocEntry = tocMap[spineIndex]
                val title = tocEntry?.title ?: extractTitleFromHtml(xhtmlContent)
                    ?: "Capítulo ${chapters.size + 1}"

                chapters.add(
                    ChapterData(
                        title = title,
                        text = cleanText,
                        href = manifestItem.href,
                        spineIndex = spineIndex
                    )
                )
            }
        }

        return chapters
    }

    private fun buildTocMap(parsedEpub: ParsedEpub): Map<Int, com.epubaudioreader.core.data.epub.model.TocEntry> {
        val map = mutableMapOf<Int, com.epubaudioreader.core.data.epub.model.TocEntry>()
        for (entry in parsedEpub.toc) {
            val spineIndex = entry.spineIndex
            if (!map.containsKey(spineIndex)) {
                map[spineIndex] = entry
            }
        }
        return map
    }

    private fun extractTitleFromHtml(xhtml: String): String? {
        // Try to find <title> or first <h1>
        val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
        val h1Regex = Regex("<h1[^>]*>(.*?)</h1>", RegexOption.IGNORE_CASE)

        return titleRegex.find(xhtml)?.groupValues?.get(1)?.trim()?.ifBlank { null }
            ?: h1Regex.find(xhtml)?.groupValues?.get(1)?.let {
                // Strip inner HTML tags
                it.replace(Regex("<[^>]+>"), "").trim().ifBlank { null }
            }
    }

    private fun readZipEntry(zip: ZipFile, path: String): String {
        val entry = zip.getEntry(path)
            ?: throw IllegalArgumentException("Entry not found: $path")
        return zip.getInputStream(entry).use { stream ->
            stream.bufferedReader().use { it.readText() }
        }
    }

    private fun resolvePath(opfDir: String, href: String): String {
        return if (opfDir.isEmpty()) href else "$opfDir/$href"
    }

    data class ChapterData(
        val title: String,
        val text: String,
        val href: String,
        val spineIndex: Int
    )
}
