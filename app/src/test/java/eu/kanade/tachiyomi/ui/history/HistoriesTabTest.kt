package eu.kanade.tachiyomi.ui.history

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Date

class HistoriesTabTest {

    @Test
    fun `resolve latest history tab returns anime when anime is newest`() {
        resolveLatestHistoryContentTab(
            animeLastSeenAt = Date(3_000),
            mangaLastReadAt = Date(2_000),
            novelLastReadAt = Date(1_000),
        ) shouldBe HistoryContentTab.ANIME
    }

    @Test
    fun `resolve latest history tab returns manga when manga is newest`() {
        resolveLatestHistoryContentTab(
            animeLastSeenAt = Date(1_000),
            mangaLastReadAt = Date(3_000),
            novelLastReadAt = Date(2_000),
        ) shouldBe HistoryContentTab.MANGA
    }

    @Test
    fun `resolve latest history tab returns novel when novel is newest`() {
        resolveLatestHistoryContentTab(
            animeLastSeenAt = Date(1_000),
            mangaLastReadAt = Date(2_000),
            novelLastReadAt = Date(3_000),
        ) shouldBe HistoryContentTab.NOVEL
    }

    @Test
    fun `resolve latest history tab returns null when all values are missing`() {
        resolveLatestHistoryContentTab(
            animeLastSeenAt = null,
            mangaLastReadAt = null,
            novelLastReadAt = null,
        ) shouldBe null
    }
}
