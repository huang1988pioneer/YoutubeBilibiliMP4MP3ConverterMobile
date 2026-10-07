package com.huang1988pioneer.mediaconverter.engine

import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

object YtDlpClient {
    fun infoRequest(url: String, cookies: File?, playlist: Boolean): YoutubeDLRequest {
        val normalized = DownloadPolicy.normalizeMediaUrl(url, playlist)
        return YoutubeDLRequest(normalized).apply {
            addOption("--dump-single-json")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("--encoding", "utf-8")
            addOption(if (playlist) "--yes-playlist" else "--no-playlist")
            addSiteArgs(this, normalized, cookies, fallback = false)
        }
    }

    fun searchRequest(searchUrl: String, bilibili: Boolean): YoutubeDLRequest {
        return YoutubeDLRequest(searchUrl).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("--encoding", "utf-8")
            if (bilibili) addBilibiliHeaders(this)
        }
    }

    fun downloadRequest(
        url: String,
        outputDir: File,
        format: String,
        quality: String,
        subtitles: Boolean,
        playlist: Boolean,
        cookies: File?,
        fallback: Boolean
    ): YoutubeDLRequest {
        val normalized = DownloadPolicy.normalizeMediaUrl(url, playlist)
        return YoutubeDLRequest(normalized).apply {
            if (format == "MP3") {
                addOption("--extract-audio")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
                addOption("--convert-thumbnails", "jpg")
                addOption("--embed-thumbnail")
                addOption("--postprocessor-args", "ffmpeg:-id3v2_version 3")
            } else {
                addOption("--format", DownloadPolicy.mp4FormatSelector(quality))
                addOption("--merge-output-format", "mp4")
            }
            addOption("--encoding", "utf-8")
            addOption("--newline")
            addOption("--retries", "8")
            addOption("--fragment-retries", "8")
            addOption("--concurrent-fragments", DownloadPolicy.concurrentFragments(normalized))
            addOption(if (playlist) "--yes-playlist" else "--no-playlist")
            if (subtitles) addSubtitleArgs(this, "zh-Hant,zh-Hans,en")
            addSiteArgs(this, normalized, cookies, fallback)
            addOption("--add-metadata")
            addOption("--no-mtime")
            addOption("--paths", outputDir.absolutePath)
            addOption(
                "--output",
                if (playlist) "%(playlist_index&{} - |)s%(title).180B.%(ext)s" else "%(title).180B.%(ext)s"
            )
        }
    }

    fun subtitleRequest(
        url: String,
        outputDir: File,
        playlist: Boolean,
        cookies: File?,
        languages: String
    ): YoutubeDLRequest {
        val normalized = DownloadPolicy.normalizeMediaUrl(url, playlist)
        return YoutubeDLRequest(normalized).apply {
            addOption("--skip-download")
            addOption("--encoding", "utf-8")
            addOption("--newline")
            addOption(if (playlist) "--yes-playlist" else "--no-playlist")
            addOption("--ignore-errors")
            addOption("--retries", "3")
            addSubtitleArgs(this, languages)
            addSiteArgs(this, normalized, cookies, fallback = false)
            addOption("--paths", outputDir.absolutePath)
            addOption(
                "--output",
                if (playlist) "%(playlist_index&{} - |)s%(title).180B.%(ext)s" else "%(title).180B.%(ext)s"
            )
        }
    }

    private fun addSubtitleArgs(request: YoutubeDLRequest, languages: String) {
        request.addOption("--write-subs")
        request.addOption("--write-auto-subs")
        request.addOption("--sub-langs", languages)
        request.addOption("--convert-subs", "srt")
        request.addOption("--sleep-subtitles", "2")
    }

    private fun addSiteArgs(
        request: YoutubeDLRequest,
        url: String,
        cookies: File?,
        fallback: Boolean
    ) {
        if (DownloadPolicy.isYouTubeUrl(url)) {
            request.addOption(
                "--extractor-args",
                if (fallback) DownloadPolicy.FALLBACK_EXTRACTOR else DownloadPolicy.STANDARD_EXTRACTOR
            )
            request.addOption("--force-ipv4")
            if (fallback) request.addOption("--rm-cache-dir")
        }
        if (DownloadPolicy.isBilibiliUrl(url)) addBilibiliHeaders(request)
        if (cookies != null && cookies.exists() && cookies.length() > 0) {
            request.addOption("--cookies", cookies.absolutePath)
        }
    }

    private fun addBilibiliHeaders(request: YoutubeDLRequest) {
        request.addOption("--add-headers", "User-Agent:${DownloadPolicy.USER_AGENT}")
        request.addOption("--add-headers", "Referer:https://www.bilibili.com/")
        request.addOption("--add-headers", "Accept-Language:zh-CN,zh-TW;q=0.9,zh;q=0.8,en;q=0.7")
    }
}
