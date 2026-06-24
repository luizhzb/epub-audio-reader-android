package com.epubaudioreader.core.data.epub.parser

import android.util.Log
import com.epubaudioreader.core.data.epub.model.TocEntry
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Parses EPUB 3 Navigation Document (XHTML with nav[@epub:type="toc"]).
 * Maps TOC entries to correct spine indices via href lookup (BUG-EPUB-003).
 * Includes XXE protection (BUG-EPUB-005) and encoding detection (BUG-EPUB-007).
 * Populates spineIndex correctly (BUG-EPUB-012).
 */
class NavDocParser @Inject constructor() {

    companion object {
        private const val TAG = "NavDocParser"
    }

    /**
     * Parses nav document and returns TOC entries with correct spine indices.
     *
     * @param zipFile The EPUB zip file
     * @param navPath Path to the nav XHTML file within the EPUB
     * @param hrefToSpineIndex Map of manifest href -> spine index for correct alignment (BUG-EPUB-003)
     */
    fun parse(
        zipFile: ZipFile,
        navPath: String,
        hrefToSpineIndex: Map<String, Int> = emptyMap()
    ): List<TocEntry> {
        val entry = zipFile.getEntry(navPath)
            ?: throw IllegalArgumentException("Nav file not found: $navPath")

        zipFile.getInputStream(entry).use { stream ->
            // BUG-EPUB-005: Secure factory with XXE protection
            // BUG-EPUB-007: Auto-detect encoding from BOM or XML declaration
            val parser = XmlParserUtils.createSecureParser(stream)

            val entries = mutableListOf<TocEntry>()
            var insideTocNav = false
            val depthStack = mutableListOf<Int>()

            // States for anchor parsing
            var currentHref: String? = null
            var currentTitleBuilder: StringBuilder? = null

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase()) {
                            "nav" -> {
                                val navType = parser.getAttributeValue(null, "epub:type")
                                    ?: parser.getAttributeValue("http://www.idpf.org/2007/ops", "type")
                                if (navType == "toc") {
                                    insideTocNav = true
                                    depthStack.clear()
                                }
                            }
                            "ol" -> {
                                if (insideTocNav) {
                                    depthStack.add(0)
                                }
                            }
                            "li" -> {
                                if (insideTocNav && depthStack.isNotEmpty()) {
                                    // will be incremented on anchor
                                }
                            }
                            "a" -> {
                                if (insideTocNav) {
                                    currentHref = parser.getAttributeValue(null, "href")
                                    currentTitleBuilder = StringBuilder()
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (currentTitleBuilder != null) {
                            currentTitleBuilder!!.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name.lowercase()) {
                            "nav" -> {
                                if (insideTocNav) {
                                    insideTocNav = false
                                }
                            }
                            "ol" -> {
                                if (insideTocNav && depthStack.isNotEmpty()) {
                                    depthStack.removeAt(depthStack.lastIndex)
                                }
                            }
                            "a" -> {
                                if (insideTocNav && currentHref != null) {
                                    val title = currentTitleBuilder?.toString()?.trim()
                                        ?: "Cap\u00edtulo ${entries.size + 1}"
                                    val href = currentHref!!.trim()
                                    if (href.isNotBlank()) {
                                        // BUG-EPUB-003 & BUG-EPUB-012:
                                        // Find actual spine index by href lookup
                                        val hrefWithoutFragment = href.substringBefore("#")
                                        val actualSpineIndex = hrefToSpineIndex[hrefWithoutFragment] ?: 0

                                        entries.add(
                                            TocEntry(
                                                title = title.ifBlank { "Cap\u00edtulo ${entries.size + 1}" },
                                                href = href,
                                                spineIndex = actualSpineIndex,
                                                depth = depthStack.size.coerceAtLeast(0),
                                                linear = true
                                            )
                                        )
                                    }
                                    currentHref = null
                                    currentTitleBuilder = null
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            Log.d(TAG, "Parsed ${entries.size} TOC entries from nav document")
            return entries
        }
    }
}
