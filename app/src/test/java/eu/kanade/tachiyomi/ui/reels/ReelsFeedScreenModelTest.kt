package eu.kanade.tachiyomi.ui.reels

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeCreatorFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
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
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsFeedScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class MapPreferenceStore : PreferenceStore {
        private val map = mutableMapOf<String, Any>()

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            createPref(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            createPref(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            createPref(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            createPref(key, defaultValue)

        override fun getString(key: String, defaultValue: String): Preference<String> =
            createPref(key, defaultValue)

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            createPref(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = createPref(key, defaultValue)

        override fun getAll(): Map<String, *> = map

        @Suppress("UNCHECKED_CAST")
        private fun <T> createPref(key: String, defaultValue: T): Preference<T> {
            return object : Preference<T> {
                override fun key(): String = key
                override fun get(): T = (map[key] as? T) ?: defaultValue
                override fun set(value: T) {
                    if (value == null) map.remove(key) else map[key] = value as Any
                }
                override fun isSet(): Boolean = map.containsKey(key)
                override fun delete() {
                    map.remove(key)
                }
                override fun defaultValue(): T = defaultValue
                override fun changes(): Flow<T> = MutableStateFlow(get())
                override fun stateIn(scope: kotlinx.coroutines.CoroutineScope): StateFlow<T> =
                    MutableStateFlow(get()).asStateFlow()
            }
        }
    }

    private class FakeReelsFavoriteRepository : ReelsFavoriteRepository {
        val favorites = mutableMapOf<Pair<String, Long>, ReelsFavorite>()

        // One-shot gate for getIdsBySource: parks the first call until released and returns
        // the snapshot, simulating a favorites DB read racing UI actions.
        private var idGate: CompletableDeferred<Unit>? = null
        private var idSnapshot: List<String>? = null

        fun parkNextIds(snapshot: List<String>): CompletableDeferred<Unit> =
            CompletableDeferred<Unit>().also {
                idGate = it
                idSnapshot = snapshot
            }

        override fun subscribeAll(): Flow<List<ReelsFavorite>> = MutableStateFlow(favorites.values.toList())

        override suspend fun getAll(): List<ReelsFavorite> = favorites.values.toList()

        override suspend fun getBySource(sourceId: Long): List<ReelsFavorite> =
            favorites.values.filter { it.sourceId == sourceId }

        override suspend fun getIdsBySource(sourceId: Long): List<String> {
            val gate = idGate
            if (gate != null) {
                idGate = null
                gate.await()
                return idSnapshot.orEmpty()
            }
            return favorites.values.filter { it.sourceId == sourceId }.map { it.videoId }
        }

        override suspend fun insert(favorite: ReelsFavorite) {
            favorites[favorite.videoId to favorite.sourceId] = favorite
        }

        override suspend fun insertAll(favorites: List<ReelsFavorite>) {
            favorites.forEach { insert(it) }
        }

        override suspend fun delete(videoId: String, sourceId: Long) {
            favorites.remove(videoId to sourceId)
        }
    }

    @Test
    fun `loads feed, toggles controls, persists queries and switches sources`() = runTest(testDispatcher) {
        val sampleItem1 = ShortVideoItem(
            id = "vid-1",
            title = "Test Reel 1",
            author = "Alice",
            videoUrl = "https://example.com/sd1.mp4",
            videoUrlHd = "https://example.com/hd1.mp4",
            posterUrl = "https://example.com/poster1.jpg",
            durationSec = 10f,
            hasAudio = true,
        )

        val sampleItem2 = ShortVideoItem(
            id = "vid-2",
            title = "Test Reel 2",
            author = "Bob",
            videoUrl = "https://example.com/sd2.mp4",
            videoUrlHd = "https://example.com/hd2.mp4",
            posterUrl = "https://example.com/poster2.jpg",
            durationSec = 12f,
            hasAudio = true,
        )

        class SortFilter : AnimeFilter.Select<String>("Sort", arrayOf("Trending", "Recent"), 0)

        val feedSource1 = object : AnimeFeedSource {
            override val id: Long = 101L
            override val name: String = "RedGIFs (Reels)"
            override val lang: String = "all"

            override fun getFilterList(): AnimeFilterList = AnimeFilterList(SortFilter())

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                return FeedPage(videos = listOf(sampleItem1), hasNextPage = true)
            }

            override suspend fun getSearchFeed(
                page: Int,
                cursor: String?,
                query: String,
                filters: AnimeFilterList,
            ): FeedPage {
                return FeedPage(videos = listOf(sampleItem1), hasNextPage = false)
            }
        }

        val feedSource2 = object : AnimeFeedSource {
            override val id: Long = 102L
            override val name: String = "TikTok (Reels)"
            override val lang: String = "all"

            override fun getFilterList(): AnimeFilterList = AnimeFilterList()

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                return FeedPage(videos = listOf(sampleItem2), hasNextPage = true)
            }

            override suspend fun getSearchFeed(
                page: Int,
                cursor: String?,
                query: String,
                filters: AnimeFilterList,
            ): FeedPage {
                return FeedPage(videos = listOf(sampleItem2), hasNextPage = false)
            }
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource1, feedSource2))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = when (sourceKey) {
                101L -> feedSource1
                102L -> feedSource2
                else -> null
            }
            override fun getOrStub(sourceKey: Long): AnimeSource = get(sourceKey) ?: feedSource1
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val sourcePreferences = SourcePreferences(MapPreferenceStore())
        val fakeFavorites = FakeReelsFavoriteRepository()

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 101L,
            sourceManager = fakeSourceManager,
            sourcePreferences = sourcePreferences,
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = fakeFavorites,
            reelsFollowRepository = FakeReelsFollowRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.items.first().id shouldBe "vid-1"
        screenModel.state.value.currentSourceId shouldBe 101L
        screenModel.state.value.sourceName shouldBe "RedGIFs (Reels)"
        sourcePreferences.lastUsedReelsSource().get() shouldBe 101L

        // Test Like toggle + persistence
        screenModel.toggleLike(sampleItem1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.likedIds.contains("vid-1") shouldBe true
        fakeFavorites.favorites.keys shouldBe setOf("vid-1" to 101L)

        screenModel.toggleLike(sampleItem1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.likedIds.contains("vid-1") shouldBe false
        fakeFavorites.favorites.isEmpty() shouldBe true

        // Test Mute toggle
        val initialMuted = screenModel.state.value.isMuted
        screenModel.toggleMute()
        screenModel.state.value.isMuted shouldBe !initialMuted

        // Test Auto-advance toggle
        screenModel.state.value.isAutoAdvance shouldBe true
        sourcePreferences.autoAdvanceReels().get() shouldBe true
        screenModel.toggleAutoAdvance()
        screenModel.state.value.isAutoAdvance shouldBe false
        sourcePreferences.autoAdvanceReels().get() shouldBe false

        // Test Crop Mode toggle
        screenModel.state.value.isCropMode shouldBe false
        screenModel.toggleCropMode()
        screenModel.state.value.isCropMode shouldBe true
        sourcePreferences.reelsCropMode().get() shouldBe true

        // Test Filter selection and persistence
        val filters = screenModel.state.value.filters
        val sortFilter = filters.filterIsInstance<SortFilter>().first()
        sortFilter.state = 1 // Change from Trending (0) to Recent (1)
        screenModel.applyFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        sourcePreferences.lastReelsFilter(101L).get() shouldBe "0=1"

        // Test Search & Query Persistence
        screenModel.search("cosplay")
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.searchQuery shouldBe "cosplay"
        sourcePreferences.lastReelsQuery(101L).get() shouldBe "cosplay"

        // Switch to Source 2 (TikTok)
        screenModel.switchSource(102L)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.currentSourceId shouldBe 102L
        screenModel.state.value.sourceName shouldBe "TikTok (Reels)"
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.items.first().id shouldBe "vid-2"
        sourcePreferences.lastUsedReelsSource().get() shouldBe 102L

        // Switch back to Source 1 (RedGIFs) and verify filter is restored
        screenModel.switchSource(101L)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.currentSourceId shouldBe 101L
        val restoredSortFilter = screenModel.state.value.filters.filterIsInstance<SortFilter>().first()
        restoredSortFilter.state shouldBe 1
    }

    @Test
    fun `does not persist reels history while incognito`() = runTest(testDispatcher) {
        val incognitoItem = ShortVideoItem(
            id = "vid-x",
            videoUrl = "https://example.com/x.mp4",
            posterUrl = "https://example.com/x.jpg",
        )
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 201L
            override val name: String = "Incognito Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(videos = listOf(incognitoItem), hasNextPage = false)
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = if (sourceKey == 201L) feedSource else null
            override fun getOrStub(sourceKey: Long): AnimeSource = feedSource
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val sourcePreferences = SourcePreferences(MapPreferenceStore())
        val fakeFavorites = FakeReelsFavoriteRepository()

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 201L,
            sourceManager = fakeSourceManager,
            sourcePreferences = sourcePreferences,
            ioDispatcher = testDispatcher,
            isIncognito = { true },
            sourceIconProvider = { null },
            reelsFavoriteRepository = fakeFavorites,
            reelsFollowRepository = FakeReelsFollowRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldHaveSize(1)

        screenModel.search("cosplay")
        testDispatcher.scheduler.advanceUntilIdle()

        // Liking still works in-session but must not be persisted while incognito.
        screenModel.toggleLike(incognitoItem)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.likedIds.contains("vid-x") shouldBe true
        fakeFavorites.favorites.isEmpty() shouldBe true

        // The in-session feed keeps working normally...
        screenModel.state.value.searchQuery shouldBe "cosplay"
        // ...but nothing leaks into persisted history while incognito.
        sourcePreferences.lastUsedReelsSource().get() shouldBe -1L
        sourcePreferences.lastReelsQuery(201L).get() shouldBe ""
        sourcePreferences.lastReelsFilter(201L).get() shouldBe ""
    }

    @Test
    fun `rapid double loadNextPageIfNeeded fetches the next page only once`() = runTest(testDispatcher) {
        val requestedPages = mutableListOf<Int>()
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 401L
            override val name: String = "Pager Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                requestedPages += page
                val videos = (0 until 5).map { idx ->
                    ShortVideoItem(
                        id = "vid-p$page-$idx",
                        videoUrl = "https://example.com/p${page}v$idx.mp4",
                        posterUrl = "https://example.com/p${page}v$idx.jpg",
                    )
                }
                return FeedPage(videos = videos, hasNextPage = true)
            }
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = if (sourceKey == 401L) feedSource else null
            override fun getOrStub(sourceKey: Long): AnimeSource = feedSource
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 401L,
            sourceManager = fakeSourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeReelsFavoriteRepository(),
            reelsFollowRepository = FakeReelsFollowRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        requestedPages shouldBe listOf(1)

        // The pager fires two page-changed events before the first load job's coroutine is
        // dispatched; only one append fetch may be launched.
        screenModel.onPageChanged(3)
        screenModel.onPageChanged(3)
        testDispatcher.scheduler.advanceUntilIdle()

        requestedPages shouldBe listOf(1, 2)
    }

    @Test
    fun `like persists even when the item was already dropped from the feed`() = runTest(testDispatcher) {
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 701L
            override val name: String = "Displaced Item Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(emptyList(), false)
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = if (sourceKey == 701L) feedSource else null
            override fun getOrStub(sourceKey: Long): AnimeSource = feedSource
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val fakeFavorites = FakeReelsFavoriteRepository()
        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 701L,
            sourceManager = fakeSourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = fakeFavorites,
            reelsFollowRepository = FakeReelsFollowRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // The video page passes the item it renders; the model must not re-find it in
        // state, where a concurrent refresh may have already displaced it.
        screenModel.toggleLike(
            ShortVideoItem(
                id = "ghost",
                videoUrl = "https://example.com/ghost.mp4",
                posterUrl = "https://example.com/ghost.jpg",
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.likedIds.contains("ghost") shouldBe true
        fakeFavorites.favorites.keys shouldBe setOf("ghost" to 701L)
    }

    @Test
    fun `unlike during favorites load is not resurrected by the stale snapshot`() = runTest(testDispatcher) {
        val gateItem = ShortVideoItem(
            id = "vid-1",
            videoUrl = "https://example.com/v1.mp4",
            posterUrl = "https://example.com/v1.jpg",
        )
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 501L
            override val name: String = "Gate Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(videos = listOf(gateItem), hasNextPage = false)
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = if (sourceKey == 501L) feedSource else null
            override fun getOrStub(sourceKey: Long): AnimeSource = feedSource
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val fakeFavorites = FakeReelsFavoriteRepository()
        // Favorites read races the UI: it parks and later reports vid-1 as persisted.
        val gate = fakeFavorites.parkNextIds(listOf("vid-1"))

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 501L,
            sourceManager = fakeSourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = fakeFavorites,
            reelsFollowRepository = FakeReelsFollowRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(1)

        // Like, then unlike while the favorites read is still parked: the DB snapshot is
        // stale the moment it is captured.
        screenModel.toggleLike(gateItem)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.toggleLike(gateItem)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.likedIds.contains("vid-1") shouldBe false
        fakeFavorites.favorites.isEmpty() shouldBe true

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // The stale persisted snapshot must not resurrect the unliked video.
        screenModel.state.value.likedIds.contains("vid-1") shouldBe false
    }

    @Test
    fun `late favorites result from the previous source does not leak into the new source`() = runTest(testDispatcher) {
        fun feedSource(id: Long, name: String) = object : AnimeFeedSource {
            override val id: Long = id
            override val name: String = name
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage = FeedPage(
                emptyList(),
                false,
            )
        }

        val sourceA = feedSource(601L, "Feed A")
        val sourceB = feedSource(602L, "Feed B")

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(sourceA, sourceB))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = when (sourceKey) {
                601L -> sourceA
                602L -> sourceB
                else -> null
            }
            override fun getOrStub(sourceKey: Long): AnimeSource = sourceA
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val fakeFavorites = FakeReelsFavoriteRepository()
        val gate = fakeFavorites.parkNextIds(listOf("vid-a1"))

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 601L,
            sourceManager = fakeSourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = fakeFavorites,
            reelsFollowRepository = FakeReelsFollowRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Switch away before source A's favorites read resolves.
        screenModel.switchSource(602L)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.currentSourceId shouldBe 602L

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.likedIds.contains("vid-a1") shouldBe false
    }

    @Test
    fun `append failure with non-empty feed sets pageError and next success clears it`() = runTest(testDispatcher) {
        val requestedPages = mutableListOf<Int>()
        // A failed append does not consume the page cursor, so the next trigger retries the
        // same page; only the first attempt of page 2 fails (transient CDN error).
        val failedAttempts = mutableSetOf<Int>()
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 801L
            override val name: String = "Failing Append Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                requestedPages += page
                if (page == 2 && failedAttempts.add(page)) throw RuntimeException("CDN exploded")
                return FeedPage(
                    videos = listOf(
                        ShortVideoItem(
                            id = "vid-p$page",
                            videoUrl = "https://example.com/p$page.mp4",
                            posterUrl = "https://example.com/p$page.jpg",
                        ),
                    ),
                    hasNextPage = true,
                )
            }
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = if (sourceKey == 801L) feedSource else null
            override fun getOrStub(sourceKey: Long): AnimeSource = feedSource
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 801L,
            sourceManager = fakeSourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeReelsFavoriteRepository(),
            reelsFollowRepository = FakeReelsFollowRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(1)

        // Page 2 fails while the feed is non-empty: the error must be transient (pageError),
        // not the full-screen error, and the feed must stay usable.
        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.pageError shouldBe "CDN exploded"
        screenModel.state.value.error shouldBe null
        screenModel.state.value.isLoading shouldBe false
        screenModel.state.value.items.shouldHaveSize(1)

        // The user swipes again; the retried append succeeds and clears the stale page error.
        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.pageError shouldBe null
        screenModel.state.value.items.shouldHaveSize(2)
        // The failed page was retried, not skipped.
        requestedPages shouldBe listOf(1, 2, 2)
    }

    @Test
    fun `pagination stops when hasNextPage is false`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(901L) { page ->
            if (page == 1) {
                FeedPage((0 until 5).map { videoItem("stop-p1-$it") }, hasNextPage = true)
            } else {
                FeedPage((0 until 2).map { videoItem("stop-p2-$it") }, hasNextPage = false)
            }
        }
        val screenModel = buildModel(sourceId = 901L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(5)

        screenModel.onPageChanged(3)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(7)

        // Further page-changed events must not fetch again once the source reported the end.
        screenModel.onPageChanged(6)
        testDispatcher.scheduler.advanceUntilIdle()

        source.requestedPages shouldBe listOf(1, 2)
        screenModel.state.value.canLoadMore shouldBe false
    }

    @Test
    fun `duplicate ids across pages are deduped`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(902L) { page ->
            if (page == 1) {
                FeedPage(listOf(videoItem("dup")), hasNextPage = true)
            } else {
                FeedPage(listOf(videoItem("dup"), videoItem("fresh")), hasNextPage = false)
            }
        }
        val screenModel = buildModel(sourceId = 902L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()

        source.requestedPages shouldBe listOf(1, 2)
        screenModel.state.value.items.map { it.id } shouldBe listOf("dup", "fresh")
    }

    @Test
    fun `clearSearch restores the base feed and scroll target`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(
            id = 903L,
            feedProvider = {
                FeedPage(listOf(videoItem("base-a"), videoItem("base-b")), hasNextPage = false)
            },
            searchProvider = {
                FeedPage(listOf(videoItem("search-result")), hasNextPage = false)
            },
        )
        val screenModel = buildModel(sourceId = 903L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(2)

        // The user was on the second video before searching.
        screenModel.onPageChanged(1)
        screenModel.search("tag")
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.searchQuery shouldBe "tag"

        screenModel.clearSearch()
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.searchQuery shouldBe ""
        screenModel.state.value.items.map { it.id } shouldBe listOf("base-a", "base-b")
        screenModel.state.value.targetPageIndex shouldBe 1
    }

    @Test
    fun `non-feed source id sets an error and stays non-critical`() = runTest(testDispatcher) {
        val notAFeed = object : AnimeSource {
            override val id: Long = 999L
            override val name: String = "Catalogue Source"
            override val lang: String = "all"

            override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
            override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()
            override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()
            override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
        }

        val screenModel = buildModel(sourceId = 999L, manager = sourceManagerOf(notAFeed))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.error shouldBe "Source is not a video feed source"
        screenModel.state.value.isLoading shouldBe false
        screenModel.state.value.items.shouldHaveSize(0)
    }

    @Test
    fun `malformed persisted filters do not crash source switch`() = runTest(testDispatcher) {
        class SortFilter : AnimeFilter.Select<String>("Sort", arrayOf("Trending", "Recent"), 0)
        class NsfwFilter : AnimeFilter.CheckBox("Nsfw", false)

        val source = object : AnimeFeedSource {
            override val id: Long = 904L
            override val name: String = "Filter Feed"
            override val lang: String = "all"

            override fun getFilterList(): AnimeFilterList = AnimeFilterList(SortFilter(), NsfwFilter())

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(listOf(videoItem("filtered")), hasNextPage = false)
        }

        val preferences = SourcePreferences(MapPreferenceStore())
        preferences.lastReelsFilter(904L).set(";;garbage=x;0=zzz;1=9:true")

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 904L,
            sourceManager = sourceManagerOf(source),
            sourcePreferences = preferences,
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeReelsFavoriteRepository(),
            reelsFollowRepository = FakeReelsFollowRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // The malformed values are skipped; the feed still loads.
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.filters.filterIsInstance<SortFilter>().first().state shouldBe 0
        screenModel.state.value.filters.filterIsInstance<NsfwFilter>().first().state shouldBe false
    }

    @Test
    fun `incognito unlike still reaches the database`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(905L) { FeedPage(listOf(videoItem("vid-x")), hasNextPage = false) }
        val fakeFavorites = FakeReelsFavoriteRepository()
        fakeFavorites.favorites["vid-x" to 905L] = ReelsFavorite(
            videoId = "vid-x",
            sourceId = 905L,
            title = null,
            author = null,
            videoUrl = "https://example.com/vid-x.mp4",
            videoUrlHd = null,
            posterUrl = "https://example.com/vid-x.jpg",
            posterUrlVertical = null,
            webUrl = null,
            durationSec = null,
            hasAudio = true,
            addedAt = Date(1_000L),
        )

        val screenModel =
            buildModel(sourceId = 905L, manager = sourceManagerOf(source), repository = fakeFavorites, incognito = true)
        testDispatcher.scheduler.advanceUntilIdle()

        // The persisted like is loaded, then unliked: the removal must persist even though
        // new likes are blocked in incognito.
        screenModel.state.value.likedIds.contains("vid-x") shouldBe true
        screenModel.toggleLike(videoItem("vid-x"))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.likedIds.contains("vid-x") shouldBe false
        fakeFavorites.favorites.isEmpty() shouldBe true
    }

    @Test
    fun `offline playlist targets the initial page`() = runTest(testDispatcher) {
        val favorites = listOf(
            offlineFavorite("off-a"),
            offlineFavorite("off-b"),
        )

        val screenModel = buildModel(
            sourceId = 301L,
            manager = sourceManagerOf(),
            initialFavorites = favorites,
            initialPage = 1,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isOffline shouldBe true
        screenModel.state.value.items.map { it.id } shouldBe listOf("off-a", "off-b")
        screenModel.state.value.targetPageIndex shouldBe 1
    }

    @Test
    fun `onPageChanged updates activeIndex and triggers pagination`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(906L) { page ->
            FeedPage((0 until 5).map { videoItem("adv-p$page-$it") }, hasNextPage = true)
        }
        val screenModel = buildModel(sourceId = 906L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.onPageChanged(4)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.activeIndex shouldBe 4
        screenModel.state.value.isPlaying shouldBe true
        source.requestedPages shouldBe listOf(1, 2)
    }

    private fun videoItem(id: String) = ShortVideoItem(
        id = id,
        videoUrl = "https://example.com/$id.mp4",
        posterUrl = "https://example.com/$id.jpg",
    )

    private fun offlineFavorite(videoId: String) = ReelsFavorite(
        videoId = videoId,
        sourceId = 301L,
        title = "Saved $videoId",
        author = "Alice",
        videoUrl = "https://example.com/$videoId.mp4",
        videoUrlHd = null,
        posterUrl = "https://example.com/$videoId.jpg",
        posterUrlVertical = null,
        webUrl = null,
        durationSec = 9.0,
        hasAudio = true,
        addedAt = Date(0),
    )

    private class RecordingFeedSource(
        override val id: Long,
        override val name: String = "Feed $id",
        private val searchProvider: ((Int) -> FeedPage)? = null,
        private val feedProvider: (Int) -> FeedPage,
    ) : AnimeFeedSource {
        val requestedPages = mutableListOf<Int>()

        override val lang: String = "all"

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
            requestedPages += page
            return feedProvider(page)
        }

        override suspend fun getSearchFeed(
            page: Int,
            cursor: String?,
            query: String,
            filters: AnimeFilterList,
        ): FeedPage {
            requestedPages += page
            return (searchProvider ?: feedProvider)(page)
        }
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

    private fun buildModel(
        sourceId: Long,
        manager: AnimeSourceManager,
        repository: ReelsFavoriteRepository = FakeReelsFavoriteRepository(),
        followRepository: ReelsFollowRepository = FakeReelsFollowRepository(),
        incognito: Boolean = false,
        initialFavorites: List<ReelsFavorite> = emptyList(),
        initialPage: Int = 0,
        creator: String? = null,
        followingFeed: Boolean = false,
        preferences: SourcePreferences = SourcePreferences(MapPreferenceStore()),
        sessionSound: ReelsSessionSoundState = ReelsSessionSoundState(),
    ) = ReelsFeedScreenModel(
        initialSourceId = sourceId,
        initialFavorites = initialFavorites,
        initialPage = initialPage,
        creator = creator,
        followingFeed = followingFeed,
        sourceManager = manager,
        sourcePreferences = preferences,
        ioDispatcher = testDispatcher,
        isIncognito = { incognito },
        sourceIconProvider = { null },
        reelsFavoriteRepository = repository,
        reelsFollowRepository = followRepository,
        sessionSound = sessionSound,
    )

    @Test
    fun `feed position is persisted per source and restored on re-entry`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(907L) {
            FeedPage((0 until 5).map { videoItem("res-p1-$it") }, hasNextPage = true)
        }
        val preferences = SourcePreferences(MapPreferenceStore())
        val first = buildModel(sourceId = 907L, manager = sourceManagerOf(source), preferences = preferences)
        testDispatcher.scheduler.advanceUntilIdle()

        first.onPageChanged(3)
        testDispatcher.scheduler.advanceUntilIdle()
        preferences.lastReelsPosition(907L).get() shouldBe 3

        // A fresh model (process restart / re-entry) resumes where the user left off.
        val second = buildModel(sourceId = 907L, manager = sourceManagerOf(source), preferences = preferences)
        testDispatcher.scheduler.advanceUntilIdle()

        second.state.value.targetPageIndex shouldBe 3
    }

    @Test
    fun `incognito session does not persist feed position`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(908L) {
            FeedPage((0 until 5).map { videoItem("inc-p1-$it") }, hasNextPage = true)
        }
        val preferences = SourcePreferences(MapPreferenceStore())
        val first = buildModel(
            sourceId = 908L,
            manager = sourceManagerOf(source),
            preferences = preferences,
            incognito = true,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        first.onPageChanged(3)
        testDispatcher.scheduler.advanceUntilIdle()

        preferences.lastReelsPosition(908L).get() shouldBe 0

        val second = buildModel(sourceId = 908L, manager = sourceManagerOf(source), preferences = preferences)
        testDispatcher.scheduler.advanceUntilIdle()

        second.state.value.targetPageIndex shouldBe 0
    }

    @Test
    fun `undecided session starts muted with the unmute hint until the user decides`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(911L) { FeedPage(listOf(videoItem("mute-a")), hasNextPage = false) }
        val preferences = SourcePreferences(MapPreferenceStore())
        val sessionSound = ReelsSessionSoundState()

        val first = buildModel(
            sourceId = 911L,
            manager = sourceManagerOf(source),
            preferences = preferences,
            sessionSound = sessionSound,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Fresh launch: always muted, hint visible — regardless of the persisted preference.
        first.state.value.isMuted shouldBe true
        first.state.value.showUnmuteHint shouldBe true

        first.toggleMute()
        first.state.value.isMuted shouldBe false
        first.state.value.showUnmuteHint shouldBe false
        preferences.reelsMuted().get() shouldBe false

        // Re-entry within the same session respects the user's decision.
        val second = buildModel(
            sourceId = 911L,
            manager = sourceManagerOf(source),
            preferences = preferences,
            sessionSound = sessionSound,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        second.state.value.isMuted shouldBe false
        second.state.value.showUnmuteHint shouldBe false
    }

    @Test
    fun `unmute hint clears on dismiss without deciding the session`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(912L) { FeedPage(listOf(videoItem("mute-b")), hasNextPage = false) }
        val sessionSound = ReelsSessionSoundState()

        val model = buildModel(
            sourceId = 912L,
            manager = sourceManagerOf(source),
            sessionSound = sessionSound,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.dismissUnmuteHint()
        model.state.value.showUnmuteHint shouldBe false
        // Dismissing is not deciding: a new model shows the hint again.
        val second = buildModel(
            sourceId = 912L,
            manager = sourceManagerOf(source),
            sessionSound = sessionSound,
        )
        testDispatcher.scheduler.advanceUntilIdle()
        second.state.value.showUnmuteHint shouldBe true
    }

    @Test
    fun `data saver toggle persists and defaults to on`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(913L) { FeedPage(listOf(videoItem("ds")), hasNextPage = false) }
        val preferences = SourcePreferences(MapPreferenceStore())

        preferences.reelsDataSaverMetered().set(false)
        val model = buildModel(sourceId = 913L, manager = sourceManagerOf(source), preferences = preferences)
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.dataSaverMetered shouldBe false
        model.toggleDataSaver()
        model.state.value.dataSaverMetered shouldBe true
        preferences.reelsDataSaverMetered().get() shouldBe true
    }

    @Test
    fun `offline favorites playlist opens liked videos without a live source`() = runTest(testDispatcher) {
        val favorite = ReelsFavorite(
            videoId = "vid-f",
            sourceId = 301L,
            title = "Saved reel",
            author = "Alice",
            videoUrl = "https://example.com/f.mp4",
            videoUrlHd = null,
            posterUrl = "https://example.com/f.jpg",
            posterUrlVertical = null,
            webUrl = "https://www.redgifs.com/watch/vid-f",
            durationSec = 9.0,
            hasAudio = true,
            addedAt = Date(0),
        )

        // No installed feed source for 301: offline mode must not depend on it.
        val emptySourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(emptyList())
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = null
            override fun getOrStub(sourceKey: Long): AnimeSource =
                StubAnimeSource(id = sourceKey, lang = "", name = "")
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 301L,
            initialFavorites = listOf(favorite),
            initialPage = 0,
            sourceManager = emptySourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeReelsFavoriteRepository(),
            reelsFollowRepository = FakeReelsFollowRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isOffline shouldBe true
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.items.first().id shouldBe "vid-f"
        screenModel.state.value.items.first().videoUrl shouldBe "https://example.com/f.mp4"
        screenModel.state.value.likedIds shouldBe setOf("vid-f")

        // Network entry points are no-ops in offline mode.
        screenModel.loadFeed(reset = true)
        screenModel.search("cosplay")
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.searchQuery shouldBe ""
    }

    @Test
    fun `first non-null nextCursor locks cursor mode and token is echoed on the next page`() = runTest(testDispatcher) {
        val source = RecordingCursorFeedSource(1001L) { page, _ ->
            if (page == 1) {
                FeedPage(listOf(videoItem("cv1")), hasNextPage = true, nextCursor = "c1")
            } else {
                FeedPage(listOf(videoItem("cv2")), hasNextPage = false, nextCursor = null)
            }
        }
        val screenModel = buildModel(sourceId = 1001L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.cursorMode shouldBe true
        screenModel.state.value.nextCursor shouldBe "c1"

        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()

        source.requested shouldBe listOf(1 to null as String?, 2 to "c1")
        screenModel.state.value.cursorMode shouldBe true
        screenModel.state.value.canLoadMore shouldBe false
    }

    @Test
    fun `append failure in cursor mode retries the same cursor, not a skipped page`() = runTest(testDispatcher) {
        val failed = booleanArrayOf(false)
        val source = RecordingCursorFeedSource(1002L) { page, _ ->
            if (page == 2 && !failed[0]) {
                failed[0] = true
                throw RuntimeException("CDN exploded")
            }
            FeedPage(listOf(videoItem("cf$page")), hasNextPage = true, nextCursor = "c$page")
        }
        val screenModel = buildModel(sourceId = 1002L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.onPageChanged(0) // page 2 fails
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.pageError shouldBe "CDN exploded"

        screenModel.onPageChanged(0) // retry page 2 with the same cursor "c1"
        testDispatcher.scheduler.advanceUntilIdle()
        source.requested shouldBe listOf(1 to null as String?, 2 to "c1", 2 to "c1")
    }

    @Test
    fun `cursor mode with null cursor and hasNextPage true is a violation and stops pagination`() = runTest(
        testDispatcher,
    ) {
        val source = RecordingCursorFeedSource(1003L) { page, _ ->
            if (page == 1) {
                FeedPage(listOf(videoItem("v1")), hasNextPage = true, nextCursor = "c1")
            } else {
                FeedPage(listOf(videoItem("v2")), hasNextPage = true, nextCursor = null)
            }
        }
        val screenModel = buildModel(sourceId = 1003L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.canLoadMore shouldBe false
        screenModel.state.value.items.shouldHaveSize(2)
    }

    @Test
    fun `clearSearch restores the snapshotted cursor mode and token`() = runTest(testDispatcher) {
        val source = RecordingCursorFeedSource(1004L) { page, _ ->
            FeedPage(listOf(videoItem("cv$page")), hasNextPage = true, nextCursor = "bc$page")
        }
        val screenModel = buildModel(sourceId = 1004L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.search("x")
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.clearSearch()
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.cursorMode shouldBe true
        screenModel.state.value.nextCursor shouldBe "bc2"
    }

    @Test
    fun `search during a cursor session starts a new generation with a null cursor`() = runTest(testDispatcher) {
        val source = RecordingCursorFeedSource(1005L) { page, _ ->
            FeedPage(listOf(videoItem("cs$page")), hasNextPage = true, nextCursor = "s$page")
        }
        val screenModel = buildModel(sourceId = 1005L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle() // (1, null)
        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle() // (2, "s1") — cursor mode locked
        screenModel.state.value.cursorMode shouldBe true

        screenModel.search("tag")
        testDispatcher.scheduler.advanceUntilIdle()

        // The search generation must restart at page 1 with the token dropped; handing the
        // old feed's cursor to getSearchFeed would skip straight past page 1 results.
        source.requested shouldBe listOf(1 to null as String?, 2 to "s1", 1 to null as String?)
    }

    private class RecordingCursorFeedSource(
        override val id: Long,
        private val provider: (Int, String?) -> FeedPage,
    ) : AnimeFeedSource {
        override val name: String = "Cursor Feed $id"
        override val lang: String = "all"
        val requested = mutableListOf<Pair<Int, String?>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
            requested += page to cursor
            return provider(page, cursor)
        }
    }

    // ---- Contract v18: creator subscriptions ----

    private class FakeReelsFollowRepository : ReelsFollowRepository {
        val follows = mutableMapOf<Pair<Long, String>, ReelsFollow>()

        override fun subscribeAll(): Flow<List<ReelsFollow>> = MutableStateFlow(follows.values.toList())

        override suspend fun getAll(): List<ReelsFollow> = follows.values.toList()

        override suspend fun getBySource(sourceId: Long): List<ReelsFollow> =
            follows.values.filter { it.sourceId == sourceId }

        override suspend fun getCreatorsBySource(sourceId: Long): List<String> =
            follows.values.filter { it.sourceId == sourceId }.map { it.creator }

        override suspend fun insert(follow: ReelsFollow) {
            follows[follow.sourceId to follow.creator] = follow
        }

        override suspend fun delete(sourceId: Long, creator: String) {
            follows.remove(sourceId to creator)
        }
    }

    private fun timedItem(id: String, createdAtSec: Long) = ShortVideoItem(
        id = id,
        videoUrl = "https://example.com/$id.mp4",
        posterUrl = "https://example.com/$id.jpg",
        createdAtEpochSec = createdAtSec,
    )

    private class RecordingCreatorFeedSource(
        override val id: Long,
        private val provider: (String, Int, String?) -> FeedPage,
    ) : AnimeFeedSource, AnimeCreatorFeedSource {
        override val name: String = "Creator Feed $id"
        override val lang: String = "all"
        val creatorRequests = mutableListOf<Triple<String, Int, String?>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getCreatorFeed(creator: String, page: Int, cursor: String?): FeedPage {
            creatorRequests += Triple(creator, page, cursor)
            return provider(creator, page, cursor)
        }
    }

    @Test
    fun `creator page on a non-capable source surfaces an error without touching getFeed`() = runTest(testDispatcher) {
        var feedCalls = 0
        val plain = object : AnimeFeedSource {
            override val id: Long = 1101L
            override val name: String = "Plain Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                feedCalls++
                return FeedPage(listOf(videoItem("should-not-appear")), hasNextPage = false)
            }
        }
        val screenModel = buildModel(sourceId = 1101L, manager = sourceManagerOf(plain), creator = "alice")
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.error shouldBe "Source does not support creator feeds"
        screenModel.state.value.isLoading shouldBe false
        screenModel.state.value.items.shouldHaveSize(0)
        screenModel.state.value.isCreatorCapable shouldBe false
        feedCalls shouldBe 0
    }

    @Test
    fun `non-capable source keeps the global chrome free of creator state`() = runTest(testDispatcher) {
        val plain = RecordingFeedSource(1102L) { FeedPage(listOf(videoItem("g1")), hasNextPage = false) }
        val screenModel = buildModel(sourceId = 1102L, manager = sourceManagerOf(plain))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isCreatorCapable shouldBe false
        screenModel.state.value.mode shouldBe ReelsFeedScreenModel.FeedMode.GLOBAL
        screenModel.state.value.followingCreators.isEmpty() shouldBe true
    }

    @Test
    fun `creator page routes through getCreatorFeed and replays the exact page and cursor pairs`() =
        runTest(testDispatcher) {
            val failed = booleanArrayOf(false)
            val source = RecordingCreatorFeedSource(1103L) { creator, page, _ ->
                if (page == 2 && !failed[0]) {
                    failed[0] = true
                    throw RuntimeException("CDN exploded")
                }
                FeedPage(listOf(videoItem("$creator-p$page")), hasNextPage = page < 3, nextCursor = "u$page")
            }
            val screenModel = buildModel(sourceId = 1103L, manager = sourceManagerOf(source), creator = "alice")
            testDispatcher.scheduler.advanceUntilIdle()

            screenModel.state.value.mode shouldBe ReelsFeedScreenModel.FeedMode.CREATOR
            screenModel.state.value.creator shouldBe "alice"
            screenModel.state.value.isCreatorCapable shouldBe true
            screenModel.state.value.items.map { it.id } shouldBe listOf("alice-p1")

            // Append: the first non-null token locked cursor mode, so page 2 must carry "u1".
            screenModel.onPageChanged(0)
            testDispatcher.scheduler.advanceUntilIdle()
            screenModel.state.value.pageError shouldBe "CDN exploded"

            // The failed page is retried with the exact same (page, cursor) pair, not skipped.
            screenModel.onPageChanged(0)
            testDispatcher.scheduler.advanceUntilIdle()

            source.creatorRequests shouldBe listOf(
                Triple("alice", 1, null),
                Triple("alice", 2, "u1"),
                Triple("alice", 2, "u1"),
            )
            screenModel.state.value.items.map { it.id } shouldBe listOf("alice-p1", "alice-p2")
        }

    @Test
    fun `toggleFollow persists both directions, restores per source and refuses past the cap`() =
        runTest(testDispatcher) {
            val sourceA = RecordingCreatorFeedSource(1104L) { _, _, _ -> FeedPage(emptyList(), false) }
            val sourceB = RecordingCreatorFeedSource(1105L) { _, _, _ -> FeedPage(emptyList(), false) }
            val follows = FakeReelsFollowRepository()
            val screenModel = buildModel(
                sourceId = 1104L,
                manager = sourceManagerOf(sourceA, sourceB),
                followRepository = follows,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            screenModel.toggleFollow("alice") shouldBe true
            testDispatcher.scheduler.advanceUntilIdle()
            screenModel.state.value.followingCreators.contains("alice") shouldBe true
            follows.follows.keys shouldBe setOf(1104L to "alice")

            // Re-switching back to the source restores the set from the repository...
            screenModel.switchSource(1105L)
            testDispatcher.scheduler.advanceUntilIdle()
            screenModel.state.value.followingCreators.isEmpty() shouldBe true
            screenModel.switchSource(1104L)
            testDispatcher.scheduler.advanceUntilIdle()
            screenModel.state.value.followingCreators.contains("alice") shouldBe true

            // ...and unfollowing removes the row.
            screenModel.toggleFollow("alice") shouldBe true
            testDispatcher.scheduler.advanceUntilIdle()
            follows.follows.isEmpty() shouldBe true

            // Soft cap: 100 rows per source, the 101st is refused without any DB write.
            repeat(100) { index -> check(screenModel.toggleFollow("creator-$index")) }
            testDispatcher.scheduler.advanceUntilIdle()
            screenModel.toggleFollow("over-cap") shouldBe false
            screenModel.state.value.followingCreators.contains("over-cap") shouldBe false
            follows.follows.size shouldBe 100
            follows.follows.keys.none { it.second == "over-cap" } shouldBe true
        }

    @Test
    fun `following feed k-way merges creator streams newest first`() = runTest(testDispatcher) {
        val follows = FakeReelsFollowRepository()
        follows.follows[1106L to "alice"] = ReelsFollow(1106L, "alice", Date(0))
        follows.follows[1106L to "bob"] = ReelsFollow(1106L, "bob", Date(0))
        val source = RecordingCreatorFeedSource(1106L) { creator, page, _ ->
            when {
                creator == "alice" && page == 1 ->
                    FeedPage(listOf(timedItem("a300", 300), timedItem("a100", 100)), hasNextPage = false)
                creator == "bob" && page == 1 ->
                    FeedPage(listOf(timedItem("b200", 200)), hasNextPage = false)
                else -> FeedPage(emptyList(), false)
            }
        }
        val screenModel = buildModel(
            sourceId = 1106L,
            manager = sourceManagerOf(source),
            followRepository = follows,
            followingFeed = true,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.mode shouldBe ReelsFeedScreenModel.FeedMode.FOLLOWING
        screenModel.state.value.items.map { it.id } shouldBe listOf("a300", "b200", "a100")
        screenModel.state.value.error shouldBe null
        screenModel.state.value.canLoadMore shouldBe false
    }

    @Test
    fun `following feed survives one failed creator stream and surfaces it as a page error`() =
        runTest(testDispatcher) {
            val follows = FakeReelsFollowRepository()
            follows.follows[1107L to "alice"] = ReelsFollow(1107L, "alice", Date(0))
            follows.follows[1107L to "bob"] = ReelsFollow(1107L, "bob", Date(0))
            val source = RecordingCreatorFeedSource(1107L) { creator, _, _ ->
                if (creator == "bob") throw RuntimeException("bob is gone")
                FeedPage(listOf(videoItem("a1")), hasNextPage = false)
            }
            val screenModel = buildModel(
                sourceId = 1107L,
                manager = sourceManagerOf(source),
                followRepository = follows,
                followingFeed = true,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            screenModel.state.value.error shouldBe null
            screenModel.state.value.items.map { it.id } shouldBe listOf("a1")
            screenModel.state.value.pageError shouldContain "'bob'"
            screenModel.state.value.pageError shouldContain "bob is gone"
        }

    @Test
    fun `following feed turns an all-failed fan-out into the full error state`() = runTest(testDispatcher) {
        val follows = FakeReelsFollowRepository()
        follows.follows[1108L to "alice"] = ReelsFollow(1108L, "alice", Date(0))
        follows.follows[1108L to "bob"] = ReelsFollow(1108L, "bob", Date(0))
        val source = RecordingCreatorFeedSource(1108L) { creator, _, _ ->
            throw RuntimeException("$creator exploded")
        }
        val screenModel = buildModel(
            sourceId = 1108L,
            manager = sourceManagerOf(source),
            followRepository = follows,
            followingFeed = true,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldHaveSize(0)
        screenModel.state.value.isLoading shouldBe false
        screenModel.state.value.error shouldContain "alice exploded"
        screenModel.state.value.error shouldContain "bob exploded"
    }

    @Test
    fun `following topup fetches only alive streams and echoes each stream cursor`() =
        runTest(testDispatcher) {
            val follows = FakeReelsFollowRepository()
            follows.follows[1109L to "alice"] = ReelsFollow(1109L, "alice", Date(0))
            follows.follows[1109L to "bob"] = ReelsFollow(1109L, "bob", Date(0))
            val source = RecordingCreatorFeedSource(1109L) { creator, page, _ ->
                when {
                    // alice: cursor API (v17 sticky), still alive.
                    creator == "alice" && page == 1 ->
                        FeedPage(listOf(videoItem("a1")), hasNextPage = true, nextCursor = "ac1")
                    creator == "alice" && page == 2 ->
                        FeedPage(listOf(videoItem("a2")), hasNextPage = false)
                    // bob: exhausted after page 1 — must never be fetched again.
                    creator == "bob" && page == 1 -> FeedPage(listOf(videoItem("b1")), hasNextPage = false)
                    else -> FeedPage(emptyList(), false)
                }
            }
            val screenModel = buildModel(
                sourceId = 1109L,
                manager = sourceManagerOf(source),
                followRepository = follows,
                followingFeed = true,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            screenModel.state.value.items.map { it.id } shouldBe listOf("a1", "b1")
            screenModel.state.value.canLoadMore shouldBe true

            // Near the merged tail only alice is topped up — with its own locked cursor.
            screenModel.onPageChanged(1)
            testDispatcher.scheduler.advanceUntilIdle()

            source.creatorRequests shouldBe listOf(
                Triple("alice", 1, null),
                Triple("bob", 1, null),
                Triple("alice", 2, "ac1"),
            )
            screenModel.state.value.items.map { it.id } shouldBe listOf("a1", "b1", "a2")
            screenModel.state.value.canLoadMore shouldBe false
        }

    @Test
    fun `following feed with no follows comes up empty instead of errored`() = runTest(testDispatcher) {
        val source = RecordingCreatorFeedSource(1110L) { _, _, _ -> FeedPage(emptyList(), false) }
        val screenModel = buildModel(
            sourceId = 1110L,
            manager = sourceManagerOf(source),
            followRepository = FakeReelsFollowRepository(),
            followingFeed = true,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.error shouldBe null
        screenModel.state.value.items.shouldHaveSize(0)
        screenModel.state.value.isLoading shouldBe false
        screenModel.state.value.canLoadMore shouldBe false
        source.creatorRequests.shouldBeEmpty()
    }
}
