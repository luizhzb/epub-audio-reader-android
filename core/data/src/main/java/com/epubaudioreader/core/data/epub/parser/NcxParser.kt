package com.epubaudioreader.core.data.epub.parser

import com.epubaudioreader.core.data.epub.model.TocEntry
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipFile
import javax.inject.Inject

class NcxParser @Inject constructor() {

    fun parse(zipFile: ZipFile, ncxPath: String): List<TocEntry> {
        val entry = zipFile.getEntry(ncxPath)
            ?: throw IllegalArgumentException("NCX file not found: $ncxPath")

        zipFile.getInputStream(entry).use { stream ->
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

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
                                        entries.add(
                                            TocEntry(
                                                title = navPoint.text.ifBlank { "Capítulo ${entries.size + 1}" },
                                                href = navPoint.src,
                                                spineIndex = navPoint.playOrder,
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
