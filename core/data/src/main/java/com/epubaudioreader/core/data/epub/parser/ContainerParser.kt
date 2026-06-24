package com.epubaudioreader.core.data.epub.parser

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Parses META-INF/container.xml to find the OPF package file path.
 * Includes XXE protection (BUG-EPUB-005) and encoding detection (BUG-EPUB-007).
 */
class ContainerParser @Inject constructor() {

    companion object {
        private const val TAG = "ContainerParser"
    }

    fun parse(zipFile: ZipFile): String {
        val entry = zipFile.getEntry("META-INF/container.xml")
            ?: throw IllegalArgumentException("META-INF/container.xml not found in EPUB")

        zipFile.getInputStream(entry).use { stream ->
            // BUG-EPUB-005: Use secure parser factory with XXE protection
            // BUG-EPUB-007: Auto-detect encoding from BOM or XML declaration
            val parser = XmlParserUtils.createSecureParser(stream)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    val path = parser.getAttributeValue(null, "full-path")
                    if (path != null && path.isNotBlank()) {
                        Log.d(TAG, "Found OPF path: $path")
                        return path
                    }
                }
                eventType = parser.next()
            }
        }
        throw IllegalArgumentException("No rootfile found in container.xml")
    }
}
