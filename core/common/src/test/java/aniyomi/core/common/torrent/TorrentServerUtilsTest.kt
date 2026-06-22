package aniyomi.core.common.torrent

import aniyomi.core.common.torrent.model.FileStats
import aniyomi.core.common.torrent.model.Torrent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TorrentServerUtilsTest {

    @Test
    fun `buildTorrentPlayLink encodes file name with spaces and cyrillic`() {
        val torrent = Torrent(
            title = "fallback title",
            hash = "abc123",
            fileStats = listOf(
                FileStats(
                    id = 2,
                    path = "/storage/emulated/0/Видео файл.mkv",
                    length = 42L,
                ),
            ),
        )

        val result = TorrentServerUtils.buildTorrentPlayLink("http://127.0.0.1:8090", torrent, 2)

        result shouldBe "http://127.0.0.1:8090/stream/%D0%92%D0%B8%D0%B4%D0%B5%D0%BE+%D1%84%D0%B0%D0%B9%D0%BB.mkv?link=abc123&index=2&play"
    }

    @Test
    fun `buildTorrentPlayLink falls back to title when file index is missing`() {
        val torrent = Torrent(
            title = "Anime Episode 01",
            hash = "deadbeef",
            fileStats = listOf(
                FileStats(id = 1, path = "/tmp/other.mkv", length = 1L),
            ),
        )

        val result = TorrentServerUtils.buildTorrentPlayLink("http://127.0.0.1:53823", torrent, 9)

        result shouldBe "http://127.0.0.1:53823/stream/Anime+Episode+01?link=deadbeef&index=9&play"
    }
}
