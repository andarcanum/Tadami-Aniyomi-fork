package eu.kanade.tachiyomi.ui.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class DiscoveryFeedWindowLogicTest {

    @Test
    fun `manual tap rotates locally during shared cooldown and requests refresh after expiry`() {
        resolveManualRefreshAction(isActive = false, remainingCooldown = 300L) shouldBe
            ManualRefreshAction.ROTATE
        resolveManualRefreshAction(isActive = false, remainingCooldown = 1L) shouldBe
            ManualRefreshAction.ROTATE
        resolveManualRefreshAction(isActive = false, remainingCooldown = 0L) shouldBe
            ManualRefreshAction.REFRESH
    }

    @Test
    fun `manual tap cancels active enqueued or running work even during cooldown`() {
        resolveManualRefreshAction(isActive = true, remainingCooldown = 300L) shouldBe
            ManualRefreshAction.CANCEL
        resolveManualRefreshAction(isActive = true, remainingCooldown = 0L) shouldBe
            ManualRefreshAction.CANCEL
    }

    private fun mockSuggestion(
        title: String,
        provider: String = "anilist",
        rowType: DiscoveryRowType = DiscoveryRowType.LIKE,
        score: Double = 1.0,
    ) = DiscoverySuggestion(
        id = 1L,
        mediaType = DiscoveryMediaType.ANIME,
        rowType = rowType,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = provider,
        score = score,
        position = 0L,
        createdAt = 0L,
    )

    @Test
    fun `resolveNextFeedOffset steps by window size and triggers network when exhausted`() {
        val poolSize = 60
        val window = 20

        // Step 1: 0 -> 20 (local rotation)
        val (step1, net1) = resolveNextFeedOffset(0, poolSize, window, canNetworkRefresh = false)
        step1 shouldBe 20
        net1 shouldBe false

        // Step 2: 20 -> 40 (local rotation)
        val (step2, net2) = resolveNextFeedOffset(20, poolSize, window, canNetworkRefresh = false)
        step2 shouldBe 40
        net2 shouldBe false

        // Step 3: 40 -> 0 (local wrap-around if cooldown active)
        val (step3Wrap, net3Wrap) = resolveNextFeedOffset(40, poolSize, window, canNetworkRefresh = false)
        step3Wrap shouldBe 0
        net3Wrap shouldBe false

        // Step 3: 40 -> 0 (network refresh triggered if cooldown elapsed)
        val (step3Net, net3Net) = resolveNextFeedOffset(40, poolSize, window, canNetworkRefresh = true)
        step3Net shouldBe 0
        net3Net shouldBe true
    }

    @Test
    fun `sliceFeedWindow slices exactly 20 items and wraps around if needed`() {
        val pool = (1..60).map { mockSuggestion("Item $it") }

        val page1 = sliceFeedWindow(pool, offset = 0, windowSize = 20)
        page1.size shouldBe 20
        page1.first().title shouldBe "Item 1"
        page1.last().title shouldBe "Item 20"

        val page2 = sliceFeedWindow(pool, offset = 20, windowSize = 20)
        page2.size shouldBe 20
        page2.first().title shouldBe "Item 21"
        page2.last().title shouldBe "Item 40"

        val page3 = sliceFeedWindow(pool, offset = 40, windowSize = 20)
        page3.size shouldBe 20
        page3.first().title shouldBe "Item 41"
        page3.last().title shouldBe "Item 60"
    }

    @Test
    fun `sliceFeedWindow wraps around when window spans beyond pool size`() {
        val pool = (1..25).map { mockSuggestion("Item $it") }
        val page = sliceFeedWindow(pool, offset = 20, windowSize = 10)
        page.size shouldBe 10
        page.take(5).map { it.title } shouldBe (21..25).map { "Item $it" }
        page.takeLast(5).map { it.title } shouldBe (1..5).map { "Item $it" }
    }

    @Test
    fun `selectBackfillCandidate prioritizes same provider among fresh titles`() {
        val removed = mockSuggestion("Removed", provider = "mangadex", rowType = DiscoveryRowType.SOURCE)
        val pool = listOf(
            mockSuggestion("Other Provider", provider = "shikimori", rowType = DiscoveryRowType.TREND),
            mockSuggestion("Same Provider Fresh", provider = "mangadex", rowType = DiscoveryRowType.SOURCE),
            mockSuggestion("Same Row Other Provider", provider = "remanga", rowType = DiscoveryRowType.SOURCE),
        )

        val candidate = selectBackfillCandidate(
            removedItem = removed,
            pool = pool,
            currentWindowTitles = setOf("other provider"),
            shownTitles = emptySet(),
        )

        candidate?.title shouldBe "Same Provider Fresh"
    }

    @Test
    fun `selectBackfillCandidate falls back to same rowType when same provider unavailable`() {
        val removed = mockSuggestion("Removed", provider = "mangadex", rowType = DiscoveryRowType.TASTE)
        val pool = listOf(
            mockSuggestion("Different Row", provider = "anilist", rowType = DiscoveryRowType.TREND),
            mockSuggestion("Same Row Different Provider", provider = "shikimori", rowType = DiscoveryRowType.TASTE),
        )

        val candidate = selectBackfillCandidate(
            removedItem = removed,
            pool = pool,
            currentWindowTitles = emptySet(),
            shownTitles = emptySet(),
        )

        candidate?.title shouldBe "Same Row Different Provider"
    }

    @Test
    fun `selectBackfillCandidate avoids current window and shown titles when fresh exists`() {
        val removed = mockSuggestion("Removed", provider = "p1", rowType = DiscoveryRowType.LIKE)
        val pool = listOf(
            mockSuggestion("Already In Window", provider = "p1", rowType = DiscoveryRowType.LIKE),
            mockSuggestion("Already Shown", provider = "p1", rowType = DiscoveryRowType.LIKE),
            mockSuggestion("Fresh Alternative", provider = "p2", rowType = DiscoveryRowType.TREND),
        )

        val candidate = selectBackfillCandidate(
            removedItem = removed,
            pool = pool,
            currentWindowTitles = setOf("already in window"),
            shownTitles = setOf("already shown"),
        )

        candidate?.title shouldBe "Fresh Alternative"
    }

    @Test
    fun `patchFeedWindowAfterRemoval replaces item at exact index without altering order`() {
        val w1 = mockSuggestion("A")
        val w2 = mockSuggestion("B")
        val w3 = mockSuggestion("C")
        val replacement = mockSuggestion("R")

        val patched = patchFeedWindowAfterRemoval(
            currentWindow = listOf(w1, w2, w3),
            removedTitle = "b",
            replacement = replacement,
        )

        patched.map { it.title } shouldBe listOf("A", "R", "C")
    }

    @Test
    fun `patchFeedWindowAfterRemoval shrinks list if replacement is null`() {
        val w1 = mockSuggestion("A")
        val w2 = mockSuggestion("B")
        val w3 = mockSuggestion("C")

        val patched = patchFeedWindowAfterRemoval(
            currentWindow = listOf(w1, w2, w3),
            removedTitle = "b",
            replacement = null,
        )

        patched.map { it.title } shouldBe listOf("A", "C")
    }
}
