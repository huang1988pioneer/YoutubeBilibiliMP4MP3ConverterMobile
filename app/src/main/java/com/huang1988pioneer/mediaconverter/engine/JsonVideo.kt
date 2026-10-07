package com.huang1988pioneer.mediaconverter.engine

import com.huang1988pioneer.mediaconverter.model.SearchHit
import com.huang1988pioneer.mediaconverter.model.VideoPreview
import org.json.JSONObject

object JsonVideo {
    fun extractObject(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw IllegalStateException("沒有讀到影片資料")
        }
        return JSONObject(text.substring(start, end + 1))
    }

    fun preview(text: String, requestedUrl: String): VideoPreview {
        val root = extractObject(text)
        val node = root.optJSONArray("entries")?.optJSONObject(0) ?: root
        val title = node.optString("title").ifBlank { node.optString("id").ifBlank { "未命名影片" } }
        val page = node.optString("webpage_url").ifBlank { node.optString("original_url") }.ifBlank { requestedUrl }
        val description = SearchRank.normalize(node.optString("description").ifBlank { null })
        return VideoPreview(
            title = SearchRank.normalize(title),
            url = page,
            uploader = node.optString("uploader").ifBlank { node.optString("channel").ifBlank { null } },
            durationSeconds = if (node.has("duration") && !node.isNull("duration")) node.optDouble("duration") else null,
            viewCount = if (node.has("view_count") && !node.isNull("view_count")) node.optLong("view_count") else null,
            uploadDate = formatUploadDate(node.optString("upload_date")),
            description = description.ifBlank { null },
            accessLabel = DownloadPolicy.accessLabel(title, description, node.optString("availability")),
            thumbnailUrl = node.optString("thumbnail").ifBlank { null }
        )
    }

    fun searchHits(text: String, platform: String): List<SearchHit> {
        if (text.isBlank()) return emptyList()
        val root = try {
            extractObject(text)
        } catch (_: Exception) {
            return emptyList()
        }
        val entries = root.optJSONArray("entries")
        if (entries == null) {
            return listOfNotNull(hitFrom(root, platform))
        }
        return buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                hitFrom(entry, platform)?.let { add(it) }
            }
        }
    }

    fun bilibiliApiHits(body: String, limit: Int): List<SearchHit> {
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) return emptyList()
        val result = root.optJSONObject("data")?.optJSONArray("result") ?: return emptyList()
        return buildList {
            for (index in 0 until result.length()) {
                if (size >= limit) break
                val entry = result.optJSONObject(index) ?: continue
                val type = entry.optString("type")
                if (type.isNotBlank() && !type.equals("video", true)) continue
                val bvid = entry.optString("bvid")
                if (bvid.isBlank()) continue
                val title = SearchRank.normalize(entry.optString("title"))
                val description = SearchRank.normalize(
                    entry.optString("description").ifBlank { entry.optString("desc") }
                )
                add(
                    SearchHit(
                        platform = "Bilibili",
                        title = title.ifBlank { bvid },
                        url = "https://www.bilibili.com/video/$bvid/",
                        uploader = entry.optString("author").ifBlank { null },
                        durationSeconds = parseClock(entry.optString("duration")),
                        viewCount = when (val play = entry.opt("play")) {
                            is Number -> play.toLong()
                            is String -> play.toLongOrNull()
                            else -> null
                        },
                        description = description.ifBlank { null }
                    )
                )
            }
        }
    }

    private fun hitFrom(entry: JSONObject, platform: String): SearchHit? {
        val id = entry.optString("id").ifBlank { null }
        val title = SearchRank.normalize(entry.optString("title")).ifBlank { id }
        val url = resolveUrl(entry, platform, id)
        if (!SearchRank.usable(url, title)) return null
        val description = SearchRank.normalize(
            entry.optString("description").ifBlank { entry.optString("desc") }
        ).ifBlank { null }
        return SearchHit(
            platform = platform,
            title = title ?: "未命名影片",
            url = url!!,
            uploader = entry.optString("uploader").ifBlank { entry.optString("channel").ifBlank { null } },
            durationSeconds = if (entry.has("duration") && !entry.isNull("duration")) entry.optDouble("duration") else null,
            viewCount = if (entry.has("view_count") && !entry.isNull("view_count")) entry.optLong("view_count") else null,
            description = description
        )
    }

    private fun resolveUrl(entry: JSONObject, platform: String, id: String?): String? {
        val webpage = entry.optString("webpage_url").ifBlank { null }
        if (webpage?.startsWith("http", true) == true) return webpage
        val url = entry.optString("url").ifBlank { null }
        if (url?.startsWith("http", true) == true) return url
        if (id.isNullOrBlank()) return null
        return if (platform.equals("Bilibili", true)) {
            if (id.startsWith("BV", true)) "https://www.bilibili.com/video/$id/"
            else "https://www.bilibili.com/video/av$id/"
        } else {
            "https://www.youtube.com/watch?v=$id"
        }
    }

    private fun formatUploadDate(raw: String): String? {
        if (raw.length != 8 || raw.any { !it.isDigit() }) return raw.ifBlank { null }
        return "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
    }

    private fun parseClock(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        return if (numbers.size == 2) {
            (numbers[0] * 60 + numbers[1]).toDouble()
        } else {
            (numbers[0] * 3600 + numbers[1] * 60 + numbers[2]).toDouble()
        }
    }
}
