package eu.kanade.tachiyomi.ui

import eu.kanade.tachiyomi.ui.download.DownloadQueueTab
import eu.kanade.tachiyomi.ui.download.downloadQueueTabs
import eu.kanade.tachiyomi.ui.history.HistoryContentTab
import eu.kanade.tachiyomi.ui.history.historyContentTabs
import eu.kanade.tachiyomi.ui.stats.StatsContentTab
import eu.kanade.tachiyomi.ui.stats.statsContentTabs
import eu.kanade.tachiyomi.ui.storage.StorageContentTab
import eu.kanade.tachiyomi.ui.storage.storageContentTabs
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelTabsConfigurationTest {

    @Test
    fun `history tabs include novel tab in expected order`() {
        historyContentTabs(true, true, true) shouldBe listOf(
            HistoryContentTab.ANIME,
            HistoryContentTab.MANGA,
            HistoryContentTab.NOVEL,
        )
    }

    @Test
    fun `download tabs include novel tab in expected order`() {
        downloadQueueTabs() shouldBe listOf(
            DownloadQueueTab.ANIME,
            DownloadQueueTab.MANGA,
            DownloadQueueTab.NOVEL,
        )
    }

    @Test
    fun `download tabs filter correctly when manga only is selected`() {
        downloadQueueTabs(
            showAnimeSection = false,
            showMangaSection = true,
            showNovelSection = false,
        ) shouldBe listOf(
            DownloadQueueTab.MANGA,
        )
    }

    @Test
    fun `download tabs filter correctly with partial section selection`() {
        downloadQueueTabs(
            showAnimeSection = true,
            showMangaSection = false,
            showNovelSection = true,
        ) shouldBe listOf(
            DownloadQueueTab.ANIME,
            DownloadQueueTab.NOVEL,
        )
    }

    @Test
    fun `download tabs fallback to all tabs when all sections disabled`() {
        downloadQueueTabs(
            showAnimeSection = false,
            showMangaSection = false,
            showNovelSection = false,
        ) shouldBe listOf(
            DownloadQueueTab.ANIME,
            DownloadQueueTab.MANGA,
            DownloadQueueTab.NOVEL,
        )
    }

    @Test
    fun `stats tabs include novel tab in expected order`() {
        statsContentTabs() shouldBe listOf(
            StatsContentTab.ANIME,
            StatsContentTab.MANGA,
            StatsContentTab.NOVEL,
        )
    }

    @Test
    fun `storage tabs include novel tab in expected order`() {
        storageContentTabs() shouldBe listOf(
            StorageContentTab.ANIME,
            StorageContentTab.MANGA,
            StorageContentTab.NOVEL,
        )
    }
}
