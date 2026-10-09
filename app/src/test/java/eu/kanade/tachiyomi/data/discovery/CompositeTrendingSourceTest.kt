package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import java.io.IOException

class CompositeTrendingSourceTest {

    private class MockTrendingSource(
        private val name: String,
        private val items: List<DiscoveryTrendingItem> = emptyList(),
        private val meta: DiscoveryMeta? = null,
        private val shouldFail: Boolean = false,
    ) : DiscoveryTrendingSource {
        var fetchCalled = false
        var fetchMetaCalled = false

        override suspend fun fetch(
            mediaType: DiscoveryMediaType,
            season: TrendSeason,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> {
            fetchCalled = true
            if (shouldFail) throw IOException("$name simulated error")
            return items
        }

        override suspend fun fetchByGenres(
            mediaType: DiscoveryMediaType,
            genres: List<String>,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> {
            fetchCalled = true
            if (shouldFail) throw IOException("$name simulated error")
            return items
        }

        override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
            fetchMetaCalled = true
            if (shouldFail) throw IOException("$name simulated error")
            return meta
        }
    }

    @Test
    fun `anime queries Shikimori first and returns when successful`() = runTest {
        val shikimoriItem =
            DiscoveryTrendingItem(
                "Frieren",
                "frieren",
                "http://shiki/1.jpg",
                1L,
                "current",
                provider = "shikimori_trend",
            )
        val shikimori = MockTrendingSource("shikimori", items = listOf(shikimoriItem))
        val jikan = MockTrendingSource("jikan")
        val anilist = MockTrendingSource("anilist")

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = MockTrendingSource("mangadex"),
            jikan = jikan,
            anilist = anilist,
        )

        val result = composite.fetch(DiscoveryMediaType.ANIME)
        result.size shouldBe 1
        result.first().title shouldBe "Frieren"
        result.first().provider shouldBe "shikimori_trend"
        shikimori.fetchCalled shouldBe true
        jikan.fetchCalled shouldBe true
        anilist.fetchCalled shouldBe false
    }

    @Test
    fun `anime falls back to Jikan and AniList when Shikimori fails`() = runTest {
        val anilistItem =
            DiscoveryTrendingItem("Bleach", "bleach", "http://ani/1.jpg", 2L, "current", provider = "anilist_trend")
        val shikimori = MockTrendingSource("shikimori", shouldFail = true)
        val jikan = MockTrendingSource("jikan", items = emptyList())
        val anilist = MockTrendingSource("anilist", items = listOf(anilistItem))

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = MockTrendingSource("mangadex"),
            jikan = jikan,
            anilist = anilist,
        )

        val result = composite.fetch(DiscoveryMediaType.ANIME)
        result.size shouldBe 1
        result.first().title shouldBe "Bleach"
        shikimori.fetchCalled shouldBe true
        jikan.fetchCalled shouldBe true
        anilist.fetchCalled shouldBe true
    }

    @Test
    fun `manga queries MangaDex first and returns when successful`() = runTest {
        val mangadexItem =
            DiscoveryTrendingItem(
                "Chainsaw Man",
                "chainsaw man",
                "http://md/1.jpg",
                0L,
                null,
                provider = "mangadex_trend",
            )
        val mangadex = MockTrendingSource("mangadex", items = listOf(mangadexItem))
        val shikimori = MockTrendingSource("shikimori")
        val anilist = MockTrendingSource("anilist")

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = mangadex,
            jikan = MockTrendingSource("jikan"),
            anilist = anilist,
        )

        val result = composite.fetch(DiscoveryMediaType.MANGA)
        result.size shouldBe 1
        result.first().title shouldBe "Chainsaw Man"
        result.first().provider shouldBe "mangadex_trend"
        mangadex.fetchCalled shouldBe true
        shikimori.fetchCalled shouldBe true
        anilist.fetchCalled shouldBe false
    }

    @Test
    fun `manga falls back to Shikimori when MangaDex fails`() = runTest {
        val shikimoriItem =
            DiscoveryTrendingItem("Berserk", "berserk", "http://shiki/2.jpg", 2L, null, provider = "shikimori_trend")
        val mangadex = MockTrendingSource("mangadex", shouldFail = true)
        val shikimori = MockTrendingSource("shikimori", items = listOf(shikimoriItem))

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = mangadex,
            jikan = MockTrendingSource("jikan"),
            anilist = MockTrendingSource("anilist"),
        )

        val result = composite.fetch(DiscoveryMediaType.MANGA)
        result.size shouldBe 1
        result.first().title shouldBe "Berserk"
        mangadex.fetchCalled shouldBe true
        shikimori.fetchCalled shouldBe true
    }

    @Test
    fun `genre queries prefer Shikimori and never call MangaDex for manga`() = runTest {
        val shikiItem = DiscoveryTrendingItem(
            "Frieren",
            "frieren",
            null,
            1L,
            null,
            genres = listOf("Fantasy"),
            provider = "shikimori_trend",
        )
        val shikimori = MockTrendingSource("shikimori", items = listOf(shikiItem))
        val mangadex = MockTrendingSource(
            "mangadex",
            items = listOf(DiscoveryTrendingItem("MD", "md", null, 0L, null, provider = "mangadex_trend")),
        )
        val anilist = MockTrendingSource("anilist")

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = mangadex,
            jikan = MockTrendingSource("jikan"),
            anilist = anilist,
        )

        val result = composite.fetchByGenres(DiscoveryMediaType.MANGA, listOf("Фэнтези"))
        result.map { it.title } shouldBe listOf("Frieren")
        shikimori.fetchCalled shouldBe true
        mangadex.fetchCalled shouldBe false
        anilist.fetchCalled shouldBe false
    }

    @Test
    fun `genre queries fall back to AniList when Shikimori has no genre match`() = runTest {
        val aniItem = DiscoveryTrendingItem("LN Title", "ln title", null, 2L, null, provider = "anilist_trend")
        val shikimori = MockTrendingSource("shikimori", items = emptyList())
        val anilist = MockTrendingSource("anilist", items = listOf(aniItem))

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = MockTrendingSource("mangadex"),
            jikan = MockTrendingSource("jikan"),
            anilist = anilist,
        )

        val result = composite.fetchByGenres(DiscoveryMediaType.ANIME, listOf("Экшен"))
        result.first().provider shouldBe "anilist_trend"
        shikimori.fetchCalled shouldBe true
        anilist.fetchCalled shouldBe true
    }

    @Test
    fun `novel fetchMeta skips shikimori even in russian locale`() = runTest {
        val aniMeta = DiscoveryMeta("LN description", listOf("Fantasy"), null)
        val shikimori = MockTrendingSource("shikimori", meta = DiscoveryMeta("wrong", emptyList(), null))
        val anilist = MockTrendingSource("anilist", meta = aniMeta)

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = MockTrendingSource("mangadex"),
            jikan = MockTrendingSource("jikan"),
            anilist = anilist,
            isRussianLocaleProvider = { true },
        )

        val result = composite.fetchMeta("Re:Zero", DiscoveryMediaType.NOVEL)
        result shouldBe aniMeta
        shikimori.fetchMetaCalled shouldBe false
        anilist.fetchMetaCalled shouldBe true
    }

    @Test
    fun `fetchMeta returns metadata from available provider`() = runTest {
        val expectedMeta = DiscoveryMeta("Awesome story", listOf("Action", "Fantasy"), "Alt Title")
        val mangadex = MockTrendingSource("mangadex", meta = expectedMeta)

        val composite = CompositeTrendingSource(
            shikimori = MockTrendingSource("shikimori", shouldFail = true),
            mangadex = mangadex,
            jikan = MockTrendingSource("jikan"),
            anilist = MockTrendingSource("anilist"),
        )

        val result = composite.fetchMeta("Solo Leveling", DiscoveryMediaType.MANGA)
        result shouldBe expectedMeta
        mangadex.fetchMetaCalled shouldBe true
    }

    @Test
    fun `fetchMeta queries Shikimori first when locale is Russian`() = runTest {
        val shikimoriMeta = DiscoveryMeta("Русское описание", listOf("Экшен"), "Оригинальное имя")
        val shikimori = MockTrendingSource("shikimori", meta = shikimoriMeta)
        val mangadex = MockTrendingSource("mangadex", meta = DiscoveryMeta("English desc", emptyList(), null))

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = mangadex,
            jikan = MockTrendingSource("jikan"),
            anilist = MockTrendingSource("anilist"),
            isRussianLocaleProvider = { true },
        )

        val result = composite.fetchMeta("Тайтл Тест", DiscoveryMediaType.MANGA)
        result shouldBe shikimoriMeta
        shikimori.fetchMetaCalled shouldBe true
        mangadex.fetchMetaCalled shouldBe false
    }

    @Test
    fun `fetchMeta auto-translates foreign description when locale is Russian`() = runTest {
        val englishMeta = DiscoveryMeta("A great story about hunters", listOf("Action"), "Original Name")
        val mangadex = MockTrendingSource("mangadex", meta = englishMeta)
        val service = io.mockk.mockk<eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService>()
        io.mockk.coEvery { service.translateSingle("A great story about hunters", "auto", "ru", any()) } returns
            "Отличная история об охотниках"

        val composite = CompositeTrendingSource(
            shikimori = MockTrendingSource("shikimori", shouldFail = true),
            mangadex = mangadex,
            jikan = MockTrendingSource("jikan"),
            anilist = MockTrendingSource("anilist"),
            isRussianLocaleProvider = { true },
            translationServiceProvider = { service },
        )

        val result = composite.fetchMeta("Solo Leveling RU", DiscoveryMediaType.MANGA)
        result?.description shouldBe "Отличная история об охотниках"
    }

    @Test
    fun `trend fetch merges primary and secondary providers deduped`() = runTest {
        val primary = MockTrendingSource(
            "mangadex",
            items = listOf(
                DiscoveryTrendingItem("A", "a", null, 0L, null),
                DiscoveryTrendingItem("B", "b", null, 0L, null),
            ),
        )
        val secondary = MockTrendingSource(
            "shikimori",
            items = listOf(
                DiscoveryTrendingItem("B", "b", null, 0L, null),
                DiscoveryTrendingItem("C", "c", null, 0L, null),
            ),
        )
        val composite = CompositeTrendingSource(shikimori = secondary, mangadex = primary)
        composite.fetch(DiscoveryMediaType.MANGA, TrendSeason.CURRENT, TrendSort.POPULARITY, 1, emptySet())
            .map { it.cleanTitle } shouldBe listOf("a", "b", "c")
    }
}
