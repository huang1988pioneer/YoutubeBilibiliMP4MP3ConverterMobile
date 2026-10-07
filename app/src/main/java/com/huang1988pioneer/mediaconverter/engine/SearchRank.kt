package com.huang1988pioneer.mediaconverter.engine

object SearchRank {
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val stripped = text
            .replace(Regex("<.*?>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace('\u3000', ' ')
            .replace('\u00A0', ' ')
            .trim()
        return Regex("\\s+").replace(stripped, " ")
    }

    fun matchesKeyword(title: String?, description: String?, query: String): Boolean {
        val keyword = normalize(query)
        if (keyword.isBlank()) return true
        val haystack = normalize(title) + "\n" + normalize(description)
        return haystack.contains(keyword, ignoreCase = true)
    }

    fun usable(url: String?, title: String?): Boolean {
        if (url.isNullOrBlank() || !url.startsWith("http", true)) return false
        if (title.isNullOrBlank()) return false
        if (url.contains("ytsearch", true) || url.contains("bilisearch", true)) return false
        return true
    }

    fun <T> interleave(youtube: List<T>, bilibili: List<T>): List<T> {
        if (youtube.isEmpty() || bilibili.isEmpty()) return youtube + bilibili
        val ordered = ArrayList<T>(youtube.size + bilibili.size)
        val max = maxOf(youtube.size, bilibili.size)
        for (index in 0 until max) {
            if (index < youtube.size) ordered += youtube[index]
            if (index < bilibili.size) ordered += bilibili[index]
        }
        return ordered
    }
}
