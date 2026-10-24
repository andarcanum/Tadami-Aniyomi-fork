package eu.kanade.tachiyomi.ui.reels

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeAlbumSearchSource
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeCategorizedSearchSource
import eu.kanade.tachiyomi.animesource.AnimeCategorySubscriptionSource
import eu.kanade.tachiyomi.animesource.AnimeContentPreferencesSource
import eu.kanade.tachiyomi.animesource.AnimeCreatorFeedSource
import eu.kanade.tachiyomi.animesource.AnimeCustomFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.AnimeFeedLoginSource
import eu.kanade.tachiyomi.animesource.AnimeFeedOriginScopedSessionSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedWebLoginSource
import eu.kanade.tachiyomi.animesource.AnimeReelsFeedbackSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.ContentPreferenceOption
import eu.kanade.tachiyomi.animesource.model.CustomFeedDetail
import eu.kanade.tachiyomi.animesource.model.CustomFeedRef
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.FeedCategoryPage
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.SearchSuggestion
import eu.kanade.tachiyomi.animesource.model.SearchSuggestionKind
import eu.kanade.tachiyomi.animesource.model.SearchSuggestions
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.model.WebLoginCookie
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.domain.reels.anime.model.ReelsWatchEntry
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
import tachiyomi.domain.reels.anime.repository.ReelsHiddenRepository
import tachiyomi.domain.reels.anime.repository.ReelsWatchRepository
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
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

        override suspend fun deleteBySource(sourceId: Long) {
            favorites.keys.filter { it.second == sourceId }.forEach { favorites.remove(it) }
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
            reelsWatchRepository = FakeReelsWatchRepository(),
            reelsHiddenRepository = FakeReelsHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            offlineStore = FakeReelsOfflineStore(),
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
            reelsWatchRepository = FakeReelsWatchRepository(),
            reelsHiddenRepository = FakeReelsHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            offlineStore = FakeReelsOfflineStore(),
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
            reelsWatchRepository = FakeReelsWatchRepository(),
            reelsHiddenRepository = FakeReelsHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            offlineStore = FakeReelsOfflineStore(),
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
            reelsWatchRepository = FakeReelsWatchRepository(),
            reelsHiddenRepository = FakeReelsHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            offlineStore = FakeReelsOfflineStore(),
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
    fun `repeated setLike with the same target is a no-op`() = runTest(testDispatcher) {
        val item = videoItem("idem-1")
        val source = FeedbackRecordingSource(733L) { FeedPage(listOf(item), hasNextPage = false) }
        val favorites = FakeReelsFavoriteRepository()
        val model = buildModel(sourceId = 733L, manager = sourceManagerOf(source), repository = favorites)
        testDispatcher.scheduler.advanceUntilIdle()

        // A double-tap replayed after the page was rebound must not flip the like back.
        model.setLike(item, liked = true)
        model.setLike(item, liked = true)
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.likedIds.contains(item.id) shouldBe true
        favorites.favorites.keys shouldBe setOf(item.id to 733L)
        source.likedCalls shouldBe listOf(item.id to true)

        model.setLike(item, liked = true)
        model.setLike(item, liked = false)
        model.setLike(item, liked = false)
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.likedIds.contains(item.id) shouldBe false
        favorites.favorites.isEmpty() shouldBe true
        source.likedCalls shouldBe listOf(item.id to true, item.id to false)
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
            reelsWatchRepository = FakeReelsWatchRepository(),
            reelsHiddenRepository = FakeReelsHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            offlineStore = FakeReelsOfflineStore(),
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
            reelsWatchRepository = FakeReelsWatchRepository(),
            reelsHiddenRepository = FakeReelsHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            offlineStore = FakeReelsOfflineStore(),
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
            reelsWatchRepository = FakeReelsWatchRepository(),
            reelsHiddenRepository = FakeReelsHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            offlineStore = FakeReelsOfflineStore(),
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

        screenModel.state.value.error shouldBe null
        screenModel.state.value.errorRes shouldBe MR.strings.reels_source_not_feed
        screenModel.state.value.isLoading shouldBe false
        screenModel.state.value.items.shouldHaveSize(0)
    }

    @Test
    fun `retryFromError opens the picker for a rejected global source and refuses fixed modes`() = runTest(
        testDispatcher,
    ) {
        val notAFeed = object : AnimeSource {
            override val id: Long = 999L
            override val name: String = "Catalogue Source"
            override val lang: String = "all"

            override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
            override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()
            override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()
            override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
        }

        // GLOBAL: a retry with source=null must not be a silent no-op — it opens the picker.
        val global = buildModel(sourceId = 999L, manager = sourceManagerOf(notAFeed))
        testDispatcher.scheduler.advanceUntilIdle()
        global.state.value.errorRes shouldBe MR.strings.reels_source_not_feed

        global.retryFromError() shouldBe true
        global.state.value.isSourcePickerOpen shouldBe true

        // Fixed mode (creator): nowhere to switch — the screen falls back to a back-navigation.
        val creator = buildModel(sourceId = 999L, manager = sourceManagerOf(notAFeed), creator = "alice")
        testDispatcher.scheduler.advanceUntilIdle()
        creator.state.value.errorRes shouldBe MR.strings.reels_source_not_feed

        creator.retryFromError() shouldBe false
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
            reelsWatchRepository = FakeReelsWatchRepository(),
            reelsHiddenRepository = FakeReelsHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            offlineStore = FakeReelsOfflineStore(),
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

        val repository = FakeReelsFavoriteRepository()
        favorites.forEach { repository.favorites[it.videoId to it.sourceId] = it }
        val screenModel = buildModel(
            sourceId = 301L,
            manager = sourceManagerOf(),
            repository = repository,
            offlinePlaylist = true,
            initialVideoId = "off-b",
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

    @Test
    fun `a deliberate pause survives swipes until explicitly resumed`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9061L) { page ->
            FeedPage((0 until 5).map { videoItem("pause-p$page-$it") }, hasNextPage = true)
        }
        val screenModel = buildModel(sourceId = 9061L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.togglePlayPause()
        screenModel.state.value.isPlaying shouldBe false
        screenModel.state.value.userPaused shouldBe true

        // Auto-advance must not undo the deliberate pause.
        screenModel.onPageChanged(2)
        screenModel.state.value.isPlaying shouldBe false
        screenModel.state.value.userPaused shouldBe true

        // Only an explicit resume tap clears the sticky pause.
        screenModel.togglePlayPause()
        screenModel.onPageChanged(3)
        screenModel.state.value.isPlaying shouldBe true
        screenModel.state.value.userPaused shouldBe false
    }

    @Test
    fun `player-reported pauses flip the actual state without touching the play intent`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9062L) { page -> FeedPage(emptyList(), hasNextPage = false) }
        val screenModel = buildModel(sourceId = 9062L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        // P03: nothing is confirmed playing until the active player reports it.
        screenModel.state.value.isActuallyPlaying shouldBe false

        screenModel.setPlaybackRunning(true)
        screenModel.state.value.isActuallyPlaying shouldBe true

        // Audio-focus loss pauses the player without the user asking for it.
        screenModel.setPlaybackRunning(false)
        screenModel.state.value.isActuallyPlaying shouldBe false
        screenModel.state.value.isPlaying shouldBe true

        // A swipe resumes the play INTENT; actual playback is confirmed by the new page's
        // player and must not be assumed at page-change time (it may still be buffering).
        screenModel.onPageChanged(0)
        screenModel.state.value.isPlaying shouldBe true
        screenModel.state.value.isActuallyPlaying shouldBe false

        screenModel.setPlaybackRunning(true)
        screenModel.state.value.isActuallyPlaying shouldBe true
    }

    @Test
    fun `search suggestions debounce to the last typed query`() = runTest(testDispatcher) {
        val source = FakeCategorizedSource(2305)
        val screenModel = buildModel(sourceId = 2305, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.requestSearchSuggestions("d")
        testDispatcher.scheduler.advanceTimeBy(150)
        screenModel.requestSearchSuggestions("da")
        testDispatcher.scheduler.advanceTimeBy(150)
        screenModel.requestSearchSuggestions("dan")
        testDispatcher.scheduler.advanceUntilIdle()

        source.queries shouldBe listOf("dan")
        screenModel.state.value.searchSuggestions?.tags?.size shouldBe 1
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

    private class FakeAlbumRepository : ReelsAlbumRepository {
        val albums = mutableMapOf<Pair<String, Long>, ReelsAlbum>()

        override fun subscribeBySource(sourceId: Long): Flow<List<ReelsAlbum>> =
            MutableStateFlow(albums.values.filter { it.sourceId == sourceId })

        override suspend fun getBySource(sourceId: Long): List<ReelsAlbum> =
            albums.values.filter { it.sourceId == sourceId }

        override suspend fun insert(album: ReelsAlbum) {
            albums[album.albumId to album.sourceId] = album
        }

        override suspend fun delete(sourceId: Long, albumId: String) {
            albums.remove(albumId to sourceId)
        }

        override suspend fun deleteBySource(sourceId: Long) {
            albums.keys.filter { it.second == sourceId }.forEach { albums.remove(it) }
        }

        override fun subscribeWatched(sourceId: Long): Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun markWatched(sourceId: Long, videoId: String) = Unit

        override suspend fun unmarkWatched(sourceId: Long, videoId: String) = Unit

        override suspend fun deleteWatchedBySource(sourceId: Long) = Unit
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
        watchRepository: ReelsWatchRepository = FakeReelsWatchRepository(),
        hiddenRepository: ReelsHiddenRepository = FakeReelsHiddenRepository(),
        albumRepository: ReelsAlbumRepository = FakeAlbumRepository(),
        offlineStore: ReelsOfflineStore = FakeReelsOfflineStore(),
        incognito: Boolean = false,
        offlinePlaylist: Boolean = false,
        playlistSort: FavoritesSort = FavoritesSort.DateDesc,
        initialVideoId: String? = null,
        creator: String? = null,
        followingFeed: Boolean = false,
        customFeedId: String? = null,
        customFeedName: String? = null,
        nicheId: String? = null,
        nicheName: String? = null,
        resumeVideoId: String? = null,
        preferences: SourcePreferences = SourcePreferences(MapPreferenceStore()),
        sessionSound: ReelsSessionSoundState = ReelsSessionSoundState(),
    ) = ReelsFeedScreenModel(
        initialSourceId = sourceId,
        offlinePlaylist = offlinePlaylist,
        playlistSort = playlistSort,
        initialVideoId = initialVideoId,
        creator = creator,
        followingFeed = followingFeed,
        customFeedId = customFeedId,
        customFeedName = customFeedName,
        nicheId = nicheId,
        nicheName = nicheName,
        resumeVideoId = resumeVideoId,
        sourceManager = manager,
        sourcePreferences = preferences,
        ioDispatcher = testDispatcher,
        isIncognito = { incognito },
        sourceIconProvider = { null },
        reelsFavoriteRepository = repository,
        reelsFollowRepository = followRepository,
        reelsWatchRepository = watchRepository,
        reelsHiddenRepository = hiddenRepository,
        reelsAlbumRepository = albumRepository,
        offlineStore = offlineStore,
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
    fun `a left reel lands in the watch history with its position`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9081L) { FeedPage(listOf(videoItem("h-1")), hasNextPage = false) }
        val watch = FakeReelsWatchRepository()
        val screenModel = buildModel(sourceId = 9081L, manager = sourceManagerOf(source), watchRepository = watch)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.recordWatchHistory(screenModel.state.value.items.first(), 0.5f, 10f)
        testDispatcher.scheduler.advanceUntilIdle()

        val entry = watch.entries["h-1" to 9081L]
        entry shouldNotBe null
        entry?.positionMs?.shouldBe(5_000L)
    }

    @Test
    fun `watch history is not written in incognito`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9082L) { FeedPage(listOf(videoItem("h-2")), hasNextPage = false) }
        val watch = FakeReelsWatchRepository()
        val screenModel = buildModel(
            sourceId = 9082L,
            manager = sourceManagerOf(source),
            watchRepository = watch,
            incognito = true,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.recordWatchHistory(screenModel.state.value.items.first(), 0.5f, 10f)
        testDispatcher.scheduler.advanceUntilIdle()

        watch.entries.isEmpty() shouldBe true
    }

    @Test
    fun `resume lookup arms the stored fraction for the requested clip`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9083L) { FeedPage(listOf(videoItem("h-3")), hasNextPage = false) }
        val watch = FakeReelsWatchRepository()
        watch.entries["h-3" to 9083L] = ReelsWatchEntry(
            videoId = "h-3",
            sourceId = 9083L,
            title = null,
            author = null,
            posterUrl = null,
            webUrl = null,
            videoUrl = "https://example.com/h-3.mp4",
            durationSec = 10.0,
            positionMs = 5_000L,
            watchedAt = Date(0),
        )
        val screenModel = buildModel(
            sourceId = 9083L,
            manager = sourceManagerOf(source),
            watchRepository = watch,
            resumeVideoId = "h-3",
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.resumeVideoId shouldBe "h-3"
        (screenModel.state.value.resumeFraction ?: 0f) shouldBe 0.5f
    }

    @Test
    fun `a hidden video stays out of future pages`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9091L) { page ->
            if (page == 1) {
                FeedPage(listOf(videoItem("seed-1"), videoItem("seed-2")), hasNextPage = true)
            } else {
                FeedPage(listOf(videoItem("hidden-b"), videoItem("keep-b")), hasNextPage = false)
            }
        }
        val screenModel = buildModel(sourceId = 9091L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.hideVideo(videoItem("hidden-b"))
        screenModel.onPageChanged(1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.map { it.id }.shouldNotContain("hidden-b")
        screenModel.state.value.items.map { it.id }.shouldContain("keep-b")
    }

    @Test
    fun `hiding an author filters their items from future pages`() = runTest(testDispatcher) {
        fun authored(id: String, author: String) = videoItem(id).copy(author = author)
        val source = RecordingFeedSource(9092L) { page ->
            if (page == 1) {
                FeedPage(listOf(authored("a-1", "alice"), authored("b-1", "bob")), hasNextPage = true)
            } else {
                FeedPage(listOf(authored("a-2", "alice"), authored("b-2", "bob")), hasNextPage = false)
            }
        }
        val screenModel = buildModel(sourceId = 9092L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.hideAuthor("alice")
        screenModel.onPageChanged(1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.map { it.id }.shouldNotContain("a-2")
        screenModel.state.value.items.map { it.id }.shouldContain("b-2")
    }

    @Test
    fun `undo restores the hidden video locally and in the repository`() = runTest(testDispatcher) {
        val hidden = FakeReelsHiddenRepository()
        val source = RecordingFeedSource(9093L) { page ->
            if (page == 1) {
                FeedPage(listOf(videoItem("undo-seed")), hasNextPage = true)
            } else {
                FeedPage(listOf(videoItem("undo-v"), videoItem("undo-other")), hasNextPage = false)
            }
        }
        val screenModel = buildModel(sourceId = 9093L, manager = sourceManagerOf(source), hiddenRepository = hidden)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.hideVideo(videoItem("undo-v"))
        testDispatcher.scheduler.advanceUntilIdle()
        hidden.entries.keys.shouldContain(Triple(9093L, "video", "undo-v"))

        screenModel.undoHide(screenModel.lastHideToken())
        testDispatcher.scheduler.advanceUntilIdle()
        hidden.entries.isEmpty() shouldBe true

        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.map { it.id }.shouldContain("undo-v")
    }

    @Test
    fun `persisted hides load on source switch and filter future pages`() = runTest(testDispatcher) {
        val hidden = FakeReelsHiddenRepository()
        hidden.entries[Triple(9094L, ReelsHiddenEntry.KIND_VIDEO, "seeded-v")] =
            ReelsHiddenEntry(9094L, ReelsHiddenEntry.KIND_VIDEO, "seeded-v", Date(0))
        val source = RecordingFeedSource(9094L) { page ->
            if (page == 1) {
                FeedPage(listOf(videoItem("seed-s")), hasNextPage = true)
            } else {
                FeedPage(listOf(videoItem("seeded-v")), hasNextPage = false)
            }
        }
        val screenModel = buildModel(sourceId = 9094L, manager = sourceManagerOf(source), hiddenRepository = hidden)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.map { it.id }.shouldNotContain("seeded-v")
    }

    @Test
    fun `offline playlist prefers stored local copies`() = runTest(testDispatcher) {
        val favorites = FakeReelsFavoriteRepository()
        favorites.favorites["off-1" to 302L] = offlineFavorite("off-1").copy(sourceId = 302L)
        val store = FakeReelsOfflineStore()
        store.stored[302L to "off-1"] = "file:///offline/302_off-1.mp4"
        val screenModel = buildModel(
            sourceId = 302L,
            manager = sourceManagerOf(),
            repository = favorites,
            offlineStore = store,
            offlinePlaylist = true,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.first().videoUrl shouldBe "file:///offline/302_off-1.mp4"
        screenModel.state.value.offlineStored.contains("302:off-1") shouldBe true
    }

    @Test
    fun `toggleOfflineCopy downloads and rewrites the item URL`() = runTest(testDispatcher) {
        val favorites = FakeReelsFavoriteRepository()
        favorites.favorites["off-2" to 303L] = offlineFavorite("off-2").copy(sourceId = 303L)
        val store = FakeReelsOfflineStore()
        val screenModel = buildModel(
            sourceId = 303L,
            manager = sourceManagerOf(),
            repository = favorites,
            offlineStore = store,
            offlinePlaylist = true,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val result = screenModel.toggleOfflineCopy(screenModel.state.value.items.first(), 0)
        result shouldBe ReelsFeedScreenModel.OfflineCopyResult.SAVED

        screenModel.state.value.items.first().videoUrl shouldBe "file:///offline/303_off-2.mp4"
        screenModel.state.value.offlineStored.contains("303:off-2") shouldBe true
    }

    @Test
    fun `toggleOfflineCopy removes a stored copy and restores the network URL`() = runTest(testDispatcher) {
        val favorites = FakeReelsFavoriteRepository()
        favorites.favorites["off-3" to 304L] = offlineFavorite("off-3").copy(sourceId = 304L)
        val store = FakeReelsOfflineStore()
        store.stored[304L to "off-3"] = "file:///offline/304_off-3.mp4"
        val screenModel = buildModel(
            sourceId = 304L,
            manager = sourceManagerOf(),
            repository = favorites,
            offlineStore = store,
            offlinePlaylist = true,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val result = screenModel.toggleOfflineCopy(screenModel.state.value.items.first(), 0)
        result shouldBe ReelsFeedScreenModel.OfflineCopyResult.REMOVED

        screenModel.state.value.offlineStored.contains("304:off-3") shouldBe false
        store.stored.isEmpty() shouldBe true
        screenModel.state.value.items.first().videoUrl shouldBe "https://example.com/off-3.mp4"
    }

    @Test
    fun `a 429 verdict surfaces the wait and auto-retries after it`() = runTest(testDispatcher) {
        var calls = 0
        val source = object : AnimeFeedSource {
            override val id: Long = 9099L
            override val name: String = "Rate Limited"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                calls++
                if (calls == 1) throw java.io.IOException("HTTP 429 Too Many Requests, Retry-After: 7")
                return FeedPage(listOf(videoItem("rl-1")), hasNextPage = false)
            }
        }
        val screenModel = buildModel(sourceId = 9099L, manager = sourceManagerOf(source))

        // The initial load fails with 429; the model schedules ONE capped auto-retry after the
        // Retry-After window, which the virtual clock executes inside advanceUntilIdle.
        testDispatcher.scheduler.advanceUntilIdle()

        calls shouldBe 2
        screenModel.state.value.retryAfterSec shouldBe 0
        screenModel.state.value.items.map { it.id } shouldContain "rl-1"
    }

    @Test
    fun `rate-limit auto-retry is capped at three consecutive attempts`() = runTest(testDispatcher) {
        var calls = 0
        val source = object : AnimeFeedSource {
            override val id: Long = 9100L
            override val name: String = "Always 429"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                calls++
                throw java.io.IOException("HTTP 429 Too Many Requests")
            }
        }
        val screenModel = buildModel(sourceId = 9100L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial failure + exactly RATE_LIMIT_MAX_RETRIES auto-retries, then it stops.
        calls shouldBe 4
        screenModel.state.value.error.orEmpty().contains("429") shouldBe true
    }

    @Test
    fun `sleep timer countdown pauses the feed stickily on expiry`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9101L) {
            FeedPage(listOf(videoItem("st-1"), videoItem("st-2")), hasNextPage = false)
        }
        val screenModel = buildModel(sourceId = 9101L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.setSleepTimer(ReelsFeedScreenModel.SleepTimerOption.M15)
        screenModel.state.value.sleepTimerRemainingSec shouldBe 900

        testDispatcher.scheduler.advanceTimeBy(901_000)
        screenModel.state.value.sleepTimerRemainingSec shouldBe 0
        screenModel.state.value.isPlaying shouldBe false
        screenModel.state.value.userPaused shouldBe true
    }

    @Test
    fun `end-of-video sleep pauses at the completion boundary once`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9102L) { FeedPage(listOf(videoItem("ev-1")), hasNextPage = false) }
        val screenModel = buildModel(sourceId = 9102L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.setSleepTimer(ReelsFeedScreenModel.SleepTimerOption.END_OF_VIDEO)
        screenModel.state.value.sleepAtVideoEnd shouldBe true

        screenModel.consumeSleepAtVideoEnd() shouldBe true
        screenModel.state.value.isPlaying shouldBe false
        screenModel.state.value.sleepAtVideoEnd shouldBe false
        // A second completion must advance normally.
        screenModel.consumeSleepAtVideoEnd() shouldBe false
    }

    @Test
    fun `history seed plays the tapped clip at the head of the offline playlist`() = runTest(testDispatcher) {
        val favorites = FakeReelsFavoriteRepository()
        favorites.favorites["fav-1" to 305L] = offlineFavorite("fav-1").copy(sourceId = 305L)
        val store = FakeReelsOfflineStore()
        val seeded = offlineFavorite("hist-clip").copy(sourceId = 305L)
        ReelsPlaybackSeed.pending = seeded
        try {
            val screenModel = buildModel(
                sourceId = 305L,
                manager = sourceManagerOf(),
                repository = favorites,
                offlineStore = store,
                offlinePlaylist = true,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            // The history clip leads even though it is NOT favorited; favorites follow.
            screenModel.state.value.items.map { it.id } shouldBe listOf("hist-clip", "fav-1")
            // The seed is one-shot.
            ReelsPlaybackSeed.pending shouldBe null
        } finally {
            ReelsPlaybackSeed.pending = null
        }
    }

    @Test
    fun `session remembers the source when incognito blocks the disk write`() = runTest(testDispatcher) {
        ReelsSessionSource.lastSourceId = null
        try {
            val source = RecordingFeedSource(9104L) { FeedPage(emptyList(), false) }
            val preferences = SourcePreferences(MapPreferenceStore())
            val before = preferences.lastUsedReelsSource().get()
            buildModel(
                sourceId = 9104L,
                manager = sourceManagerOf(source),
                preferences = preferences,
                incognito = true,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            // Incognito policy: no disk trace…
            preferences.lastUsedReelsSource().get() shouldBe before
            // …but re-entering the feed within the process returns to the picked source
            // (device report: the entry point fell back to the first installed source).
            ReelsSessionSource.lastSourceId shouldBe 9104L

            // Without incognito the disk preference keeps working as before.
            val open = SourcePreferences(MapPreferenceStore())
            buildModel(sourceId = 9104L, manager = sourceManagerOf(source), preferences = open)
            testDispatcher.scheduler.advanceUntilIdle()
            open.lastUsedReelsSource().get() shouldBe 9104L
        } finally {
            ReelsSessionSource.lastSourceId = null
        }
    }

    @Test
    fun `reels incognito toggle persists and defaults to off`() = runTest(testDispatcher) {
        val preferences = SourcePreferences(MapPreferenceStore())
        preferences.reelsIncognitoMode().get() shouldBe false
        val source = RecordingFeedSource(9105L) { FeedPage(emptyList(), false) }
        val model = buildModel(sourceId = 9105L, manager = sourceManagerOf(source), preferences = preferences)
        testDispatcher.scheduler.advanceUntilIdle()

        model.toggleReelsIncognito()
        preferences.reelsIncognitoMode().get() shouldBe true

        model.toggleReelsIncognito()
        preferences.reelsIncognitoMode().get() shouldBe false
    }

    @Test
    fun `settings block holds playback and resumes on close unless the user paused`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9106L) { FeedPage(listOf(videoItem("sb-1")), hasNextPage = false) }
        val model = buildModel(sourceId = 9106L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.isPlaying shouldBe true

        // Open: temporary pause, sticky pause untouched.
        model.setSettingsHold(true)
        model.state.value.isPlaying shouldBe false
        model.state.value.userPaused shouldBe false

        // Close: playback resumes on its own.
        model.setSettingsHold(false)
        model.state.value.isPlaying shouldBe true

        // A deliberate pause before opening survives the open/close cycle.
        model.togglePlayPause()
        model.state.value.userPaused shouldBe true
        model.setSettingsHold(true)
        model.setSettingsHold(false)
        model.state.value.isPlaying shouldBe false
        model.state.value.userPaused shouldBe true
    }

    @Test
    fun `pip toggle persists and defaults to off`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9095L) { FeedPage(emptyList(), false) }
        val preferences = SourcePreferences(MapPreferenceStore())
        val screenModel = buildModel(sourceId = 9095L, manager = sourceManagerOf(source), preferences = preferences)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isPipEnabled shouldBe false

        screenModel.togglePip()
        screenModel.state.value.isPipEnabled shouldBe true
        preferences.reelsPipEnabled().get() shouldBe true

        screenModel.togglePip()
        screenModel.state.value.isPipEnabled shouldBe false
        preferences.reelsPipEnabled().get() shouldBe false
    }

    @Test
    fun `cf bootstrap budget exhausts to an unmounted terminal state`() = runTest(testDispatcher) {
        val failing = object : AnimeFeedSource, AnimeFeedWebLoginSource {
            override val id: Long = 9096L
            override val name: String = "Always 403"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                throw java.io.IOException("HTTP 403: managed challenge")

            override fun webLoginUrl(): String = "https://example.invalid/"
            override fun ownAuthorizeUrl(): String = "https://example.invalid/oauth2/auth?state=own"
            override fun isOwnLoginRedirect(url: String): Boolean = false
            override suspend fun importWebRedirect(url: String, cookies: Map<String, String>): Boolean = false
            override suspend fun importWebSession(
                cookies: Map<String, String>,
                localStorage: Map<String, String>,
            ): Boolean = false
        }
        val screenModel = buildModel(sourceId = 9096L, manager = sourceManagerOf(failing))
        testDispatcher.scheduler.advanceUntilIdle()

        // The initial failed load bumps the first bootstrap attempt.
        screenModel.state.value.cfBootstrapAttempt shouldBe 1

        // Attempts 2 and 3 mount the WebView; the 4th failure exhausts the budget: the
        // WebView must UNMOUNT (attempt back to 0) and the terminal flag must stop any
        // further mounts for this source entry (audit A4/H6 follow-up).
        repeat(3) {
            screenModel.loadFeed(reset = true)
            testDispatcher.scheduler.advanceUntilIdle()
        }
        screenModel.state.value.cfBootstrapExhausted shouldBe true
        screenModel.state.value.cfBootstrapAttempt shouldBe 0

        // A further failure must not re-arm the budget.
        screenModel.loadFeed(reset = true)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.cfBootstrapAttempt shouldBe 0
    }

    @Test
    fun `history entry navigates the first page to the tapped clip`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(9097L) {
            FeedPage(
                listOf(videoItem("nav-a"), videoItem("nav-target"), videoItem("nav-b")),
                hasNextPage = false,
            )
        }
        val screenModel = buildModel(
            sourceId = 9097L,
            manager = sourceManagerOf(source),
            resumeVideoId = "nav-target",
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // The one-shot resume navigation outranks the saved source position when the
        // tapped clip is on the first page.
        screenModel.state.value.targetPageIndex shouldBe 1

        // A clip the shuffled first page does not contain falls back to the normal restore.
        val absent = RecordingFeedSource(9098L) {
            FeedPage(listOf(videoItem("other-a"), videoItem("other-b")), hasNextPage = false)
        }
        val fallbackModel = buildModel(
            sourceId = 9098L,
            manager = sourceManagerOf(absent),
            resumeVideoId = "nav-target",
        )
        testDispatcher.scheduler.advanceUntilIdle()
        fallbackModel.state.value.targetPageIndex shouldBe 0
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

        val repository = FakeReelsFavoriteRepository()
        repository.favorites[favorite.videoId to favorite.sourceId] = favorite
        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 301L,
            offlinePlaylist = true,
            initialVideoId = favorite.videoId,
            sourceManager = emptySourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = repository,
            reelsFollowRepository = FakeReelsFollowRepository(),
            reelsWatchRepository = FakeReelsWatchRepository(),
            reelsHiddenRepository = FakeReelsHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            offlineStore = FakeReelsOfflineStore(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isOffline shouldBe true
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.items.first().id shouldBe "vid-f"
        screenModel.state.value.items.first().videoUrl shouldBe "https://example.com/f.mp4"
        screenModel.state.value.likedIds shouldBe setOf("301:vid-f")

        // Network entry points are no-ops in offline mode.
        screenModel.loadFeed(reset = true)
        screenModel.search("cosplay")
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.searchQuery shouldBe ""
    }

    @Test
    fun `offline playlist keeps same-videoId likes separate per source`() = runTest(testDispatcher) {
        val sourceA = offlineFavorite("dup").copy(sourceId = 401L, addedAt = Date(1_000L))
        val sourceB = offlineFavorite("dup").copy(sourceId = 402L, addedAt = Date(2_000L))
        val repository = FakeReelsFavoriteRepository()
        repository.favorites[sourceA.videoId to sourceA.sourceId] = sourceA
        repository.favorites[sourceB.videoId to sourceB.sourceId] = sourceB
        val screenModel = buildModel(
            sourceId = 401L,
            manager = sourceManagerOf(),
            repository = repository,
            offlinePlaylist = true,
            initialVideoId = "dup",
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Newest favorite first: the tapped source-A copy sits at index 1, not index 0.
        screenModel.state.value.items.map { it.id } shouldBe listOf("dup", "dup")
        screenModel.state.value.offlineSourceIds shouldBe listOf(402L, 401L)
        screenModel.state.value.targetPageIndex shouldBe 1
        screenModel.state.value.isLiked("dup", 0) shouldBe true
        screenModel.state.value.isLiked("dup", 1) shouldBe true

        // Unliking source B must not clear source A's heart.
        screenModel.setLike(screenModel.state.value.items[0], liked = false, itemIndex = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isLiked("dup", 0) shouldBe false
        screenModel.state.value.isLiked("dup", 1) shouldBe true
        repository.favorites.keys shouldBe setOf("dup" to 401L)

        // And toggling source A uses its own identity, not the surviving videoId entry.
        screenModel.toggleLike(screenModel.state.value.items[1], itemIndex = 1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.isLiked("dup", 1) shouldBe false
        repository.favorites.isEmpty() shouldBe true
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

    private class FakeReelsWatchRepository : ReelsWatchRepository {
        val entries = mutableMapOf<Pair<String, Long>, ReelsWatchEntry>()

        override fun subscribeAll(): Flow<List<ReelsWatchEntry>> = MutableStateFlow(entries.values.toList())

        override suspend fun getByVideo(videoId: String, sourceId: Long): ReelsWatchEntry? =
            entries[videoId to sourceId]

        override suspend fun upsert(entry: ReelsWatchEntry) {
            entries[entry.videoId to entry.sourceId] = entry
        }

        override suspend fun delete(videoId: String, sourceId: Long) {
            entries.remove(videoId to sourceId)
        }

        override suspend fun deleteAll() = entries.clear()
    }

    private class FakeReelsHiddenRepository : ReelsHiddenRepository {
        val entries = mutableMapOf<Triple<Long, String, String>, ReelsHiddenEntry>()

        override fun subscribeAll(): Flow<List<ReelsHiddenEntry>> = MutableStateFlow(entries.values.toList())

        override suspend fun getBySource(sourceId: Long): List<ReelsHiddenEntry> =
            entries.values.filter { it.sourceId == sourceId }

        override suspend fun insert(entry: ReelsHiddenEntry) {
            entries[Triple(entry.sourceId, entry.kind, entry.value)] = entry
        }

        override suspend fun delete(sourceId: Long, kind: String, value: String) {
            entries.remove(Triple(sourceId, kind, value))
        }

        override suspend fun deleteBySource(sourceId: Long) {
            entries.keys.filter { it.first == sourceId }.forEach { entries.remove(it) }
        }

        override suspend fun deleteAll() = entries.clear()
    }

    private class FakeReelsOfflineStore : ReelsOfflineStore {
        val stored = mutableMapOf<Pair<Long, String>, String>()

        override fun isStored(sourceId: Long, videoId: String): Boolean = (sourceId to videoId) in stored

        override fun localUrl(sourceId: Long, videoId: String): String? = stored[sourceId to videoId]

        override fun storedPairs(): Set<Pair<Long, String>> = stored.keys

        override fun usedBytes(): Long = stored.size * 1024L

        override suspend fun download(item: ShortVideoItem, sourceId: Long): ReelsOfflineStore.DownloadResult {
            stored[sourceId to item.id] = "file:///offline/${sourceId}_${item.id}.mp4"
            return ReelsOfflineStore.DownloadResult.SAVED
        }

        override suspend fun delete(sourceId: Long, videoId: String): Boolean =
            stored.remove(sourceId to videoId) != null

        override suspend fun deleteBySource(sourceId: Long): Int =
            stored.keys.filter { it.first == sourceId }.count { stored.remove(it) != null }

        override suspend fun clearAll(): Long {
            val freed = usedBytes()
            stored.clear()
            return freed
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

    private class FeedbackRecordingSource(
        override val id: Long,
        private val provider: (Int) -> FeedPage,
    ) : AnimeFeedSource, AnimeReelsFeedbackSource {
        override val name: String = "Feedback $id"
        override val lang: String = "all"
        val likedCalls = mutableListOf<Pair<String, Boolean>>()
        val viewedCalls = mutableListOf<String>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            provider(page)

        override suspend fun onVideoLiked(itemId: String, liked: Boolean) {
            likedCalls += itemId to liked
        }

        override suspend fun onVideoViewed(itemId: String, secondsWatched: Double, duration: Double) {
            viewedCalls += itemId
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

        screenModel.state.value.error shouldBe null
        screenModel.state.value.errorRes shouldBe MR.strings.reels_source_missing_creator_feeds
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
    fun `toggleFollow persists both directions and restores per source`() =
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

            screenModel.toggleFollow("alice")
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
            screenModel.toggleFollow("alice")
            testDispatcher.scheduler.advanceUntilIdle()
            follows.follows.isEmpty() shouldBe true

            // Uncapped: follows beyond the former 100-row soft cap persist like any other row.
            repeat(100) { index -> screenModel.toggleFollow("creator-$index") }
            testDispatcher.scheduler.advanceUntilIdle()
            screenModel.toggleFollow("over-cap")
            testDispatcher.scheduler.advanceUntilIdle()
            screenModel.state.value.followingCreators.contains("over-cap") shouldBe true
            follows.follows.size shouldBe 101
        }

    @Test
    fun `incognito blocks follow inserts but a removal still reaches the database`() = runTest(testDispatcher) {
        val source = RecordingCreatorFeedSource(1109L) { _, _, _ -> FeedPage(emptyList(), false) }
        val follows = FakeReelsFollowRepository()
        follows.follows[1109L to "alice"] = ReelsFollow(1109L, "alice", Date(0))
        val screenModel = buildModel(
            sourceId = 1109L,
            manager = sourceManagerOf(source),
            followRepository = follows,
            incognito = true,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // New follows are session-only while incognito.
        screenModel.toggleFollow("bob")
        testDispatcher.scheduler.advanceUntilIdle()
        follows.follows.keys shouldBe setOf(1109L to "alice")

        // Removing an existing follow must persist even in incognito (no resurrection later).
        screenModel.toggleFollow("alice")
        testDispatcher.scheduler.advanceUntilIdle()
        follows.follows.isEmpty() shouldBe true
    }

    @Test
    fun `incognito suppresses the remote like and view feedback`() = runTest(testDispatcher) {
        val source =
            FeedbackRecordingSource(907L) { page -> FeedPage(listOf(videoItem("fb-p$page")), hasNextPage = false) }
        val screenModel = buildModel(sourceId = 907L, manager = sourceManagerOf(source), incognito = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val item = screenModel.state.value.items.first()
        screenModel.toggleLike(item)
        screenModel.reportVideoView(item.id, 3f, 6f)
        testDispatcher.scheduler.advanceUntilIdle()

        source.likedCalls shouldBe emptyList()
        source.viewedCalls shouldBe emptyList()
    }

    @Test
    fun `following feed shuffles merged creator streams without same-author runs`() = runTest(testDispatcher) {
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
        // 2 alice + 1 bob: the only separable shape puts bob in the middle.
        screenModel.state.value.items.map { it.id }.sorted() shouldBe listOf("a100", "a300", "b200")
        screenModel.state.value.items[1].id shouldBe "b200"
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

            screenModel.state.value.items.map { it.id }.sorted() shouldBe listOf("a1", "b1")
            screenModel.state.value.canLoadMore shouldBe true

            // Near the merged tail only alice is topped up — with its own locked cursor.
            screenModel.onPageChanged(1)
            testDispatcher.scheduler.advanceUntilIdle()

            source.creatorRequests shouldBe listOf(
                Triple("alice", 1, null),
                Triple("bob", 1, null),
                Triple("alice", 2, "ac1"),
            )
            screenModel.state.value.items.map { it.id }.sorted() shouldBe listOf("a1", "a2", "b1")
            screenModel.state.value.items.last().id shouldBe "a2"
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

    @Test
    fun `following feed never places the same author twice in a row when separable`() =
        runTest(testDispatcher) {
            val follows = FakeReelsFollowRepository()
            follows.follows[1111L to "alice"] = ReelsFollow(1111L, "alice", Date(0))
            follows.follows[1111L to "bob"] = ReelsFollow(1111L, "bob", Date(0))
            // alice posts strictly newer reels: the pre-shuffle newest-first merge would
            // emit aaa/bbb runs; the separated feed must interleave (3+3 of 6 is feasible).
            val source = RecordingCreatorFeedSource(1111L) { creator, _, _ ->
                val base = if (creator == "alice") 300L else 100L
                FeedPage(
                    (0 until 3).map { timedItem("$creator-${base - it * 10}", base - it * 10) },
                    hasNextPage = false,
                )
            }
            repeat(3) {
                val screenModel = buildModel(
                    sourceId = 1111L,
                    manager = sourceManagerOf(source),
                    followRepository = follows,
                    followingFeed = true,
                )
                testDispatcher.scheduler.advanceUntilIdle()

                val items = screenModel.state.value.items
                items.shouldHaveSize(6)
                // The shuffle keys on the stream creator even when items carry no author
                // metadata: derive it from the id prefix.
                items.zipWithNext { a, b -> a.id.substringBefore('-') to b.id.substringBefore('-') }
                    .none { (first, second) -> first == second } shouldBe true
            }
        }

    // ---- Contract v19: account login + custom feeds ----

    private class FakeAccountSource(
        override val id: Long,
        override val name: String = "Account Feed $id",
    ) : AnimeFeedSource, AnimeFeedLoginSource, AnimeCustomFeedSource {
        override val lang: String = "all"

        val loginRequests = mutableListOf<Pair<String, String>>()
        var loginResult: Boolean = true
        var storedEmail: String? = null
        val feeds = mutableListOf(CustomFeedRef("f1", "Feed 1"), CustomFeedRef("f2", "Feed 2"))
        val requestedFeedPages = mutableListOf<Pair<String, Int>>()
        val createdFeeds = mutableListOf<Pair<String, List<String>>>()
        var detail = CustomFeedDetail("Feed 1", listOf("Tag A"))

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun login(email: String, password: String): Boolean {
            loginRequests += email to password
            if (loginResult) storedEmail = email
            return loginResult
        }

        override fun isLoggedIn(): Boolean = storedEmail != null

        override fun loggedInAccount(): String? = storedEmail

        override suspend fun logout() {
            storedEmail = null
        }

        override suspend fun getCustomFeeds(): List<CustomFeedRef> = feeds.toList()

        override suspend fun getCustomFeed(id: String, page: Int, cursor: String?): FeedPage {
            requestedFeedPages += id to page
            return FeedPage(
                videos = listOf(
                    ShortVideoItem(
                        id = "custom-$id-p$page",
                        videoUrl = "https://example.com/custom-$id-p$page.mp4",
                        posterUrl = "https://example.com/custom-$id-p$page.jpg",
                    ),
                ),
                hasNextPage = false,
            )
        }

        override suspend fun getCustomFeedTags(): List<String> = listOf("Tag A", "Tag B", "Tag C")

        override suspend fun getCustomFeedDetail(id: String): CustomFeedDetail = detail

        override suspend fun createCustomFeed(name: String, tags: List<String>): CustomFeedRef {
            createdFeeds += name to tags
            return CustomFeedRef("new-1", name)
        }

        override suspend fun updateCustomFeed(id: String, name: String, tags: List<String>): Boolean = true

        override suspend fun deleteCustomFeed(id: String): Boolean = feeds.removeAll { it.id == id }
    }

    @Test
    fun `login capability is detected and a successful login updates the account state`() = runTest(testDispatcher) {
        val source = FakeAccountSource(2001)
        val model = buildModel(sourceId = 2001, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isLoginCapable shouldBe true
        model.state.value.loggedInAccount shouldBe null

        model.login("a@b.c", "pw")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.loggedInAccount shouldBe "a@b.c"
        model.state.value.loginError shouldBe null
        model.state.value.isLoggingIn shouldBe false
        source.loginRequests shouldBe listOf("a@b.c" to "pw")
    }

    @Test
    fun `rejected login surfaces the generic error and stays logged out`() = runTest(testDispatcher) {
        val source = FakeAccountSource(2002)
        source.loginResult = false
        val model = buildModel(sourceId = 2002, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.login("x@y.z", "bad")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.loggedInAccount shouldBe null
        model.state.value.loginRejected shouldBe true
        model.state.value.loginError shouldBe null
        model.state.value.isLoggingIn shouldBe false
    }

    @Test
    fun `transport failure during login surfaces the exception message`() = runTest(testDispatcher) {
        val source = object : AnimeFeedSource, AnimeFeedLoginSource {
            override val id: Long = 2009L
            override val name: String = "Failing Login Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(emptyList(), false)

            override suspend fun login(email: String, password: String): Boolean =
                throw RuntimeException("network down")

            override fun isLoggedIn(): Boolean = false
            override fun loggedInAccount(): String? = null
            override suspend fun logout() = Unit
        }
        val model = buildModel(sourceId = 2009, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.login("a@b.c", "pw")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.loggedInAccount shouldBe null
        model.state.value.loginError shouldBe "network down"
    }

    // ---- Contract v20: hosted web login ----

    private open class FakeWebLoginSource(
        override val id: Long,
        override val name: String = "Web Login Feed $id",
    ) : AnimeFeedSource, AnimeFeedLoginSource, AnimeFeedWebLoginSource {
        override val lang: String = "all"

        var storedEmail: String? = null
        var importResult: Boolean = true
        val importedSessions = mutableListOf<Pair<Map<String, String>, Map<String, String>>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun login(email: String, password: String): Boolean = false

        override fun isLoggedIn(): Boolean = storedEmail != null

        override fun loggedInAccount(): String? = storedEmail

        override suspend fun logout() {
            storedEmail = null
        }

        override fun webLoginUrl(): String = "https://example.invalid/"

        override fun ownAuthorizeUrl(): String = "https://example.invalid/oauth2/auth?state=own"

        override fun isOwnLoginRedirect(url: String): Boolean = url.contains("state=own")

        override suspend fun importWebRedirect(url: String, cookies: Map<String, String>): Boolean {
            importedSessions += emptyMap<String, String>() to mapOf("redirect" to url)
            if (!importResult) return false
            storedEmail = "web@example.invalid"
            return true
        }

        override suspend fun importWebSession(
            cookies: Map<String, String>,
            localStorage: Map<String, String>,
        ): Boolean {
            importedSessions += cookies to localStorage
            if (!importResult) return false
            storedEmail = "web@example.invalid"
            return true
        }
    }

    private class FakeScopedWebLoginSource(
        id: Long,
    ) : FakeWebLoginSource(id), AnimeFeedOriginScopedSessionSource {
        val scopedSessions = mutableListOf<Pair<List<WebLoginCookie>, Map<String, String>>>()
        val scopedRedirects = mutableListOf<Pair<String, List<WebLoginCookie>>>()

        override suspend fun importWebSessionScoped(
            cookies: List<WebLoginCookie>,
            localStorage: Map<String, String>,
        ): Boolean {
            scopedSessions += cookies to localStorage
            if (!importResult) return false
            storedEmail = "web@example.invalid"
            return true
        }

        override suspend fun importWebRedirectScoped(
            url: String,
            cookies: List<WebLoginCookie>,
        ): Boolean {
            scopedRedirects += url to cookies
            if (!importResult) return false
            storedEmail = "web@example.invalid"
            return true
        }
    }

    // ---- Contract v20: category browse (NICHE mode) ----

    private class FakeBrowseSource(
        override val id: Long,
        override val name: String = "Browse Feed $id",
    ) : AnimeFeedSource, AnimeFeedBrowseSource, AnimeCategorySubscriptionSource {
        override val lang: String = "all"

        val categoryRequests = mutableListOf<Int>()
        val feedRequests = mutableListOf<Pair<String, Int>>()
        val followed = mutableSetOf<String>()
        val subscriptionCalls = mutableListOf<Pair<String, Boolean>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage {
            categoryRequests += page
            return FeedCategoryPage(
                categories = listOf(FeedCategory(id = "c$page", name = "niche$page")),
                hasNextPage = page < 2,
            )
        }

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage {
            feedRequests += categoryId to page
            return FeedPage(
                videos = listOf(
                    ShortVideoItem(
                        id = "niche-$categoryId-p$page",
                        videoUrl = "https://example.com/niche-$categoryId-p$page.mp4",
                        posterUrl = "https://example.com/niche-$categoryId-p$page.jpg",
                    ),
                ),
                hasNextPage = false,
            )
        }

        override suspend fun getSubscribedCategoryIds(): List<String> = followed.toList()

        override suspend fun setCategorySubscription(categoryId: String, followed: Boolean): Boolean {
            subscriptionCalls += categoryId to followed
            if (followed) this.followed += categoryId else this.followed -= categoryId
            return true
        }
    }

    // ---- Contract v20: categorized search ----

    private class FakeCategorizedSource(
        override val id: Long,
        override val name: String = "Categorized Feed $id",
    ) : AnimeFeedSource, AnimeCategorizedSearchSource {
        override val lang: String = "all"

        var fail = false
        val queries = mutableListOf<String>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getSearchFeed(
            page: Int,
            cursor: String?,
            query: String,
            filters: AnimeFilterList,
        ): FeedPage = FeedPage(emptyList(), false)

        override suspend fun getCategorizedSearch(query: String): SearchSuggestions {
            queries += query
            if (fail) throw RuntimeException("search down")
            return SearchSuggestions(
                tags = listOf(SearchSuggestion("t1", "tag1", SearchSuggestionKind.TAG)),
            )
        }
    }

    @Test
    fun `categorized search fills the suggestion tabs`() = runTest(testDispatcher) {
        val source = FakeCategorizedSource(2301)
        val model = buildModel(sourceId = 2301, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.search("dance")
        testDispatcher.scheduler.advanceUntilIdle()

        source.queries shouldBe listOf("dance")
        model.state.value.searchSuggestions?.tags?.size shouldBe 1
    }

    @Test
    fun `source without the categorized capability keeps suggestions null`() = runTest(testDispatcher) {
        val source = FakeBrowseSource(2302)
        val model = buildModel(sourceId = 2302, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.search("dance")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.searchSuggestions shouldBe null
    }

    @Test
    fun `clearing the search clears the suggestions`() = runTest(testDispatcher) {
        val source = FakeCategorizedSource(2303)
        val model = buildModel(sourceId = 2303, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.search("dance")
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.searchSuggestions shouldNotBe null

        model.clearSearch()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.searchSuggestions shouldBe null
    }

    @Test
    fun `throwing categorized capability leaves suggestions null and the flat feed alive`() = runTest(testDispatcher) {
        val source = FakeCategorizedSource(2304)
        source.fail = true
        val model = buildModel(sourceId = 2304, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.search("dance")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.searchSuggestions shouldBe null
        model.state.value.error shouldBe null
    }

    @Test
    fun `niche mode serves the category feed with its title`() = runTest(testDispatcher) {
        val source = FakeBrowseSource(2201)
        val model = buildModel(
            sourceId = 2201,
            manager = sourceManagerOf(source),
            nicheId = "c1",
            nicheName = "niche one",
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.mode shouldBe ReelsFeedScreenModel.FeedMode.NICHE
        model.state.value.nicheName shouldBe "niche one"
        source.feedRequests shouldBe listOf("c1" to 1)
        model.state.value.items.shouldHaveSize(1)
    }

    @Test
    fun `niche mode loads and toggles the category follow state`() = runTest(testDispatcher) {
        val source = FakeBrowseSource(2203)
        source.followed += "c1"
        val model = buildModel(
            sourceId = 2203,
            manager = sourceManagerOf(source),
            nicheId = "c1",
            nicheName = "niche one",
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isCategorySubscribable shouldBe true
        model.state.value.isCategoryFollowed shouldBe true

        model.toggleCategoryFollow()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isCategoryFollowed shouldBe false
        source.subscriptionCalls shouldBe listOf("c1" to false)
    }

    @Test
    fun `web login import success closes the dialog and snapshots the account`() = runTest(testDispatcher) {
        val source = FakeWebLoginSource(2101)
        val model = buildModel(sourceId = 2101, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isWebLoginCapable shouldBe true
        model.openLoginFlow()
        model.state.value.isWebLoginDialogOpen shouldBe true
        model.state.value.isLoginDialogOpen shouldBe false

        // The first import arms the stage-2 upgrade (refreshable PKCE session) instead of
        // closing; the second one closes and snapshots the account.
        model.tryImportWebSession(
            listOf(WebLoginCookie("https://example.invalid", "sid", "1")),
            mapOf("kinde" to """{"access_token":"t"}"""),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.isWebLoginDialogOpen shouldBe true
        model.state.value.webLoginPendingClose shouldBe true
        model.state.value.webLoginStage2Attempt shouldBe 1

        model.tryImportWebSession(
            listOf(WebLoginCookie("https://example.invalid", "sid", "1")),
            mapOf("kinde" to """{"access_token":"t"}"""),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.isWebLoginDialogOpen shouldBe false
        model.state.value.loggedInAccount shouldBe "web@example.invalid"
        model.state.value.webLoginHint shouldBe false
        model.state.value.webLoginPendingClose shouldBe false
        source.importedSessions.shouldHaveSize(2)
    }

    // ---- Contract v20: content preferences ----

    private class FakePreferencesSource(
        override val id: Long,
        override val name: String = "Prefs Feed $id",
    ) : AnimeFeedSource, AnimeContentPreferencesSource {
        override val lang: String = "all"

        var options = listOf(
            ContentPreferenceOption("a", "A", true),
            ContentPreferenceOption("b", "B", false),
        )
        val saved = mutableListOf<List<String>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getContentPreferences(): List<ContentPreferenceOption> = options

        override suspend fun setContentPreferences(enabledIds: List<String>): Boolean {
            saved += enabledIds
            return true
        }
    }

    @Test
    fun `opening the preferences sheet loads the account toggles`() = runTest(testDispatcher) {
        val source = FakePreferencesSource(2401)
        val model = buildModel(sourceId = 2401, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isContentPreferencesCapable shouldBe true
        model.toggleContentPreferences(true)
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isContentPreferencesLoading shouldBe false
        model.state.value.contentPreferences?.size shouldBe 2
    }

    @Test
    fun `saving preferences forwards the enabled set to the source`() = runTest(testDispatcher) {
        val source = FakePreferencesSource(2402)
        val model = buildModel(sourceId = 2402, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.saveContentPreferences(listOf("a"))
        testDispatcher.scheduler.advanceUntilIdle()

        source.saved shouldBe listOf(listOf("a"))
        model.state.value.contentPreferencesError shouldBe null
    }

    @Test
    fun `own pkce redirect import closes the dialog and snapshots the account`() = runTest(testDispatcher) {
        val source = FakeWebLoginSource(2110)
        val model = buildModel(sourceId = 2110, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.isOwnWebLoginRedirect("https://example.invalid/?code=abc&state=own") shouldBe true
        model.isOwnWebLoginRedirect("https://example.invalid/?code=abc&state=other") shouldBe false
        model.openLoginFlow()
        model.tryImportWebRedirect("https://example.invalid/?code=abc&state=own", emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isWebLoginDialogOpen shouldBe false
        model.state.value.loggedInAccount shouldBe "web@example.invalid"
    }

    @Test
    fun `failed done-import bumps the stage-2 counter and keeps the dialog open`() = runTest(testDispatcher) {
        val source = FakeWebLoginSource(2111)
        source.importResult = false
        val model = buildModel(sourceId = 2111, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.openLoginFlow()
        model.tryImportWebSessionStage2(emptyList(), emptyMap())
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isWebLoginDialogOpen shouldBe true
        model.state.value.webLoginHint shouldBe true
        model.state.value.webLoginStage2Attempt shouldBe 1
    }

    @Test
    fun `web login import false keeps the dialog open with the hint`() = runTest(testDispatcher) {
        val source = FakeWebLoginSource(2102)
        source.importResult = false
        val model = buildModel(sourceId = 2102, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.openLoginFlow()
        model.tryImportWebSession(emptyList(), emptyMap())
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isWebLoginDialogOpen shouldBe true
        model.state.value.webLoginHint shouldBe true
        model.state.value.loggedInAccount shouldBe null
    }

    @Test
    fun `origin-scoped source receives per-origin records for session and redirect`() =
        runTest(testDispatcher) {
            val source = FakeScopedWebLoginSource(2120)
            val model = buildModel(sourceId = 2120, manager = sourceManagerOf(source))
            testDispatcher.scheduler.advanceUntilIdle()

            val records = listOf(
                WebLoginCookie("https://web.example.invalid", "sid", "web"),
                WebLoginCookie("https://api.example.invalid", "sid", "api"),
            )
            model.openLoginFlow()
            model.tryImportWebSession(records, mapOf("k" to "v"))
            model.tryImportWebRedirect("https://example.invalid/?code=abc", records)
            testDispatcher.scheduler.advanceUntilIdle()

            source.scopedSessions.shouldHaveSize(1)
            source.scopedSessions.single().first shouldBe records
            source.scopedRedirects.single().second shouldBe records
            // The legacy name -> value API must not be called when the capability exists.
            source.importedSessions.shouldHaveSize(0)
        }

    @Test
    fun `legacy source still receives the last-wins name merge`() = runTest(testDispatcher) {
        val source = FakeWebLoginSource(2121)
        val model = buildModel(sourceId = 2121, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.openLoginFlow()
        model.tryImportWebSession(
            listOf(
                WebLoginCookie("https://web.example.invalid", "sid", "web"),
                WebLoginCookie("https://api.example.invalid", "sid", "api"),
            ),
            emptyMap(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        source.importedSessions.single().first shouldBe mapOf("sid" to "api")
    }

    @Test
    fun `logout clears the account state`() = runTest(testDispatcher) {
        val source = FakeAccountSource(2003)
        source.storedEmail = "a@b.c"
        val model = buildModel(sourceId = 2003, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.loggedInAccount shouldBe "a@b.c"

        model.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.loggedInAccount shouldBe null
    }

    @Test
    fun `custom feed mode routes loadFeed through getCustomFeed with the feed id`() = runTest(testDispatcher) {
        val source = FakeAccountSource(2004)
        val model = buildModel(
            sourceId = 2004,
            manager = sourceManagerOf(source),
            customFeedId = "f1",
            customFeedName = "Feed 1",
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.mode shouldBe ReelsFeedScreenModel.FeedMode.CUSTOM
        model.state.value.items.map { it.id } shouldBe listOf("custom-f1-p1")
        source.requestedFeedPages shouldBe listOf("f1" to 1)
    }

    @Test
    fun `custom feed mode on a non-capable source surfaces an error`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(2005) { FeedPage(emptyList(), false) }
        val model = buildModel(sourceId = 2005, manager = sourceManagerOf(source), customFeedId = "f1")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.error shouldBe null
        model.state.value.errorRes shouldBe MR.strings.reels_source_missing_custom_feeds
        model.state.value.items.shouldHaveSize(0)
    }

    @Test
    fun `custom feeds picker loads the list and delete refreshes it`() = runTest(testDispatcher) {
        val source = FakeAccountSource(2006)
        val model = buildModel(sourceId = 2006, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.toggleCustomFeeds(true)
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.isCustomFeedsOpen shouldBe true
        model.state.value.customFeeds.map { it.id } shouldBe listOf("f1", "f2")

        model.deleteCustomFeed("f1")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.customFeeds.map { it.id } shouldBe listOf("f2")
    }

    @Test
    fun `album search routes from niche mode and its pagination flag gates loadMore`() = runTest(testDispatcher) {
        val source = FakeAlbumSearchSource()
        val model = buildModel(
            sourceId = 9201L,
            manager = sourceManagerOf(source),
            nicheId = "alb-1",
            nicheName = "Album One",
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Report fix: album search works from NICHE mode, not only GLOBAL.
        model.search("summer")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.albumSearchResults.map { it.id } shouldBe listOf("alb-1")
        source.albumRequests shouldBe listOf(1 to null)

        model.loadFeed()
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.albumSearchResults.map { it.id } shouldBe listOf("alb-1", "alb-2")
        model.state.value.albumSearchCanLoadMore shouldBe false

        // The album-search flag (not the feed flag) gates further pages now.
        model.loadFeed()
        testDispatcher.scheduler.advanceUntilIdle()
        source.albumRequests shouldBe listOf(1 to null, 2 to "c1")
    }

    @Test
    fun `album-capable live typing renders album cards and keeps the bar open`() = runTest(testDispatcher) {
        val source = FakeAlbumSearchSource()
        val model = buildModel(sourceId = 9201L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.toggleSearchBar(true)
        model.requestSearchSuggestions("sum")
        testDispatcher.scheduler.advanceUntilIdle()

        // Live album search (device fix): cards replace the 100-chip suggestion panel,
        // the bar stays open while typing, and no categorized suggestions are requested.
        model.state.value.searchQuery shouldBe "sum"
        model.state.value.albumSearchResults.map { it.id } shouldBe listOf("alb-1")
        model.state.value.isSearchBarOpen shouldBe true
        model.state.value.searchSuggestions shouldBe null
        source.albumRequests shouldBe listOf(1 to null)
    }

    @Test
    fun `deep-link seek silently appends pages until the tapped clip surfaces`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(
            id = 3301L,
            feedProvider = { page ->
                FeedPage(
                    videos = (1..3).map { i -> videoItem("p$page-$i") },
                    hasNextPage = page < 3,
                )
            },
        )
        val model = buildModel(sourceId = 3301L, manager = sourceManagerOf(source), resumeVideoId = "p2-2")
        testDispatcher.scheduler.advanceUntilIdle()

        val ids = model.state.value.items.map { it.id }
        ids shouldContain "p2-2"
        model.state.value.targetPageIndex shouldBe ids.indexOfFirst { it == "p2-2" }
        source.requestedPages shouldBe listOf(1, 2)
    }

    @Test
    fun `deep-link seek is bounded by the page budget and leaves the feed pageable`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(
            id = 3302L,
            feedProvider = { page ->
                // Endless feed where the tapped clip never appears.
                FeedPage(
                    videos = (1..3).map { i -> videoItem("q$page-$i") },
                    hasNextPage = true,
                )
            },
        )
        val model = buildModel(sourceId = 3302L, manager = sourceManagerOf(source), resumeVideoId = "never-there")
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial page + exactly RESUME_SEEK_MAX_PAGES silent appends, then the seek gives up.
        source.requestedPages shouldBe (1..1 + ReelsFeedScreenModel.RESUME_SEEK_MAX_PAGES).toList()
        model.state.value.canLoadMore shouldBe true

        model.loadFeed(reset = false)
        testDispatcher.scheduler.advanceUntilIdle()

        // The budget ended only the silent seek: ordinary pagination still appends.
        source.requestedPages.last() shouldBe 1 + ReelsFeedScreenModel.RESUME_SEEK_MAX_PAGES + 1
        model.state.value.items.size shouldBe (1 + ReelsFeedScreenModel.RESUME_SEEK_MAX_PAGES + 1) * 3
    }

    @Test
    fun `album search stops when the locked cursor disappears`() = runTest(testDispatcher) {
        val source = CursorAlbumSearchSource()
        val model = buildModel(sourceId = 9301L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.search("summer")
        testDispatcher.scheduler.advanceUntilIdle()
        source.albumRequests shouldBe listOf(1 to null)

        model.loadFeed(reset = false)
        testDispatcher.scheduler.advanceUntilIdle()

        source.albumRequests shouldBe listOf(1 to null, 2 to "c1")
        model.state.value.albumSearchResults.map { it.id } shouldBe listOf("alb-1", "alb-2")
        model.state.value.albumSearchCanLoadMore shouldBe false

        // A protocol violation must not restart the directory head.
        model.loadFeed(reset = false)
        testDispatcher.scheduler.advanceUntilIdle()
        source.albumRequests shouldBe listOf(1 to null, 2 to "c1")
    }

    @Test
    fun `a late album search response never overwrites the newer query results`() = runTest(testDispatcher) {
        val source = GatedAlbumSearchSource()
        val model = buildModel(sourceId = 9302L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        // The slow query ignores cancellation and stays in flight...
        model.search("slow")
        testDispatcher.scheduler.runCurrent()
        source.queries shouldBe listOf("slow")

        // ...while the newer query completes first and owns the state.
        model.search("fresh")
        testDispatcher.scheduler.advanceUntilIdle()
        source.queries shouldBe listOf("slow", "fresh")
        model.state.value.albumSearchResults.map { it.id } shouldBe listOf("fresh")
        model.state.value.albumSearchNextCursor shouldBe "fresh-token"

        // The stale page lands last and must be dropped wholesale.
        source.gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.albumSearchResults.map { it.id } shouldBe listOf("fresh")
        model.state.value.albumSearchNextCursor shouldBe "fresh-token"
        model.state.value.albumSearchCanLoadMore shouldBe true
    }

    @Test
    fun `album-capable source renders search as paginated album directory`() = runTest(testDispatcher) {
        val source = FakeAlbumSearchSource()
        val model = buildModel(sourceId = 9201L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        model.search("summer")
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.albumSearchCapable shouldBe true
        model.state.value.albumSearchResults.map { it.id } shouldBe listOf("alb-1")
        model.state.value.albumSearchCanLoadMore shouldBe true

        model.loadFeed()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.albumSearchResults.map { it.id } shouldBe listOf("alb-1", "alb-2")
        model.state.value.albumSearchCanLoadMore shouldBe false
        source.albumRequests shouldBe listOf(1 to null, 2 to "c1")
    }

    private class FakeAlbumSearchSource : AnimeFeedSource, AnimeAlbumSearchSource, AnimeFeedBrowseSource {
        override val id: Long = 9201L
        override val name: String = "Album Search"
        override val lang: String = "all"
        val albumRequests = mutableListOf<Pair<Int, String?>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage =
            FeedCategoryPage(emptyList(), hasNextPage = false)

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), hasNextPage = false)

        override suspend fun searchAlbums(query: String, page: Int, cursor: String?): FeedCategoryPage {
            albumRequests += page to cursor
            return if (page == 1) {
                FeedCategoryPage(
                    categories = listOf(
                        FeedCategory(id = "alb-1", name = "Album One", imageUrl = null, itemCount = 3),
                    ),
                    hasNextPage = true,
                    nextCursor = "c1",
                )
            } else {
                FeedCategoryPage(
                    categories = listOf(
                        FeedCategory(id = "alb-2", name = "Album Two", imageUrl = null, itemCount = 1),
                    ),
                    hasNextPage = false,
                )
            }
        }
    }

    /** Page 1 locks cursor "c1"; page 2 drops it while still claiming more pages. */
    private class CursorAlbumSearchSource : AnimeFeedSource, AnimeAlbumSearchSource, AnimeFeedBrowseSource {
        override val id: Long = 9301L
        override val name: String = "Cursor Album Search"
        override val lang: String = "all"
        val albumRequests = mutableListOf<Pair<Int, String?>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage =
            FeedCategoryPage(emptyList(), hasNextPage = false)

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), hasNextPage = false)

        override suspend fun searchAlbums(query: String, page: Int, cursor: String?): FeedCategoryPage {
            albumRequests += page to cursor
            return if (page == 1) {
                FeedCategoryPage(
                    categories = listOf(FeedCategory(id = "alb-1", name = "Album One")),
                    hasNextPage = true,
                    nextCursor = "c1",
                )
            } else {
                FeedCategoryPage(
                    categories = listOf(FeedCategory(id = "alb-2", name = "Album Two")),
                    hasNextPage = true,
                    nextCursor = null,
                )
            }
        }
    }

    /** The "slow" query survives cancellation so its late response really reaches the model. */
    private class GatedAlbumSearchSource : AnimeFeedSource, AnimeAlbumSearchSource, AnimeFeedBrowseSource {
        override val id: Long = 9302L
        override val name: String = "Gated Album Search"
        override val lang: String = "all"
        val queries = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage =
            FeedCategoryPage(emptyList(), hasNextPage = false)

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), hasNextPage = false)

        override suspend fun searchAlbums(query: String, page: Int, cursor: String?): FeedCategoryPage {
            queries += query
            return if (query == "slow") {
                withContext(NonCancellable) { gate.await() }
                FeedCategoryPage(
                    categories = listOf(FeedCategory(id = "stale", name = "Stale")),
                    hasNextPage = true,
                    nextCursor = "stale-token",
                )
            } else {
                FeedCategoryPage(
                    categories = listOf(FeedCategory(id = "fresh", name = "Fresh")),
                    hasNextPage = true,
                    nextCursor = "fresh-token",
                )
            }
        }
    }
}
