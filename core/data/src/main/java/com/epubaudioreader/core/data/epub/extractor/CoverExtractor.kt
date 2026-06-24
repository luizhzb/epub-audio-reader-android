package com.epubaudioreader.core.data.epub.extractor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.epubaudioreader.core.data.epub.model.ParsedEpub
import com.epubaudioreader.core.data.local.storage.EpubStorageManager
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Extracts cover image from EPUB with 5 levels of fallback:
 * 1. meta name="cover" in OPF
 * 2. item properties="cover-image" in manifest
 * 3. guide reference type="cover"
 * 4. heuristic by name "cover" in manifest
 * 5. first image in manifest
 *
 * Resizes to max 512x768 and saves as JPEG via EpubStorageManager.
 * Includes OOM protection via inSampleSize calculation (BUG-EPUB-002).
 * Normalizes relative paths (BUG-EPUB-014).
 * Detects SVG covers (BUG-EPUB-019).
 * Accepts optional pre-opened ZipFile to avoid re-opening (BUG-EPUB-004).
 */
class CoverExtractor @Inject constructor(
    private val storageManager: EpubStorageManager
) {

    companion object {
        private const val TAG = "CoverExtractor"
        private const val MAX_WIDTH = 512
        private const val MAX_HEIGHT = 768
        private const val JPEG_QUALITY = 85
    }

    /**
     * Extracts cover image from the EPUB.
     *
     * @param parsedEpub The parsed EPUB
     * @param bookId The book ID for logging
     * @param zipFile Optional pre-opened ZipFile to avoid re-opening (BUG-EPUB-004)
     */
    suspend fun extractCover(
        parsedEpub: ParsedEpub,
        bookId: Long,
        zipFile: ZipFile? = null
    ): String? {
        val coverBytes = if (zipFile != null) {
            findCoverBytes(parsedEpub, zipFile)
        } else {
            ZipFile(parsedEpub.bookFile).use { zip ->
                findCoverBytes(parsedEpub, zip)
            }
        } ?: return null

        return try {
            // BUG-EPUB-002: Decode with OOM protection using inSampleSize
            val bitmap = decodeSampledBitmap(coverBytes)
                ?: return null

            val resized = resizeBitmap(bitmap, MAX_WIDTH, MAX_HEIGHT)

            // Save cover via storageManager to ensure consistent path
            val coverPath = storageManager.saveCover(resized, bookId)

            // Recycle if original was different
            if (resized !== bitmap) {
                bitmap.recycle()
            }

            coverPath
        } catch (e: Exception) {
            // BUG-EPUB-008: Log errors instead of silently swallowing
            Log.e(TAG, "Failed to extract cover for book $bookId", e)
            null
        }
    }

    /**
     * Decodes bitmap with inSampleSize to prevent OOM on high-res covers. (BUG-EPUB-002)
     */
    private fun decodeSampledBitmap(coverBytes: ByteArray): Bitmap? {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            Log.w(TAG, "Invalid bitmap dimensions: ${options.outWidth}x${options.outHeight}")
            return null
        }

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(
            options.outWidth, options.outHeight, MAX_WIDTH, MAX_HEIGHT
        )
        options.inJustDecodeBounds = false

        Log.d(TAG, "Decoding bitmap with inSampleSize=${options.inSampleSize} " +
                "(original: ${options.outWidth}x${options.outHeight})")

        return BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, options)
    }

    private fun calculateInSampleSize(
        width: Int, height: Int, reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfWidth / inSampleSize >= reqWidth && halfHeight / inSampleSize >= reqHeight) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Finds cover bytes using an already-opened ZipFile. (BUG-EPUB-004)
     */
    private fun findCoverBytes(parsedEpub: ParsedEpub, zip: ZipFile): ByteArray? {
        // Level 1: meta name="cover" -> find item by id in manifest
        val coverItemId = findCoverMetaItemId(parsedEpub, zip)
        if (coverItemId != null) {
            val item = parsedEpub.manifest[coverItemId]
            if (item != null) {
                val path = resolvePath(parsedEpub.opfDir, item.href)
                // BUG-EPUB-019: Skip SVG covers - BitmapFactory can't decode them
                if (item.mediaType == "image/svg+xml") {
                    Log.w(TAG, "SVG cover detected at $path - skipping Bitmap extraction")
                    return null
                }
                zip.getEntry(path)?.let { entry ->
                    zip.getInputStream(entry).use { it.readBytes() }.let { return it }
                }
            }
        }

        // Level 2: item with properties="cover-image"
        val coverImageItem = parsedEpub.manifest.values.find { it.properties == "cover-image" }
        if (coverImageItem != null) {
            val path = resolvePath(parsedEpub.opfDir, coverImageItem.href)
            if (coverImageItem.mediaType == "image/svg+xml") {
                Log.w(TAG, "SVG cover detected at $path - skipping Bitmap extraction")
                return null
            }
            zip.getEntry(path)?.let { entry ->
                zip.getInputStream(entry).use { it.readBytes() }.let { return it }
            }
        }

        // Level 3: guide reference type="cover"
        val guideCover = parsedEpub.guide.find { it.type.equals("cover", ignoreCase = true) }
        if (guideCover != null) {
            val path = resolvePath(parsedEpub.opfDir, guideCover.href)
            zip.getEntry(path)?.let { entry ->
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                // Check if the guide points to an image or XHTML
                if (guideCover.href.endsWith(".jpg", ignoreCase = true) ||
                    guideCover.href.endsWith(".jpeg", ignoreCase = true) ||
                    guideCover.href.endsWith(".png", ignoreCase = true)
                ) {
                    return bytes
                }
                // If XHTML, try to extract first image from it
                if (guideCover.href.endsWith(".xhtml", ignoreCase = true) ||
                    guideCover.href.endsWith(".html", ignoreCase = true)
                ) {
                    val html = bytes.toString(Charsets.UTF_8)
                    val imgRegex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    val match = imgRegex.find(html)
                    if (match != null) {
                        val imgSrc = match.groupValues[1]
                        val imgPath = resolvePath(parsedEpub.opfDir, imgSrc)
                        zip.getEntry(imgPath)?.let { imgEntry ->
                            zip.getInputStream(imgEntry).use { it.readBytes() }.let { return it }
                        }
                    }
                }
            }
        }

        // Level 4: heuristic by name "cover" in manifest
        val heuristicItem = parsedEpub.manifest.values.find { item ->
            item.href.lowercase().contains("cover") &&
                item.mediaType.startsWith("image/") &&
                item.mediaType != "image/svg+xml" // BUG-EPUB-019: skip SVG
        }
        if (heuristicItem != null) {
            val path = resolvePath(parsedEpub.opfDir, heuristicItem.href)
            zip.getEntry(path)?.let { entry ->
                zip.getInputStream(entry).use { it.readBytes() }.let { return it }
            }
        }

        // Level 5: first image in manifest
        val firstImage = parsedEpub.manifest.values.find {
            it.mediaType.startsWith("image/") && it.mediaType != "image/svg+xml"
        }
        if (firstImage != null) {
            val path = resolvePath(parsedEpub.opfDir, firstImage.href)
            zip.getEntry(path)?.let { entry ->
                zip.getInputStream(entry).use { it.readBytes() }.let { return it }
            }
        }

        return null
    }

    /**
     * Finds the cover item ID from OPF meta tags using XML parser.
     * BUG-EPUB-009: Uses XmlPullParser instead of fragile regex.
     */
    private fun findCoverMetaItemId(parsedEpub: ParsedEpub, zip: ZipFile): String? {
        return try {
            // Find OPF path from container
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: return null
            val opfPath = zip.getInputStream(containerEntry).use { stream ->
                stream.bufferedReader().use { it.readText() }
            }.let { xml ->
                val regex = Regex("""full-path=["']([^"']*)["']""")
                regex.find(xml)?.groupValues?.get(1)
            } ?: return null

            val opfEntry = zip.getEntry(opfPath) ?: return null

            // Use XML parser instead of regex (BUG-EPUB-009)
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newPullParser()
            parser.setInput(zip.getInputStream(opfEntry), null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "meta") {
                    val name = parser.getAttributeValue(null, "name")
                    if (name != null && name.equals("cover", ignoreCase = true)) {
                        return parser.getAttributeValue(null, "content")
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OPF meta for cover", e)
            null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val ratio = minOf(
            maxWidth.toFloat() / width.toFloat(),
            maxHeight.toFloat() / height.toFloat()
        )

        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Resolves and normalizes paths. BUG-EPUB-014: Handles ../ and ./ segments.
     */
    private fun resolvePath(opfDir: String, href: String): String {
        return normalizePath(if (opfDir.isEmpty()) href else "$opfDir/$href")
    }

    /**
     * Normalizes a path by resolving . and .. segments. (BUG-EPUB-014)
     */
    private fun normalizePath(path: String): String {
        val parts = path.split("/")
        val result = mutableListOf<String>()
        for (part in parts) {
            when {
                part == "." || part.isEmpty() -> continue
                part == ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex)
                else -> result.add(part)
            }
        }
        return result.joinToString("/")
    }
}
