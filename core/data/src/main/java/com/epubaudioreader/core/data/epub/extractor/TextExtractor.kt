package com.epubaudioreader.core.data.epub.extractor

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import javax.inject.Inject

/**
 * Extracts clean text from XHTML content using Jsoup.
 * Preserves paragraph breaks (\n\n), ignores script/style/head nodes.
 * Decodes common HTML entities.
 */
class TextExtractor @Inject constructor() {

    private val skipTags = setOf(
        "script", "style", "noscript", "iframe", "canvas",
        "audio", "video", "embed", "object", "svg", "math",
        "head", "meta", "link", "title"
    )

    private val blockTags = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6",
        "br", "hr", "li", "dt", "dd", "td", "th",
        "blockquote", "pre", "section", "article", "aside",
        "header", "footer", "nav", "figure", "figcaption"
    )

    fun extract(xhtml: String): String {
        if (xhtml.isBlank()) return ""

        val document = Jsoup.parse(xhtml)
        val result = StringBuilder()
        var lastWasBlock = true

        fun appendText(text: String) {
            val normalized = text.replace("""\s+""".toRegex(), " ").trim()
            if (normalized.isBlank()) return

            if (result.isNotEmpty() && !lastWasBlock && result.last() != ' ') {
                result.append(" ")
            }
            result.append(normalized)
            lastWasBlock = false
        }

        fun appendBlockBreak() {
            if (result.isNotEmpty()) {
                result.append("\n\n")
            }
            lastWasBlock = true
        }

        fun appendLineBreak() {
            if (result.isNotEmpty() && result.last() != '\n') {
                result.append("\n")
            }
            lastWasBlock = true
        }

        fun walk(node: Node) {
            when (node) {
                is TextNode -> appendText(decodeHtmlEntities(node.text()))
                is Element -> {
                    val tagName = node.tagName().lowercase()
                    if (tagName in skipTags) return

                    when {
                        tagName == "br" -> appendLineBreak()
                        tagName == "hr" -> appendLineBreak()
                        tagName == "img" || tagName == "image" -> {
                            val alt = node.attr("alt")
                            if (alt.isNotBlank()) appendText(alt)
                        }
                        tagName in blockTags -> {
                            appendBlockBreak()
                            node.childNodes().forEach(::walk)
                            lastWasBlock = true
                        }
                        else -> node.childNodes().forEach(::walk)
                    }
                }
            }
        }

        document.body()?.childNodes()?.forEach(::walk)
            ?: document.childNodes().forEach(::walk)

        return result.toString().trim()
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
            .replace(Regex("""&#(\\d+);""")) { match ->
                val code = match.groupValues[1].toIntOrNull() ?: 0
                if (code in 32..65535) code.toChar().toString() else match.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                val code = match.groupValues[1].toIntOrNull(16) ?: 0
                if (code in 32..65535) code.toChar().toString() else match.value
            }
    }
}
