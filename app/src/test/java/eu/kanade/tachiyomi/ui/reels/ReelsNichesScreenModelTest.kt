package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.FeedCategoryPage
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsNichesScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeBrowseSource(
        override val id: Long = 4001L,
    ) : AnimeFeedSource, AnimeFeedBrowseSource {
        override val name: String = "Browse Source"
        override val lang: String = "all"

        val categoryRequests = mutableListOf<Pair<Int, String?>>()
        var pages: Int = 2

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage {
            categoryRequests += page to cursor
            return FeedCategoryPage(
                categories = listOf(FeedCategory(id = "c$page", name = "niche$page")),
                hasNextPage = page < pages,
            )
        }

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), false)
    }

    private class PlainFeedSource(override val id: Long = 4002L) : AnimeFeedSource {
        override val name: String = "Plain Feed"
        override val lang: String = "all"
        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)
    }

    private fun sourceManagerOf(vararg sources: AnimeSource): AnimeSourceManager = object : AnimeSourceManager {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
        override val sources: Flow<List<AnimeSource>> = MutableStateFlow(sources.toList())
        override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
        override fun get(sourceKey: Long): AnimeSource? = sources.firstOrNull { it.id == sourceKey }
        override fun getOrStub(sourceKey: Long): AnimeSource = get(sourceKey) ?: sources.first()
        override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
        override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
        override fun getStubSources(): List<StubAnimeSource> = emptyList()
    }

    @Test
    fun `paginates the category directory with the sticky protocol`() = runTest(testDispatcher) {
        val source = FakeBrowseSource()
        val model = ReelsNichesScreenModel(
            sourceId = 4001L,
            sourceManager = sourceManagerOf(source),
            ioDispatcher = testDispatcher,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.categories.shouldHaveSize(1)
        model.state.value.canLoadMore shouldBe true
        model.state.value.isLoading shouldBe false

        model.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.categories.shouldHaveSize(2)
        model.state.value.canLoadMore shouldBe false
        source.categoryRequests shouldBe listOf(1 to null, 2 to null)
    }

    @Test
    fun `source without the browse capability yields the empty state`() = runTest(testDispatcher) {
        val model = ReelsNichesScreenModel(
            sourceId = 4002L,
            sourceManager = sourceManagerOf(PlainFeedSource()),
            ioDispatcher = testDispatcher,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.categories.shouldHaveSize(0)
        model.state.value.canLoadMore shouldBe false
        model.state.value.error shouldBe null
    }
}
