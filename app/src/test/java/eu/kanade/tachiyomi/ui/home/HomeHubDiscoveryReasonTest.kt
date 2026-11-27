package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

class HomeHubDiscoveryReasonTest {

    private fun testItem(
        rowType: DiscoveryRowType,
        seedTitle: String? = null,
        reasonPayload: String? = null,
        provider: String = "TestProvider",
    ) = HomeHubDiscoveryItem(
        title = "Test Title",
        cleanTitle = "test title",
        coverUrl = null,
        seedTitle = seedTitle,
        reasonPayload = reasonPayload,
        provider = provider,
        rowType = rowType,
        mediaType = DiscoveryMediaType.NOVEL,
    )

    @Test
    fun `LIKE returns Similar to Solo Leveling`() {
        val item = testItem(DiscoveryRowType.LIKE, seedTitle = "Solo Leveling")
        val text = discoveryReasonText(
            item = item,
            similarTemplate = "Similar to «%1\$s»",
            trendTemplate = "Trending now",
            nextSeasonTemplate = "Next season",
            tasteTemplate = "Based on your taste: %1\$s",
        )
        text shouldBe "Similar to «Solo Leveling»"
    }

    @Test
    fun `TASTE with genres returns Based on your taste`() {
        val item = testItem(DiscoveryRowType.TASTE, reasonPayload = "Fantasy, Action")
        val text = discoveryReasonText(
            item = item,
            similarTemplate = "Similar to «%1\$s»",
            trendTemplate = "Trending now",
            nextSeasonTemplate = "Next season",
            tasteTemplate = "Based on your taste: %1\$s",
        )
        text shouldBe "Based on your taste: Fantasy, Action"
    }

    @Test
    fun `TREND returns Trending now or Next season`() {
        val trendItem = testItem(DiscoveryRowType.TREND, reasonPayload = "current")
        discoveryReasonText(
            item = trendItem,
            similarTemplate = "Similar to «%1\$s»",
            trendTemplate = "Trending now",
            nextSeasonTemplate = "Next season",
            tasteTemplate = "Based on your taste: %1\$s",
        ) shouldBe "Trending now"

        val nextSeasonItem = testItem(DiscoveryRowType.TREND, reasonPayload = "next")
        discoveryReasonText(
            item = nextSeasonItem,
            similarTemplate = "Similar to «%1\$s»",
            trendTemplate = "Trending now",
            nextSeasonTemplate = "Next season",
            tasteTemplate = "Based on your taste: %1\$s",
        ) shouldBe "Next season"
    }

    @Test
    fun `SOURCE returns source provider name`() {
        val item = testItem(DiscoveryRowType.SOURCE, provider = "RanobeHub")
        discoveryReasonText(
            item = item,
            similarTemplate = "Similar to «%1\$s»",
            trendTemplate = "Trending now",
            nextSeasonTemplate = "Next season",
            tasteTemplate = "Based on your taste: %1\$s",
        ) shouldBe "RanobeHub"
    }

    @Test
    fun `Null or blank seed or reasons gracefully fall back to defaults without crashes`() {
        val likeWithoutSeed = testItem(DiscoveryRowType.LIKE, seedTitle = null)
        discoveryReasonText(
            item = likeWithoutSeed,
            similarTemplate = "Similar to «%1\$s»",
            trendTemplate = "Trending now",
            nextSeasonTemplate = "Next season",
            tasteTemplate = "Based on your taste: %1\$s",
        ) shouldBe null

        val blankTaste = testItem(DiscoveryRowType.TASTE, reasonPayload = "")
        discoveryReasonText(
            item = blankTaste,
            similarTemplate = "Similar to «%1\$s»",
            trendTemplate = "Trending now",
            nextSeasonTemplate = "Next season",
            tasteTemplate = "Based on your taste: %1\$s",
        ) shouldBe null
    }
}
