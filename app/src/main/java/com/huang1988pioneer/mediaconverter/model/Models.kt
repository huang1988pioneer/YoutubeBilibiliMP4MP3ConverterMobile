package com.huang1988pioneer.mediaconverter.model

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val format: String,
    val quality: String,
    val subtitles: Boolean,
    val playlist: Boolean,
    val status: String,
    val progress: Float = 0f,
    val detail: String = "",
    val error: String? = null,
    val outputName: String? = null,
    val outputUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class VideoPreview(
    val title: String,
    val url: String,
    val uploader: String?,
    val durationSeconds: Double?,
    val viewCount: Long?,
    val uploadDate: String?,
    val description: String?,
    val accessLabel: String?,
    val thumbnailUrl: String?
)

data class SearchHit(
    val platform: String,
    val title: String,
    val url: String,
    val uploader: String?,
    val durationSeconds: Double?,
    val viewCount: Long?,
    val description: String?
)

data class RecentSearch(
    val query: String,
    val platform: String,
    val at: Long
)

data class Favorite(
    val title: String,
    val url: String,
    val platform: String,
    val at: Long
)

data class UiState(
    val preparing: Boolean = true,
    val ready: Boolean = false,
    val prepareError: String? = null,
    val ytdlpVersion: String? = null,
    val urlText: String = "",
    val format: String = "MP4",
    val quality: String = "1080P",
    val subtitles: Boolean = false,
    val playlist: Boolean = false,
    val cookiesLabel: String? = null,
    val preview: VideoPreview? = null,
    val previewError: String? = null,
    val parsing: Boolean = false,
    val tasks: List<DownloadTask> = emptyList(),
    val searchQuery: String = "",
    val searchPlatform: String = "both",
    val searchLimit: Int = 8,
    val searching: Boolean = false,
    val searchStatus: String = "",
    val searchResults: List<SearchHit> = emptyList(),
    val recentSearches: List<RecentSearch> = emptyList(),
    val favorites: List<Favorite> = emptyList(),
    val status: String = "準備中…",
    val todayCount: Int = 0,
    val todayDate: String = ""
)
