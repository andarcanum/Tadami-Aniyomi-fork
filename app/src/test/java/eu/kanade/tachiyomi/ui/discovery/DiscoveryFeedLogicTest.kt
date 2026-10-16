package eu.kanade.tachiyomi.ui.discovery

import androidx.work.WorkManager
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.util.system.isRunningOrEnqueuedFlow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class DiscoveryFeedLogicTest {

    private fun suggestion(
        row: DiscoveryRowType,
        title: String,
        provider: String = "AniList",
        reason: String? = null,
    ) = DiscoverySuggestion(
        id = 1,
        mediaType = DiscoveryMediaType.ANIME,
        rowType = row,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = reason,
        seedTitle = null,
        provider = provider,
        score = 0.0,
        position = 0,
        createdAt = 100L,
    )

    @Test
    fun `countBlacklistImpact counts taste items with translated tag`() {
        val items = listOf(
            suggestion(DiscoveryRowType.TASTE, "A", reason = "Фэнтези"),
            suggestion(DiscoveryRowType.TASTE, "B", reason = "Драма"),
            suggestion(DiscoveryRowType.LIKE, "C", reason = "Фэнтези"),
        )
        countBlacklistImpact(items, "Fantasy") shouldBe 1
        countBlacklistImpact(items, "Драма") shouldBe 1
        countBlacklistImpact(items, "Комедия") shouldBe 0
    }

    @Test
    fun `manual refresh blocked during cooldown and allowed after`() {
        val now = 1_000_000L
        canManualRefresh(lastRefreshAt = now - 60_000, now = now) shouldBe false
        canManualRefresh(lastRefreshAt = now - 6 * 60_000, now = now) shouldBe true
        canManualRefresh(lastRefreshAt = null, now = now) shouldBe true
    }

    @Test
    fun `foreign and legacy manual tags do not mark target media refreshing`() = runBlocking {
        val workManager = mockk<WorkManager>()
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        try {
            val activeTags = setOf("DiscoveryUpdateManual", "DiscoveryUpdateManual:anime")
            every { workManager.isRunningOrEnqueuedFlow(any()) } answers {
                flowOf(firstArg<String>() in activeTags)
            }
            manualRefreshFlow(workManager, DiscoveryMediaType.ANIME).first() shouldBe true
            manualRefreshFlow(workManager, DiscoveryMediaType.MANGA).first() shouldBe false
            manualRefreshFlow(workManager, DiscoveryMediaType.NOVEL).first() shouldBe false
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `remaining cooldown seconds calculated correctly`() {
        val now = 1_000_000L
        val cooldownMs = 300_000L
        remainingCooldownSeconds(lastRefreshAt = now - 60_000L, now = now, cooldownMs = cooldownMs) shouldBe 240L
        remainingCooldownSeconds(lastRefreshAt = now - 270_000L, now = now, cooldownMs = cooldownMs) shouldBe 30L
        remainingCooldownSeconds(lastRefreshAt = now - 300_000L, now = now, cooldownMs = cooldownMs) shouldBe 0L
        remainingCooldownSeconds(lastRefreshAt = now - 350_000L, now = now, cooldownMs = cooldownMs) shouldBe 0L
        remainingCooldownSeconds(lastRefreshAt = null, now = now, cooldownMs = cooldownMs) shouldBe 0L
    }

    @Test
    fun `rows grouped by type keeping like before trend`() {
        val grouped = groupFeedRows(
            listOf(
                suggestion(DiscoveryRowType.TREND, "T1"),
                suggestion(DiscoveryRowType.LIKE, "L1"),
                suggestion(DiscoveryRowType.LIKE, "L2"),
            ),
        )
        grouped.keys.toList() shouldBe listOf(DiscoveryRowType.LIKE, DiscoveryRowType.TREND)
        grouped[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("L1", "L2")
    }

    @Test
    fun `updated label resolves minutes hours and never`() {
        val now = 60L * 60 * 1000 * 10
        resolveUpdatedLabel(lastUpdatedAt = now - 5 * 60_000, now = now) shouldBe (UpdatedLabelKind.MINUTES to 5L)
        resolveUpdatedLabel(lastUpdatedAt = now - 3 * 60 * 60_000, now = now) shouldBe (UpdatedLabelKind.HOURS to 3L)
        resolveUpdatedLabel(lastUpdatedAt = null, now = now) shouldBe (UpdatedLabelKind.NEVER to null)
    }

    @Test
    fun `db row maps to suggestion item with global-search-ready queries`() {
        val item = suggestion(DiscoveryRowType.LIKE, "Some Title").toSuggestionItem()
        item.title shouldBe "Some Title"
        item.searchQueries shouldBe listOf("Some Title")
        item.nativeSourceTarget shouldBe null // slice 1: всегда global search fallback
    }

    @Test
    fun `real provider names map to external suggestion reasons`() {
        suggestion(DiscoveryRowType.LIKE, "A", provider = "AniList")
            .toSuggestionItem().reason shouldBe SuggestionReason.EXTERNAL_ANILIST
        suggestion(DiscoveryRowType.LIKE, "M", provider = "MyAnimeList")
            .toSuggestionItem().reason shouldBe SuggestionReason.EXTERNAL_MAL
        suggestion(DiscoveryRowType.LIKE, "U", provider = "MangaUpdates")
            .toSuggestionItem().reason shouldBe SuggestionReason.EXTERNAL_MU
        suggestion(DiscoveryRowType.LIKE, "N", provider = "NovelUpdates")
            .toSuggestionItem().reason shouldBe SuggestionReason.EXTERNAL_NU
        suggestion(DiscoveryRowType.LIKE, "S", provider = "Shikimori")
            .toSuggestionItem().reason shouldBe SuggestionReason.EXTERNAL_SHIKIMORI
        suggestion(DiscoveryRowType.SOURCE, "P", provider = "InkStory")
            .toSuggestionItem().reason shouldBe SuggestionReason.SEARCH_TITLE
    }

    @Test
    fun `itemsForTab returns mix for MIX and row items for signal tabs`() {
        val s1 = suggestion(DiscoveryRowType.LIKE, "S1")
        val s2 = suggestion(DiscoveryRowType.TREND, "S2")
        val state = DiscoveryFeedUiState(
            mix = listOf(s1, s2),
            rows = mapOf(
                DiscoveryRowType.LIKE to listOf(s1),
                DiscoveryRowType.TREND to listOf(s2),
            ),
        )
        itemsForTab(state, FeedSignalTab.MIX) shouldBe listOf(s1, s2)
        itemsForTab(state, FeedSignalTab.SIMILAR) shouldBe listOf(s1)
        itemsForTab(state, FeedSignalTab.FRESH) shouldBe listOf(s2)
        itemsForTab(state, FeedSignalTab.TASTE) shouldBe emptyList()
    }

    @Test
    fun `filterByProvider filters by provider name when non-null and preserves all when null or blank`() {
        val s1 = suggestion(DiscoveryRowType.LIKE, "S1", provider = "AniList")
        val s2 = suggestion(DiscoveryRowType.LIKE, "S2", provider = "MAL")
        val items = listOf(s1, s2)
        filterByProvider(items, null) shouldBe items
        filterByProvider(items, "") shouldBe items
        filterByProvider(items, "AniList") shouldBe listOf(s1)
        filterByProvider(items, "Unknown") shouldBe emptyList()
    }
}
