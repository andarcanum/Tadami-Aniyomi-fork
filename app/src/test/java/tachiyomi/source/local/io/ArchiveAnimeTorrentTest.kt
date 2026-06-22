package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ArchiveAnimeTorrentTest {

    @Test
    fun `anime archive supports torrent files`() {
        ArchiveAnime.isSupported(fileNamed("episode.torrent")) shouldBe true
    }

    @Test
    fun `anime archive still supports regular video files`() {
        ArchiveAnime.isSupported(fileNamed("episode.mkv")) shouldBe true
        ArchiveAnime.isSupported(fileNamed("episode.mp4")) shouldBe true
    }

    @Test
    fun `anime archive rejects unrelated files`() {
        ArchiveAnime.isSupported(fileNamed("episode.zip")) shouldBe false
    }

    private fun fileNamed(name: String): UniFile {
        return mockk<UniFile> {
            every { this@mockk.name } returns name
        }
    }
}
