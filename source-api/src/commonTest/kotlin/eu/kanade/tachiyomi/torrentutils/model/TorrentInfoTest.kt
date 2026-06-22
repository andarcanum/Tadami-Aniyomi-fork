package eu.kanade.tachiyomi.torrentutils.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorrentInfoTest {

    @Test
    fun `torrent file magnet URI includes hash index and encoded trackers`() {
        val file = TorrentFile(
            path = "Episode 01.mkv",
            indexFile = 2,
            size = 123L,
            torrentHash = "abcdef",
            trackers = listOf("udp://tracker.example:6969/announce"),
        )

        val magnet = file.toMagnetURI()

        assertTrue(magnet.startsWith("magnet:?xt=urn:btih:abcdef"))
        assertTrue(magnet.contains("&index=2"))
        assertTrue(magnet.contains("udp%3A%2F%2Ftracker.example%3A6969%2Fannounce"))
    }

    @Test
    fun `setTrackers returns copy preserving torrent metadata`() {
        val info = TorrentInfo(
            title = "Series",
            files = listOf(
                TorrentFile(
                    path = "Episode 01.mkv",
                    indexFile = 1,
                    size = 456L,
                    torrentHash = "abcdef",
                ),
            ),
            hash = "abcdef",
            size = 456L,
        )

        val result = info.setTrackers(listOf("udp://tracker.example:6969/announce"))

        assertEquals("Series", result.title)
        assertEquals("abcdef", result.hash)
        assertEquals(456L, result.size)
        assertEquals(listOf("udp://tracker.example:6969/announce"), result.trackers)
        assertEquals(1, result.files.single().indexFile)
    }
}
