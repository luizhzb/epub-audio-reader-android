package com.epubaudioreader.core.data.epub.extractor

import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import javax.inject.Inject

/**
 * Extracts clean text from XHTML content using Jsoup.
 * Preserves paragraph breaks (\n\n), ignores script/style/head nodes.
 * Decodes common HTML entities.
 * Removes HTML comments.
 * Preserves list semantics with bullet markers.
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

        // Remove HTML comments before parsing (BUG-EPUB-017)
        val commentRegex = Regex("<!--[\\s\\S]*?-->")
        val cleanedHtml = xhtml.replace(commentRegex, "")

        val document = Jsoup.parse(cleanedHtml)

        // Remove comment nodes that Jsoup may have parsed (BUG-EPUB-017)
        // BUG FIX: traverse callback takes (Node, Int) not just (Node)
        document.traverse { node, _ ->
            if (node is Comment) {
                node.remove()
            }
        }

        val result = StringBuilder()
        var lastWasBlock = true
        var listCounter = 0
        var listDepth = 0

        fun appendText(text: String) {
            // BUG-EPUB-001: Fix regex - raw string """\s+""" searches literal \s+
            // Must use "\\s+".toRegex() or Regex("\\s+")
            val normalized = text.replace("\\s+".toRegex(), " ").trim()
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
                        tagName == "ul" -> {
                            appendBlockBreak()
                            listDepth++
                            listCounter = 0
                            node.childNodes().forEach(::walk)
                            listDepth--
                            lastWasBlock = true
                        }
                        tagName == "ol" -> {
                            appendBlockBreak()
                            listDepth++
                            listCounter = 0
                            node.childNodes().forEach(::walk)
                            listDepth--
                            lastWasBlock = true
                        }
                        tagName == "li" -> {
                            // BUG-EPUB-018: Preserve list semantics
                            if (listDepth > 0) {
                                listCounter++
                                val parent = node.parent()
                                val prefix = if (parent != null && parent.tagName().lowercase() == "ol") {
                                    "$listCounter. "
                                } else {
                                    "\u2022 " // bullet
                                }
                                appendBlockBreak()
                                result.append(prefix)
                                lastWasBlock = false
                            } else {
                                appendBlockBreak()
                            }
                            node.childNodes().forEach(::walk)
                            lastWasBlock = true
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

    /**
     * Decodes HTML named and numeric entities. (BUG-EPUB-016)
     */
    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            // Common named entities
            .replace("&nbsp;", " ")
            .replace("&ndash;", "\u2013")
            .replace("&mdash;", "\u2014")
            .replace("&lsquo;", "\u2018")
            .replace("&rsquo;", "\u2019")
            .replace("&ldquo;", "\u201C")
            .replace("&rdquo;", "\u201D")
            .replace("&hellip;", "\u2026")
            .replace("&euro;", "\u20AC")
            .replace("&pound;", "\u00A3")
            .replace("&yen;", "\u00A5")
            .replace("&copy;", "\u00A9")
            .replace("&reg;", "\u00AE")
            .replace("&trade;", "\u2122")
            .replace("&deg;", "\u00B0")
            .replace("&plusmn;", "\u00B1")
            .replace("&para;", "\u00B6")
            .replace("&middot;", "\u00B7")
            .replace("&bull;", "\u2022")
            .replace("&laquo;", "\u00AB")
            .replace("&raquo;", "\u00BB")
            // Hex entities with x prefix
            .replace("&#x201C;", "\"")
            .replace("&#x201D;", "\"")
            .replace("&#x2018;", "'")
            .replace("&#x2019;", "'")
            .replace("&#x2014;", "\u2014")
            .replace("&#x2013;", "\u2013")
            .replace("&#x2026;", "\u2026")
            .replace("&#xA0;", " ")
            .replace("&#x00A0;", " ")
            // Decimal entities
            .replace("&#160;", " ")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&#8216;", "'")
            .replace("&#8217;", "'")
            .replace("&#8212;", "\u2014")
            .replace("&#8211;", "\u2013")
            .replace("&#8230;", "\u2026")
            // Generic numeric entities
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
