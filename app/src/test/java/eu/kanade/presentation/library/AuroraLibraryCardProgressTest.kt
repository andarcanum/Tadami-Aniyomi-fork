package eu.kanade.presentation.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraLibraryCardProgressTest {

    @Test
    fun `anime progress percent resolves from seen and total counts`() {
        resolveAnimeLibraryCardProgressPercent(seenCount = 73, totalCount = 100) shouldBe 73
    }

    @Test
    fun `manga progress percent resolves from read and total counts`() {
        resolveMangaLibraryCardProgressPercent(readCount = 57, totalCount = 100) shouldBe 57
    }

    @Test
    fun `novel progress percent resolves from read and total counts`() {
        resolveNovelLibraryCardProgressPercent(readCount = 61, totalCount = 100) shouldBe 61
    }

    @Test
    fun `progress percent returns null when total is missing`() {
        resolveAnimeLibraryCardProgressPercent(seenCount = 5, totalCount = 0) shouldBe null
        resolveMangaLibraryCardProgressPercent(readCount = 5, totalCount = 0) shouldBe null
        resolveNovelLibraryCardProgressPercent(readCount = 5, totalCount = 0) shouldBe null
    }

    @Test
    fun `progress percent clamps into valid percentage range`() {
        resolveAnimeLibraryCardProgressPercent(seenCount = 120, totalCount = 100) shouldBe 100
        resolveMangaLibraryCardProgressPercent(readCount = -4, totalCount = 100) shouldBe 0
    }
}
