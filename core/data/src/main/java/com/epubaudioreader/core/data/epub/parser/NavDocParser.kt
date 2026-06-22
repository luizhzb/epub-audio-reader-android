package com.epubaudioreader.core.data.epub.parser

import com.epubaudioreader.core.data.epub.model.TocEntry
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipFile
import javax.inject.Inject

class NavDocParser @Inject constructor() {

    fun parse(zipFile: ZipFile, navPath: String): List<TocEntry> {
        val entry = zipFile.getEntry(navPath)
            ?: throw IllegalArgumentException("Nav file not found: $navPath")

        zipFile.getInputStream(entry).use { stream ->
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            val entries = mutableListOf<TocEntry>()
            var insideTocNav = false
            var depthStack = mutableListOf<Int>()

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
                                        ?: "Capítulo ${entries.size + 1}"
                                    val href = currentHref!!.trim()
                                    if (href.isNotBlank()) {
                                        entries.add(
                                            TocEntry(
                                                title = title.ifBlank { "Capítulo ${entries.size + 1}" },
                                                href = href,
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

            return entries
        }
    }
}
