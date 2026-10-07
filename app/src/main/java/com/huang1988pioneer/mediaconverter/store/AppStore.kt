package com.huang1988pioneer.mediaconverter.store

import android.content.Context
import com.huang1988pioneer.mediaconverter.model.DownloadTask
import com.huang1988pioneer.mediaconverter.model.Favorite
import com.huang1988pioneer.mediaconverter.model.RecentSearch
import com.huang1988pioneer.mediaconverter.model.UiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

object AppStore {
    private val lock = Any()

    fun load(context: Context): UiState {
        val file = file(context)
        if (!file.exists()) return UiState(todayDate = LocalDate.now().toString())
        return try {
            val root = JSONObject(file.readText())
            val today = LocalDate.now().toString()
            val storedDate = root.optString("todayDate")
            UiState(
                preparing = true,
                urlText = "",
                format = if (root.optString("format").equals("MP3", true)) "MP3" else "MP4",
                quality = root.optString("quality", "1080P").ifBlank { "1080P" },
                subtitles = root.optBoolean("subtitles", false),
                playlist = root.optBoolean("playlist", false),
                cookiesLabel = root.optString("cookiesLabel").ifBlank { null },
                tasks = readTasks(root.optJSONArray("tasks")).map { task ->
                    if (task.status == "running") task.copy(status = "queued", detail = "等待繼續") else task
                },
                recentSearches = readRecent(root.optJSONArray("recentSearches")),
                favorites = readFavorites(root.optJSONArray("favorites")),
                todayCount = if (storedDate == today) root.optInt("todayCount") else 0,
                todayDate = today,
                status = "準備中…"
            )
        } catch (_: Exception) {
            UiState(todayDate = LocalDate.now().toString())
        }
    }

    fun save(context: Context, state: UiState) {
        synchronized(lock) {
            val root = JSONObject()
            root.put("format", state.format)
            root.put("quality", state.quality)
            root.put("subtitles", state.subtitles)
            root.put("playlist", state.playlist)
            root.put("cookiesLabel", state.cookiesLabel ?: "")
            root.put("todayCount", state.todayCount)
            root.put("todayDate", state.todayDate.ifBlank { LocalDate.now().toString() })
            root.put("tasks", tasksJson(cap(state.tasks)))
            root.put("recentSearches", recentJson(state.recentSearches.take(12)))
            root.put("favorites", favoritesJson(state.favorites.take(100)))
            val file = file(context)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        }
    }

    private fun cap(tasks: List<DownloadTask>): List<DownloadTask> {
        val active = tasks.filter { it.status == "queued" || it.status == "running" }
        val rest = tasks.filter { it.status != "queued" && it.status != "running" }.take(60)
        return active + rest
    }

    private fun file(context: Context) = File(context.filesDir, "state.json")

    private fun tasksJson(tasks: List<DownloadTask>): JSONArray {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id)
                put("url", task.url)
                put("title", task.title)
                put("format", task.format)
                put("quality", task.quality)
                put("subtitles", task.subtitles)
                put("playlist", task.playlist)
                put("status", task.status)
                put("progress", task.progress.toDouble())
                put("detail", task.detail)
                put("error", task.error ?: "")
                put("outputName", task.outputName ?: "")
                put("outputUri", task.outputUri ?: "")
                put("createdAt", task.createdAt)
            })
        }
        return array
    }

    private fun readTasks(array: JSONArray?): List<DownloadTask> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    DownloadTask(
                        id = item.optString("id"),
                        url = item.optString("url"),
                        title = item.optString("title"),
                        format = item.optString("format", "MP4"),
                        quality = item.optString("quality", "1080P"),
                        subtitles = item.optBoolean("subtitles"),
                        playlist = item.optBoolean("playlist"),
                        status = item.optString("status", "done"),
                        progress = item.optDouble("progress").toFloat(),
                        detail = item.optString("detail"),
                        error = item.optString("error").ifBlank { null },
                        outputName = item.optString("outputName").ifBlank { null },
                        outputUri = item.optString("outputUri").ifBlank { null },
                        createdAt = item.optLong("createdAt")
                    )
                )
            }
        }
    }

    private fun recentJson(items: List<RecentSearch>): JSONArray {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().apply {
                put("query", it.query)
                put("platform", it.platform)
                put("at", it.at)
            })
        }
        return array
    }

    private fun readRecent(array: JSONArray?): List<RecentSearch> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val query = item.optString("query")
                if (query.isBlank()) continue
                add(RecentSearch(query, item.optString("platform", "both"), item.optLong("at")))
            }
        }
    }

    private fun favoritesJson(items: List<Favorite>): JSONArray {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().apply {
                put("title", it.title)
                put("url", it.url)
                put("platform", it.platform)
                put("at", it.at)
            })
        }
        return array
    }

    private fun readFavorites(array: JSONArray?): List<Favorite> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (url.isBlank()) continue
                add(
                    Favorite(
                        title = item.optString("title", url),
                        url = url,
                        platform = item.optString("platform"),
                        at = item.optLong("at")
                    )
                )
            }
        }
    }
}
