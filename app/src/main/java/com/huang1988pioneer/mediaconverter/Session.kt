package com.huang1988pioneer.mediaconverter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.huang1988pioneer.mediaconverter.download.DownloadService
import com.huang1988pioneer.mediaconverter.engine.DownloadPolicy
import com.huang1988pioneer.mediaconverter.engine.FailureText
import com.huang1988pioneer.mediaconverter.engine.JsonVideo
import com.huang1988pioneer.mediaconverter.engine.MediaFinisher
import com.huang1988pioneer.mediaconverter.engine.MediaStoreExport
import com.huang1988pioneer.mediaconverter.engine.SearchRank
import com.huang1988pioneer.mediaconverter.engine.YtDlpClient
import com.huang1988pioneer.mediaconverter.model.DownloadTask
import com.huang1988pioneer.mediaconverter.model.Favorite
import com.huang1988pioneer.mediaconverter.model.RecentSearch
import com.huang1988pioneer.mediaconverter.model.SearchHit
import com.huang1988pioneer.mediaconverter.model.UiState
import com.huang1988pioneer.mediaconverter.store.AppStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object Session {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ytLock = Mutex()
    private val prepareLock = Mutex()
    private val draining = AtomicBoolean(false)
    private val http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()

    val state = MutableStateFlow(UiState())

    @Volatile private var appContext: Context? = null
    @Volatile private var service: DownloadService? = null
    @Volatile var currentProcessId: String? = null
    @Volatile var currentTaskId: String? = null
    private var prepared = false
    private var lastPersist = 0L
    private var lastProgressUi = 0L

    fun attach(context: Context) {
        val app = context.applicationContext
        appContext = app
        val loaded = AppStore.load(app)
        val cookies = cookiesFile(app)
        state.value = loaded.copy(
            cookiesLabel = if (cookies.exists() && cookies.length() > 0) loaded.cookiesLabel ?: "cookies.txt" else null,
            todayDate = LocalDate.now().toString()
        )
    }

    fun bindService(downloadService: DownloadService) {
        service = downloadService
    }

    fun unbindService(downloadService: DownloadService) {
        if (service === downloadService) service = null
    }

    fun prepare(context: Context, manualUpdate: Boolean = false) {
        val app = context.applicationContext
        scope.launch {
            prepareLock.withLock {
                if (prepared && !manualUpdate) return@withLock
                state.update { it.copy(preparing = true, prepareError = null, status = if (manualUpdate) "正在更新 yt-dlp…" else "正在準備 yt-dlp 與 ffmpeg…") }
                try {
                    withContext(Dispatchers.IO) {
                        YoutubeDL.getInstance().init(app)
                        FFmpeg.getInstance().init(app)
                        if (manualUpdate || shouldUpdate(app)) {
                            runCatching {
                                YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
                                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                    .edit()
                                    .putString(KEY_UPDATE, LocalDate.now().toString())
                                    .apply()
                            }
                        }
                    }
                    val version = YoutubeDL.getInstance().versionName(app)
                    prepared = true
                    state.update {
                        it.copy(
                            preparing = false,
                            ready = true,
                            prepareError = null,
                            ytdlpVersion = version,
                            status = "可以開始轉換"
                        )
                    }
                } catch (error: Exception) {
                    state.update {
                        it.copy(
                            preparing = false,
                            ready = prepared,
                            prepareError = error.message ?: "無法初始化 yt-dlp",
                            status = error.message ?: "無法初始化 yt-dlp"
                        )
                    }
                }
            }
        }
    }

    fun setUrl(text: String) {
        state.update { it.copy(urlText = text, previewError = null) }
    }

    fun setSearchQuery(text: String) {
        state.update { it.copy(searchQuery = text) }
    }

    fun setFormat(format: String, quality: String) {
        state.update { it.copy(format = format, quality = DownloadPolicy.normalizeQuality(quality)) }
        persist(force = true)
    }

    fun setSubtitles(enabled: Boolean) {
        state.update { it.copy(subtitles = enabled) }
        persist(force = true)
    }

    fun setPlaylist(enabled: Boolean) {
        state.update { it.copy(playlist = enabled) }
        persist(force = true)
    }

    fun setSearchPlatform(platform: String) {
        state.update { it.copy(searchPlatform = platform) }
    }

    fun setSearchLimit(limit: Int) {
        state.update { it.copy(searchLimit = limit) }
    }

    fun importCookies(context: Context, uri: Uri) {
        val app = context.applicationContext
        val name = displayName(app, uri)
        app.contentResolver.openInputStream(uri)?.use { input ->
            cookiesFile(app).outputStream().use { output -> input.copyTo(output) }
        }
        state.update { it.copy(cookiesLabel = name, status = "已匯入 $name") }
        persist(force = true)
    }

    fun clearCookies(context: Context) {
        cookiesFile(context.applicationContext).delete()
        state.update { it.copy(cookiesLabel = null, status = "已清除 cookies") }
        persist(force = true)
    }

    fun parse(context: Context) {
        val snapshot = state.value
        val url = DownloadPolicy.splitUrls(snapshot.urlText).firstOrNull()
        if (url == null) {
            state.update { it.copy(previewError = "請貼上 YouTube 或 Bilibili 網址", status = "請貼上網址") }
            return
        }
        if (!snapshot.ready) {
            state.update { it.copy(status = "yt-dlp 還沒準備好") }
            return
        }
        state.update { it.copy(parsing = true, previewError = null, status = "正在解析網址…") }
        scope.launch(Dispatchers.IO) {
            try {
                val response = runYtDlp(
                    YtDlpClient.infoRequest(url, cookiesIfAny(context), snapshot.playlist),
                    "parse-${UUID.randomUUID()}"
                ) { _, _, _ -> }
                val preview = JsonVideo.preview(response.out, url)
                state.update {
                    it.copy(parsing = false, preview = preview, previewError = null, status = "已解析：${preview.title}")
                }
            } catch (error: Exception) {
                val message = FailureText.summarize(error.message)
                state.update { it.copy(parsing = false, previewError = message, status = message) }
            }
        }
    }

    fun enqueueAndStart(context: Context) {
        val snapshot = state.value
        if (!snapshot.ready) {
            state.update { it.copy(status = "yt-dlp 還沒準備好") }
            prepare(context)
            return
        }
        val urls = DownloadPolicy.splitUrls(snapshot.urlText)
        if (urls.isEmpty() && snapshot.tasks.none { it.status == "queued" }) {
            state.update { it.copy(status = "請貼上影片網址") }
            return
        }
        val existing = snapshot.tasks.filter { it.status == "queued" || it.status == "running" }.map { it.url }.toSet()
        val added = urls.filter { it !in existing }
        if (added.isNotEmpty()) {
            val tasks = added.map { url ->
                val preview = snapshot.preview
                val title = if (preview != null && (preview.url == url || snapshot.urlText.contains(url))) preview.title else url
                DownloadTask(
                    id = UUID.randomUUID().toString(),
                    url = url,
                    title = title,
                    format = snapshot.format,
                    quality = snapshot.quality,
                    subtitles = snapshot.subtitles,
                    playlist = snapshot.playlist,
                    status = "queued",
                    detail = "排隊中"
                )
            }
            state.update { it.copy(tasks = tasks + it.tasks, status = "已加入 ${tasks.size} 個轉換") }
            persist(force = true)
        }
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, DownloadService::class.java)
        )
    }

    fun useSearchHit(hit: SearchHit) {
        state.update { it.copy(urlText = hit.url, previewError = null, status = "已帶入網址") }
    }

    fun favorite(hitTitle: String, url: String, platform: String) {
        state.update { current ->
            val without = current.favorites.filterNot { it.url == url }
            current.copy(
                favorites = listOf(Favorite(hitTitle, url, platform, System.currentTimeMillis())) + without,
                status = "已加入我的最愛"
            )
        }
        persist(force = true)
    }

    fun removeFavorite(url: String) {
        state.update { it.copy(favorites = it.favorites.filterNot { fav -> fav.url == url }) }
        persist(force = true)
    }

    fun removeRecent(query: String, platform: String) {
        state.update {
            it.copy(recentSearches = it.recentSearches.filterNot { item -> item.query == query && item.platform == platform })
        }
        persist(force = true)
    }

    fun clearRecent() {
        state.update { it.copy(recentSearches = emptyList()) }
        persist(force = true)
    }

    fun search() {
        val snapshot = state.value
        val query = snapshot.searchQuery.trim()
        if (query.isEmpty()) {
            state.update { it.copy(searchStatus = "請輸入搜尋關鍵字", status = "請輸入搜尋關鍵字") }
            return
        }
        if (!snapshot.ready) {
            state.update { it.copy(searchStatus = "yt-dlp 還沒準備好") }
            return
        }
        rememberSearch(query, snapshot.searchPlatform)
        state.update { it.copy(searching = true, searchStatus = "搜尋中…", searchResults = emptyList(), status = "正在搜尋：$query") }
        val platform = snapshot.searchPlatform
        val limit = snapshot.searchLimit
        scope.launch(Dispatchers.IO) {
            try {
                val youtube = if (platform == "youtube" || platform == "both") {
                    searchYouTube(query, limit)
                } else {
                    emptyList()
                }
                val bilibili = if (platform == "bilibili" || platform == "both") {
                    searchBilibili(query, limit)
                } else {
                    emptyList()
                }
                var dropped = 0
                fun keep(items: List<SearchHit>): List<SearchHit> {
                    val kept = ArrayList<SearchHit>()
                    for (item in items) {
                        if (!SearchRank.usable(item.url, item.title)) continue
                        if (!SearchRank.matchesKeyword(item.title, item.description, query)) {
                            dropped++
                            continue
                        }
                        if (kept.size < limit) kept += item
                    }
                    return kept
                }
                val ordered = SearchRank.interleave(keep(youtube), keep(bilibili))
                val ytCount = ordered.count { it.platform == "YouTube" }
                val biliCount = ordered.count { it.platform == "Bilibili" }
                val status = if (ordered.isEmpty()) {
                    if (dropped > 0) "沒有標題或介紹含「$query」的影片（已過濾 $dropped 筆）" else "找不到相關影片"
                } else {
                    val hint = if (dropped > 0) "，已過濾 $dropped 筆" else ""
                    "找到 ${ordered.size} 筆（YouTube $ytCount · Bilibili $biliCount$hint）"
                }
                state.update { it.copy(searching = false, searchResults = ordered, searchStatus = status, status = status) }
            } catch (error: Exception) {
                val message = FailureText.summarize(error.message)
                state.update { it.copy(searching = false, searchStatus = message, status = message) }
            }
        }
    }

    fun cancelCurrent() {
        val id = currentTaskId ?: return
        cancel(id)
    }

    fun cancel(taskId: String) {
        val task = state.value.tasks.firstOrNull { it.id == taskId } ?: return
        if (task.status == "queued") {
            editTask(taskId, force = true) { it.copy(status = "canceled", detail = "已取消", progress = 0f) }
            return
        }
        if (currentTaskId == taskId) {
            val process = currentProcessId
            if (process != null) {
                runCatching { YoutubeDL.getInstance().destroyProcessById(process) }
            }
        }
    }

    fun removeTask(taskId: String) {
        state.update { it.copy(tasks = it.tasks.filterNot { task -> task.id == taskId }) }
        persist(force = true)
    }

    fun openOutput(context: Context, task: DownloadTask) {
        val uri = outputUri(context, task) ?: return
        val name = task.outputName ?: task.outputUri.orEmpty()
        val mime = MediaStoreExport.mimeFor(name.ifBlank { if (task.format == "MP3") "a.mp3" else "a.mp4" })
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { state.update { current -> current.copy(status = "沒有可以開啟這個檔案的應用程式") } }
    }

    fun shareOutput(context: Context, task: DownloadTask) {
        val uri = outputUri(context, task) ?: return
        val name = task.outputName ?: "media"
        val intent = Intent(Intent.ACTION_SEND)
            .setType(MediaStoreExport.mimeFor(name))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openPage(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun onServiceStart(downloadService: DownloadService) {
        service = downloadService
        if (!draining.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            val wake = downloadService.acquireWakeLock()
            try {
                prepareBlocking(downloadService.applicationContext)
                while (true) {
                    val next = state.value.tasks.firstOrNull { it.status == "queued" } ?: break
                    runTask(downloadService.applicationContext, next)
                }
            } finally {
                downloadService.releaseWakeLock(wake)
                draining.set(false)
                downloadService.stopForegroundAndSelf()
                if (state.value.tasks.any { it.status == "queued" }) {
                    ContextCompat.startForegroundService(
                        downloadService.applicationContext,
                        Intent(downloadService.applicationContext, DownloadService::class.java)
                    )
                }
            }
        }
    }

    private suspend fun prepareBlocking(context: Context) {
        if (prepared) return
        prepareLock.withLock {
            if (prepared) return
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            if (shouldUpdate(context)) {
                runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(KEY_UPDATE, LocalDate.now().toString()).apply()
                }
            }
            prepared = true
            val version = YoutubeDL.getInstance().versionName(context)
            state.update { it.copy(preparing = false, ready = true, ytdlpVersion = version) }
        }
    }

    private suspend fun runTask(context: Context, task: DownloadTask) {
        currentTaskId = task.id
        editTask(task.id, force = true) { it.copy(status = "running", progress = 0f, detail = "開始轉換", error = null) }
        service?.updateNotification(task.title, 0, true)
        val dir = workDir(context)
        val startedAt = System.currentTimeMillis()
        try {
            try {
                downloadOnce(context, task, fallback = false)
            } catch (error: YoutubeDLException) {
                if (DownloadPolicy.isYouTubeUrl(task.url) && DownloadPolicy.looksLikeHttpForbidden(error.message)) {
                    editTask(task.id, force = true) {
                        it.copy(detail = "相容模式重試", progress = 0f)
                    }
                    state.update { it.copy(status = DownloadPolicy.forbiddenRetryHint()) }
                    downloadOnce(context, task, fallback = true)
                } else {
                    throw error
                }
            }
            if (task.subtitles) {
                editTask(task.id, force = true) { it.copy(detail = "下載字幕", progress = it.progress.coerceAtLeast(92f)) }
                downloadSubtitles(context, task)
            }
            var produced = MediaFinisher.producedFiles(dir, startedAt)
            if (task.subtitles) {
                val extras = MediaFinisher.pairSubtitles(context, produced, task.format)
                produced = (produced + extras).distinctBy { it.absolutePath }
            }
            val saved = ArrayList<MediaStoreExport.SavedFile>()
            for (file in produced) {
                val exported = MediaStoreExport.export(context, file)
                if (exported != null) {
                    saved += exported
                    file.delete()
                }
            }
            val primary = saved.firstOrNull { it.displayName.endsWith(".mp4", true) || it.displayName.endsWith(".mp3", true) || it.displayName.endsWith(".m4a", true) }
                ?: saved.firstOrNull()
            if (primary == null && produced.isEmpty()) {
                fail(task.id, "轉換結束，但沒有找到輸出檔。")
                return
            }
            val today = LocalDate.now().toString()
            state.update { current ->
                val count = if (current.todayDate == today) current.todayCount + 1 else 1
                current.copy(todayCount = count, todayDate = today, status = "轉換完成")
            }
            editTask(task.id, force = true) {
                it.copy(
                    status = "done",
                    progress = 100f,
                    detail = if (saved.size > 1) "已存到下載資料夾（${saved.size} 個檔案）" else "已存到下載/影音轉換大師",
                    error = null,
                    outputName = primary?.displayName ?: produced.firstOrNull()?.name,
                    outputUri = primary?.uri?.toString()
                )
            }
        } catch (_: YoutubeDL.CanceledException) {
            editTask(task.id, force = true) { it.copy(status = "canceled", detail = "已取消") }
        } catch (error: Exception) {
            fail(task.id, FailureText.summarize(error.message))
        } finally {
            if (currentTaskId == task.id) {
                currentTaskId = null
                currentProcessId = null
            }
        }
    }

    private fun fail(taskId: String, message: String) {
        editTask(taskId, force = true) { it.copy(status = "failed", error = message, detail = "失敗") }
        state.update { it.copy(status = message) }
    }

    private suspend fun downloadOnce(context: Context, task: DownloadTask, fallback: Boolean) {
        val request = YtDlpClient.downloadRequest(
            url = task.url,
            outputDir = workDir(context),
            format = task.format,
            quality = task.quality,
            subtitles = false,
            playlist = task.playlist,
            cookies = cookiesIfAny(context),
            fallback = fallback
        )
        val processId = task.id + if (fallback) "-fb" else "-dl"
        currentProcessId = processId
        runYtDlp(request, processId) { progress, _, line ->
            onDownloadLine(task.id, progress, line)
        }
    }

    private suspend fun downloadSubtitles(context: Context, task: DownloadTask) {
        val dir = workDir(context)
        val languages = listOf("zh-Hant,zh-TW,zh-HK", "zh-Hans,zh-CN", "zh.*", "en")
        for (language in languages) {
            if (currentTaskId != task.id) return
            val before = dir.listFiles()?.map { it.name }?.toSet().orEmpty()
            val processId = task.id + "-sub-" + language.hashCode()
            currentProcessId = processId
            try {
                runYtDlp(
                    YtDlpClient.subtitleRequest(task.url, dir, task.playlist, cookiesIfAny(context), language),
                    processId
                ) { _, _, _ -> }
            } catch (_: YoutubeDLException) {
                // Captions are optional. A 429 on one language should not fail the media file.
            }
            val created = dir.listFiles()?.any { file ->
                file.name !in before && (file.extension.equals("srt", true) || file.extension.equals("vtt", true))
            } == true
            if (created) return
        }
    }

    private fun onDownloadLine(taskId: String, progress: Float, line: String) {
        val now = System.currentTimeMillis()
        val useful = line.substringAfterLast('\n').trim()
        val speed = Regex("""at\s+(\S+)""").find(useful)?.groupValues?.getOrNull(1)
        val destination = Regex("""(?i)Destination:\s*(.+)$""").find(useful)?.groupValues?.getOrNull(1)?.trim()
        val merged = Regex("""Merging formats into "([^"]+)"""").find(useful)?.groupValues?.getOrNull(1)
        val titleHint = destination ?: merged
        if (now - lastProgressUi < 250 && progress < 99f && titleHint == null) return
        lastProgressUi = now
        editTask(taskId, force = progress >= 99f) { task ->
            val title = titleHint?.let { File(it).nameWithoutExtension }?.takeIf { it.isNotBlank() } ?: task.title
            task.copy(
                title = if (task.title == task.url && title.isNotBlank()) title else task.title,
                progress = if (progress > 0f) progress.coerceIn(0f, 100f) else task.progress,
                detail = speed?.let { "$it" } ?: useful.take(80).ifBlank { task.detail }
            )
        }
        val shown = state.value.tasks.firstOrNull { it.id == taskId }
        service?.updateNotification(shown?.title ?: "正在轉換", (shown?.progress ?: progress).toInt(), progress <= 0f)
    }

    private suspend fun searchYouTube(query: String, limit: Int): List<SearchHit> {
        val fetch = (limit * 3).coerceIn(limit, 50)
        val response = runYtDlp(
            YtDlpClient.searchRequest("ytsearch$fetch:$query", bilibili = false),
            "search-yt-${UUID.randomUUID()}"
        ) { _, _, _ -> }
        return JsonVideo.searchHits(response.out, "YouTube")
    }

    private suspend fun searchBilibili(query: String, limit: Int): List<SearchHit> {
        val fetch = (limit * 3).coerceIn(limit, 50)
        val api = runCatching { bilibiliApi(query, fetch) }.getOrDefault(emptyList())
        if (api.isNotEmpty()) return api
        return try {
            val response = runYtDlp(
                YtDlpClient.searchRequest("bilisearch$fetch:$query", bilibili = true),
                "search-bili-${UUID.randomUUID()}"
            ) { _, _, _ -> }
            JsonVideo.searchHits(response.out, "Bilibili")
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun bilibiliApi(query: String, limit: Int): List<SearchHit> {
        val url = "https://api.bilibili.com/x/web-interface/search/type" +
            "?search_type=video&keyword=${Uri.encode(query)}&page=1&page_size=$limit&order=totalrank"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DownloadPolicy.USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh-TW;q=0.9,zh;q=0.8,en;q=0.7")
            .header("Referer", "https://search.bilibili.com")
            .header("Origin", "https://search.bilibili.com")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return emptyList()
            return JsonVideo.bilibiliApiHits(body, limit)
        }
    }

    private suspend fun runYtDlp(
        request: com.yausername.youtubedl_android.YoutubeDLRequest,
        processId: String,
        callback: (Float, Long, String) -> Unit
    ) = ytLock.withLock {
        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().execute(request, processId, false, callback)
        }
    }

    private fun rememberSearch(query: String, platform: String) {
        state.update { current ->
            val kept = current.recentSearches.filterNot {
                it.query.equals(query, true) && it.platform == platform
            }
            current.copy(
                recentSearches = listOf(RecentSearch(query, platform, System.currentTimeMillis())) + kept
            )
        }
        persist(force = true)
    }

    private fun editTask(id: String, force: Boolean, block: (DownloadTask) -> DownloadTask) {
        state.update { current ->
            current.copy(tasks = current.tasks.map { if (it.id == id) block(it) else it })
        }
        persist(force)
    }

    private fun persist(force: Boolean) {
        val context = appContext ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastPersist < 1_500) return
        lastPersist = now
        AppStore.save(context, state.value)
    }

    private fun shouldUpdate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UPDATE, "") != LocalDate.now().toString()
    }

    private fun cookiesIfAny(context: Context): File? {
        val file = cookiesFile(context.applicationContext)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    private fun cookiesFile(context: Context) = File(context.applicationContext.filesDir, "cookies.txt")

    private fun workDir(context: Context): File {
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "converted").apply { mkdirs() }
    }

    private fun outputUri(context: Context, task: DownloadTask): Uri? {
        val raw = task.outputUri
        if (!raw.isNullOrBlank()) return Uri.parse(raw)
        val name = task.outputName ?: return null
        val file = File(workDir(context), name)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
        return "cookies.txt"
    }

    private const val PREFS = "converter"
    private const val KEY_UPDATE = "ytdlpUpdateDay"
}
