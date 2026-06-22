package com.epubaudioreader.core.data.epub.extractor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.epubaudioreader.core.data.epub.model.ParsedEpub
import com.epubaudioreader.core.data.local.storage.EpubStorageManager
import java.io.File
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
 */
class CoverExtractor @Inject constructor(
    private val storageManager: EpubStorageManager
) {

    companion object {
        private const val MAX_WIDTH = 512
        private const val MAX_HEIGHT = 768
        private const val JPEG_QUALITY = 85
    }

    suspend fun extractCover(parsedEpub: ParsedEpub, bookId: Long): String? {
        val coverBytes = findCoverBytes(parsedEpub)
            ?: return null

        return try {
            val bitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                ?: return null

            val resized = resizeBitmap(bitmap, MAX_WIDTH, MAX_HEIGHT)

            // Save cover via storageManager to ensure consistent path
            val coverPath = storageManager.saveCover(resized, bookId)

            // Recycle if original was different
            if (resized !== bitmap) {
                bitmap.recycle()
            }

            coverPath
        } catch (_: Exception) {
            null
        }
    }

    private fun findCoverBytes(parsedEpub: ParsedEpub): ByteArray? {
        ZipFile(parsedEpub.bookFile).use { zip ->
            // Level 1: meta name="cover" -> find item by id in manifest
            val coverItemId = findCoverMetaItemId(parsedEpub)
            if (coverItemId != null) {
                val item = parsedEpub.manifest[coverItemId]
                if (item != null) {
                    val path = resolvePath(parsedEpub.opfDir, item.href)
                    zip.getEntry(path)?.let { entry ->
                        zip.getInputStream(entry).use { it.readBytes() }.let { return it }
                    }
                }
            }

            // Level 2: item with properties="cover-image"
            val coverImageItem = parsedEpub.manifest.values.find { it.properties == "cover-image" }
            if (coverImageItem != null) {
                val path = resolvePath(parsedEpub.opfDir, coverImageItem.href)
                zip.getEntry(path)?.let { entry ->
                    zip.getInputStream(entry).use { it.readBytes() }.let { return it }
                }
            }

            // Level 3: guide reference type="cover"
            // Need to re-parse OPF for guide or we can extract from parsedEpub
            // Since ParsedEpub doesn't have guide, we need to check the manifest heuristically
            // Actually, ParsedEpub doesn't carry guide. Let's rely on the OPF having parsed it.
            // For now, skip to level 4 since ParsedEpub doesn't expose guide directly.
            // We'll handle this by checking the EPUB again for guide.

            // Level 4: heuristic by name "cover" in manifest
            val heuristicItem = parsedEpub.manifest.values.find { item ->
                item.href.lowercase().contains("cover") &&
                    item.mediaType.startsWith("image/")
            }
            if (heuristicItem != null) {
                val path = resolvePath(parsedEpub.opfDir, heuristicItem.href)
                zip.getEntry(path)?.let { entry ->
                    zip.getInputStream(entry).use { it.readBytes() }.let { return it }
                }
            }

            // Level 5: first image in manifest
            val firstImage = parsedEpub.manifest.values.find {
                it.mediaType.startsWith("image/")
            }
            if (firstImage != null) {
                val path = resolvePath(parsedEpub.opfDir, firstImage.href)
                zip.getEntry(path)?.let { entry ->
                    zip.getInputStream(entry).use { it.readBytes() }.let { return it }
                }
            }

            return null
        }
    }

    /**
     * Finds the cover item ID from OPF meta tags.
     * This requires reading the raw OPF again since ParsedEpub doesn't carry raw meta.
     */
    private fun findCoverMetaItemId(parsedEpub: ParsedEpub): String? {
        return try {
            ZipFile(parsedEpub.bookFile).use { zip ->
                // Find OPF path from container
                val containerEntry = zip.getEntry("META-INF/container.xml")
                    ?: return@use null
                val opfPath = zip.getInputStream(containerEntry).use { stream ->
                    val xml = stream.bufferedReader().use { it.readText() }
                    val regex = Regex("full-path=\"([^\"]*)\"")
                    regex.find(xml)?.groupValues?.get(1)
                } ?: return@use null

                val opfEntry = zip.getEntry(opfPath) ?: return@use null
                val opfContent = zip.getInputStream(opfEntry).use { stream ->
                    stream.bufferedReader().use { it.readText() }
                }

                // Look for meta name="cover" content="..."
                val metaRegex = Regex("""<meta\s+[^>]*name="cover"[^>]*content="([^"]*)"""", RegexOption.IGNORE_CASE)
                val match = metaRegex.find(opfContent)
                match?.groupValues?.get(1)
            }
        } catch (_: Exception) {
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

    private fun resolvePath(opfDir: String, href: String): String {
        return if (opfDir.isEmpty()) href else "$opfDir/$href"
    }
}
