package com.huang1988pioneer.mediaconverter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.huang1988pioneer.mediaconverter.Session
import com.huang1988pioneer.mediaconverter.model.DownloadTask
import com.huang1988pioneer.mediaconverter.model.SearchHit
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by Session.state.collectAsState()
    var openSearch by rememberSaveable { mutableStateOf(false) }
    var openActive by rememberSaveable { mutableStateOf(true) }
    var openDone by rememberSaveable { mutableStateOf(false) }
    var openFav by rememberSaveable { mutableStateOf(false) }

    val cookiesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Session.importCookies(context, uri)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Session.enqueueAndStart(context)
    }

    fun startConvert() {
        val needsNotification = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotification) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Session.enqueueAndStart(context)
        }
    }

    Scaffold(containerColor = BgApp) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("影音轉換大師", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                buildString {
                    append("v1.5.4  ·  Android")
                    state.ytdlpVersion?.let { append("  ·  yt-dlp $it") }
                    if (state.todayCount > 0) append("  ·  今日 ${state.todayCount}")
                },
                color = TextSecondary,
                fontSize = 13.sp
            )
            Text(state.status, color = if (state.prepareError != null) Danger else TextSecondary, fontSize = 14.sp)
            if (state.prepareError != null) {
                OutlinedButton(onClick = { Session.prepare(context, manualUpdate = true) }) {
                    Text("重試準備")
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.urlText,
                        onValueChange = Session::setUrl,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text("影片網址") },
                        placeholder = { Text("貼上 YouTube 或 Bilibili 網址，可多行") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val text = clipboard.getText()?.text.orEmpty()
                            if (text.isNotBlank()) Session.setUrl(text)
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "貼上")
                            Spacer(Modifier.width(6.dp))
                            Text("貼上")
                        }
                        OutlinedButton(onClick = { Session.parse(context) }, enabled = !state.parsing && state.ready) {
                            Text(if (state.parsing) "解析中…" else "解析網址")
                        }
                    }
                    state.preview?.let { preview ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(preview.title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(
                                listOfNotNull(
                                    preview.uploader,
                                    preview.durationSeconds?.let { formatDuration(it) },
                                    preview.viewCount?.let { "${formatCount(it)} 次觀看" },
                                    preview.uploadDate,
                                    preview.accessLabel
                                ).joinToString("  ·  "),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Row {
                                TextButton(onClick = { Session.openPage(context, preview.url) }) { Text("開啟原片") }
                                TextButton(onClick = { Session.favorite(preview.title, preview.url, platformOf(preview.url)) }) {
                                    Text("加入最愛")
                                }
                            }
                        }
                    }
                    state.previewError?.let { Text(it, color = Danger, fontSize = 13.sp) }

                    Text("輸出格式", color = TextSecondary, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.format == "MP3",
                            onClick = { Session.setFormat("MP3", state.quality) },
                            label = { Text("MP3") }
                        )
                        listOf("480P", "720P", "1080P", "4K").forEach { quality ->
                            FilterChip(
                                selected = state.format == "MP4" && state.quality == quality,
                                onClick = { Session.setFormat("MP4", quality) },
                                label = { Text(quality) }
                            )
                        }
                    }
                    ToggleRow("字幕搭配", "中文優先 .srt；MP4 內嵌，MP3 另存 .lrc", state.subtitles, Session::setSubtitles)
                    ToggleRow("播放清單", "下載整份清單，預設只轉單一影片", state.playlist, Session::setPlaylist)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Cookies", color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text(state.cookiesLabel ?: "未設定。會員或登入限制的影片才需要。", color = TextMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { cookiesPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }) {
                            Text("匯入")
                        }
                        TextButton(onClick = { Session.clearCookies(context) }, enabled = state.cookiesLabel != null) {
                            Text("清除")
                        }
                    }
                    Button(
                        onClick = { startConvert() },
                        enabled = state.ready && !state.preparing,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(if (state.preparing) "正在準備工具…" else "開始轉換")
                    }
                    TextButton(onClick = { Session.prepare(context, manualUpdate = true) }, enabled = !state.preparing) {
                        Text("更新 yt-dlp")
                    }
                }
            }

            SectionCard("搜尋影片", openSearch, { openSearch = !openSearch }) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = Session::setSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("關鍵字") }
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("both" to "兩者", "youtube" to "YouTube", "bilibili" to "Bilibili").forEach { (id, label) ->
                        FilterChip(
                            selected = state.searchPlatform == id,
                            onClick = { Session.setSearchPlatform(id) },
                            label = { Text(label) }
                        )
                    }
                    listOf(5, 8, 12).forEach { count ->
                        FilterChip(
                            selected = state.searchLimit == count,
                            onClick = { Session.setSearchLimit(count) },
                            label = { Text("$count 筆") }
                        )
                    }
                }
                Button(onClick = { Session.search() }, enabled = state.ready && !state.searching) {
                    Text(if (state.searching) "搜尋中…" else "搜尋")
                }
                if (state.searchStatus.isNotBlank()) {
                    Text(state.searchStatus, color = TextSecondary, fontSize = 13.sp)
                }
                if (state.recentSearches.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("最近搜尋", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = Session::clearRecent) { Text("清除全部") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.recentSearches.forEach { recent ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    Session.setSearchQuery(recent.query)
                                    Session.setSearchPlatform(recent.platform)
                                    Session.search()
                                },
                                label = { Text(recent.query) },
                                trailingIcon = {
                                    IconButton(onClick = { Session.removeRecent(recent.query, recent.platform) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "移除", tint = TextMuted)
                                    }
                                }
                            )
                        }
                    }
                }
                state.searchResults.forEach { hit ->
                    SearchRow(hit)
                }
            }

            val active = state.tasks.filter { it.status == "queued" || it.status == "running" }
            SectionCard("下載中（${active.size}）", openActive, { openActive = !openActive }) {
                if (active.isEmpty()) {
                    Text("還沒有進行中的轉換", color = TextMuted, fontSize = 13.sp)
                }
                active.forEach { task -> TaskRow(task) }
            }

            val done = state.tasks.filter { it.status == "done" || it.status == "failed" || it.status == "canceled" }
            SectionCard("已完成（${done.size}）", openDone, { openDone = !openDone }) {
                if (done.isEmpty()) {
                    Text("完成的檔案會出現在這裡，也會存到「下載/影音轉換大師」。", color = TextMuted, fontSize = 13.sp)
                }
                done.forEach { task -> TaskRow(task) }
            }

            SectionCard("我的最愛（${state.favorites.size}）", openFav, { openFav = !openFav }) {
                if (state.favorites.isEmpty()) {
                    Text("解析或搜尋後可以加入最愛。", color = TextMuted, fontSize = 13.sp)
                }
                state.favorites.forEach { fav ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(fav.title, fontWeight = FontWeight.Medium)
                        Text(fav.url, color = TextMuted, fontSize = 12.sp, maxLines = 1)
                        Row {
                            TextButton(onClick = { Session.setUrl(fav.url) }) { Text("使用網址") }
                            TextButton(onClick = {
                                Session.setUrl(fav.url)
                                Session.parse(context)
                            }) { Text("解析") }
                            TextButton(onClick = { Session.removeFavorite(fav.url) }) { Text("移除") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionCard(title: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = TextPrimary)
                IconButton(onClick = onToggle) {
                    Icon(
                        if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (open) "收合" else "展開"
                    )
                }
            }
            if (open) content()
        }
    }
}

@Composable
private fun SearchRow(hit: SearchHit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            hit.platform,
            color = if (hit.platform == "Bilibili") Pink else Blue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(hit.title, fontWeight = FontWeight.Medium, color = TextPrimary)
        Text(
            listOfNotNull(
                hit.uploader,
                hit.durationSeconds?.let { formatDuration(it) },
                hit.viewCount?.let { "${formatCount(it)} 次觀看" }
            ).joinToString("  ·  "),
            color = TextSecondary,
            fontSize = 12.sp
        )
        Row {
            TextButton(onClick = { Session.useSearchHit(hit) }) { Text("使用網址") }
            TextButton(onClick = {
                Session.useSearchHit(hit)
                Session.parse(context)
            }) { Text("解析") }
            TextButton(onClick = {
                Session.setUrl(hit.url)
                Session.enqueueAndStart(context)
            }) { Text("轉換") }
            IconButton(onClick = { Session.favorite(hit.title, hit.url, hit.platform) }) {
                Icon(Icons.Filled.Star, contentDescription = "加入最愛", tint = Pink)
            }
        }
    }
}

@Composable
private fun TaskRow(task: DownloadTask) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(task.title, fontWeight = FontWeight.Medium, maxLines = 2)
        Text(
            buildString {
                append(if (task.format == "MP3") "MP3" else "MP4 ${task.quality}")
                append("  ·  ")
                append(statusLabel(task.status))
                if (task.detail.isNotBlank()) {
                    append("  ·  ")
                    append(task.detail)
                }
            },
            color = TextSecondary,
            fontSize = 12.sp
        )
        if (task.status == "running" || task.status == "queued") {
            LinearProgressIndicator(
                progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        task.error?.let { Text(it, color = Danger, fontSize = 12.sp) }
        Row {
            if (task.status == "queued" || task.status == "running") {
                TextButton(onClick = { Session.cancel(task.id) }) { Text("取消") }
            }
            if (task.status == "done") {
                TextButton(onClick = { Session.openOutput(context, task) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Text("開啟")
                }
                TextButton(onClick = { Session.shareOutput(context, task) }) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("分享")
                }
            }
            if (task.status != "running") {
                TextButton(onClick = { Session.removeTask(task.id) }) { Text("移除") }
            }
        }
    }
}

private fun statusLabel(status: String) = when (status) {
    "queued" -> "排隊"
    "running" -> "轉換中"
    "done" -> "完成"
    "failed" -> "失敗"
    "canceled" -> "已取消"
    else -> status
}

private fun platformOf(url: String) = if (url.contains("bilibili.com", true) || url.contains("b23.tv", true)) "Bilibili" else "YouTube"

private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    else String.format(Locale.US, "%d:%02d", minutes, secs)
}

private fun formatCount(value: Long): String {
    return when {
        value >= 100_000_000 -> String.format(Locale.US, "%.1f億", value / 100_000_000.0)
        value >= 10_000 -> String.format(Locale.US, "%.1f萬", value / 10_000.0)
        else -> String.format(Locale.US, "%,d", value)
    }
}
