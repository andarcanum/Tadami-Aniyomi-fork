package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeAlbumSearchSource
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.AnimeFeedErrorClassifierSource
import eu.kanade.tachiyomi.animesource.AnimeFeedLoginSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.FeedCategoryPage
import eu.kanade.tachiyomi.animesource.model.FeedError
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsAlbumsScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class CatalogSource(
        override val id: Long = 6001L,
        override val name: String = "Catalog Source",
        private val provider: suspend (page: Int, cursor: String?) -> FeedCategoryPage,
    ) : AnimeFeedSource, AnimeFeedBrowseSource {
        override val lang: String = "all"
        val requests = mutableListOf<Pair<Int, String?>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage {
            requests += page to cursor
            return provider(page, cursor)
        }

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), false)
    }

    private class AlbumSearchCapableSource(
        override val id: Long,
        override val name: String,
        private val browseProvider: suspend (Int, String?) -> FeedCategoryPage,
        private val searchProvider: suspend (String, Int, String?) -> FeedCategoryPage,
    ) : AnimeFeedSource, AnimeFeedBrowseSource, AnimeAlbumSearchSource {
        override val lang: String = "all"
        val browseCalls = mutableListOf<Pair<Int, String?>>()
        val searchCalls = mutableListOf<Triple<String, Int, String?>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage {
            browseCalls += page to cursor
            return browseProvider(page, cursor)
        }

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun searchAlbums(query: String, page: Int, cursor: String?): FeedCategoryPage {
            searchCalls += Triple(query, page, cursor)
            return searchProvider(query, page, cursor)
        }
    }

    private class AuthClassifiedSource(
        override val id: Long,
        override val name: String,
        var loggedIn: Boolean = false,
        private val classifier: (Throwable) -> FeedError? = { null },
        private val loginHandler: (suspend (String, String) -> Boolean)? = null,
        private val provider: suspend (Int, String?) -> FeedCategoryPage,
    ) : AnimeFeedSource, AnimeFeedBrowseSource, AnimeFeedLoginSource, AnimeFeedErrorClassifierSource {
        override val lang: String = "all"
        var browseCount = 0

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage {
            browseCount++
            return provider(page, cursor)
        }

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun login(email: String, password: String): Boolean {
            if (loginHandler != null) return loginHandler.invoke(email, password)
            loggedIn = email.isNotBlank() && password == "secret"
            return loggedIn
        }

        override fun isLoggedIn(): Boolean = loggedIn

        override fun loggedInAccount(): String? = if (loggedIn) "user@example.com" else null

        override suspend fun logout() {
            loggedIn = false
        }

        override fun classifyFeedError(error: Throwable): FeedError? = classifier(error)
    }

    private fun sourceManagerOf(
        vararg sources: AnimeSource,
        initialized: Boolean = true,
    ): AnimeSourceManager = object : AnimeSourceManager {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(initialized).asStateFlow()
        override val sources: Flow<List<AnimeSource>> = MutableStateFlow(sources.toList())
        override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
        override fun get(sourceKey: Long): AnimeSource? = sources.firstOrNull { it.id == sourceKey }
        override fun getOrStub(sourceKey: Long): AnimeSource = get(sourceKey) ?: sources.first()
        override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
        override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
        override fun getStubSources(): List<StubAnimeSource> = emptyList()
    }

    private fun buildModel(
        vararg sources: AnimeSource,
        sourceId: Long? = sources.firstOrNull()?.id,
        albumRepository: ReelsAlbumRepository = FakeAlbumRepository(),
        disabledSourcesFlow: Flow<Set<String>> = MutableStateFlow(emptySet()),
        remoteCache: ReelsRemoteAlbumsCache = ReelsRemoteAlbumsCache(),
        nowEpochMs: () -> Long = { 10_000L },
    ) = ReelsAlbumsScreenModel(
        sourceId = sourceId,
        albumRepository = albumRepository,
        sourceManager = sourceManagerOf(*sources),
        ioDispatcher = testDispatcher,
        disabledSourcesFlow = disabledSourcesFlow,
        sourceIconProvider = { null },
        remoteCache = remoteCache,
        nowEpochMs = nowEpochMs,
    )

    private fun category(id: String, name: String = "Category $id") = FeedCategory(id = id, name = name)

    @Test
    fun `the remote catalog paginates and dedupes ids across pages`() = runTest(testDispatcher) {
        val source = CatalogSource { page, _ ->
            if (page == 1) {
                FeedCategoryPage(listOf(category("c1"), category("c2")), hasNextPage = true)
            } else {
                FeedCategoryPage(listOf(category("c2"), category("c3")), hasNextPage = false)
            }
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialGroup = model.state.value.remoteGroups.single()
        initialGroup.categories.map { it.id } shouldBe listOf("c1", "c2")
        initialGroup.pagination.canLoadMore shouldBe true

        model.loadCatalogMore()
        testDispatcher.scheduler.advanceUntilIdle()

        source.requests shouldBe listOf(1 to null, 2 to null)
        val loadedGroup = model.state.value.remoteGroups.single()
        loadedGroup.categories.map { it.id } shouldBe listOf("c1", "c2", "c3")
        loadedGroup.pagination.canLoadMore shouldBe false
    }

    @Test
    fun `a dropped catalog cursor stops pagination instead of replaying the head`() = runTest(testDispatcher) {
        val source = CatalogSource { page, _ ->
            if (page == 1) {
                FeedCategoryPage(listOf(category("c1")), hasNextPage = true, nextCursor = "T")
            } else {
                FeedCategoryPage(listOf(category("c2")), hasNextPage = true, nextCursor = null)
            }
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.loadCatalogMore()
        testDispatcher.scheduler.advanceUntilIdle()

        val group = model.state.value.remoteGroups.single()
        group.categories.map { it.id } shouldBe listOf("c1", "c2")
        group.pagination.canLoadMore shouldBe false

        model.loadCatalogMore()
        testDispatcher.scheduler.advanceUntilIdle()
        source.requests shouldBe listOf(1 to null, 2 to "T")
    }

    @Test
    fun `catalog append failure keeps the rows and retry repeats the same page and cursor`() =
        runTest(testDispatcher) {
            var failed = false
            val source = CatalogSource { page, _ ->
                if (page == 1) {
                    FeedCategoryPage(listOf(category("c1"), category("c2")), hasNextPage = true, nextCursor = "T")
                } else {
                    if (!failed) {
                        failed = true
                        throw IllegalStateException("offline")
                    }
                    FeedCategoryPage(listOf(category("c3")), hasNextPage = false)
                }
            }
            val model = buildModel(source)
            testDispatcher.scheduler.advanceUntilIdle()

            model.loadCatalogMore()
            testDispatcher.scheduler.advanceUntilIdle()

            val failedGroup = model.state.value.remoteGroups.single()
            failedGroup.categories.map { it.id } shouldBe listOf("c1", "c2")
            failedGroup.pageError shouldNotBe null
            failedGroup.pagination.canLoadMore shouldBe true
            failedGroup.pagination.nextPage shouldBe 2
            failedGroup.pagination.nextCursor shouldBe "T"

            model.loadCatalogMore()
            testDispatcher.scheduler.advanceUntilIdle()

            source.requests shouldBe listOf(1 to null, 2 to "T", 2 to "T")
            val retriedGroup = model.state.value.remoteGroups.single()
            retriedGroup.pageError shouldBe null
            retriedGroup.categories.map { it.id } shouldBe listOf("c1", "c2", "c3")
            retriedGroup.pagination.canLoadMore shouldBe false
        }

    @Test
    fun `reloading the catalog resets the stream and its cursor`() = runTest(testDispatcher) {
        val source = CatalogSource { page, _ ->
            if (page == 1) {
                FeedCategoryPage(listOf(category("c1")), hasNextPage = true, nextCursor = "T")
            } else {
                FeedCategoryPage(listOf(category("c2")), hasNextPage = false)
            }
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.loadCatalogMore()
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.remoteGroups.single().categories.map { it.id } shouldBe listOf("c1", "c2")

        model.loadCatalog()
        testDispatcher.scheduler.advanceUntilIdle()

        source.requests shouldBe listOf(1 to null, 2 to "T", 1 to null)
        val resetGroup = model.state.value.remoteGroups.single()
        resetGroup.categories.map { it.id } shouldBe listOf("c1")
        resetGroup.pagination.canLoadMore shouldBe true
    }

    @Test
    fun `saved albums aggregate across all plugins, filter by source, and clean up missing sources`() =
        runTest(testDispatcher) {
            val repo = FakeAlbumRepository()
            repo.insert(ReelsAlbum(6001L, "shared", "Alpha One", null, Date(1_000L)))
            repo.insert(ReelsAlbum(6002L, "shared", "Beta Two", null, Date(3_000L)))
            repo.insert(ReelsAlbum(9999L, "gone", "Missing Three", null, Date(2_000L)))

            val s1 = CatalogSource(id = 6001L, name = "Alpha") { _, _ -> FeedCategoryPage(emptyList(), false) }
            val s2 = CatalogSource(id = 6002L, name = "Beta") { _, _ -> FeedCategoryPage(emptyList(), false) }

            // Even when opened from source 6001, default filter stays "All" across installed plugins.
            val model = buildModel(s1, s2, sourceId = 6001L, albumRepository = repo)
            testDispatcher.scheduler.advanceUntilIdle()

            model.state.value.selectedSourceFilterId shouldBe null
            model.state.value.visibleSavedAlbums.map { it.sourceId to it.albumId } shouldBe listOf(
                6002L to "shared",
                9999L to "gone",
                6001L to "shared",
            )
            model.state.value.missingSourceIds shouldBe setOf(9999L)

            model.selectSourceFilter(6001L)
            model.state.value.visibleSavedAlbums.map { it.sourceId to it.albumId } shouldBe listOf(
                6001L to "shared",
            )

            model.selectSourceFilter(null)
            model.cleanupMissingSources()
            testDispatcher.scheduler.advanceUntilIdle()

            model.state.value.cleanupDeletedCount shouldBe 1
            model.state.value.savedAlbums.map { it.sourceId } shouldBe listOf(6002L, 6001L)
            model.state.value.missingSourceIds shouldBe emptySet()
        }

    @Test
    fun `multi-plugin fanout isolates login precheck and rate-limit errors per source`() =
        runTest(testDispatcher) {
            val okSource = CatalogSource(id = 7001L, name = "Alpha") { _, _ ->
                FeedCategoryPage(listOf(category("ok-1")), hasNextPage = false)
            }
            val authSource = AuthClassifiedSource(id = 7002L, name = "Private", loggedIn = false) { _, _ ->
                FeedCategoryPage(listOf(category("secret-1")), hasNextPage = false)
            }
            val rateLimitedSource = AuthClassifiedSource(
                id = 7003L,
                name = "Throttled",
                loggedIn = true,
                classifier = { FeedError.RateLimited(retryAtEpochMillis = 99_999L) },
            ) { _, _ ->
                throw IllegalStateException("429")
            }

            val model = buildModel(okSource, authSource, rateLimitedSource, sourceId = null)
            testDispatcher.scheduler.advanceUntilIdle()

            // Pre-check skipped network call on unauthenticated source
            authSource.browseCount shouldBe 0

            val byId = model.state.value.remoteGroups.associateBy { it.sourceId }
            byId.getValue(7001L).status shouldBe RemoteSourceStatus.Ready
            byId.getValue(7001L).categories.map { it.id } shouldBe listOf("ok-1")
            byId.getValue(7002L).status shouldBe RemoteSourceStatus.NeedsAuth
            byId.getValue(7003L).status shouldBe RemoteSourceStatus.RateLimited(99_999L)

            // Logging in on 7002 reloads only 7002 without touching 7001 again
            model.openLoginPrompt(7002L)
            model.submitLogin("user@example.com", "secret")
            testDispatcher.scheduler.advanceUntilIdle()

            val afterLogin = model.state.value.remoteGroups.associateBy { it.sourceId }
            afterLogin.getValue(7002L).status shouldBe RemoteSourceStatus.Ready
            afterLogin.getValue(7002L).categories.map { it.id } shouldBe listOf("secret-1")
            okSource.requests.size shouldBe 1
        }

    @Test
    fun `multi-plugin fanout bounds concurrency to MAX_REMOTE_CONCURRENCY`() = runTest(testDispatcher) {
        val active = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        val sources = (1..6).map { index ->
            CatalogSource(id = 8000L + index, name = "S$index") { _, _ ->
                val current = active.incrementAndGet()
                maxSeen.updateAndGet { prev -> maxOf(prev, current) }
                delay(50)
                active.decrementAndGet()
                FeedCategoryPage(listOf(category("cat-$index")), hasNextPage = false)
            }
        }.toTypedArray()

        buildModel(*sources, sourceId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        maxSeen.get() shouldBeLessThanOrEqual ReelsAlbumsScreenModel.MAX_REMOTE_CONCURRENCY
    }

    @Test
    fun `cross-plugin search routes AlbumSearchSource to searchAlbums and filters browse-only plugins`() =
        runTest(testDispatcher) {
            val searchSource = AlbumSearchCapableSource(
                id = 9001L,
                name = "Bunkr-like",
                browseProvider = { _, _ -> FeedCategoryPage(listOf(category("b1", "General")), false) },
                searchProvider = { query, _, _ ->
                    FeedCategoryPage(listOf(category("s1", "Result $query")), false)
                },
            )
            val browseOnlySource = CatalogSource(id = 9002L, name = "BrowseOnly") { _, _ ->
                FeedCategoryPage(
                    listOf(category("k1", "Neon Cyberpunk"), category("k2", "Nature")),
                    hasNextPage = false,
                )
            }

            val model = buildModel(searchSource, browseOnlySource, sourceId = null)
            testDispatcher.scheduler.advanceUntilIdle()

            model.setRemoteSearchQuery("neon")
            model.submitRemoteSearch()
            testDispatcher.scheduler.advanceUntilIdle()

            val byId = model.state.value.remoteGroups.associateBy { it.sourceId }
            searchSource.searchCalls shouldBe listOf(Triple("neon", 1, null))
            byId.getValue(9001L).categories.map { it.name } shouldBe listOf("Result neon")
            byId.getValue(9002L).categories.map { it.id } shouldBe listOf("k1")
        }

    @Test
    fun `session cache avoids re-fanout within TTL and refreshes after expiry or explicit refresh`() =
        runTest(testDispatcher) {
            var nowMs = 1_000L
            val sharedCache = ReelsRemoteAlbumsCache()
            val source = CatalogSource(id = 9100L, name = "Cached") { _, _ ->
                FeedCategoryPage(listOf(category("c1")), hasNextPage = false)
            }

            buildModel(
                source,
                sourceId = null,
                remoteCache = sharedCache,
                nowEpochMs = { nowMs },
            )
            testDispatcher.scheduler.advanceUntilIdle()
            source.requests.size shouldBe 1

            // Re-opening within TTL reuses cached categories with zero extra calls
            nowMs += 30_000L
            val secondModel = buildModel(
                source,
                sourceId = null,
                remoteCache = sharedCache,
                nowEpochMs = { nowMs },
            )
            testDispatcher.scheduler.advanceUntilIdle()
            source.requests.size shouldBe 1
            secondModel.state.value.remoteGroups.single().categories.map { it.id } shouldBe listOf("c1")

            // Explicit refresh bypasses the cache
            secondModel.refreshRemote()
            testDispatcher.scheduler.advanceUntilIdle()
            source.requests.size shouldBe 2

            // Advancing past TTL triggers a fresh fetch on a new instance
            nowMs += ReelsAlbumsScreenModel.DEFAULT_REMOTE_CACHE_TTL_MS + 1L
            buildModel(
                source,
                sourceId = null,
                remoteCache = sharedCache,
                nowEpochMs = { nowMs },
            )
            testDispatcher.scheduler.advanceUntilIdle()
            source.requests.size shouldBe 3
        }

    @Test
    fun `submitLogin rethrows CancellationException without surfacing an error state`() =
        runTest(testDispatcher) {
            val authSource = AuthClassifiedSource(
                id = 9200L,
                name = "AuthCancel",
                loggedIn = false,
                loginHandler = { _, _ ->
                    throw CancellationException("login cancelled")
                },
            ) { _, _ ->
                FeedCategoryPage(listOf(category("c1")), hasNextPage = false)
            }

            val model = buildModel(authSource, sourceId = null)
            testDispatcher.scheduler.advanceUntilIdle()

            model.openLoginPrompt(9200L)
            model.submitLogin("user@example.com", "secret")
            testDispatcher.scheduler.advanceUntilIdle()

            val prompt = model.state.value.loginPrompt
            prompt shouldNotBe null
            prompt?.error shouldBe null
            prompt?.rejected shouldBe false
        }

    @Test
    fun `superseded loadCatalogMore finally block does not clear a newer append generation`() =
        runTest(testDispatcher) {
            val staleGate = CompletableDeferred<Unit>()
            val freshGate = CompletableDeferred<Unit>()
            val callCount = AtomicInteger(0)
            val source = CatalogSource(id = 9300L, name = "RaceSource") { _, _ ->
                when (callCount.incrementAndGet()) {
                    1 -> FeedCategoryPage(listOf(category("p1")), hasNextPage = true)
                    2 -> {
                        staleGate.await()
                        FeedCategoryPage(listOf(category("p2-stale")), hasNextPage = false)
                    }
                    3 -> FeedCategoryPage(listOf(category("p1-retry")), hasNextPage = true)
                    else -> {
                        freshGate.await()
                        FeedCategoryPage(listOf(category("p2-fresh")), hasNextPage = false)
                    }
                }
            }

            val model = buildModel(source, sourceId = null)
            testDispatcher.scheduler.advanceUntilIdle()

            // Gen 1 loadCatalogMore suspends at staleGate.
            model.loadCatalogMore(9300L)
            testDispatcher.scheduler.runCurrent()
            model.isAppendInFlight(9300L) shouldBe true

            // Retry supersedes Gen 1 and enables a new Gen 2 append.
            model.retryRemoteSource(9300L)
            testDispatcher.scheduler.runCurrent()

            // Gen 2 loadCatalogMore suspends at freshGate.
            model.loadCatalogMore(9300L)
            testDispatcher.scheduler.runCurrent()
            model.isAppendInFlight(9300L) shouldBe true

            // Let Gen 1 finish and execute its finally block while Gen 2 is still in-flight.
            staleGate.complete(Unit)
            testDispatcher.scheduler.runCurrent()

            model.isAppendInFlight(9300L) shouldBe true
            model.state.value.remoteGroups.single().categories.map { it.id } shouldBe listOf("p1-retry")

            // Completing Gen 2 appends its result and finally clears the lock.
            freshGate.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()

            model.isAppendInFlight(9300L) shouldBe false
            model.state.value.remoteGroups.single().categories.map { it.id } shouldBe listOf("p1-retry", "p2-fresh")
        }

    private class FakeAlbumRepository : ReelsAlbumRepository {
        private val albums = linkedMapOf<Pair<Long, String>, ReelsAlbum>()
        private val allFlow = MutableStateFlow<List<ReelsAlbum>>(emptyList())
        private val bySource = mutableMapOf<Long, MutableStateFlow<List<ReelsAlbum>>>()

        private fun emitFlows() {
            val ordered = albums.values.sortedByDescending { it.addedAt }
            allFlow.value = ordered
            bySource.forEach { (sid, flow) ->
                flow.value = ordered.filter { it.sourceId == sid }
            }
        }

        override fun subscribeAll(): Flow<List<ReelsAlbum>> = allFlow

        override suspend fun getAll(): List<ReelsAlbum> =
            albums.values.sortedByDescending { it.addedAt }

        override fun subscribeBySource(sourceId: Long): Flow<List<ReelsAlbum>> =
            bySource.getOrPut(sourceId) {
                MutableStateFlow(albums.values.filter { it.sourceId == sourceId }.sortedByDescending { it.addedAt })
            }

        override suspend fun getBySource(sourceId: Long): List<ReelsAlbum> =
            albums.values.filter { it.sourceId == sourceId }.sortedByDescending { it.addedAt }

        override suspend fun insert(album: ReelsAlbum) {
            albums[album.sourceId to album.albumId] = album
            emitFlows()
        }

        override suspend fun delete(sourceId: Long, albumId: String) {
            albums.remove(sourceId to albumId)
            emitFlows()
        }

        override suspend fun deleteBySource(sourceId: Long) {
            albums.keys.filter { it.first == sourceId }.forEach { albums.remove(it) }
            emitFlows()
        }

        override fun subscribeWatched(sourceId: Long): Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun markWatched(sourceId: Long, videoId: String) = Unit

        override suspend fun unmarkWatched(sourceId: Long, videoId: String) = Unit

        override suspend fun deleteWatchedBySource(sourceId: Long) = Unit
    }
}
