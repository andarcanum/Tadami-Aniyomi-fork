package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeCustomFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.CustomFeedDetail
import eu.kanade.tachiyomi.animesource.model.CustomFeedRef
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
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
class ReelsCustomFeedEditorScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeEditorSource(
        override val id: Long = 3001L,
    ) : AnimeFeedSource, AnimeCustomFeedSource {
        override val name: String = "Editor Source"
        override val lang: String = "all"
        val tagCatalog = listOf("Tag A", "Tag B", "Tag C")
        var failTags = false
        var failDetail = false
        var detail = CustomFeedDetail("My Feed", listOf("Tag A"))
        val createRequests = mutableListOf<Pair<String, List<String>>>()
        val updateRequests = mutableListOf<Triple<String, String, List<String>>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getCustomFeeds(): List<CustomFeedRef> = emptyList()

        override suspend fun getCustomFeed(id: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getCustomFeedTags(): List<String> {
            if (failTags) throw RuntimeException("tags down")
            return tagCatalog
        }

        override suspend fun getCustomFeedDetail(id: String): CustomFeedDetail {
            if (failDetail) throw RuntimeException("detail down")
            return detail
        }

        override suspend fun createCustomFeed(name: String, tags: List<String>): CustomFeedRef {
            createRequests += name to tags
            return CustomFeedRef("new-1", name)
        }

        override suspend fun updateCustomFeed(id: String, name: String, tags: List<String>): Boolean {
            updateRequests += Triple(id, name, tags)
            return true
        }

        override suspend fun deleteCustomFeed(id: String): Boolean = true
    }

    private fun managerOf(vararg sources: AnimeSource): AnimeSourceManager = object : AnimeSourceManager {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
        override val sources: Flow<List<AnimeSource>> = MutableStateFlow(sources.toList())
        override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
        override fun get(sourceKey: Long): AnimeSource? = sources.firstOrNull { it.id == sourceKey }
        override fun getOrStub(sourceKey: Long): AnimeSource = get(sourceKey) ?: sources.first()
        override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
        override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
        override fun getStubSources(): List<StubAnimeSource> = emptyList()
    }

    private fun buildEditor(
        source: FakeEditorSource,
        feedId: String? = null,
        initialName: String? = null,
    ) = ReelsCustomFeedEditorScreenModel(
        sourceId = source.id,
        feedId = feedId,
        initialName = initialName,
        sourceManager = managerOf(source),
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `create mode loads the tag catalog and toggles selection`() = runTest(testDispatcher) {
        val source = FakeEditorSource()
        val model = buildEditor(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isLoading shouldBe false
        model.state.value.name shouldBe "" // blank name keeps Save disabled
        model.state.value.allTags shouldBe source.tagCatalog
        model.state.value.selectedTags.isEmpty() shouldBe true

        model.toggleTag("Tag A")
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.selectedTags shouldBe setOf("Tag A")

        model.toggleTag("Tag A")
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.selectedTags.isEmpty() shouldBe true
    }

    @Test
    fun `edit mode prefills name and tags from the detail`() = runTest(testDispatcher) {
        val source = FakeEditorSource()
        val model = buildEditor(source, feedId = "f1", initialName = "stale")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isLoading shouldBe false
        model.state.value.name shouldBe "My Feed"
        model.state.value.selectedTags shouldBe setOf("Tag A")
        model.state.value.error shouldBe null
    }

    @Test
    fun `save in create mode submits the name and selected tags`() = runTest(testDispatcher) {
        val source = FakeEditorSource()
        val model = buildEditor(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.setName("Fresh Feed")
        model.toggleTag("Tag B")
        model.save()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isSaved shouldBe true
        model.state.value.isSaving shouldBe false
        source.createRequests shouldBe listOf("Fresh Feed" to listOf("Tag B"))
    }

    @Test
    fun `save in edit mode submits an update for the feed id`() = runTest(testDispatcher) {
        val source = FakeEditorSource()
        val model = buildEditor(source, feedId = "f1")
        testDispatcher.scheduler.advanceUntilIdle()

        model.setName("Renamed")
        model.save()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isSaved shouldBe true
        source.updateRequests shouldBe listOf(Triple("f1", "Renamed", listOf("Tag A")))
        source.createRequests.isEmpty() shouldBe true
    }

    @Test
    fun `save with a blank name is a no-op`() = runTest(testDispatcher) {
        val source = FakeEditorSource()
        val model = buildEditor(source, initialName = "   ")
        testDispatcher.scheduler.advanceUntilIdle()

        model.save()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isSaved shouldBe false
        source.createRequests.isEmpty() shouldBe true
        source.updateRequests.isEmpty() shouldBe true
    }

    @Test
    fun `failed tag catalog load surfaces an error and keeps save disabled`() = runTest(testDispatcher) {
        val source = FakeEditorSource()
        source.failTags = true
        val model = buildEditor(source, initialName = "Named")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isLoading shouldBe false
        model.state.value.error shouldBe "Failed to load tags"
        model.state.value.allTags.isEmpty() shouldBe true
    }
}
