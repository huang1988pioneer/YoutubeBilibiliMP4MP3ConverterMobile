package com.huang1988pioneer.mediaconverter.engine

object FailureText {
    fun summarize(log: String?): String {
        if (DownloadPolicy.looksLikeAccountRequired(log)) {
            return DownloadPolicy.accountRequiredHint()
        }
        if (DownloadPolicy.looksLikeHttpForbidden(log)) {
            return DownloadPolicy.forbiddenGiveUpHint()
        }
        val useful = pickUseful(log)
        if (!useful.isNullOrBlank()) return useful
        return "下載沒有完成。"
    }

    fun pickUseful(log: String?): String? {
        if (log.isNullOrBlank()) return null
        val lines = log.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        var fallback: String? = null
        for (line in lines.asReversed()) {
            if (isGeneric(line)) continue
            if (line.contains("ERROR:", true) ||
                DownloadPolicy.looksLikeAccountRequired(line) ||
                DownloadPolicy.looksLikeHttpForbidden(line)
            ) {
                return trim(line)
            }
            if (fallback == null) fallback = trim(line)
        }
        return fallback
    }

    private fun isGeneric(line: String): Boolean =
        line.startsWith("正在轉換") ||
            line.startsWith("輸出") ||
            line.startsWith("Cookies:") ||
            line.startsWith("字幕") ||
            (line.startsWith("[") && line.contains("] http"))

    private fun trim(line: String): String = if (line.length <= 280) line else line.take(280)
}
