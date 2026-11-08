package io.github.thanhhfishyu.flazy

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scanned books are plain .txt files in the app's private storage.
 * Each page is appended as soon as it is read, so nothing is lost if the scan stops early.
 */
object BookStore {

    fun dir(context: Context): File = File(context.filesDir, "books").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun create(context: Context, title: String): Book {
        val clean = title.trim().ifEmpty { "Sach" }
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
            .take(60)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(dir(context), "${clean}_$stamp.txt")
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())
        file.writeText("Tên sách: ${title.trim().ifEmpty { "(chưa đặt tên)" }}\nQuét bằng FLazy lúc $now\n")
        return Book(file)
    }

    /** Copies a finished book to Download/FLazy so it is visible in the phone's file manager. */
    fun exportToDownloads(context: Context, file: File): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FLazy")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        true
    }.getOrDefault(false)

    class Book(val file: File) {
        fun appendPage(number: Int, text: String) {
            file.appendText("\n--- Trang $number ---\n$text\n")
        }

        fun appendNote(note: String) {
            file.appendText("\n[$note]\n")
        }
    }
}
