package com.huang1988pioneer.mediaconverter.engine

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

object MediaFinisher {
    private val languageRank = listOf("zh-hant", "zh-tw", "zh-hk", "zh-hans", "zh-cn", "zh", "en")

    fun producedFiles(dir: File, startedAt: Long): List<File> {
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.lastModified() >= startedAt - 2_000 &&
                    MediaStoreExport.isDeliverable(file.name)
            }
            .sortedBy { it.lastModified() }
            .toList()
    }

    fun pairSubtitles(context: Context, files: List<File>, format: String): List<File> {
        val media = files.filter { it.extension.equals("mp4", true) || it.extension.equals("mp3", true) || it.extension.equals("m4a", true) }
        val extras = ArrayList<File>()
        for (item in media) {
            val dir = item.parentFile ?: continue
            val stem = item.nameWithoutExtension
            convertMatchingVtt(dir, stem)
            val best = findBestSubtitle(dir, stem) ?: continue
            val paired = File(dir, "$stem.srt")
            if (!best.absolutePath.equals(paired.absolutePath, true)) {
                best.copyTo(paired, overwrite = true)
            }
            if (!files.contains(paired) && paired.exists()) extras += paired
            if (format.equals("MP3", true) || item.extension.equals("mp3", true)) {
                val lrc = File(dir, "$stem.lrc")
                if (SubtitleText.writeLrcFromSrt(paired, lrc) && lrc.exists()) extras += lrc
            }
            if (item.extension.equals("mp4", true)) {
                embed(context, item, paired)
            }
        }
        return extras
    }

    private fun convertMatchingVtt(dir: File, stem: String) {
        dir.listFiles()?.filter { it.name.startsWith(stem) && it.extension.equals("vtt", true) }?.forEach { vtt ->
            val srt = File(dir, vtt.nameWithoutExtension + ".srt")
            if (!srt.exists()) SubtitleText.convertVttToSrt(vtt, srt)
        }
    }

    private fun findBestSubtitle(dir: File, stem: String): File? {
        val candidates = dir.listFiles()?.filter {
            it.name.startsWith(stem) && (it.extension.equals("srt", true) || it.extension.equals("vtt", true))
        }.orEmpty()
        if (candidates.isEmpty()) return null
        return candidates.minByOrNull { file ->
            val lower = file.name.lowercase()
            val lang = languageRank.indexOfFirst { lower.contains(it) }.let { if (it < 0) 50 else it }
            val extPenalty = if (file.extension.equals("srt", true)) 0 else 1
            lang * 10 + extPenalty
        }
    }

    private fun embed(context: Context, media: File, srt: File): Boolean {
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!ffmpeg.exists() || !srt.exists()) return false
        val tmp = File(media.parentFile, media.name + ".sub.tmp.mp4")
        if (tmp.exists()) tmp.delete()
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val libraryPath = listOf("python/usr/lib", "ffmpeg/usr/lib", "aria2c/usr/lib")
            .joinToString(":") { File(packages, it).absolutePath }
        return try {
            val process = ProcessBuilder(
                ffmpeg.absolutePath,
                "-hide_banner", "-loglevel", "error", "-y",
                "-i", media.absolutePath,
                "-i", srt.absolutePath,
                "-c", "copy",
                "-c:s", "mov_text",
                "-map", "0",
                "-map", "1:0",
                "-metadata:s:s:0", "language=zho",
                tmp.absolutePath
            ).apply {
                environment()["LD_LIBRARY_PATH"] = libraryPath
                redirectErrorStream(true)
            }.start()
            val gobbler = Thread { process.inputStream.bufferedReader().use { it.readText() } }
            gobbler.start()
            val finished = process.waitFor(180, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                tmp.delete()
                return false
            }
            gobbler.join(2_000)
            if (process.exitValue() != 0 || !tmp.exists() || tmp.length() <= 0L) {
                tmp.delete()
                return false
            }
            val backup = File(media.absolutePath + ".bak")
            if (backup.exists()) backup.delete()
            if (!media.renameTo(backup)) {
                tmp.delete()
                return false
            }
            if (!tmp.renameTo(media)) {
                backup.renameTo(media)
                tmp.delete()
                return false
            }
            backup.delete()
            true
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }
}
