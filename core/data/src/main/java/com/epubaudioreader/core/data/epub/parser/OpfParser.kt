package com.epubaudioreader.core.data.epub.parser

import com.epubaudioreader.core.data.epub.model.GuideReference
import com.epubaudioreader.core.data.epub.model.ManifestItem
import com.epubaudioreader.core.data.epub.model.ParsedMetadata
import com.epubaudioreader.core.data.epub.model.ParsedOpf
import com.epubaudioreader.core.data.epub.model.SpineItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipFile
import javax.inject.Inject

class OpfParser @Inject constructor() {

    fun parse(zipFile: ZipFile, opfPath: String): ParsedOpf {
        val entry = zipFile.getEntry(opfPath)
            ?: throw IllegalArgumentException("OPF file not found: $opfPath")

        zipFile.getInputStream(entry).use { stream ->
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            val metadata = ParsedMetadata()
            val manifest = mutableMapOf<String, ManifestItem>()
            val spine = mutableListOf<SpineItem>()
            val guide = mutableListOf<GuideReference>()
            var opfVersion = "2.0"
            val authors = mutableListOf<String>()
            val subjects = mutableListOf<String>()

            var opfDir = opfPath.substringBeforeLast('/', "")
            if (opfDir.isNotEmpty()) opfDir += "/"

            var insideMetadata = false
            var currentMetaName: String? = null
            var currentMetaContent: String? = null
            var dcTitle: String? = null
            var dcLanguage: String? = null
            var dcIdentifier: String? = null
            var dcDescription: String? = null
            var dcPublisher: String? = null
            var dcDate: String? = null
            var dcRights: String? = null

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        when {
                            name == "package" -> {
                                opfVersion = parser.getAttributeValue(null, "version") ?: "2.0"
                            }
                            name == "metadata" -> insideMetadata = true
                            name == "manifest" -> insideMetadata = false
                            name == "spine" -> insideMetadata = false
                            name == "guide" -> insideMetadata = false
                            name == "item" && !insideMetadata && parser.depth <= 4 -> {
                                val id = parser.getAttributeValue(null, "id") ?: ""
                                val href = parser.getAttributeValue(null, "href") ?: ""
                                val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                                val properties = parser.getAttributeValue(null, "properties")
                                val fallback = parser.getAttributeValue(null, "fallback")
                                if (id.isNotBlank() && href.isNotBlank()) {
                                    manifest[id] = ManifestItem(
                                        id = id,
                                        href = href,
                                        mediaType = mediaType,
                                        properties = properties,
                                        fallback = fallback
                                    )
                                }
                            }
                            name == "itemref" && !insideMetadata -> {
                                val idref = parser.getAttributeValue(null, "idref") ?: ""
                                val linearStr = parser.getAttributeValue(null, "linear")
                                val linear = linearStr == null || linearStr == "yes"
                                val id = parser.getAttributeValue(null, "id")
                                if (idref.isNotBlank()) {
                                    spine.add(SpineItem(idref = idref, linear = linear, id = id))
                                }
                            }
                            name == "reference" && !insideMetadata -> {
                                val refType = parser.getAttributeValue(null, "type") ?: ""
                                val refTitle = parser.getAttributeValue(null, "title") ?: ""
                                val refHref = parser.getAttributeValue(null, "href") ?: ""
                                if (refHref.isNotBlank()) {
                                    guide.add(GuideReference(type = refType, title = refTitle, href = refHref))
                                }
                            }
                            name == "meta" && insideMetadata -> {
                                val propName = parser.getAttributeValue(null, "name")
                                val propContent = parser.getAttributeValue(null, "content")
                                val propProperty = parser.getAttributeValue(null, "property")
                                val propText = parser.getAttributeValue(null, "refines")
                                if (propName != null && propContent != null) {
                                    currentMetaName = propName
                                    currentMetaContent = propContent
                                    when (propName.lowercase()) {
                                        "cover" -> { /* handled separately */ }
                                    }
                                }
                            }
                            // EPUB 2 DC elements
                            name == "dc:title" || name.endsWith(":title") -> {
                                dcTitle = parser.nextText().trim()
                            }
                            name == "dc:language" || name.endsWith(":language") -> {
                                dcLanguage = parser.nextText().trim()
                            }
                            name == "dc:identifier" || name.endsWith(":identifier") -> {
                                val scheme = parser.getAttributeValue(null, "scheme")
                                val text = parser.nextText().trim()
                                if (dcIdentifier == null || scheme != null) {
                                    dcIdentifier = text
                                }
                            }
                            name == "dc:description" || name.endsWith(":description") -> {
                                dcDescription = parser.nextText().trim()
                            }
                            name == "dc:publisher" || name.endsWith(":publisher") -> {
                                dcPublisher = parser.nextText().trim()
                            }
                            name == "dc:date" || name.endsWith(":date") -> {
                                dcDate = parser.nextText().trim()
                            }
                            name == "dc:rights" || name.endsWith(":rights") -> {
                                dcRights = parser.nextText().trim()
                            }
                            name == "dc:creator" || name.endsWith(":creator") ||
                            name == "dc:author" || name.endsWith(":author") -> {
                                val role = parser.getAttributeValue(null, "role")
                                val text = parser.nextText().trim()
                                if (text.isNotBlank() && (role == null || role == "aut")) {
                                    authors.add(text)
                                }
                            }
                            name == "dc:subject" || name.endsWith(":subject") -> {
                                val text = parser.nextText().trim()
                                if (text.isNotBlank()) subjects.add(text)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            // Handle potential unclosed metadata parsing
            // Some EPUBs have meta elements with cover reference that we need

            val finalMetadata = ParsedMetadata(
                title = dcTitle ?: "Sem título",
                authors = authors.ifEmpty { subjects },
                language = dcLanguage ?: "pt-BR",
                identifier = dcIdentifier ?: "",
                description = dcDescription,
                publisher = dcPublisher,
                date = dcDate,
                rights = dcRights
            )

            return ParsedOpf(
                metadata = finalMetadata,
                manifest = manifest,
                spine = spine,
                guide = guide,
                opfDir = opfPath.substringBeforeLast('/', ""),
                version = opfVersion
            )
        }
    }
}
