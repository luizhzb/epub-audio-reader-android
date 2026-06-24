package com.epubaudioreader.core.data.epub.parser

import android.util.Log
import com.epubaudioreader.core.data.epub.model.GuideReference
import com.epubaudioreader.core.data.epub.model.ManifestItem
import com.epubaudioreader.core.data.epub.model.ParsedMetadata
import com.epubaudioreader.core.data.epub.model.ParsedOpf
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Parses the OPF (Open Packaging Format) file to extract metadata, manifest, spine, and guide.
 * Includes XXE protection (BUG-EPUB-005) and encoding detection (BUG-EPUB-007).
 * Uses proper parent-node tracking instead of fragile depth check (BUG-EPUB-010).
 */
class OpfParser @Inject constructor() {

    companion object {
        private const val TAG = "OpfParser"
    }

    fun parse(zipFile: ZipFile, opfPath: String): ParsedOpf {
        val entry = zipFile.getEntry(opfPath)
            ?: throw IllegalArgumentException("OPF file not found: $opfPath")

        zipFile.getInputStream(entry).use { stream ->
            // BUG-EPUB-005: Secure factory with XXE protection
            // BUG-EPUB-007: Auto-detect encoding from BOM or XML declaration
            val parser = XmlParserUtils.createSecureParser(stream)

            val manifest = mutableMapOf<String, ManifestItem>()
            val spine = mutableListOf<com.epubaudioreader.core.data.epub.model.SpineItem>()
            val guide = mutableListOf<GuideReference>()
            var opfVersion = "2.0"
            val authors = mutableListOf<String>()
            val subjects = mutableListOf<String>()

            var insideMetadata = false
            // BUG-EPUB-010: Track manifest scope explicitly instead of relying on parser.depth
            var insideManifest = false
            var insideSpine = false
            var insideGuide = false

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
                            name == "metadata" -> {
                                insideMetadata = true
                                insideManifest = false
                                insideSpine = false
                                insideGuide = false
                            }
                            name == "manifest" -> {
                                insideMetadata = false
                                insideManifest = true
                                insideSpine = false
                                insideGuide = false
                            }
                            name == "spine" -> {
                                insideMetadata = false
                                insideManifest = false
                                insideSpine = true
                                insideGuide = false
                            }
                            name == "guide" -> {
                                insideMetadata = false
                                insideManifest = false
                                insideSpine = false
                                insideGuide = true
                            }
                            // BUG-EPUB-010: Use insideManifest flag instead of parser.depth <= 4
                            name == "item" && insideManifest -> {
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
                            name == "itemref" && insideSpine -> {
                                val idref = parser.getAttributeValue(null, "idref") ?: ""
                                val linearStr = parser.getAttributeValue(null, "linear")
                                val linear = linearStr == null || linearStr == "yes"
                                val id = parser.getAttributeValue(null, "id")
                                if (idref.isNotBlank()) {
                                    spine.add(com.epubaudioreader.core.data.epub.model.SpineItem(idref = idref, linear = linear, id = id))
                                }
                            }
                            name == "reference" && insideGuide -> {
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
                                if (propName != null && propContent != null) {
                                    currentMetaName = propName
                                    currentMetaContent = propContent
                                }
                            }
                            // EPUB 2 DC elements - only parse when inside metadata
                            (name == "dc:title" || name.endsWith(":title")) && insideMetadata -> {
                                dcTitle = readTextSafely(parser)
                            }
                            (name == "dc:language" || name.endsWith(":language")) && insideMetadata -> {
                                dcLanguage = readTextSafely(parser)
                            }
                            (name == "dc:identifier" || name.endsWith(":identifier")) && insideMetadata -> {
                                val scheme = parser.getAttributeValue(null, "scheme")
                                val text = readTextSafely(parser)
                                if (dcIdentifier == null || scheme != null) {
                                    dcIdentifier = text
                                }
                            }
                            (name == "dc:description" || name.endsWith(":description")) && insideMetadata -> {
                                dcDescription = readTextSafely(parser)
                            }
                            (name == "dc:publisher" || name.endsWith(":publisher")) && insideMetadata -> {
                                dcPublisher = readTextSafely(parser)
                            }
                            (name == "dc:date" || name.endsWith(":date")) && insideMetadata -> {
                                dcDate = readTextSafely(parser)
                            }
                            (name == "dc:rights" || name.endsWith(":rights")) && insideMetadata -> {
                                dcRights = readTextSafely(parser)
                            }
                            (name == "dc:creator" || name.endsWith(":creator") ||
                            name == "dc:author" || name.endsWith(":author")) && insideMetadata -> {
                                val role = parser.getAttributeValue(null, "role")
                                val text = readTextSafely(parser)
                                if (text.isNotBlank() && (role == null || role == "aut")) {
                                    authors.add(text)
                                }
                            }
                            (name == "dc:subject" || name.endsWith(":subject")) && insideMetadata -> {
                                val text = readTextSafely(parser)
                                if (text.isNotBlank()) subjects.add(text)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            val finalMetadata = ParsedMetadata(
                title = dcTitle ?: "Sem t\u00edtulo",
                authors = authors.ifEmpty { subjects },
                language = dcLanguage ?: "pt-BR",
                identifier = dcIdentifier ?: "",
                description = dcDescription,
                publisher = dcPublisher,
                date = dcDate,
                rights = dcRights
            )

            Log.d(TAG, "Parsed OPF: ${manifest.size} manifest items, ${spine.size} spine items, ${guide.size} guide refs")

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

    /**
     * Safely reads text content from the current XML element.
     * Handles cases where nextText() might fail on complex nested elements.
     */
    private fun readTextSafely(parser: XmlPullParser): String {
        return try {
            parser.nextText().trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read text for element ${parser.name}", e)
            ""
        }
    }
}
