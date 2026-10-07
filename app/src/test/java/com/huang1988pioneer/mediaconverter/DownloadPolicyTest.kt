package com.huang1988pioneer.mediaconverter

import com.huang1988pioneer.mediaconverter.engine.DownloadPolicy
import com.huang1988pioneer.mediaconverter.engine.FailureText
import com.huang1988pioneer.mediaconverter.engine.SearchRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {
    @Test
    fun youtubeUsesFewerFragmentsThanBilibili() {
        assertEquals(4, DownloadPolicy.concurrentFragments("https://www.youtube.com/watch?v=abc"))
        assertEquals(16, DownloadPolicy.concurrentFragments("https://www.bilibili.com/video/BV1xx/"))
        assertEquals(8, DownloadPolicy.concurrentFragments("https://vimeo.com/1"))
    }

    @Test
    fun mp4SelectorCapsHeightAndPrefersAvc() {
        val selector = DownloadPolicy.mp4FormatSelector("1080P")
        assertTrue(selector.startsWith("bestvideo[height<=1080][vcodec^=avc1]"))
        assertEquals(2160, DownloadPolicy.parseMaxHeight("4K"))
        assertEquals("720P", DownloadPolicy.normalizeQuality("720"))
    }

    @Test
    fun stripsTrackingAndPlaylistWhenSingleVideo() {
        val cleaned = DownloadPolicy.normalizeMediaUrl(
            "https://www.youtube.com/watch?v=abc&list=PLxx&si=track",
            preservePlaylistParams = false
        )
        assertTrue(cleaned.contains("v=abc"))
        assertFalse(cleaned.contains("list="))
        assertFalse(cleaned.contains("si="))
        val playlist = DownloadPolicy.normalizeMediaUrl(
            "https://www.youtube.com/watch?v=abc&list=PLxx",
            preservePlaylistParams = true
        )
        assertTrue(playlist.contains("list=PLxx"))
    }

    @Test
    fun splitsSeveralUrls() {
        val urls = DownloadPolicy.splitUrls("https://youtu.be/a\nhttps://www.bilibili.com/video/BV1/\nnot-a-url")
        assertEquals(2, urls.size)
    }

    @Test
    fun searchKeepsOnlyFullKeyword() {
        assertTrue(SearchRank.matchesKeyword("煙とブルー live", "desc", "煙とブルー"))
        assertFalse(SearchRank.matchesKeyword("只提到煙", "藍色天空", "煙とブルー"))
        val ordered = SearchRank.interleave(listOf("y1", "y2"), listOf("b1"))
        assertEquals(listOf("y1", "b1", "y2"), ordered)
    }

    @Test
    fun forbiddenLogBecomesCookieHint() {
        val text = FailureText.summarize("ERROR: unable to download video data: HTTP Error 403: Forbidden")
        assertTrue(text.contains("403"))
        assertTrue(text.contains("cookies"))
    }
}
