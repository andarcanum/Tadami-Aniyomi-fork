package eu.kanade.tachiyomi.ui.player.torrent

import android.content.ContentResolver
import android.net.Uri
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tachiyomi.core.common.preference.InMemoryPreferenceStore

@RunWith(RobolectricTestRunner::class)
class TorrentPlaybackResolverTest {

    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val preferences = TorrentPreferences(InMemoryPreferenceStore())
    private val api = mockk<TorrentServerApi>(relaxed = true)
    private val utils = mockk<TorrentServerUtils>(relaxed = true)

    private val resolver = TorrentPlaybackResolver(
        contentResolver = contentResolver,
        torrentPreferences = preferences,
        torrentServerApi = api,
        torrentServerUtils = utils,
    )

    @Test
    fun `detects magnet links`() {
        resolver.isTorrentLikeUrl("magnet:?xt=urn:btih:abc") shouldBe true
    }

    @Test
    fun `detects torrent URLs with query params`() {
        resolver.isTorrentLikeUrl("https://example.org/file.torrent?token=abc") shouldBe true
    }

    @Test
    fun `does not detect ordinary http video`() {
        resolver.isTorrentLikeUrl("https://example.org/video.mp4") shouldBe false
    }

    @Test
    fun `detects existing TorrServer stream URL by host and path`() {
        resolver.isTorrServerUrl("http://127.0.0.1:53219/stream/Episode.mkv?link=abc&index=2&play") shouldBe true
        resolver.isTorrServerUrl("http://localhost:53219/stream/Episode.mkv?link=abc&index=2&play") shouldBe true
    }

    @Test
    fun `rejects localhost URLs that are not TorrServer streams`() {
        resolver.isTorrServerUrl("http://127.0.0.1:53219/echo") shouldBe false
    }

    @Test
    fun `extracts magnet index and falls back to zero`() {
        resolver.extractMagnetIndex("magnet:?xt=urn:btih:abc&index=3") shouldBe 3
        resolver.extractMagnetIndex("magnet:?xt=urn:btih:abc&index=bad") shouldBe 0
        resolver.extractMagnetIndex("magnet:?xt=urn:btih:abc") shouldBe 0
    }

    @Test
    fun `disabled preference prevents enabled torrent URL`() {
        resolver.isEnabledTorrentUrl("magnet:?xt=urn:btih:abc") shouldBe false
    }

    @Test
    fun `content uri with torrent mime type is detected`() {
        val uri = Uri.parse("content://downloads/my-file")
        every { contentResolver.getType(uri) } returns "application/x-bittorrent"

        resolver.isTorrentLikeUrl(uri.toString()) shouldBe true
    }
}
