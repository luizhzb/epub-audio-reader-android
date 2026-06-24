package com.epubaudioreader.core.data.epub.extractor

import android.util.Log
import com.epubaudioreader.core.data.epub.model.ManifestItem
import com.epubaudioreader.core.data.epub.model.ParsedEpub
import org.jsoup.Jsoup
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * For each item in the spine, resolves the href via manifest,
 * reads the XHTML from the ZIP, extracts text via TextExtractor,
 * and returns the clean text along with metadata.
 *
 * BUG-EPUB-003: Uses href-based TOC lookup instead of spineIndex.
 * BUG-EPUB-011: Validates mediaType before extracting text.
 * BUG-EPUB-013: Uses Jsoup for robust title extraction instead of regex.
 * BUG-EPUB-014: Normalizes relative paths.
 * BUG-EPUB-004: Accepts optional ZipFile to avoid re-opening.
 * BUG-EPUB-008: Logs errors instead of silently swallowing.
 */
class ChapterExtractor @Inject constructor(
    private val textExtractor: TextExtractor
) {

    companion object {
        private const val TAG = "ChapterExtractor"
        private val SUPPORTED_MEDIA_TYPES = setOf(
            "application/xhtml+xml",
            "text/html",
            "application/xml",
            "text/xml"
        )
    }

    /**
     * Extracts chapters from the parsed EPUB.
     * Returns list of tuples: (title, text, href, spineIndex)
     *
     * @param parsedEpub The parsed EPUB
     * @param bookId The book ID for logging
     * @param zipFile Optional pre-opened ZipFile to avoid re-opening (BUG-EPUB-004)
     */
    fun extractChapters(
        parsedEpub: ParsedEpub,
        bookId: Long,
        zipFile: ZipFile? = null
    ): List<ChapterData> {
        val chapters = mutableListOf<ChapterData>()
        // BUG-EPUB-003: Build TOC map by href instead of spineIndex
        val tocMap = buildTocMapByHref(parsedEpub)

        val processZip: (ZipFile) -> Unit = { zip ->
            for ((spineIndex, spineItem) in parsedEpub.spine.withIndex()) {
                if (!spineItem.linear) {
                    continue
                }

                val manifestItem = parsedEpub.manifest[spineItem.idref]
                if (manifestItem == null) {
                    chapters.add(
                        ChapterData(
                            title = tocMap.entries.find { it.value.spineIndex == spineIndex }?.value?.title
                                ?: "Cap\u00edtulo ${chapters.size + 1}",
                            text = "",
                            href = "",
                            spineIndex = spineIndex
                        )
                    )
                    continue
                }

                // BUG-EPUB-011: Validate mediaType before extracting
                if (!isSupportedMediaType(manifestItem.mediaType)) {
                    Log.d(TAG, "Skipping unsupported mediaType '${manifestItem.mediaType}' for ${manifestItem.href}")
                    chapters.add(
                        ChapterData(
                            title = tocMap[manifestItem.href]?.title
                                ?: "Cap\u00edtulo ${chapters.size + 1}",
                            text = "",
                            href = manifestItem.href,
                            spineIndex = spineIndex
                        )
                    )
                    continue
                }

                val contentPath = resolvePath(parsedEpub.opfDir, manifestItem.href)
                val xhtmlContent = try {
                    readZipEntry(zip, contentPath)
                } catch (e: Exception) {
                    // BUG-EPUB-008: Log errors
                    Log.e(TAG, "Failed to read $contentPath for book $bookId", e)
                    ""
                }

                val cleanText = if (xhtmlContent.isNotBlank()) {
                    textExtractor.extract(xhtmlContent)
                } else {
                    ""
                }

                // BUG-EPUB-003: Look up TOC by href for correct title
                val tocEntry = tocMap[manifestItem.href]
                val title = tocEntry?.title
                    ?: extractTitleFromHtml(xhtmlContent)
                    ?: "Cap\u00edtulo ${chapters.size + 1}"

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

        if (zipFile != null) {
            // BUG-EPUB-004: Use provided ZipFile
            processZip(zipFile)
        } else {
            ZipFile(parsedEpub.bookFile).use { zip ->
                processZip(zip)
            }
        }

        Log.i(TAG, "Extracted ${chapters.size} chapters for book $bookId")
        return chapters
    }

    /**
     * Builds TOC map keyed by href (without fragment) for reliable lookup. (BUG-EPUB-003)
     */
    private fun buildTocMapByHref(parsedEpub: ParsedEpub): Map<String, com.epubaudioreader.core.data.epub.model.TocEntry> {
        val map = mutableMapOf<String, com.epubaudioreader.core.data.epub.model.TocEntry>()
        for (entry in parsedEpub.toc) {
            val hrefKey = entry.href.substringBefore("#")
            if (!map.containsKey(hrefKey)) {
                map[hrefKey] = entry
            }
        }
        return map
    }

    /**
     * Checks if the media type is supported for text extraction. (BUG-EPUB-011)
     */
    private fun isSupportedMediaType(mediaType: String): Boolean {
        return mediaType in SUPPORTED_MEDIA_TYPES ||
                mediaType.startsWith("text/") ||
                mediaType.contains("html") ||
                mediaType.contains("xhtml")
    }

    /**
     * Extracts title using Jsoup for robust parsing. (BUG-EPUB-013)
     */
    private fun extractTitleFromHtml(xhtml: String): String? {
        if (xhtml.isBlank()) return null
        return try {
            val document = Jsoup.parse(xhtml)
            // Try <title> first
            val title = document.selectFirst("title")?.text()?.trim()
            if (!title.isNullOrBlank()) return title

            // Then try <h1>
            val h1 = document.selectFirst("h1")?.text()?.trim()
            if (!h1.isNullOrBlank()) return h1

            // Then try any heading
            for (level in 2..6) {
                val heading = document.selectFirst("h$level")?.text()?.trim()
                if (!heading.isNullOrBlank()) return heading
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract title from HTML", e)
            null
        }
    }

    private fun readZipEntry(zip: ZipFile, path: String): String {
        val entry = zip.getEntry(path)
            ?: throw IllegalArgumentException("Entry not found: $path")
        return zip.getInputStream(entry).use { stream ->
            stream.bufferedReader().use { it.readText() }
        }
    }

    /**
     * Resolves and normalizes paths. BUG-EPUB-014: Handles ../ and ./ segments.
     */
    private fun resolvePath(opfDir: String, href: String): String {
        return normalizePath(if (opfDir.isEmpty()) href else "$opfDir/$href")
    }

    /**
     * Normalizes a path by resolving . and .. segments. (BUG-EPUB-014)
     */
    private fun normalizePath(path: String): String {
        val parts = path.split("/")
        val result = mutableListOf<String>()
        for (part in parts) {
            when {
                part == "." || part.isEmpty() -> continue
                part == ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex)
                else -> result.add(part)
            }
        }
        return result.joinToString("/")
    }

    data class ChapterData(
        val title: String,
        val text: String,
        val href: String,
        val spineIndex: Int
    )
}
