package com.epubaudioreader.core.data.epub.extractor

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import javax.inject.Inject

/**
 * Extracts clean text from XHTML content using streaming XmlPullParser.
 * Preserves paragraph breaks (\n\n), ignores script/style/comment nodes.
 * Decodes common HTML entities.
 */
class TextExtractor @Inject constructor() {

    private val skipTags = setOf(
        "script", "style", "noscript", "iframe", "canvas",
        "audio", "video", "embed", "object", "svg", "math",
        "head", "meta", "link", "title", "comment"
    )

    private val blockTags = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6",
        "br", "hr", "li", "dt", "dd", "td", "th",
        "blockquote", "pre", "section", "article", "aside",
        "header", "footer", "nav", "figure", "figcaption"
    )

    fun extract(xhtml: String): String {
        // Pre-process: remove HTML comments to avoid parser issues
        val cleaned = xhtml.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<\?xml[^?]*\?>""", RegexOption.IGNORE_CASE), "")

        if (cleaned.isBlank()) return ""

        val result = StringBuilder()
        var skipDepth = 0
        var lastWasBlock = true // start true to avoid leading newline

        try {
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(cleaned))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name?.lowercase() ?: ""
                        if (skipTags.contains(tagName)) {
                            skipDepth++
                        } else if (skipDepth == 0) {
                            if (blockTags.contains(tagName)) {
                                if (!lastWasBlock && result.isNotEmpty()) {
                                    result.append("\n\n")
                                    lastWasBlock = true
                                }
                            }
                            if (tagName == "br" || tagName == "hr") {
                                result.append("\n")
                                lastWasBlock = true
                            }
                            if (tagName == "img" || tagName == "image") {
                                val alt = parser.getAttributeValue(null, "alt")
                                if (!alt.isNullOrBlank()) {
                                    if (result.isNotEmpty() && !lastWasBlock) {
                                        result.append(" ")
                                    }
                                    result.append(alt)
                                    lastWasBlock = false
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (skipDepth == 0) {
                            val text = parser.text ?: ""
                            // Decode HTML entities and normalize whitespace
                            val decoded = decodeHtmlEntities(text)
                            val normalized = decoded.replace("""\s+""".toRegex(), " ")
                            if (normalized.isNotBlank()) {
                                if (result.isNotEmpty() && !lastWasBlock && result.last() != ' ') {
                                    result.append(" ")
                                }
                                result.append(normalized.trim())
                                lastWasBlock = false
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name?.lowercase() ?: ""
                        if (skipTags.contains(tagName)) {
                            skipDepth = (skipDepth - 1).coerceAtLeast(0)
                        } else if (skipDepth == 0) {
                            if (blockTags.contains(tagName)) {
                                if (!lastWasBlock && result.isNotEmpty()) {
                                    result.append("\n\n")
                                    lastWasBlock = true
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Fallback: strip all tags with regex
            return fallbackExtract(cleaned)
        }

        return result.toString().trim()
    }

    private fun fallbackExtract(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace("""\s+""".toRegex(), " ")
            .let { decodeHtmlEntities(it) }
            .trim()
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#x201C;", "\"")
            .replace("&#x201D;", "\"")
            .replace("&#x2018;", "'")
            .replace("&#x2019;", "'")
            .replace("&#x2014;", "—")
            .replace("&#x2013;", "–")
            .replace("&#x2026;", "…")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&#xA0;", " ")
            // Numeric entities
            .replace(Regex("""&#(\d+);""")) { match ->
                val code = match.groupValues[1].toIntOrNull() ?: 0
                if (code in 32..65535) code.toChar().toString() else match.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                val code = match.groupValues[1].toIntOrNull(16) ?: 0
                if (code in 32..65535) code.toChar().toString() else match.value
            }
    }
}
