package com.huang1988pioneer.mediaconverter.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

object MediaStoreExport {
    data class SavedFile(val displayName: String, val uri: Uri)

    fun export(context: Context, file: File): SavedFile? {
        if (!file.exists() || file.length() <= 0L) return null
        val mime = mimeFor(file.name)
        return if (Build.VERSION.SDK_INT >= 29) {
            exportMediaStore(context, file, mime)
        } else {
            exportLegacy(file)
        }
    }

    @RequiresApi(29)
    private fun exportMediaStore(context: Context, file: File, mime: String): SavedFile? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/影音轉換大師")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: return null
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            SavedFile(file.name, uri)
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun exportLegacy(file: File): SavedFile? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "影音轉換大師"
        )
        if (!dir.exists() && !dir.mkdirs()) return null
        val dest = uniqueFile(dir, file.name)
        file.copyTo(dest, overwrite = false)
        return SavedFile(dest.name, Uri.fromFile(dest))
    }

    private fun uniqueFile(dir: File, name: String): File {
        val candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        var index = 2
        while (true) {
            val next = File(dir, "$stem ($index)$suffix")
            if (!next.exists()) return next
            index++
        }
    }

    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "srt" -> "application/x-subrip"
        "lrc", "vtt" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

    fun isDeliverable(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("mp4", "mp3", "m4a", "srt", "lrc")) return false
        if (name.endsWith(".part") || name.endsWith(".ytdl") || name.contains(".tmp")) return false
        return true
    }
}
