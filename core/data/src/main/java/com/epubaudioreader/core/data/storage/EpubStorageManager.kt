package com.epubaudioreader.core.data.storage

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages file storage for EPUB books, covers, and chapter text files.
 * Directory structure:
 *   app_data/
 *     books/        - stored EPUB files (named by bookId)
 *     covers/       - cover images (cover_$bookId.jpg)
 *     chapters/     - chapter text files (book_${bookId}_chapter_${chapterId}.txt)
 */
@Singleton
class EpubStorageManager @Inject constructor(
    private val context: Context
) {
    private val baseDir: File by lazy {
        context.getDir("epub_data", Context.MODE_PRIVATE).apply {
            if (!exists()) mkdirs()
        }
    }

    val booksDir: File
        get() = File(baseDir, "books").apply { if (!exists()) mkdirs() }

    val coversDir: File
        get() = File(baseDir, "covers").apply { if (!exists()) mkdirs() }

    val chaptersDir: File
        get() = File(baseDir, "chapters").apply { if (!exists()) mkdirs() }

    /**
     * Copies the source EPUB file to the app's internal storage.
     * Returns the path of the copied file.
     */
    fun copyBookFile(sourceFile: File, bookId: Long): File {
        val destFile = File(booksDir, "book_$bookId.epub")
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    /**
     * Saves chapter text to a file and returns the file path.
     */
    fun saveChapterText(bookId: Long, chapterId: Long, text: String): String {
        val file = File(chaptersDir, "book_${bookId}_chapter_${chapterId}.txt")
        file.writeText(text, Charsets.UTF_8)
        return file.absolutePath
    }

    /**
     * Reads chapter text from file.
     */
    fun readChapterText(contentFilePath: String): String {
        return try {
            File(contentFilePath).readText(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Computes SHA-256 hash of a file for duplicate detection.
     */
    fun computeFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Deletes all files associated with a book.
     */
    fun deleteBookFiles(bookId: Long) {
        // Delete EPUB file
        File(booksDir, "book_$bookId.epub").delete()
        // Delete cover
        File(coversDir, "cover_$bookId.jpg").delete()
        // Delete all chapter files
        val chapterPrefix = "book_${bookId}_chapter_"
        chaptersDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(chapterPrefix)) {
                file.delete()
            }
        }
    }

    /**
     * Returns the total storage used by a book in bytes.
     */
    fun getBookStorageSize(bookId: Long): Long {
        var size = 0L
        val epubFile = File(booksDir, "book_$bookId.epub")
        if (epubFile.exists()) size += epubFile.length()
        val coverFile = File(coversDir, "cover_$bookId.jpg")
        if (coverFile.exists()) size += coverFile.length()
        val chapterPrefix = "book_${bookId}_chapter_"
        chaptersDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(chapterPrefix)) {
                size += file.length()
            }
        }
        return size
    }
}
