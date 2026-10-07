package com.huang1988pioneer.mediaconverter.engine

import java.net.URI
import java.net.URLDecoder

/**
 * Same format and retry rules as the desktop converter.
 * YouTube stays on fewer fragments so extra connections do not trip HTTP 403.
 * Bilibili uses more fragments because a single connection is throttled.
 */
object DownloadPolicy {
    const val STANDARD_EXTRACTOR = "youtube:player_client=default,ios,tv,web"
    const val FALLBACK_EXTRACTOR = "youtube:player_client=ios,tv,web"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

    private val trackingKeys = setOf(
        "spm_id_from", "from_spmid", "vd_source", "share_source",
        "share_medium", "share_plat", "share_session_id", "unique_k",
        "si", "feature", "ab_channel", "t"
    )
    private val playlistKeys = setOf("list", "index", "start_radio", "pp", "playnext")

    fun isBilibiliUrl(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return host.equals("b23.tv", true) ||
            host.endsWith(".b23.tv", true) ||
            host.equals("bilibili.com", true) ||
            host.endsWith(".bilibili.com", true)
    }

    fun isYouTubeUrl(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return host.contains("youtube.com", true) ||
            host.equals("youtu.be", true) ||
            host.contains("youtube-nocookie.com", true)
    }

    fun concurrentFragments(url: String?): Int = when {
        isBilibiliUrl(url) -> 16
        isYouTubeUrl(url) -> 4
        else -> 8
    }

    fun parseMaxHeight(mp4Quality: String?): Int {
        val raw = (mp4Quality ?: "1080P").trim().uppercase()
        if (raw == "4K" || raw == "2160" || raw == "2160P") return 2160
        val digits = if (raw.endsWith("P")) raw.dropLast(1) else raw
        return digits.toIntOrNull()?.takeIf { it > 0 } ?: 1080
    }

    fun normalizeQuality(quality: String?): String {
        val raw = (quality ?: "").trim().uppercase()
        if (raw == "4K" || raw == "2160" || raw == "2160P") return "4K"
        val digits = if (raw.endsWith("P")) raw.dropLast(1) else raw
        val height = digits.toIntOrNull()
        return if (height != null && height > 0) "${height}P" else "1080P"
    }

    fun mp4FormatSelector(mp4Quality: String?): String {
        val maxHeight = parseMaxHeight(mp4Quality)
        return "bestvideo[height<=$maxHeight][vcodec^=avc1]+bestaudio[acodec^=mp4a]/" +
            "bestvideo[height<=$maxHeight][ext=mp4]+bestaudio[ext=m4a]/" +
            "best[height<=$maxHeight][ext=mp4]/" +
            "bestvideo[height<=$maxHeight]+bestaudio/" +
            "best[height<=$maxHeight]/best"
    }

    fun looksLikeHttpForbidden(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.contains("HTTP Error 403", true) || text.contains("403: Forbidden", true)
    }

    fun looksLikeAccountRequired(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val needles = listOf(
            "members-only", "members only", "member-only", "channel's members",
            "Join this channel", "subscriber_only", "premium_only",
            "會員專屬", "会员专属", "充電專屬", "充电专属", "付費", "付费"
        )
        return needles.any { text.contains(it, ignoreCase = true) }
    }

    fun accountRequiredHint() =
        "這是會員或付費影片。請用已登入、且有觀看權限的帳號匯出 cookies.txt，再匯入後重試。"

    fun forbiddenGiveUpHint() =
        "YouTube 仍回傳 403。請按「更新 yt-dlp」，或匯入已登入的 cookies.txt 後重試。"

    fun forbiddenRetryHint() =
        "YouTube 拒絕下載（403）。正在改用相容畫質與播放器客戶端重試。"

    fun splitUrls(text: String): List<String> =
        text.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()

    fun normalizeMediaUrl(url: String, preservePlaylistParams: Boolean): String {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        val host = uri.host ?: return trimmed
        val path = uri.path ?: ""
        val isBiliVideo = host.endsWith("bilibili.com", true) && path.startsWith("/video/", true)
        val isYt = isYouTubeUrl(trimmed)
        if (!isBiliVideo && !isYt) return trimmed
        val isPlaylistPage = isYt && path.startsWith("/playlist", true)
        val query = stripQuery(uri.rawQuery, preservePlaylistParams || isPlaylistPage)
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val q = if (query.isEmpty()) "" else "?$query"
        val fragment = if (uri.rawFragment.isNullOrEmpty()) "" else "#${uri.rawFragment}"
        return "${uri.scheme}://${uri.host}$port$path$q$fragment"
    }

    fun accessLabel(title: String?, description: String?, availability: String?): String? {
        val text = "${title.orEmpty()}\n${description.orEmpty()}"
        if (availability.equals("premium_only", true) ||
            text.contains("充電專屬") || text.contains("充电专属") ||
            text.contains("付費專屬") || text.contains("付费专属") ||
            text.contains("付費影片") || text.contains("付费视频")
        ) {
            return "付費影片"
        }
        if (availability.equals("subscriber_only", true) ||
            text.contains("members-only", true) || text.contains("members only", true) ||
            text.contains("member-only", true) || text.contains("會員專屬") ||
            text.contains("会员专属") || text.contains("大會員") || text.contains("大会员")
        ) {
            return "會員影片"
        }
        if (availability.equals("needs_auth", true) || availability.equals("private", true)) {
            return "需登入"
        }
        return null
    }

    private fun stripQuery(raw: String?, preservePlaylist: Boolean): String {
        if (raw.isNullOrBlank()) return ""
        val drop = trackingKeys.toMutableSet()
        if (!preservePlaylist) drop += playlistKeys
        return raw.split("&")
            .filter { it.isNotBlank() }
            .filter { part ->
                val key = runCatching {
                    URLDecoder.decode(part.substringBefore("="), Charsets.UTF_8.name())
                }.getOrDefault(part.substringBefore("="))
                !drop.contains(key)
            }
            .joinToString("&")
    }

    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url.trim()).host }.getOrNull()
    }
}
