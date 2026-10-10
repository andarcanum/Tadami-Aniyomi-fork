package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeroMode
import eu.kanade.tachiyomi.data.discovery.extractSeriesKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class HomeHubDiscoveryRotationTest {

    private fun suggestion(title: String, type: DiscoveryRowType = DiscoveryRowType.TREND) = DiscoverySuggestion(
        id = 0L,
        mediaType = DiscoveryMediaType.MANGA,
        rowType = type,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "test",
        score = 1.0,
        position = 0L,
        createdAt = 0L,
    )

    @Test
    fun `composeTeaserItems with offset produces non-overlapping window from large pool`() {
        val items = (1..32).map { suggestion("Item $it") }

        val window1 = composeTeaserItems(items, limit = 16, offset = 0)
        val window2 = composeTeaserItems(items, limit = 16, offset = 16)

        window1.size shouldBe 16
        window2.size shouldBe 16

        val titles1 = window1.map { it.title }.toSet()
        val titles2 = window2.map { it.title }.toSet()

        // Windows should not overlap when pool has enough items
        titles1.intersect(titles2) shouldBe emptySet()
    }

    @Test
    fun `composeTeaserItems offset wraps around safely`() {
        val items = (1..10).map { suggestion("Item $it") }
        val window = composeTeaserItems(items, limit = 5, offset = 12)
        window.size shouldBe 5
        window.map { it.title } shouldBe listOf("Item 3", "Item 4", "Item 5", "Item 6", "Item 7")
    }

    @Test
    fun `resolveHybridDiscoveryStripLayoutSpec aligns with HomeHub recent card metrics across device classes`() {
        resolveHybridDiscoveryStripLayoutSpec(
            eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass.Phone,
        ) shouldBe
            HybridDiscoveryStripLayoutSpec(
                cardWidth = 128,
                sectionHorizontalPadding = 24,
                rowSpacing = 14,
            )

        resolveHybridDiscoveryStripLayoutSpec(
            eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass.TabletCompact,
        ) shouldBe
            HybridDiscoveryStripLayoutSpec(
                cardWidth = 152,
                sectionHorizontalPadding = 28,
                rowSpacing = 16,
            )

        resolveHybridDiscoveryStripLayoutSpec(
            eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass.TabletExpanded,
        ) shouldBe
            HybridDiscoveryStripLayoutSpec(
                cardWidth = 176,
                sectionHorizontalPadding = 32,
                rowSpacing = 18,
            )
    }

    @Test
    fun `shouldRenderHomeHubHeroSlot renders collage even when hero is null and not reserved`() {
        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Collage,
            hasHero = false,
            reserveHeroSlot = false,
            hasDiscovery = true,
        ) shouldBe true

        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Collage,
            hasHero = false,
            reserveHeroSlot = false,
            hasDiscovery = false,
        ) shouldBe false

        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Continue,
            hasHero = false,
            reserveHeroSlot = false,
            hasDiscovery = true,
        ) shouldBe false

        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Continue,
            hasHero = true,
            reserveHeroSlot = false,
            hasDiscovery = false,
        ) shouldBe true
    }

    @Test
    fun `shouldRenderHomeHubHeroSlot renders stage even when hero is null and not reserved`() {
        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Stage,
            hasHero = false,
            reserveHeroSlot = false,
            hasDiscovery = true,
        ) shouldBe true

        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Stage,
            hasHero = false,
            reserveHeroSlot = false,
            hasDiscovery = false,
        ) shouldBe false

        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Stage,
            hasHero = true,
            reserveHeroSlot = false,
            hasDiscovery = false,
        ) shouldBe true
    }

    @Test
    fun `resolveCollageSlotTransition produces instant transitions on EInk and animated on standard display`() {
        val eInkTransition = resolveCollageSlotTransition(delayMillis = 0, isEInk = true)
        (eInkTransition != null) shouldBe true

        val standardTransition = resolveCollageSlotTransition(delayMillis = 140, isEInk = false)
        (standardTransition != null) shouldBe true

        val fastTransition = resolveCollageSlotTransition(delayMillis = 40, isEInk = false, speed = "fast")
        (fastTransition != null) shouldBe true

        val smoothTransition = resolveCollageSlotTransition(delayMillis = 110, isEInk = false, speed = "smooth")
        (smoothTransition != null) shouldBe true
    }

    @Test
    fun `composeTeaserItems rotates order even when pool size is smaller than or equal to limit`() {
        val items = listOf(suggestion("A"), suggestion("B"), suggestion("C"))
        val normal = composeTeaserItems(items, limit = 5, offset = 0)
        normal.map { it.title } shouldBe listOf("A", "B", "C")

        val rotated1 = composeTeaserItems(items, limit = 5, offset = 1)
        rotated1.map { it.title } shouldBe listOf("B", "C", "A")

        val rotated2 = composeTeaserItems(items, limit = 5, offset = 2)
        rotated2.map { it.title } shouldBe listOf("C", "A", "B")
    }

    @Test
    fun `selectFreshTeaserItems prioritizes fresh titles over shown titles`() {
        val pool = (1..10).map { suggestion("Title $it") }
        val shown = setOf("title 1", "title 2", "title 3")

        val freshSelection = selectFreshTeaserItems(pool, shownTitles = shown, count = 5)
        freshSelection.size shouldBe 5
        freshSelection.map { it.title } shouldBe listOf("Title 4", "Title 5", "Title 6", "Title 7", "Title 8")
    }

    @Test
    fun `selectFreshTeaserItems backfills with least-recently shown when fresh titles insufficient`() {
        val pool = listOf(
            suggestion("Fresh 1"),
            suggestion("Fresh 2"),
            suggestion("Old Shown"),
            suggestion("Recent Shown"),
        )
        val shown = setOf("old shown", "recent shown")
        val cutoffMap = mapOf(
            "old shown" to 1000L, // older timestamp -> least recently shown
            "recent shown" to 5000L, // newer timestamp -> more recently shown
        )

        val result = selectFreshTeaserItems(
            pool = pool,
            shownTitles = shown,
            count = 3,
            shownCutoffMap = cutoffMap,
        )

        result.size shouldBe 3
        // Fresh items first, then oldest shown item backfilled
        result.map { it.title } shouldBe listOf("Fresh 1", "Fresh 2", "Old Shown")
    }

    @Test
    fun `selectFreshTeaserItems backfills correctly when fresh pool is completely empty`() {
        val pool = listOf(
            suggestion("Shown A"),
            suggestion("Shown B"),
            suggestion("Shown C"),
        )
        val shown = setOf("shown a", "shown b", "shown c")
        val cutoffMap = mapOf(
            "shown a" to 3000L,
            "shown b" to 1000L, // oldest
            "shown c" to 2000L,
        )

        val result = selectFreshTeaserItems(
            pool = pool,
            shownTitles = shown,
            count = 3,
            shownCutoffMap = cutoffMap,
        )

        result.size shouldBe 3
        // Sorted by cutoffMap ascending: B (1000), C (2000), A (3000)
        result.map { it.title } shouldBe listOf("Shown B", "Shown C", "Shown A")
    }

    @Test
    fun `selectFreshTeaserItems preserves timestamp ordering during backfill across different row types`() {
        val pool = listOf(
            suggestion("Shown Like", type = DiscoveryRowType.LIKE),
            suggestion("Shown Trend", type = DiscoveryRowType.TREND),
        )
        val shown = setOf("shown like", "shown trend")
        val cutoffMap = mapOf(
            "shown like" to 5000L, // more recently shown
            "shown trend" to 1000L, // least recently shown
        )

        val result = selectFreshTeaserItems(
            pool = pool,
            shownTitles = shown,
            count = 3,
            shownCutoffMap = cutoffMap,
        )

        result.size shouldBe 2
        // Strict timestamp order must be preserved regardless of row type quotas
        result.map { it.title } shouldBe listOf("Shown Trend", "Shown Like")
    }

    @Test
    fun `selectFreshTeaserItems does not skip fresh candidates across sequential batches`() {
        val pool = (1..15).map { suggestion("Candidate $it") }
        val shown = mutableSetOf<String>()

        // Batch 1: Candidates 1..5
        val batch1 = selectFreshTeaserItems(pool, shownTitles = shown, count = 5)
        batch1.map { it.title } shouldBe
            listOf("Candidate 1", "Candidate 2", "Candidate 3", "Candidate 4", "Candidate 5")
        shown.addAll(batch1.map { it.cleanTitle })

        // Batch 2: Candidates 6..10 (must NOT skip to 11..15)
        val batch2 = selectFreshTeaserItems(pool, shownTitles = shown, count = 5)
        batch2.map { it.title } shouldBe
            listOf("Candidate 6", "Candidate 7", "Candidate 8", "Candidate 9", "Candidate 10")
        shown.addAll(batch2.map { it.cleanTitle })

        // Batch 3: Candidates 11..15
        val batch3 = selectFreshTeaserItems(pool, shownTitles = shown, count = 5)
        batch3.map { it.title } shouldBe
            listOf("Candidate 11", "Candidate 12", "Candidate 13", "Candidate 14", "Candidate 15")
    }

    @Test
    fun `selectFreshTeaserItems rotates order when pool size is smaller than or equal to count`() {
        val pool = listOf(suggestion("A"), suggestion("B"), suggestion("C"))
        val shown = setOf("a", "b", "c")

        val r0 = selectFreshTeaserItems(pool, shownTitles = shown, count = 5, offset = 0)
        r0.map { it.title } shouldBe listOf("A", "B", "C")

        val r1 = selectFreshTeaserItems(pool, shownTitles = shown, count = 5, offset = 1)
        r1.map { it.title } shouldBe listOf("B", "C", "A")

        val r2 = selectFreshTeaserItems(pool, shownTitles = shown, count = 5, offset = 2)
        r2.map { it.title } shouldBe listOf("C", "A", "B")
    }

    @Test
    fun `selectFreshTeaserItems handles partial fresh pool with exact backfill count without coercion`() {
        val pool = listOf(
            suggestion("Fresh 1"),
            suggestion("Fresh 2"),
            suggestion("Old 1"),
            suggestion("Old 2"),
            suggestion("Old 3"),
        )
        val shown = setOf("old 1", "old 2", "old 3")
        val cutoffMap = mapOf(
            "old 1" to 100L,
            "old 2" to 200L,
            "old 3" to 300L,
        )

        // Count = 3: 2 fresh + 1 oldest backfill
        val result = selectFreshTeaserItems(pool, shownTitles = shown, count = 3, shownCutoffMap = cutoffMap)
        result.size shouldBe 3
        result.map { it.title } shouldBe listOf("Fresh 1", "Fresh 2", "Old 1")
    }

    @Test
    fun `selectFreshTeaserItems avoids currentTitles when alternatives available`() {
        val pool = (1..6).map { suggestion("Item $it") }
        val current = setOf("item 1", "item 2", "item 3")

        val result = selectFreshTeaserItems(
            pool = pool,
            shownTitles = emptySet(),
            count = 3,
            currentTitles = current,
        )

        result.size shouldBe 3
        val resultCleanTitles = result.map { it.cleanTitle }.toSet()
        resultCleanTitles.intersect(current) shouldBe emptySet()
        result.map { it.title } shouldBe listOf("Item 4", "Item 5", "Item 6")
    }

    @Test
    fun `selectFreshTeaserItems falls back gracefully when pool is exhausted or only currentTitles remain`() {
        val pool = (1..4).map { suggestion("Candidate $it") }
        val current = setOf("candidate 1", "candidate 2", "candidate 3")

        val result = selectFreshTeaserItems(
            pool = pool,
            shownTitles = emptySet(),
            count = 3,
            currentTitles = current,
        )

        result.size shouldBe 3
        result[0].title shouldBe "Candidate 4"
        val remainingTitles = result.drop(1).map { it.title }
        (remainingTitles.all { it in listOf("Candidate 1", "Candidate 2", "Candidate 3") }) shouldBe true
    }

    @Test
    fun `composeTeaserItems prevents franchise clustering by prioritizing diverse series`() {
        val pool = listOf(
            suggestion("Jujutsu Kaisen: Shibuya Incident"),
            suggestion("Jujutsu Kaisen Season 2"),
            suggestion("Jujutsu Kaisen Movie 0"),
            suggestion("Dungeon Meshi"),
            suggestion("Chainsaw Man"),
        )

        val result = composeTeaserItems(pool, limit = 3)
        result.size shouldBe 3
        val titles = result.map { it.title }
        titles shouldBe listOf("Jujutsu Kaisen: Shibuya Incident", "Dungeon Meshi", "Chainsaw Man")
    }

    @Test
    fun `extractSeriesKey correctly identifies sequels, seasons, and subtitles`() {
        extractSeriesKey("Solo Leveling: Ragnarok", "solo leveling: ragnarok") shouldBe "solo leveling"
        extractSeriesKey("Jujutsu Kaisen Season 2", "jujutsu kaisen season 2") shouldBe "jujutsu kaisen"
        extractSeriesKey("Sword Art Online II", "sword art online ii") shouldBe "sword art online"
        extractSeriesKey("Spy x Family Part 2", "spy x family part 2") shouldBe "spy x family"
        extractSeriesKey("Attack on Titan: The Final Season", "attack on titan: the final season") shouldBe
            "attack on titan"
    }

    @Test
    fun `exhausted pool larger than window rotates selection by offset`() {
        val pool = (1..10).map { suggestion("title$it") }
        val shown = pool.map { it.cleanTitle }.toSet()
        val cutoffMap = pool.mapIndexed { idx, item -> item.cleanTitle to 100L * (idx + 1) }.toMap()
        val window1 =
            selectFreshTeaserItems(pool, shownTitles = shown, count = 5, offset = 0, shownCutoffMap = cutoffMap)
        val window2 =
            selectFreshTeaserItems(pool, shownTitles = shown, count = 5, offset = 5, shownCutoffMap = cutoffMap)
        window2.map { it.cleanTitle }.toSet() shouldNotBe window1.map { it.cleanTitle }.toSet()
    }

    private fun discoveryItem(title: String, type: DiscoveryRowType = DiscoveryRowType.TREND) = HomeHubDiscoveryItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        seedTitle = null,
        reasonPayload = null,
        provider = "test",
        rowType = type,
        mediaType = DiscoveryMediaType.MANGA,
    )

    @Test
    fun `patchAfterLocalLike replaces liked card with first available candidate from persistent pool at same index`() {
        val currentTeaser = listOf(discoveryItem("Card 1"), discoveryItem("Card 2"), discoveryItem("Card 3"))
        val persistedPool = listOf(
            discoveryItem("Card 1"),
            discoveryItem("Card 2"),
            discoveryItem("Card 3"),
            discoveryItem("Card 4"),
            discoveryItem("Card 5"),
        )

        val result = patchAfterLocalLike(
            currentTeaser = currentTeaser,
            persistedPool = persistedPool,
            likedCleanTitle = "card 2",
            targetCount = 3,
        )

        result.map { it.title } shouldBe listOf("Card 1", "Card 4", "Card 3")
    }

    @Test
    fun `patchAfterLocalLike skips candidates already in current teaser or in dismissed set`() {
        val currentTeaser = listOf(discoveryItem("Card 1"), discoveryItem("Card 2"), discoveryItem("Card 3"))
        val persistedPool = listOf(
            discoveryItem("Card 1"),
            discoveryItem("Card 2"),
            discoveryItem("Card 3"),
            discoveryItem("Card 4"),
            discoveryItem("Card 5"),
        )

        val result = patchAfterLocalLike(
            currentTeaser = currentTeaser,
            persistedPool = persistedPool,
            likedCleanTitle = "card 2",
            targetCount = 3,
            dismissedTitles = setOf("card 4"),
        )

        result.map { it.title } shouldBe listOf("Card 1", "Card 5", "Card 3")
    }

    @Test
    fun `patchAfterLocalLike removes card and shrinks list when no candidates available in persistent pool`() {
        val currentTeaser = listOf(discoveryItem("Card 1"), discoveryItem("Card 2"), discoveryItem("Card 3"))
        val persistedPool = listOf(
            discoveryItem("Card 1"),
            discoveryItem("Card 2"),
            discoveryItem("Card 3"),
        )

        val result = patchAfterLocalLike(
            currentTeaser = currentTeaser,
            persistedPool = persistedPool,
            likedCleanTitle = "card 2",
            targetCount = 3,
        )

        result.map { it.title } shouldBe listOf("Card 1", "Card 3")
    }

    @Test
    fun `patchAfterLocalLike returns unchanged list when liked title is not in current teaser`() {
        val currentTeaser = listOf(discoveryItem("Card 1"), discoveryItem("Card 2"))
        val persistedPool = listOf(discoveryItem("Card 1"), discoveryItem("Card 2"), discoveryItem("Card 3"))

        val result = patchAfterLocalLike(
            currentTeaser = currentTeaser,
            persistedPool = persistedPool,
            likedCleanTitle = "nonexistent",
            targetCount = 2,
        )

        result.map { it.title } shouldBe listOf("Card 1", "Card 2")
    }

    @Test
    fun `reduceDismissTeaserCard updates discovery list in HomeHubUiState using patchAfterLocalLike`() {
        val state = HomeHubUiState(
            userName = "User",
            userAvatar = "",
            greeting = tachiyomi.i18n.MR.strings.app_name,
            greetingReady = true,
            isLoading = false,
            showWelcome = false,
            discovery = listOf(discoveryItem("Card 1"), discoveryItem("Card 2")),
            discoveryPool = listOf(discoveryItem("Card 1"), discoveryItem("Card 2"), discoveryItem("Card 3")),
        )

        val updated = state.reduceDismissTeaserCard(likedCleanTitle = "card 1")
        updated.discovery.map { it.title } shouldBe listOf("Card 3", "Card 2")
        updated.discoveryPool.map { it.title } shouldBe listOf("Card 1", "Card 2", "Card 3")
    }
}
