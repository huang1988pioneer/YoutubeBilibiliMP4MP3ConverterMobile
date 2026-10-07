package com.huang1988pioneer.mediaconverter.engine

import java.io.File
import java.util.Locale

object SubtitleText {
    fun convertVttToSrt(vttPath: File, srtPath: File): Boolean {
        return try {
            val lines = vttPath.readText().replace("\r\n", "\n").split('\n')
            val output = ArrayList<String>()
            var index = 1
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.contains("-->")) break
                i++
            }
            while (i < lines.size) {
                while (i < lines.size && lines[i].isBlank()) i++
                if (i >= lines.size) break
                if (!lines[i].contains("-->") && i + 1 < lines.size && lines[i + 1].contains("-->")) {
                    i++
                }
                if (i >= lines.size || !lines[i].contains("-->")) {
                    i++
                    continue
                }
                val timing = lines[i].replace('.', ',').split(" --> ")
                if (timing.size < 2) {
                    i++
                    continue
                }
                val start = timing[0].trim()
                val end = timing[1].trim().substringBefore(' ')
                i++
                val textLines = ArrayList<String>()
                while (i < lines.size && lines[i].isNotBlank() && !lines[i].contains("-->")) {
                    textLines += lines[i].replace(Regex("</?[^>]+>"), "").trim()
                    i++
                }
                if (textLines.isEmpty()) continue
                output += index.toString()
                output += "$start --> $end"
                output += textLines
                output += ""
                index++
            }
            if (output.isEmpty()) return false
            srtPath.writeText(output.joinToString("\n"))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun writeLrcFromSrt(srtPath: File, lrcPath: File): Boolean {
        return try {
            val blocks = srtPath.readText().replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            val lines = mutableListOf("[ti:]", "[ar:]", "[by:YoutubeBilibiliConverter]")
            for (block in blocks) {
                val parts = block.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size < 2) continue
                val timeLineIndex = if (parts[0].contains("-->")) 0 else 1
                if (timeLineIndex >= parts.size || !parts[timeLineIndex].contains("-->")) continue
                val timePart = parts[timeLineIndex].substringBefore("-->").trim()
                val stamp = lrcStamp(timePart) ?: continue
                val content = parts.drop(timeLineIndex + 1).joinToString(" ").trim()
                if (content.isEmpty()) continue
                lines += "[$stamp]$content"
            }
            if (lines.size <= 3) return false
            lrcPath.writeText(lines.joinToString("\n"))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun lrcStamp(raw: String): String? {
        val value = raw.trim().replace(',', '.')
        val match = Regex("""(\d+):(\d+):(\d+)(?:\.(\d+))?""").find(value) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: return null
        val minutes = match.groupValues[2].toIntOrNull() ?: return null
        val seconds = match.groupValues[3].toIntOrNull() ?: return null
        val fraction = match.groupValues[4].padEnd(3, '0').take(3).toIntOrNull() ?: 0
        val totalMinutes = hours * 60 + minutes
        val centis = fraction / 10
        return String.format(Locale.US, "%02d:%02d.%02d", totalMinutes, seconds, centis)
    }
}
