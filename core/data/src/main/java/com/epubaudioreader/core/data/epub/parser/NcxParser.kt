package com.epubaudioreader.core.data.epub.parser

import android.util.Log
import com.epubaudioreader.core.data.epub.model.TocEntry
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Parses NCX (Navigation Control file for XML) for EPUB 2 TOC.
 * Maps TOC entries to correct spine indices via href lookup (BUG-EPUB-003).
 * Includes XXE protection (BUG-EPUB-005) and encoding detection (BUG-EPUB-007).
 * Populates spineIndex correctly (BUG-EPUB-012).
 */
class NcxParser @Inject constructor() {

    companion object {
        private const val TAG = "NcxParser"
    }

    /**
     * Parses NCX file and returns TOC entries with correct spine indices.
     *
     * @param zipFile The EPUB zip file
     * @param ncxPath Path to the NCX file within the EPUB
     * @param hrefToSpineIndex Map of manifest href -> spine index for correct alignment (BUG-EPUB-003)
     */
    fun parse(
        zipFile: ZipFile,
        ncxPath: String,
        hrefToSpineIndex: Map<String, Int> = emptyMap()
    ): List<TocEntry> {
        val entry = zipFile.getEntry(ncxPath)
            ?: throw IllegalArgumentException("NCX file not found: $ncxPath")

        zipFile.getInputStream(entry).use { stream ->
            // BUG-EPUB-005: Secure factory with XXE protection
            // BUG-EPUB-007: Auto-detect encoding from BOM or XML declaration
            val parser = XmlParserUtils.createSecureParser(stream)

            val entries = mutableListOf<TocEntry>()
            val navPointStack = mutableListOf<NavPoint>()
            var currentText: String? = null

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "navPoint" -> {
                                val playOrder = parser.getAttributeValue(null, "playOrder") ?: "0"
                                navPointStack.add(NavPoint(playOrder = playOrder.toIntOrNull() ?: 0))
                            }
                            "text" -> {
                                if (navPointStack.isNotEmpty()) {
                                    currentText = ""
                                }
                            }
                            "content" -> {
                                if (navPointStack.isNotEmpty()) {
                                    val src = parser.getAttributeValue(null, "src") ?: ""
                                    // Normalize src: remove fragment
                                    navPointStack.last().src = src
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (currentText != null && navPointStack.isNotEmpty()) {
                            currentText = parser.text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "text" -> {
                                if (navPointStack.isNotEmpty() && currentText != null) {
                                    navPointStack.last().text = currentText!!.trim()
                                    currentText = null
                                }
                            }
                            "navPoint" -> {
                                if (navPointStack.isNotEmpty()) {
                                    val navPoint = navPointStack.removeAt(navPointStack.lastIndex)
                                    if (navPoint.src.isNotBlank()) {
                                        // BUG-EPUB-003 & BUG-EPUB-012:
                                        // Find actual spine index by href lookup instead of using playOrder
                                        val hrefWithoutFragment = navPoint.src.substringBefore("#")
                                        val actualSpineIndex = hrefToSpineIndex[hrefWithoutFragment]
                                            ?: navPoint.playOrder

                                        if (actualSpineIndex != navPoint.playOrder) {
                                            Log.d(TAG,
                                                "TOC entry '${navPoint.text}' remapped: " +
                                                "playOrder=${navPoint.playOrder} -> spineIndex=$actualSpineIndex " +
                                                "(href=$hrefWithoutFragment)"
                                            )
                                        }

                                        entries.add(
                                            TocEntry(
                                                title = navPoint.text.ifBlank { "Cap\u00edtulo ${entries.size + 1}" },
                                                href = navPoint.src,
                                                spineIndex = actualSpineIndex,
                                                depth = navPointStack.size,
                                                linear = true
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            return entries
        }
    }

    private data class NavPoint(
        var text: String = "",
        var src: String = "",
        val playOrder: Int = 0
    )
}
