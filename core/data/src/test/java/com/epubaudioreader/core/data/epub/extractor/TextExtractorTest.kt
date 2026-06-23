package com.epubaudioreader.core.data.epub.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextExtractorTest {

    private val extractor = TextExtractor()

    @Test
    fun `extract returns plain text from simple paragraph`() {
        val xhtml = "<html><body><p>Ola mundo</p></body></html>"
        val result = extractor.extract(xhtml)
        assertEquals("Ola mundo", result)
    }

    @Test
    fun `extract separates paragraphs with blank lines`() {
        val xhtml = "<html><body><p>Primeiro paragrafo.</p><p>Segundo paragrafo.</p></body></html>"
        val result = extractor.extract(xhtml)
        assertEquals("Primeiro paragrafo.\n\nSegundo paragrafo.", result)
    }

    @Test
    fun `extract strips script and style content`() {
        val xhtml = """
            <html><body>
            <script>var x = 1;</script>
            <style>.x { color: red; }</style>
            <p>Texto visivel</p>
            </body></html>
        """.trimIndent()
        val result = extractor.extract(xhtml)
        assertEquals("Texto visivel", result)
    }

    @Test
    fun `extract decodes html entities`() {
        val xhtml = "<html><body><p>A &amp; B &lt; C &gt; D</p></body></html>"
        val result = extractor.extract(xhtml)
        assertEquals("A & B < C > D", result)
    }

    @Test
    fun `extract handles br tags`() {
        val xhtml = "<html><body><p>Linha 1<br/>Linha 2</p></body></html>"
        val result = extractor.extract(xhtml)
        assertTrue(result.contains("Linha 1"))
        assertTrue(result.contains("Linha 2"))
    }

    @Test
    fun `extract returns empty string for blank input`() {
        assertEquals("", extractor.extract(""))
        assertEquals("", extractor.extract("   "))
    }
}
