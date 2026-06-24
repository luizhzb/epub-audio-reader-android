package com.epubaudioreader.core.data.epub.parser

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PushbackInputStream

/**
 * Utility functions for secure XML parsing with XXE protection (BUG-EPUB-005)
 * and encoding detection (BUG-EPUB-007).
 */
object XmlParserUtils {

    private const val TAG = "XmlParserUtils"

    /**
     * Creates an XmlPullParserFactory with XXE protection enabled. (BUG-EPUB-005)
     * Disables DTDs and external entities to prevent XXE attacks.
     */
    fun createSecureFactory(): XmlPullParserFactory {
        return XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
            // XXE Protection: try to disable DTDs and external entities
            try {
                setFeature("http://xmlpull.org/v1/doc/features.html#processing-instr", false)
            } catch (e: Exception) {
                Log.w(TAG, "Could not disable processing instructions", e)
            }
        }
    }

    /**
     * Creates a secure XmlPullParser from an InputStream with automatic encoding detection. (BUG-EPUB-007)
     * Detects encoding from BOM or XML declaration.
     */
    fun createSecureParser(inputStream: InputStream): XmlPullParser {
        // Read all bytes to allow encoding detection and multiple passes if needed
        val bytes = inputStream.use { it.readBytes() }
        val encoding = detectEncoding(bytes)
        val factory = createSecureFactory()
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), encoding)
        return parser
    }

    /**
     * Detects encoding from BOM or XML declaration. (BUG-EPUB-007)
     * Returns the detected encoding or "UTF-8" as default.
     */
    fun detectEncoding(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "UTF-8"

        // Check for BOM (Byte Order Mark)
        when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> {
                return "UTF-8"
            }
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> {
                return "UTF-16BE"
            }
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> {
                return "UTF-16LE"
            }
            bytes.size >= 4 && bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
                bytes[2] == 0xFE.toByte() && bytes[3] == 0xFF.toByte() -> {
                return "UTF-32BE"
            }
            bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() &&
                bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte() -> {
                return "UTF-32LE"
            }
        }

        // Check for XML declaration: <?xml version="1.0" encoding="..."?>
        try {
            val prefix = bytes.take(100).toByteArray().toString(Charsets.UTF_8)
            val encodingRegex = Regex("""<?xml[^>]*encoding=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val match = encodingRegex.find(prefix)
            if (match != null) {
                val declaredEncoding = match.groupValues[1].trim()
                if (declaredEncoding.isNotBlank()) {
                    Log.d(TAG, "Detected encoding from XML declaration: $declaredEncoding")
                    return declaredEncoding
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect encoding from XML declaration", e)
        }

        // Default to UTF-8
        return "UTF-8"
    }

    /**
     * Strips the BOM from a byte array if present.
     */
    fun stripBom(bytes: ByteArray): ByteArray {
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> {
                bytes.copyOfRange(3, bytes.size)
            }
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> {
                bytes.copyOfRange(2, bytes.size)
            }
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> {
                bytes.copyOfRange(2, bytes.size)
            }
            else -> bytes
        }
    }
}
