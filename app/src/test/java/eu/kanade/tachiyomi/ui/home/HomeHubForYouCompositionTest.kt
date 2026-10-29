package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeroMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class HomeHubForYouCompositionTest {

    private fun suggestion(
        row: DiscoveryRowType,
        title: String,
        position: Long,
        reason: String? = null,
        provider: String = "test",
    ) =
        DiscoverySuggestion(
            id = position,
            mediaType = DiscoveryMediaType.NOVEL,
            rowType = row,
            title = title,
            cleanTitle = title.lowercase(),
            coverUrl = null,
            reason = reason,
            seedTitle = if (row == DiscoveryRowType.LIKE) "Seed" else null,
            provider = provider,
            score = 0.0,
            position = position,
            createdAt = 1L,
        )

    @Test
    fun `teaser is mixed round-robin across signals and respects limit`() {
        val items = listOf(
            suggestion(DiscoveryRowType.TREND, "Trend1", 0),
            suggestion(DiscoveryRowType.LIKE, "Like1", 0),
            suggestion(DiscoveryRowType.LIKE, "Like2", 1),
            suggestion(DiscoveryRowType.TREND, "Trend2", 1),
        )
        val out = composeTeaserItems(items, limit = 3)
        out.map { it.title } shouldBe listOf("Like1", "Trend1", "Like2")
    }

    @Test
    fun `teaser limit coerced into 3 to 20`() {
        val items = (1..25).map { suggestion(DiscoveryRowType.LIKE, "L$it", it.toLong()) }
        composeTeaserItems(items, limit = 99).size shouldBe 20
        composeTeaserItems(items, limit = 1).size shouldBe 3
    }

    @Test
    fun `for you items suppressed in hybrid with hero and in collage, visible in continue`() {
        val dummy = listOf(
            composeTeaserItems(listOf(suggestion(DiscoveryRowType.LIKE, "Title", 0)), 1).first(),
        )
        resolveForYouItems(eu.kanade.domain.ui.model.HomeHeroMode.Hybrid, hasHero = true, discovery = dummy) shouldBe
            emptyList()
        resolveForYouItems(eu.kanade.domain.ui.model.HomeHeroMode.Collage, hasHero = true, discovery = dummy) shouldBe
            emptyList()
        resolveForYouItems(eu.kanade.domain.ui.model.HomeHeroMode.Continue, hasHero = true, discovery = dummy) shouldBe
            dummy
        resolveForYouItems(eu.kanade.domain.ui.model.HomeHeroMode.Hybrid, hasHero = false, discovery = dummy) shouldBe
            dummy
    }

    @Test
    fun `for you items suppressed in stage regardless of hero presence`() {
        val dummy = listOf(
            composeTeaserItems(listOf(suggestion(DiscoveryRowType.LIKE, "Title", 0)), 1).first(),
        )
        resolveForYouItems(HomeHeroMode.Stage, hasHero = true, discovery = dummy) shouldBe emptyList()
        resolveForYouItems(HomeHeroMode.Stage, hasHero = false, discovery = dummy) shouldBe emptyList()
    }

    @Test
    fun `section shown whenever enabled, hidden only when disabled`() {
        shouldShowForYouSection(enabled = false) shouldBe false
        shouldShowForYouSection(enabled = true) shouldBe true
    }

    @Test
    fun `reason text composed from seed title or season payload`() {
        val similar = "Similar to “%1\$s”"
        val like = composeTeaserItems(listOf(suggestion(DiscoveryRowType.LIKE, "X", 0)), 6).single()
        discoveryReasonText(like, similar, "Trending now", "Next season") shouldBe
            "Similar to “Seed”"
        val trendCurrent = composeTeaserItems(
            listOf(suggestion(DiscoveryRowType.TREND, "Y", 0, reason = "current")),
            6,
        ).single()
        discoveryReasonText(trendCurrent, similar, "Trending now", "Next season") shouldBe
            "Trending now"
        val trendNext = composeTeaserItems(
            listOf(suggestion(DiscoveryRowType.TREND, "Z", 0, reason = "next")),
            6,
        ).single()
        discoveryReasonText(trendNext, similar, "Trending now", "Next season") shouldBe
            "Next season"
    }

    @Test
    fun `source row shows provider name and blank taste reason is hidden`() {
        val similar = "Similar to “%1\$s”"
        val source = composeTeaserItems(
            listOf(suggestion(DiscoveryRowType.SOURCE, "S", 0, provider = "InkStory")),
            6,
        ).single()
        discoveryReasonText(source, similar, "Trending now", "Next season") shouldBe "InkStory"

        val trendFromSource = composeTeaserItems(
            listOf(suggestion(DiscoveryRowType.TREND, "T", 0, reason = "source", provider = "RanobeHub")),
            6,
        ).single()
        discoveryReasonText(trendFromSource, similar, "Trending now", "Next season") shouldBe "RanobeHub"

        val tasteBlank = composeTeaserItems(
            listOf(suggestion(DiscoveryRowType.TASTE, "X", 0, reason = "")),
            6,
        ).single()
        discoveryReasonText(tasteBlank, similar, "Trending now", "Next season") shouldBe null
    }

    @Test
    fun `firstBlacklistTag picks first genre or falls back to taste reason`() {
        firstBlacklistTag(listOf("Фэнтези", "Драма"), DiscoveryRowType.TREND, null) shouldBe "Фэнтези"
        firstBlacklistTag(emptyList(), DiscoveryRowType.TASTE, "Фэнтези, Драма") shouldBe "Фэнтези"
        firstBlacklistTag(emptyList(), DiscoveryRowType.TASTE, " Фэнтези ") shouldBe "Фэнтези"
        firstBlacklistTag(emptyList(), DiscoveryRowType.TASTE, "") shouldBe null
        firstBlacklistTag(emptyList(), DiscoveryRowType.TASTE, null) shouldBe null
        firstBlacklistTag(emptyList(), DiscoveryRowType.LIKE, "anything") shouldBe null
        firstBlacklistTag(emptyList(), DiscoveryRowType.SOURCE, "anything") shouldBe null
    }

    @Test
    fun `countAffectedTeasers counts cards matching tag with translations`() {
        val items = listOf(
            composeTeaserItems(
                listOf(suggestion(DiscoveryRowType.TASTE, "A", 0, reason = "Фэнтези, Драма")),
                6,
            ).single(),
            composeTeaserItems(
                listOf(suggestion(DiscoveryRowType.TASTE, "B", 0, reason = "Экшен")),
                6,
            ).single(),
            composeTeaserItems(
                listOf(suggestion(DiscoveryRowType.LIKE, "C", 0)),
                6,
            ).single(),
            HomeHubDiscoveryItem(
                title = "D",
                cleanTitle = "d",
                coverUrl = null,
                seedTitle = null,
                reasonPayload = null,
                provider = "test",
                rowType = DiscoveryRowType.TREND,
                mediaType = DiscoveryMediaType.NOVEL,
                genres = listOf("Fantasy"),
            ),
        )
        countAffectedTeasers(items, "Fantasy") shouldBe 2
        countAffectedTeasers(items, "Экшен") shouldBe 1
        countAffectedTeasers(items, "Комедия") shouldBe 0
    }

    @Test
    fun `scroll enabled under welcome when discovery present`() {
        shouldEnableHomeHubScroll(
            showWelcome = true,
            historyCount = 0,
            recommendationCount = 0,
            discoveryCount = 2,
        ) shouldBe true
        shouldEnableHomeHubScroll(
            showWelcome = true,
            historyCount = 0,
            recommendationCount = 0,
            discoveryCount = 0,
        ) shouldBe false
    }

    @Test
    fun `patchTeaserItems replaces only invalid cards and keeps surviving valid cards in place`() {
        val pool = (1..6).map { suggestion(DiscoveryRowType.LIKE, "Title $it", it.toLong()) }
        val current = pool.take(3).map { it.toHomeHubDiscoveryItem() }
        // Title 2 is consumed/removed from validCleanTitles:
        val validTitles = setOf("title 1", "title 3", "title 4", "title 5", "title 6")
        val patched = patchTeaserItems(
            currentItems = current,
            pool = pool,
            validCleanTitles = validTitles,
            targetCount = 3,
        )
        patched.size shouldBe 3
        patched[0].cleanTitle shouldBe "title 1"
        patched[1].cleanTitle shouldBe "title 4" // Title 2 replaced with Title 4
        patched[2].cleanTitle shouldBe "title 3"
    }

    @Test
    fun `patchTeaserItems falls back to selectFreshTeaserItems when current is empty`() {
        val pool = (1..5).map { suggestion(DiscoveryRowType.LIKE, "T $it", it.toLong()) }
        val patched = patchTeaserItems(
            currentItems = emptyList(),
            pool = pool,
            validCleanTitles = pool.mapTo(HashSet()) { it.cleanTitle },
            targetCount = 3,
        )
        patched.size shouldBe 3
    }

    @Test
    fun `balancePluginDiversity strictly preserves at least 3 titles from other plugins`() {
        val dominantItems = (1..8).map {
            suggestion(DiscoveryRowType.LIKE, "Dominant $it", it.toLong(), provider = "dominant-plugin")
        }
        val otherItems = (1..4).map {
            suggestion(DiscoveryRowType.LIKE, "Other $it", (10 + it).toLong(), provider = "other-plugin")
        }
        val mixed = dominantItems + otherItems
        val balanced = eu.kanade.tachiyomi.data.discovery.balancePluginDiversity(
            items = mixed,
            limit = 6,
            sourceKeyExtractor = { it.provider },
            minOtherCount = 3,
        )
        balanced.size shouldBe 6
        balanced.count { it.provider == "other-plugin" } shouldBe 3
        balanced.count { it.provider == "dominant-plugin" } shouldBe 3
    }

    @Test
    fun `filterGenreClustering moves excess titles of dominant genre to tail`() {
        val fantasyItems = (1..20).map {
            eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem(
                title = "Fantasy $it",
                cleanTitle = "fantasy $it",
                coverUrl = null,
                reason = "Fantasy",
                seedTitle = null,
                provider = "test",
                score = 2.0 - it * 0.01,
            )
        }
        val sciFiItems = (1..5).map {
            eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem(
                title = "SciFi $it",
                cleanTitle = "scifi $it",
                coverUrl = null,
                reason = "Sci-Fi",
                seedTitle = null,
                provider = "test",
                score = 1.0 - it * 0.01,
            )
        }
        val all = fantasyItems + sciFiItems
        val clustered = eu.kanade.tachiyomi.data.discovery.filterGenreClustering(all, maxPerGenre = 15)
        clustered.size shouldBe 25
        // Within top 20 items: first 15 are fantasy, next 5 are sci-fi (excess fantasy moved after sci-fi)
        clustered.take(20).count { it.reason == "Sci-Fi" } shouldBe 5
        clustered.take(20).count { it.reason == "Fantasy" } shouldBe 15
    }

    @Test
    fun `filterGenreClustering with default maxPerGenre caps dominant genre to 6`() {
        val fantasyItems = (1..10).map {
            eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem(
                title = "Fantasy $it",
                cleanTitle = "fantasy $it",
                coverUrl = null,
                reason = "Fantasy",
                seedTitle = null,
                provider = "test",
                score = 2.0 - it * 0.01,
            )
        }
        val sciFiItems = (1..5).map {
            eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem(
                title = "SciFi $it",
                cleanTitle = "scifi $it",
                coverUrl = null,
                reason = "Sci-Fi",
                seedTitle = null,
                provider = "test",
                score = 1.0 - it * 0.01,
            )
        }
        val all = fantasyItems + sciFiItems
        val clustered = eu.kanade.tachiyomi.data.discovery.filterGenreClustering(all)
        // Default maxPerGenre is 6: top 11 items contain 6 fantasy and 5 sci-fi
        clustered.take(11).count { it.reason == "Fantasy" } shouldBe 6
        clustered.take(11).count { it.reason == "Sci-Fi" } shouldBe 5
    }

    @Test
    fun `composeTeaserItems strictly preserves other plugins when candidates are mixed`() {
        val dominantItems = (1..10).map {
            suggestion(DiscoveryRowType.LIKE, "Dominant $it", it.toLong(), provider = "plugin-a")
        }
        val otherItems = (1..5).map {
            suggestion(DiscoveryRowType.LIKE, "Other $it", (20 + it).toLong(), provider = "plugin-b")
        }
        val mixed = dominantItems + otherItems
        val teaser = composeTeaserItems(mixed, limit = 8)
        teaser.size shouldBe 8
        teaser.count { it.provider == "plugin-b" } shouldBe 3
        teaser.count { it.provider == "plugin-a" } shouldBe 5
    }

    @Test
    fun `resolveForYouEmptyState distinguishes all five operational states`() {
        // 1. Discovery disabled
        resolveForYouEmptyState(
            discoveryEnabled = false,
            isRefreshing = false,
            hasItems = false,
            lastUpdatedAt = null,
            isFailed = false,
        ) shouldBe ForYouEmptyState.DISABLED

        // 2. Refreshing in progress
        resolveForYouEmptyState(
            discoveryEnabled = true,
            isRefreshing = true,
            hasItems = false,
            lastUpdatedAt = null,
            isFailed = false,
        ) shouldBe ForYouEmptyState.REFRESHING

        // 3. Failed refresh
        resolveForYouEmptyState(
            discoveryEnabled = true,
            isRefreshing = false,
            hasItems = false,
            lastUpdatedAt = null,
            isFailed = true,
        ) shouldBe ForYouEmptyState.FAILED

        // 4. Not loaded yet (cold install / clean database)
        resolveForYouEmptyState(
            discoveryEnabled = true,
            isRefreshing = false,
            hasItems = false,
            lastUpdatedAt = null,
            isFailed = false,
        ) shouldBe ForYouEmptyState.NOT_LOADED_YET

        // 5. Successful generation but zero candidates passed filters
        resolveForYouEmptyState(
            discoveryEnabled = true,
            isRefreshing = false,
            hasItems = false,
            lastUpdatedAt = 123456789L,
            isFailed = false,
        ) shouldBe ForYouEmptyState.NO_CANDIDATES

        // When items are present, no empty state
        resolveForYouEmptyState(
            discoveryEnabled = true,
            isRefreshing = false,
            hasItems = true,
            lastUpdatedAt = 123456789L,
            isFailed = false,
        ) shouldBe null
    }

    @Test
    fun `shouldEnableHomeHubScroll enables scrolling on welcome screen when discovery is enabled`() {
        // Cold install with welcome screen: user must be able to scroll to see the For You card / refresh button
        shouldEnableHomeHubScroll(
            showWelcome = true,
            historyCount = 0,
            recommendationCount = 0,
            discoveryCount = 0,
            discoveryEnabled = true,
        ) shouldBe true

        // Welcome screen with discovery disabled: no need to scroll
        shouldEnableHomeHubScroll(
            showWelcome = true,
            historyCount = 0,
            recommendationCount = 0,
            discoveryCount = 0,
            discoveryEnabled = false,
        ) shouldBe false

        // Welcome screen with items: scroll enabled
        shouldEnableHomeHubScroll(
            showWelcome = true,
            historyCount = 0,
            recommendationCount = 0,
            discoveryCount = 3,
            discoveryEnabled = true,
        ) shouldBe true
    }
}
