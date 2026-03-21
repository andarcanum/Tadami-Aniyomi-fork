package eu.kanade.tachiyomi.data.coil

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NovelCoverFetcherTest {

    @Test
    fun `buildNovelCoverRequest adds plugin image headers and keeps site origin fallback`() {
        val request = buildNovelCoverRequest(
            url = "https://novel.tl/images/cover.jpg",
            siteUrl = "https://novel.tl/",
            pluginHeaders = mapOf(
                "Accept" to "image/webp,image/*",
                "Referer" to "https://cdn.example/plugin/",
            ),
            readFromNetwork = true,
        )

        assertEquals("image/webp,image/*", request.header("Accept"))
        assertEquals("https://cdn.example/plugin/", request.header("Referer"))
        assertEquals("https://novel.tl", request.header("Origin"))
    }

    @Test
    fun `buildNovelCoverRequest adds referer and origin from site url`() {
        val request = buildNovelCoverRequest(
            url = "https://novel.tl/images/cover.jpg",
            siteUrl = "https://novel.tl/",
            readFromNetwork = true,
        )

        assertEquals("https://novel.tl/", request.header("Referer"))
        assertEquals("https://novel.tl", request.header("Origin"))
    }

    @Test
    fun `buildNovelCoverRequest skips referer and origin when site is missing`() {
        val request = buildNovelCoverRequest(
            url = "https://novel.tl/images/cover.jpg",
            siteUrl = "   ",
            readFromNetwork = true,
        )

        assertNull(request.header("Referer"))
        assertNull(request.header("Origin"))
    }

    @Test
    fun `buildNovelCoverRequest sets cache control based on policy`() {
        val networkRequest = buildNovelCoverRequest(
            url = "https://novel.tl/images/cover.jpg",
            siteUrl = "https://novel.tl",
            readFromNetwork = true,
        )
        val offlineRequest = buildNovelCoverRequest(
            url = "https://novel.tl/images/cover.jpg",
            siteUrl = "https://novel.tl",
            readFromNetwork = false,
        )

        assertEquals("no-store", networkRequest.header("Cache-Control"))
        assertEquals("no-cache, only-if-cached", offlineRequest.header("Cache-Control"))
    }
}
