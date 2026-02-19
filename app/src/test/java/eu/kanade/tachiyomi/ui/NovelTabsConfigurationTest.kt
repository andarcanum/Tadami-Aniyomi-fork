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
        historyContentTabs() shouldBe listOf(
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
