package com.epubaudioreader.core.data.epub.parser

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipFile
import javax.inject.Inject

class ContainerParser @Inject constructor() {

    fun parse(zipFile: ZipFile): String {
        val entry = zipFile.getEntry("META-INF/container.xml")
            ?: throw IllegalArgumentException("META-INF/container.xml not found in EPUB")

        zipFile.getInputStream(entry).use { stream ->
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    val path = parser.getAttributeValue(null, "full-path")
                    if (path != null && path.isNotBlank()) {
                        return path
                    }
                }
                eventType = parser.next()
            }
        }
        throw IllegalArgumentException("No rootfile found in container.xml")
    }
}
