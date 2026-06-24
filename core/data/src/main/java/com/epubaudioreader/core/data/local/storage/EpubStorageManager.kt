package com.epubaudioreader.core.data.local.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpubStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcher: DispatcherProvider
) {
    companion object {
        private const val DIR_BOOKS = "books"
        private const val DIR_COVERS = "covers"
        private const val DIR_CHAPTERS = "chapters"
    }

    private fun getBaseDir(): File = context.filesDir

    fun getBookDir(bookId: Long): File = File(getBaseDir(), "$DIR_BOOKS/$bookId").apply { mkdirs() }
    fun getBookFile(bookId: Long): File = File(getBookDir(bookId), "book.epub")

    private fun getCoversDir(): File = File(getBaseDir(), DIR_COVERS).apply { mkdirs() }
    fun getCoverFile(bookId: Long): File = File(getCoversDir(), "$bookId.jpg")

    fun getChaptersDir(bookId: Long): File = File(getBaseDir(), "$DIR_CHAPTERS/$bookId").apply { mkdirs() }
    fun getChapterFile(bookId: Long, chapterId: Long): File = File(getChaptersDir(bookId), "$chapterId.txt")

    suspend fun copyEpubFromUri(uri: Uri, bookId: Long): File = withContext(dispatcher.io) {
        val destFile = getBookFile(bookId)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        } ?: throw IOException("Cannot open input stream for URI: $uri")
        destFile
    }

    suspend fun saveCover(bitmap: Bitmap, bookId: Long): String = withContext(dispatcher.io) {
        val coverFile = getCoverFile(bookId)
        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        coverFile.absolutePath
    }

    suspend fun saveChapterText(text: String, bookId: Long, chapterId: Long): String = withContext(dispatcher.io) {
        val chapterFile = getChapterFile(bookId, chapterId)
        chapterFile.writeText(text, Charsets.UTF_8)
        chapterFile.absolutePath
    }

    /**
     * Reads chapter text from filesystem.
     * Returns empty string if file not found or read error occurs.
     */
    suspend fun readChapterText(chapterFilePath: String): String = withContext(dispatcher.io) {
        try {
            File(chapterFilePath).readText(Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("EpubStorageManager", "Error reading chapter file: $chapterFilePath", e)
            ""
        }
    }

    suspend fun deleteBookFiles(bookId: Long) = withContext(dispatcher.io) {
        getBookDir(bookId).deleteRecursively()
        getCoverFile(bookId).delete()
        getChaptersDir(bookId).deleteRecursively()
    }

    suspend fun copyBookFile(sourceFile: File, bookId: Long): File = withContext(dispatcher.io) {
        val destFile = getBookFile(bookId)
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
        destFile
    }

    suspend fun computeFileHash(file: File): String = withContext(dispatcher.io) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
