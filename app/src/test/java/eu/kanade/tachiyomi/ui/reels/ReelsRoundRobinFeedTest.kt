package eu.kanade.tachiyomi.ui.reels

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeCreatorFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedRoundRobinSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedReplayItemResult
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinCandidate
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinCatalog
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinCatalogResult
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinIdentity
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinReadiness
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinScanRequest
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinScanResult
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinSourceRef
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.reels.anime.model.ReelsActiveCheckpoint
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsConsumptionEntry
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.domain.reels.anime.model.ReelsQueuedCandidate
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCommit
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinHandle
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinMutation
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSnapshot
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinUpdate
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionWindow
import tachiyomi.domain.reels.anime.model.ReelsWatchEntry
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.reels.anime.repository.ReelsCandidateRepository
import tachiyomi.domain.reels.anime.repository.ReelsConsumptionRepository
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
import tachiyomi.domain.reels.anime.repository.ReelsHiddenRepository
import tachiyomi.domain.reels.anime.repository.ReelsRoundRobinRepository
import tachiyomi.domain.reels.anime.repository.ReelsSessionRepository
import tachiyomi.domain.reels.anime.repository.ReelsWatchRepository
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.util.Date
import java.util.UUID

/**
 * T8: the real ScreenModel owns exactly one round-robin route.
 *
 * These tests drive the production ScreenModel (no coordinator stubs) with a fake v28 source
 * and the real in-memory RR scheduler, and assert the host contract: no legacy getFeed on the
 * RR route, an immutable render owner that fences late callbacks, scoped consumption writes,
 * durable reopen, suffix repair, and untouched legacy routes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReelsRoundRobinFeedTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val models = mutableListOf<ReelsFeedScreenModel>()

    /**
     * The attached coordinator keeps a periodic catalog wake armed. `runTest` drains the shared
     * scheduler after the test body, so an undisposed model would make that drain chase the
     * self-renewing timer forever. Dispose (stop the actor) while the body's scheduler is still
     * under our control, then let the drain finish.
     */
    private fun roundRobinTest(block: suspend () -> Unit) = runTest(testDispatcher) {
        try {
            block()
        } finally {
            models.forEach { it.onDispose() }
            testScheduler.runCurrent()
            models.clear()
        }
    }

    private class Clock(var now: Long = 0L) {
        operator fun invoke(): Long = now
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

    private class TestConsumptionRepository : ReelsConsumptionRepository {
        var generationValue = 0L
        var ensureScopeCalls = 0
        var mergeScopeCalls = 0
        var resetCalls = 0
        var writes = 0

        // Keyed by (scope, itemId): a single item id may legitimately be watched by several
        // accounts, so a per-id map could not prove that one account's write never overwrites
        // another's.
        val watched = mutableSetOf<Pair<String, String>>()
        val skipped = mutableSetOf<Pair<String, String>>()
        val resetScopes = mutableListOf<String>()
        val externallyConsumed = mutableSetOf<String>()

        override suspend fun generation(scope: String): Long = generationValue

        override suspend fun ensureScope(scope: String, policyVersion: Int): Long {
            ensureScopeCalls++
            return generationValue
        }

        override suspend fun markWatched(
            scope: String,
            sourceId: Long,
            itemId: String,
            at: Date,
            surface: String?,
            policyVersion: Int,
            generation: Long,
        ): Boolean {
            if (generation != generationValue) return false
            writes++
            watched += scope to itemId
            return true
        }

        override suspend fun markSkipped(
            scope: String,
            sourceId: Long,
            itemId: String,
            at: Date,
            surface: String?,
            policyVersion: Int,
            generation: Long,
        ): Boolean {
            if (generation != generationValue) return false
            writes++
            skipped += scope to itemId
            return true
        }

        override suspend fun consumedIds(
            scope: String,
            sourceId: Long,
            itemIds: Collection<String>,
        ): Set<String> {
            return itemIds.filterTo(mutableSetOf()) { id ->
                id in externallyConsumed || (scope to id) in watched || (scope to id) in skipped
            }
        }

        override suspend fun entry(scope: String, sourceId: Long, itemId: String): ReelsConsumptionEntry? = null

        override suspend fun countConsumed(scope: String, sourceId: Long): Long = 0L

        override suspend fun mergeScope(fromScope: String, toScope: String, sourceId: Long) {
            mergeScopeCalls++
        }

        override suspend fun resetHistory(scope: String, policyVersion: Int): Long {
            resetCalls++
            resetScopes += scope
            watched.clear()
            skipped.clear()
            externallyConsumed.clear()
            return ++generationValue
        }
    }

    private class TestHiddenRepository : ReelsHiddenRepository {
        val hidden = mutableSetOf<String>()

        override fun subscribeAll(): Flow<List<ReelsHiddenEntry>> = emptyFlow()

        override suspend fun getBySource(sourceId: Long): List<ReelsHiddenEntry> =
            hidden.map {
                ReelsHiddenEntry(
                    sourceId = sourceId,
                    kind = ReelsHiddenEntry.KIND_VIDEO,
                    value = it,
                    hiddenAt = Date(0),
                )
            }

        override suspend fun insert(entry: ReelsHiddenEntry) {
            if (entry.kind != ReelsHiddenEntry.KIND_AUTHOR) hidden += entry.value
        }

        override suspend fun delete(sourceId: Long, kind: String, value: String) {
            hidden -= value
        }

        override suspend fun deleteBySource(sourceId: Long) {
            hidden.clear()
        }

        override suspend fun deleteAll() {
            hidden.clear()
        }
    }

    private class TestCandidateRepository : ReelsCandidateRepository {
        val removed = mutableListOf<String>()
        val resumePositions = mutableMapOf<String, Long>()

        override suspend fun ingest(scope: String, sourceId: Long, candidates: List<ReelsCandidate>): Int =
            candidates.size

        override suspend fun unconsumed(scope: String, sourceId: Long, limit: Int): List<ReelsCandidate> =
            emptyList()

        override suspend fun saveResumePosition(
            scope: String,
            sourceId: Long,
            itemId: String,
            positionMs: Long,
        ) {
            resumePositions[itemId] = positionMs
        }

        override suspend fun remove(scope: String, sourceId: Long, itemIds: Collection<String>) {
            removed += itemIds
        }

        override suspend fun count(scope: String, sourceId: Long): Long = 0L

        override suspend fun clear(scope: String) = Unit
    }

    /**
     * Session store double for the in-memory RR adapter: the adapter does not create the
     * session row itself (the durable one does), so the checkpoint is keyed by session key
     * plus history generation, exactly like the coordinator's owner fence.
     */
    private class TestSessionRepository : ReelsSessionRepository {
        private val handles = mutableMapOf<ReelsSessionKey, ReelsSessionHandle>()
        private val checkpoints = mutableMapOf<ReelsSessionKey, ReelsActiveCheckpoint>()
        private val generations = mutableMapOf<ReelsSessionKey, Long>()
        var openCalls = 0
        var checkpointWrites = 0
        var invalidations = 0
        val openKeys = mutableListOf<ReelsSessionKey>()

        // One session row per source, like the real table: a plain open with another context
        // replaces it and drops the active card (preserveCheckpoint = false).
        @Volatile
        var persistedContextKey: String? = null

        @Volatile
        var sessionActive: ReelsActiveCheckpoint? = null

        override suspend fun persistedContext(scope: String, sourceId: Long): String? =
            persistedContextKey

        override suspend fun open(
            key: ReelsSessionKey,
            ownerNonce: String,
            historyGeneration: Long,
            reset: Boolean,
        ): ReelsSessionHandle {
            openCalls++
            openKeys += key
            val existing = handles[key]
            val generationChanged = existing?.historyGeneration != historyGeneration
            val replaces = reset || existing == null || generationChanged ||
                (persistedContextKey != null && persistedContextKey != key.contextKey)
            if (replaces) {
                sessionActive = null
            }
            val sessionId = if (replaces) UUID.randomUUID().toString() else existing.sessionId
            if (reset || generationChanged) checkpoints.remove(key)
            persistedContextKey = key.contextKey
            val handle = ReelsSessionHandle(key, sessionId, ownerNonce, historyGeneration)
            handles[key] = handle
            return handle
        }

        override suspend fun active(handle: ReelsSessionHandle): ReelsActiveCheckpoint? {
            checkpoints[handle.key]?.takeIf { !it.completed }?.let { return it }
            return sessionActive?.takeIf { !it.completed }
        }

        override suspend fun window(
            handle: ReelsSessionHandle,
            afterOrdinal: Long,
            limit: Int,
        ): ReelsSessionWindow = ReelsSessionWindow(emptyList(), afterOrdinal, hasMore = false, deferredCount = 0)

        override suspend fun appendPage(
            handle: ReelsSessionHandle,
            candidates: List<ReelsCandidate>,
        ): Int = 0

        override suspend fun checkpoint(
            handle: ReelsSessionHandle,
            active: ReelsActiveCheckpoint,
        ): Boolean {
            checkpoints[handle.key] = active
            generations[handle.key] = handle.historyGeneration
            handles[handle.key] = handle
            checkpointWrites++
            return true
        }

        override suspend fun setAvailability(
            handle: ReelsSessionHandle,
            itemId: String,
            availability: String,
            retryAfter: Date?,
        ) = Unit

        override suspend fun invalidate(handle: ReelsSessionHandle, dropCheckpoint: Boolean) {
            invalidations++
            if (dropCheckpoint) checkpoints.remove(handle.key)
        }

        override suspend fun clearScope(scope: String) {
            handles.keys.filter { it.accountScopeId == scope }.forEach {
                handles.remove(it)
                checkpoints.remove(it)
                generations.remove(it)
            }
        }
    }

    /** Persistent RR adapter double: the real memory scheduler plus a signalable observer. */
    private class TestRoundRobinRepository(
        private val delegate: InMemoryReelsRoundRobinRepository,
    ) : ReelsRoundRobinRepository by delegate {
        private val eligibility = MutableSharedFlow<Unit>(extraBufferCapacity = 32)
        var openCalls = 0

        /** Every payload handed to durable storage, captured before the adapter persists it. */
        val storedCandidates = mutableListOf<ReelsCandidate>()

        fun signalEligibility() {
            eligibility.tryEmit(Unit)
        }

        override suspend fun open(
            key: ReelsSessionKey,
            ownerNonce: String,
            historyGeneration: Long,
            unseenOnly: Boolean,
        ): ReelsRoundRobinHandle {
            openCalls++
            return delegate.open(key, ownerNonce, historyGeneration, unseenOnly)
        }

        override suspend fun mutate(
            handle: ReelsRoundRobinHandle,
            expectedRevision: Long,
            change: ReelsRoundRobinMutation,
        ): ReelsRoundRobinCommit {
            if (change is ReelsRoundRobinMutation.Pump) {
                change.updates.filterIsInstance<ReelsRoundRobinUpdate.Page>().forEach { page ->
                    storedCandidates += page.entries.map { it.candidate }
                }
            }
            return delegate.mutate(handle, expectedRevision, change)
        }

        override fun observeEligibility(scope: String, sourceId: Long): Flow<Unit> = eligibility
    }

    private class TestRoundRobinSource(
        override val id: Long,
        private val clock: Clock,
    ) : AnimeFeedSource, AnimeFeedRoundRobinSource {
        override val name: String = "Round Robin $id"
        override val lang: String = "all"

        var roundRobinEnabled = true
        var identity: String? = "acct-a"
        private val replayContexts = MutableSharedFlow<FeedReplayContext?>(extraBufferCapacity = 16)
        var readinessOverride: FeedRoundRobinReadiness? = null
        var readinessHandler: (() -> FeedRoundRobinReadiness)? = null
        var catalogKeys: List<String> = listOf("a", "b", "c")
        var excludedKeys: Set<String> = emptySet()
        var historyExhausted = false
        val catalogRequests = mutableListOf<Pair<FeedReplayContext, Boolean>>()
        val scanRequests = mutableListOf<FeedRoundRobinScanRequest>()
        val scanCounts = mutableMapOf<String, Int>()
        val resolveRequests = mutableListOf<String>()
        var getFeedCalls = 0
        var searchFeedCalls = 0
        var prepareRoundRobinCalls = 0
        var resolveResult: (String) -> FeedReplayItemResult = { itemId ->
            FeedReplayItemResult.Available(item(itemId))
        }

        fun item(itemId: String): ShortVideoItem = ShortVideoItem(
            id = itemId,
            title = "Title $itemId",
            author = "Author $itemId",
            videoUrl = "https://cdn.example/$itemId.mp4",
            posterUrl = "https://cdn.example/$itemId.jpg",
            durationSec = 30f,
            hasAudio = true,
            webUrl = "https://web.example/$itemId",
        )

        private fun context() = FeedReplayContext(identity ?: "anon", "v27-${identity ?: "anon"}")

        override fun getFilterList(): AnimeFilterList = AnimeFilterList()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
            getFeedCalls++
            return FeedPage(listOf(item("legacy-$page")), hasNextPage = false)
        }

        override suspend fun getSearchFeed(
            page: Int,
            cursor: String?,
            query: String,
            filters: AnimeFilterList,
        ): FeedPage {
            searchFeedCalls++
            return FeedPage(listOf(item("search-$query")), hasNextPage = false)
        }

        override fun isRoundRobinEnabled(filters: AnimeFilterList): Boolean = roundRobinEnabled

        override suspend fun prepareReplay(): FeedReplayContext? = null

        override suspend fun resolveReplayItem(
            itemId: String,
            expectedContext: FeedReplayContext,
            filters: AnimeFilterList,
        ): FeedReplayItemResult {
            resolveRequests += itemId
            return resolveResult(itemId)
        }

        override val replayContextChanges: Flow<FeedReplayContext?> = replayContexts

        /** Emits the context the current [identity] would now prove, like a TDLib reauth. */
        fun emitReplayContextChange() {
            replayContexts.tryEmit(context())
        }

        override fun accountScopeId(): String? = identity

        override suspend fun prepareRoundRobin(): FeedRoundRobinReadiness {
            prepareRoundRobinCalls++
            readinessHandler?.let { return it() }
            return readinessOverride ?: FeedRoundRobinReadiness.Ready(context(), "tdlib-rr-v1")
        }

        override fun identifyRoundRobinItem(itemId: String): FeedRoundRobinIdentity? = null

        override suspend fun getRoundRobinCatalog(
            expectedContext: FeedReplayContext,
            forceRefresh: Boolean,
        ): FeedRoundRobinCatalogResult {
            catalogRequests += expectedContext to forceRefresh
            return FeedRoundRobinCatalogResult.Snapshot(
                FeedRoundRobinCatalog(
                    context = expectedContext,
                    revision = "rev-${catalogKeys.joinToString("-")}",
                    complete = true,
                    sources = catalogKeys.map { key ->
                        FeedRoundRobinSourceRef(
                            key = key,
                            title = "Chat $key",
                            author = null,
                            contentRevision = null,
                            excluded = key in excludedKeys,
                            previousKeys = emptyList(),
                        )
                    },
                    retryAtEpochMillis = null,
                ),
            )
        }

        override suspend fun scanRoundRobinSource(request: FeedRoundRobinScanRequest): FeedRoundRobinScanResult {
            scanRequests += request
            val count = scanCounts[request.sourceKey] ?: 0
            scanCounts[request.sourceKey] = count + 1
            val itemId = "${request.sourceKey}-$count"
            return FeedRoundRobinScanResult.Page(
                candidates = listOf(
                    FeedRoundRobinCandidate(
                        item = item(itemId),
                        identity = FeedRoundRobinIdentity(request.sourceKey, (1_000L - count).coerceAtLeast(1L)),
                        contentKey = null,
                    ),
                ),
                checkpoint = "cp-${request.sourceKey}-$count",
                historyExhausted = historyExhausted,
                nextHeadCheckAtEpochMillis = null,
            )
        }
    }

    private class LegacyFeedSource(override val id: Long) : AnimeFeedSource {
        override val name: String = "Legacy $id"
        override val lang: String = "all"
        var getFeedCalls = 0
        var searchFeedCalls = 0

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
            getFeedCalls++
            return FeedPage(
                listOf(
                    ShortVideoItem(
                        id = "legacy-$page",
                        posterUrl = "https://cdn/legacy.jpg",
                        videoUrl = "https://cdn/legacy.mp4",
                    ),
                ),
                hasNextPage = false,
            )
        }

        override suspend fun getSearchFeed(
            page: Int,
            cursor: String?,
            query: String,
            filters: AnimeFilterList,
        ): FeedPage {
            searchFeedCalls++
            return FeedPage(
                listOf(
                    ShortVideoItem(
                        id = "search-$query",
                        posterUrl = "https://cdn/search.jpg",
                        videoUrl = "https://cdn/search.mp4",
                    ),
                ),
                hasNextPage = false,
            )
        }
    }

    private class CreatorSource(override val id: Long) : AnimeFeedSource, AnimeCreatorFeedSource {
        override val name: String = "Creator $id"
        override val lang: String = "all"
        var creatorFeedCalls = 0
        var getFeedCalls = 0

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
            getFeedCalls++
            return FeedPage(emptyList(), hasNextPage = false)
        }

        override suspend fun getCreatorFeed(creator: String, page: Int, cursor: String?): FeedPage {
            creatorFeedCalls++
            return FeedPage(
                listOf(
                    ShortVideoItem(
                        id = "creator-$creator",
                        posterUrl = "https://cdn/creator.jpg",
                        videoUrl = "https://cdn/creator.mp4",
                    ),
                ),
                hasNextPage = false,
            )
        }
    }

    private class FakeAlbumRepository : ReelsAlbumRepository {
        override fun subscribeBySource(sourceId: Long): Flow<List<ReelsAlbum>> = emptyFlow()
        override suspend fun getBySource(sourceId: Long): List<ReelsAlbum> = emptyList()
        override suspend fun insert(album: ReelsAlbum) = Unit
        override suspend fun delete(sourceId: Long, albumId: String) = Unit
        override suspend fun deleteBySource(sourceId: Long) = Unit
        override fun subscribeWatched(sourceId: Long): Flow<Set<String>> = emptyFlow()
        override suspend fun markWatched(sourceId: Long, videoId: String) = Unit
        override suspend fun unmarkWatched(sourceId: Long, videoId: String) = Unit
        override suspend fun deleteWatchedBySource(sourceId: Long) = Unit
    }

    private class FakeFavoriteRepository : ReelsFavoriteRepository {
        override fun subscribeAll(): Flow<List<ReelsFavorite>> = emptyFlow()
        override suspend fun getAll(): List<ReelsFavorite> = emptyList()
        override suspend fun getBySource(sourceId: Long): List<ReelsFavorite> = emptyList()
        override suspend fun getIdsBySource(sourceId: Long): List<String> = emptyList()
        override suspend fun insert(favorite: ReelsFavorite) = Unit
        override suspend fun insertAll(favorites: List<ReelsFavorite>) = Unit
        override suspend fun delete(videoId: String, sourceId: Long) = Unit
        override suspend fun deleteBySource(sourceId: Long) = Unit
    }

    private class FakeFollowRepository : ReelsFollowRepository {
        override fun subscribeAll(): Flow<List<ReelsFollow>> = emptyFlow()
        override suspend fun getAll(): List<ReelsFollow> = emptyList()
        override suspend fun getBySource(sourceId: Long): List<ReelsFollow> = emptyList()
        override suspend fun getCreatorsBySource(sourceId: Long): List<String> = emptyList()
        override suspend fun insert(follow: ReelsFollow) = Unit
        override suspend fun delete(sourceId: Long, creator: String) = Unit
    }

    private class FakeWatchRepository : ReelsWatchRepository {
        val entries = mutableMapOf<String, ReelsWatchEntry>()
        override fun subscribeAll(): Flow<List<ReelsWatchEntry>> = emptyFlow()
        override suspend fun getByVideo(videoId: String, sourceId: Long): ReelsWatchEntry? =
            entries[videoId]
        override suspend fun upsert(entry: ReelsWatchEntry) {
            entries[entry.videoId] = entry
        }
        override suspend fun delete(videoId: String, sourceId: Long) = Unit
        override suspend fun deleteAll() {
            entries.clear()
        }
    }

    private class FakeOfflineStore : ReelsOfflineStore {
        override fun isStored(sourceId: Long, videoId: String): Boolean = false
        override fun localUrl(sourceId: Long, videoId: String): String? = null
        override fun storedQuality(sourceId: Long, videoId: String): ReelsStoredVideoQuality? = null
        override fun storedPairs(): Set<Pair<Long, String>> = emptySet()
        override fun usedBytes(): Long = 0L
        override suspend fun download(
            item: ShortVideoItem,
            sourceId: Long,
        ): ReelsOfflineStore.DownloadResult = ReelsOfflineStore.DownloadResult.SAVED
        override suspend fun downloadSelected(
            snapshot: ReelsQualityDownloadSnapshot,
        ): ReelsOfflineStore.DownloadResult = ReelsOfflineStore.DownloadResult.SAVED
        override suspend fun delete(sourceId: Long, videoId: String): Boolean = false
        override suspend fun deleteBySource(sourceId: Long): Int = 0
        override suspend fun clearAll(): Long = 0L
    }

    private class Harness(
        val clock: Clock,
        val source: TestRoundRobinSource,
        val consumption: TestConsumptionRepository,
        val hidden: TestHiddenRepository,
        val candidates: TestCandidateRepository,
        val sessions: TestSessionRepository,
        val roundRobin: TestRoundRobinRepository,
        val model: ReelsFeedScreenModel,
    )

    private fun newHarness(
        sourceId: Long = 101L,
        clock: Clock = Clock(),
        source: TestRoundRobinSource = TestRoundRobinSource(sourceId, clock),
        consumption: TestConsumptionRepository = TestConsumptionRepository(),
        hidden: TestHiddenRepository = TestHiddenRepository(),
        candidates: TestCandidateRepository = TestCandidateRepository(),
        sessions: TestSessionRepository = TestSessionRepository(),
        roundRobin: TestRoundRobinRepository? = null,
        incognito: () -> Boolean = { false },
        creator: String? = null,
        preferences: SourcePreferences = SourcePreferences(MapPreferenceStore()),
    ): Harness {
        val rr = roundRobin ?: TestRoundRobinRepository(
            InMemoryReelsRoundRobinRepository(
                locallyEligible = { candidate ->
                    candidate.itemId !in consumption.externallyConsumed &&
                        candidate.itemId !in hidden.hidden &&
                        (candidate.accountScopeId to candidate.itemId) !in consumption.watched &&
                        (candidate.accountScopeId to candidate.itemId) !in consumption.skipped
                },
            ),
        )
        val model = ReelsFeedScreenModel(
            initialSourceId = sourceId,
            creator = creator,
            sourceManager = sourceManagerOf(source, sourceId),
            sourcePreferences = preferences,
            ioDispatcher = testDispatcher,
            isIncognito = { incognito() },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeFavoriteRepository(),
            reelsFollowRepository = FakeFollowRepository(),
            reelsWatchRepository = FakeWatchRepository(),
            reelsHiddenRepository = hidden,
            reelsAlbumRepository = FakeAlbumRepository(),
            reelsConsumptionRepository = consumption,
            reelsCandidateRepository = candidates,
            reelsSessionRepository = sessions,
            reelsRoundRobinRepository = rr,
            nowMs = clock::invoke,
            wallClock = { Date(clock.now) },
            offlineStore = FakeOfflineStore(),
        )
        return Harness(clock, source, consumption, hidden, candidates, sessions, rr, model.also { models += it })
    }

    private fun sourceManagerOf(source: AnimeSource, sourceId: Long): AnimeSourceManager =
        object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(source))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = source.takeIf { sourceKey == sourceId }
            override fun getOrStub(sourceKey: Long): AnimeSource = source
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

    /** A ScreenModel for a source without the v28 capability (legacy constructor path). */
    private fun legacyModel(
        source: AnimeFeedSource,
        sourceId: Long,
        roundRobin: ReelsRoundRobinRepository? = null,
    ): ReelsFeedScreenModel = ReelsFeedScreenModel(
        initialSourceId = sourceId,
        sourceManager = sourceManagerOf(source, sourceId),
        sourcePreferences = SourcePreferences(MapPreferenceStore()),
        ioDispatcher = testDispatcher,
        isIncognito = { false },
        sourceIconProvider = { null },
        reelsFavoriteRepository = FakeFavoriteRepository(),
        reelsFollowRepository = FakeFollowRepository(),
        reelsWatchRepository = FakeWatchRepository(),
        reelsHiddenRepository = TestHiddenRepository(),
        reelsAlbumRepository = FakeAlbumRepository(),
        reelsConsumptionRepository = TestConsumptionRepository(),
        reelsCandidateRepository = TestCandidateRepository(),
        reelsSessionRepository = TestSessionRepository(),
        reelsRoundRobinRepository = roundRobin,
        nowMs = { 0L },
        wallClock = { Date(0) },
        offlineStore = FakeOfflineStore(),
    )

    private fun idle() {
        // Bounded: the coordinator keeps a periodic catalog wake armed while attached, so
        // advanceUntilIdle would chase it forever. One millisecond still fires tasks scheduled
        // exactly at the current virtual time.
        testDispatcher.scheduler.runCurrent()
        testDispatcher.scheduler.advanceTimeBy(1L)
        testDispatcher.scheduler.runCurrent()
    }

    @Test
    fun `round robin route serves catalog pages without any legacy feed call`() = roundRobinTest {
        val chats = (0 until 300).map { "c%03d".format(it) }
        val source = TestRoundRobinSource(101L, Clock()).also { it.catalogKeys = chats }
        val h = newHarness(source = source)
        idle()

        val owner = h.model.state.value.roundRobinOwner
        owner shouldNotBe null
        h.model.state.value.items.size shouldBe 12
        h.model.state.value.items.take(4).map { it.id } shouldBe listOf(
            "c000-0",
            "c001-0",
            "c002-0",
            "c003-0",
        )
        source.getFeedCalls shouldBe 0
        source.searchFeedCalls shouldBe 0
        source.scanRequests.isNotEmpty() shouldBe true

        // Activation advances the frontier and publishes the next suffix only; the host
        // prefix (already shown cards) keeps its positions and no legacy page is fetched.
        val shown = h.model.state.value.items
        val anchor = shown.first()
        h.clock.now = 100L
        h.model.onRoundRobinVisitStarted(owner!!, anchor)
        idle()

        h.model.state.value.items.first().id shouldBe anchor.id
        h.model.state.value.items.map { it.id }.distinct().size shouldBe h.model.state.value.items.size
        source.getFeedCalls shouldBe 0

        // A consumed future card is removed from the suffix and replaced from the remaining
        // catalog; the anchored prefix stays exactly where it was.
        h.consumption.externallyConsumed += "c005-0"
        h.model.loadFeed(reset = true)
        idle()

        val repaired = h.model.state.value.items.map { it.id }
        repaired shouldNotContain "c005-0"
        repaired.first() shouldBe anchor.id
        repaired.size shouldBe h.model.state.value.items.map { it.id }.distinct().size
        source.getFeedCalls shouldBe 0
    }

    @Test
    fun `refresh repairs a consumed future slot and keeps the active card first`() = roundRobinTest {
        val h = newHarness()
        idle()

        val first = h.model.state.value.items.first().id
        h.model.state.value.items.map { it.id } shouldContain "b-0"

        h.consumption.externallyConsumed += "b-0"
        h.model.loadFeed(reset = true)
        idle()

        h.model.state.value.items.map { it.id } shouldNotContain "b-0"
        h.model.state.value.items.first().id shouldBe first
        h.model.state.value.items.isNotEmpty() shouldBe true
        h.source.getFeedCalls shouldBe 0
    }

    @Test
    fun `hiding and unhiding a prepared card repairs only the suffix`() = roundRobinTest {
        val h = newHarness()
        idle()

        h.model.state.value.items.map { it.id } shouldContain "b-0"
        h.hidden.hidden += "b-0"
        h.roundRobin.signalEligibility()
        idle()

        h.model.state.value.items.map { it.id } shouldNotContain "b-0"
        h.model.state.value.items.isNotEmpty() shouldBe true

        h.hidden.hidden -= "b-0"
        h.roundRobin.signalEligibility()
        idle()
        // The source is rewound only for chat b, so its next candidate is admitted again.
        h.model.state.value.items.map { it.id }.any { it.startsWith("b-") } shouldBe true
    }

    @Test
    fun `reopen restores the anchored card and its pending seek from the session checkpoint`() = roundRobinTest {
        val clock = Clock()
        val source = TestRoundRobinSource(101L, clock).also { it.catalogKeys = listOf("a", "b", "c") }
        val consumption = TestConsumptionRepository()
        val hidden = TestHiddenRepository()
        val candidates = TestCandidateRepository()
        val sessions = TestSessionRepository()
        val preferences = SourcePreferences(MapPreferenceStore())
        val rr = TestRoundRobinRepository(
            InMemoryReelsRoundRobinRepository(
                locallyEligible = { candidate ->
                    candidate.itemId !in consumption.externallyConsumed && candidate.itemId !in hidden.hidden
                },
            ),
        )

        val first = newHarness(
            clock = clock,
            source = source,
            consumption = consumption,
            hidden = hidden,
            candidates = candidates,
            sessions = sessions,
            roundRobin = rr,
            preferences = preferences,
        )
        idle()
        val firstOwner = first.model.state.value.roundRobinOwner!!
        val anchored = first.model.state.value.items.first()
        clock.now = 1_000L
        first.model.onRoundRobinVisitStarted(firstOwner, anchored)
        first.model.onRoundRobinFirstFrame(firstOwner, anchored.id)
        clock.now = 2_500L
        first.model.onRoundRobinPlaybackSample(firstOwner, anchored.id, 1_500L, eligible = true)
        first.model.onRoundRobinVisitEnded(firstOwner, anchored, 1_500L, 0.5f, 30f)
        idle()
        sessions.checkpointWrites shouldBe 1L
        consumption.watched shouldContain ("acc:101:acct-a" to anchored.id)
        // The checkpoint pre-read claims a handle; it must be released before the actor opens
        // its own owner, or the stale claim outlives the restore.
        (sessions.invalidations > 0).shouldBe(true)

        // Same durable fakes, fresh screen model + fresh plugin source (process reopen).
        val reopenedSource = TestRoundRobinSource(101L, clock).also { it.catalogKeys = listOf("a", "b", "c") }
        val second = newHarness(
            clock = clock,
            source = reopenedSource,
            consumption = consumption,
            hidden = hidden,
            candidates = candidates,
            sessions = sessions,
            roundRobin = rr,
            preferences = preferences,
        )
        idle()

        val state = second.model.state.value
        state.roundRobinOwner shouldNotBe null
        state.roundRobinOwner shouldNotBe firstOwner
        state.items.first().id shouldBe anchored.id
        state.pendingSeekItemId shouldBe anchored.id
        state.pendingSeekPositionMs shouldBe 1_500L
        reopenedSource.resolveRequests.firstOrNull() shouldBe anchored.id
        reopenedSource.getFeedCalls shouldBe 0
    }

    @Test
    fun `a refresh keeps the current active card stable`() = roundRobinTest {
        val h = newHarness()
        idle()
        val owner = h.model.state.value.roundRobinOwner!!
        val first = h.model.state.value.items[0]
        val second = h.model.state.value.items[1]
        h.model.onPageChanged(1, ReelsTransitionCause.PROGRAMMATIC)
        h.model.onRoundRobinVisitStarted(owner, second)
        idle()

        val active = h.model.state.value.items[1].id
        h.model.loadFeed(reset = true)
        idle()

        h.model.state.value.items[0].id shouldBe first.id
        h.model.state.value.items[1].id shouldBe active
        h.model.state.value.activeIndex shouldBe 1
        h.source.getFeedCalls shouldBe 0
    }

    @Test
    fun `late callbacks from a replaced owner never move or write the new account`() = roundRobinTest {
        val clock = Clock()
        val source = TestRoundRobinSource(101L, clock).also { it.catalogKeys = listOf("a") }
        val h = newHarness(clock = clock, source = source)
        idle()
        val ownerA = h.model.state.value.roundRobinOwner!!
        val itemId = h.model.state.value.items.first().id
        val itemA = h.model.state.value.items.first()

        // Qualified watch accepted under account A, with the write launched asynchronously.
        h.model.onRoundRobinVisitStarted(ownerA, itemA)
        h.model.onRoundRobinFirstFrame(ownerA, itemId)
        clock.now = 1_500L
        h.model.onRoundRobinPlaybackSample(ownerA, itemId, 1_500L, eligible = true)
        h.model.onRoundRobinVisitEnded(ownerA, itemA, 1_500L, 0.5f, 30f)
        idle()
        h.consumption.watched shouldContain ("acc:101:acct-a" to itemId)

        // Account B serves the SAME item id.
        source.identity = "acct-b"
        source.scanCounts.clear()
        h.model.switchSource(101L)
        idle()
        val ownerB = h.model.state.value.roundRobinOwner!!
        ownerB shouldNotBe ownerA
        ownerB.accountScope shouldBe "acc:101:acct-b"
        val itemB = h.model.state.value.items.first()
        itemB.id shouldBe itemId

        // Late A callbacks: no effect on B's tracker/checkpoint/history.
        val writesBefore = h.consumption.writes
        h.model.onRoundRobinVisitStarted(ownerA, itemA)
        clock.now = 6_500L
        h.model.onRoundRobinPlaybackSample(ownerA, itemId, 6_500L, eligible = true)
        h.model.onRoundRobinVisitEnded(ownerA, itemA, 6_500L, 0.9f, 30f)
        idle()
        h.consumption.writes shouldBe writesBefore

        // B's own visit must not inherit A's accumulator: a quick leave is a skip.
        h.model.onRoundRobinVisitStarted(ownerB, itemB)
        h.model.onRoundRobinFirstFrame(ownerB, itemB.id)
        clock.now = 6_600L
        h.model.onRoundRobinVisitEnded(ownerB, itemB, 100L, 0.01f, 30f)
        idle()
        h.consumption.watched shouldContain ("acc:101:acct-a" to itemId)
        h.consumption.skipped shouldContain ("acc:101:acct-b" to itemId)
    }

    @Test
    fun `incognito events never become persistent after a normal attach`() = roundRobinTest {
        var incognito = true
        val clock = Clock()
        val h = newHarness(
            clock = clock,
            incognito = { incognito },
        )
        idle()

        val owner = h.model.state.value.roundRobinOwner!!
        owner.persistEligible shouldBe false
        h.model.state.value.items.isNotEmpty() shouldBe true
        h.consumption.ensureScopeCalls shouldBe 0
        h.consumption.mergeScopeCalls shouldBe 0
        h.sessions.openCalls shouldBe 0
        h.roundRobin.openCalls shouldBe 0

        val item = h.model.state.value.items.first()
        h.model.onRoundRobinVisitStarted(owner, item)
        h.model.onRoundRobinFirstFrame(owner, item.id)
        clock.now = 1_500L
        h.model.onRoundRobinPlaybackSample(owner, item.id, 1_500L, eligible = true)
        h.model.onRoundRobinVisitEnded(owner, item, 1_500L, 0.5f, 30f)
        idle()
        h.consumption.writes shouldBe 0
        h.consumption.watched.isEmpty() shouldBe true
        h.consumption.skipped.isEmpty() shouldBe true
        h.candidates.resumePositions.isEmpty() shouldBe true
        h.sessions.checkpointWrites shouldBe 0

        incognito = false
        h.model.switchSource(101L)
        idle()

        h.model.state.value.roundRobinOwner!!.persistEligible shouldBe true
        h.consumption.writes shouldBe 0
        h.consumption.watched.isEmpty() shouldBe true
        h.consumption.skipped.isEmpty() shouldBe true
        h.roundRobin.openCalls shouldBe 1
    }

    @Test
    fun `the reels incognito toggle restarts the round robin route ephemerally and back`() =
        roundRobinTest {
            val preferences = SourcePreferences(MapPreferenceStore())
            val h = newHarness(
                preferences = preferences,
                incognito = { preferences.reelsIncognitoMode().get() },
            )
            idle()

            val persistentOwner = h.model.state.value.roundRobinOwner!!
            persistentOwner.persistEligible shouldBe true
            val persistentOpens = h.roundRobin.openCalls
            val sessionOpens = h.sessions.openCalls

            // The More-menu switch, not switchSource: a mid-feed flip must re-attach so the
            // published owner and its frozen persistEligible match the new mode.
            h.model.toggleReelsIncognito()
            idle()

            val ephemeralOwner = h.model.state.value.roundRobinOwner!!
            ephemeralOwner.token shouldNotBe persistentOwner.token
            ephemeralOwner.persistEligible shouldBe false
            h.model.state.value.items.isNotEmpty() shouldBe true
            h.roundRobin.openCalls shouldBe persistentOpens
            h.sessions.openCalls shouldBe sessionOpens

            h.model.toggleReelsIncognito()
            idle()

            val restoredOwner = h.model.state.value.roundRobinOwner!!
            restoredOwner.token shouldNotBe ephemeralOwner.token
            restoredOwner.persistEligible shouldBe true
            h.roundRobin.openCalls shouldBe persistentOpens + 1
            h.sessions.openCalls shouldBe sessionOpens + 1
            h.source.getFeedCalls shouldBe 0
        }

    @Test
    fun `search and creator routes keep their legacy calls`() = roundRobinTest {
        val rrSource = TestRoundRobinSource(301L, Clock())
        val rr = newHarness(source = rrSource)
        idle()
        rrSource.getFeedCalls shouldBe 0

        rr.model.search("cats")
        idle()
        rrSource.searchFeedCalls shouldBe 1
        rrSource.getFeedCalls shouldBe 0
        rr.model.state.value.roundRobinOwner shouldBe null

        val legacy = LegacyFeedSource(302L)
        val legacyModel = ReelsFeedScreenModel(
            initialSourceId = 302L,
            sourceManager = sourceManagerOf(legacy, 302L),
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeFavoriteRepository(),
            reelsFollowRepository = FakeFollowRepository(),
            reelsWatchRepository = FakeWatchRepository(),
            reelsHiddenRepository = TestHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            reelsConsumptionRepository = TestConsumptionRepository(),
            reelsCandidateRepository = TestCandidateRepository(),
            reelsSessionRepository = TestSessionRepository(),
            nowMs = { 0L },
            wallClock = { Date(0) },
            offlineStore = FakeOfflineStore(),
        )
        idle()
        legacy.getFeedCalls shouldBe 1
        legacyModel.state.value.roundRobinOwner shouldBe null
        legacyModel.state.value.items.map { it.id } shouldBe listOf("legacy-1")

        val creatorSource = CreatorSource(303L)
        val creatorModel = ReelsFeedScreenModel(
            initialSourceId = 303L,
            creator = "alice",
            sourceManager = sourceManagerOf(creatorSource, 303L),
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeFavoriteRepository(),
            reelsFollowRepository = FakeFollowRepository(),
            reelsWatchRepository = FakeWatchRepository(),
            reelsHiddenRepository = TestHiddenRepository(),
            reelsAlbumRepository = FakeAlbumRepository(),
            reelsConsumptionRepository = TestConsumptionRepository(),
            reelsCandidateRepository = TestCandidateRepository(),
            reelsSessionRepository = TestSessionRepository(),
            nowMs = { 0L },
            wallClock = { Date(0) },
            offlineStore = FakeOfflineStore(),
        )
        idle()
        creatorSource.creatorFeedCalls shouldBe 1
        creatorSource.getFeedCalls shouldBe 0
        creatorModel.state.value.items.map { it.id } shouldBe listOf("creator-alice")
    }

    @Test
    fun `disabled mode zero and pending identity never start the round robin or legacy feed`() = roundRobinTest {
        val disabledSource = TestRoundRobinSource(401L, Clock()).also { it.roundRobinEnabled = false }
        val disabled = newHarness(sourceId = 401L, source = disabledSource)
        idle()
        disabledSource.getFeedCalls shouldBe 1
        disabled.model.state.value.roundRobinOwner shouldBe null

        val pendingSource = TestRoundRobinSource(402L, Clock()).also {
            it.readinessOverride = FeedRoundRobinReadiness.Pending(5_000L)
        }
        val pending = newHarness(sourceId = 402L, source = pendingSource)
        idle()
        pendingSource.getFeedCalls shouldBe 0
        pendingSource.searchFeedCalls shouldBe 0
        pendingSource.prepareRoundRobinCalls shouldBe 1
        pending.model.state.value.items.isEmpty() shouldBe true
        pending.model.state.value.roundRobinOwner shouldBe null
        // Pending is a waiting state, not a terminal error: the host keeps loading and the
        // automatic retry is still ahead of its due time.
        pending.model.state.value.error shouldBe null
        pending.model.state.value.errorRes shouldBe null
        pending.model.state.value.isLoading shouldBe true
        pending.model.state.value.canLoadMore shouldBe false

        val signedOutSource = TestRoundRobinSource(403L, Clock()).also {
            it.readinessOverride = FeedRoundRobinReadiness.SignedOut
        }
        val signedOut = newHarness(sourceId = 403L, source = signedOutSource)
        idle()
        signedOutSource.getFeedCalls shouldBe 1
        signedOut.model.state.value.roundRobinOwner shouldBe null
        signedOut.model.state.value.items.map { it.id } shouldBe listOf("legacy-1")
    }

    @Test
    fun `pending readiness waits for its hint and retries the feed automatically`() = roundRobinTest {
        val clock = Clock()
        val source = TestRoundRobinSource(404L, clock)
        var prepares = 0
        source.readinessHandler = {
            prepares++
            if (prepares == 1) {
                FeedRoundRobinReadiness.Pending(retryAtEpochMillis = clock.now + 1_200L)
            } else {
                FeedRoundRobinReadiness.Ready(FeedReplayContext("acct-a", "v27-acct-a"), "tdlib-rr-v1")
            }
        }

        val h = newHarness(sourceId = 404L, source = source)
        idle()

        source.getFeedCalls shouldBe 0
        source.prepareRoundRobinCalls shouldBe 1
        h.model.state.value.error shouldBe null
        h.model.state.value.errorRes shouldBe null
        h.model.state.value.isLoading shouldBe true
        h.model.state.value.items.isEmpty() shouldBe true
        h.model.state.value.roundRobinOwner shouldBe null

        // The Pending hint is honored: nothing retries before it is due.
        testDispatcher.scheduler.advanceTimeBy(1_198L)
        testDispatcher.scheduler.runCurrent()
        source.prepareRoundRobinCalls shouldBe 1

        // Due exactly at the hint: the retry re-runs the build and the ready source publishes.
        // (One readiness read for the rebuild, one more for the actor's attach handshake.)
        testDispatcher.scheduler.advanceTimeBy(1L)
        testDispatcher.scheduler.runCurrent()
        idle()

        source.prepareRoundRobinCalls shouldBe 3
        source.getFeedCalls shouldBe 0
        source.searchFeedCalls shouldBe 0
        h.model.state.value.error shouldBe null
        h.model.state.value.isLoading shouldBe false
        h.model.state.value.items.isNotEmpty() shouldBe true
        h.model.state.value.roundRobinOwner shouldNotBe null
    }

    @Test
    fun `round robin manual forward swipes record skips with the owner scope`() = roundRobinTest {
        val h = newHarness()
        idle()
        val first = h.model.state.value.items.first()

        h.model.onPageChanged(1, ReelsTransitionCause.USER_FORWARD)
        idle()
        h.consumption.skipped shouldContain ("acc:101:acct-a" to first.id)
        h.consumption.writes shouldBe 1

        // Only a committed manual forward is a skip: programmatic and backward moves are not.
        h.model.onPageChanged(2, ReelsTransitionCause.PROGRAMMATIC)
        idle()
        h.model.onPageChanged(3, ReelsTransitionCause.USER_BACKWARD)
        idle()
        h.consumption.writes shouldBe 1
    }

    @Test
    fun `three hundred chats advance one full user round across page cuts`() = roundRobinTest {
        val chats = (0 until 300).map { "c%03d".format(it) }
        val source = TestRoundRobinSource(101L, Clock()).also { it.catalogKeys = chats }
        val h = newHarness(source = source)
        idle()

        val owner = h.model.state.value.roundRobinOwner!!
        h.model.state.value.items.size shouldBe 12

        val activated = mutableListOf<String>()
        repeat(300) { index ->
            val items = h.model.state.value.items
            val item = requireNotNull(items.getOrNull(index)) {
                "round lost card $index: size=${items.size} ids=${items.map { it.id }}"
            }
            h.model.onRoundRobinVisitStarted(owner, item)
            idle()
            activated += item.id
            h.model.state.value.roundRobinOwner!!.token shouldBe owner.token
        }

        activated.size shouldBe 300
        activated.map { it.substringBeforeLast('-') }.distinct().size shouldBe 300
        activated.all { it.endsWith("-0") } shouldBe true
        h.model.state.value.items.take(300).map { it.id } shouldBe activated
        h.model.state.value.items.map { it.id }.distinct().size shouldBe h.model.state.value.items.size

        // The card after the completed round is the same catalog order on round two, and the
        // page cuts along the way never restarted or repeated the round.
        h.model.state.value.items.getOrNull(300)?.id shouldBe "c000-1"
        h.model.onRoundRobinVisitStarted(owner, h.model.state.value.items[300])
        idle()
        h.model.state.value.items[300].id shouldBe "c000-1"
        h.model.state.value.roundRobinOwner!!.token shouldBe owner.token
        source.getFeedCalls shouldBe 0
        source.searchFeedCalls shouldBe 0
        h.roundRobin.openCalls shouldBe 1
    }

    @Test
    fun `a consumed future card is replaced at a page cut without moving the active card`() = roundRobinTest {
        val chats = (0 until 300).map { "c%03d".format(it) }
        val source = TestRoundRobinSource(101L, Clock()).also { it.catalogKeys = chats }
        val h = newHarness(source = source)
        idle()
        val owner = h.model.state.value.roundRobinOwner!!

        repeat(20) { index ->
            h.model.onRoundRobinVisitStarted(owner, h.model.state.value.items[index])
            idle()
            h.model.onPageChanged(index, ReelsTransitionCause.PROGRAMMATIC)
            idle()
        }
        val activeIndex = h.model.state.value.activeIndex
        val active = h.model.state.value.items[activeIndex].id
        val future = h.model.state.value.items[activeIndex + 6].id
        val sizeBefore = h.model.state.value.items.size
        activeIndex shouldBe 19
        active shouldBe "c019-0"
        future shouldBe "c025-0"

        h.consumption.externallyConsumed += future
        h.model.loadFeed(reset = true)
        idle()

        val repaired = h.model.state.value.items
        repaired[h.model.state.value.activeIndex].id shouldBe active
        h.model.state.value.activeIndex shouldBe activeIndex
        repaired.map { it.id } shouldNotContain future
        repaired.size shouldBe sizeBefore
        repaired[activeIndex + 1].id shouldNotBe future
        h.model.state.value.roundRobinOwner!!.token shouldBe owner.token
        source.getFeedCalls shouldBe 0
    }

    @Test
    fun `a chat added while a next frame is prepared joins the next user round`() = roundRobinTest {
        val source = TestRoundRobinSource(101L, Clock()).also { it.catalogKeys = listOf("a", "b") }
        val h = newHarness(source = source)
        idle()
        val owner = h.model.state.value.roundRobinOwner!!

        h.model.onRoundRobinVisitStarted(owner, h.model.state.value.items.first())
        idle()
        h.model.state.value.items.map { it.id } shouldContain "a-1"
        h.model.state.value.items.map { it.id } shouldContain "b-1"

        source.catalogKeys = listOf("a", "b", "c")
        h.model.loadFeed(reset = true)
        idle()

        val ids = h.model.state.value.items.map { it.id }
        ids.first() shouldBe "a-0"
        val added = ids.indexOf("c-0")
        added shouldNotBe -1
        (ids.indexOf("b-0") < added) shouldBe true
        (ids.indexOf("a-1") < added) shouldBe true
        (ids.indexOf("b-1") < added) shouldBe true
        h.model.state.value.roundRobinOwner!!.token shouldBe owner.token

        // The current round still finishes before the new chat's first card is reached.
        h.model.onRoundRobinVisitStarted(owner, h.model.state.value.items[1])
        idle()
        h.model.state.value.items[1].id shouldBe "b-0"
        h.model.state.value.items[2].id shouldBe "a-1"
        source.getFeedCalls shouldBe 0
    }

    @Test
    fun `removing or excluding a prepared source keeps the current round and active card`() = roundRobinTest {
        val source = TestRoundRobinSource(101L, Clock()).also { it.catalogKeys = listOf("a", "b", "c") }
        val h = newHarness(source = source)
        idle()
        val owner = h.model.state.value.roundRobinOwner!!
        h.model.onRoundRobinVisitStarted(owner, h.model.state.value.items[0])
        idle()
        h.model.onRoundRobinVisitStarted(owner, h.model.state.value.items[1])
        idle()

        h.model.state.value.items.take(2).map { it.id } shouldBe listOf("a-0", "b-0")
        h.model.state.value.items.map { it.id } shouldContain "c-0"

        // Removed from the catalog entirely: only its not-yet-played prepared turns vanish.
        source.catalogKeys = listOf("b", "c")
        h.model.loadFeed(reset = true)
        idle()
        h.model.state.value.items.take(2).map { it.id } shouldBe listOf("a-0", "b-0")
        h.model.state.value.items.map { it.id } shouldContain "c-0"
        h.model.state.value.items.drop(2).none { it.id.startsWith("a-") } shouldBe true

        // Excluded instead of absent: identical guarantee for the prepared source.
        source.catalogKeys = listOf("a", "b", "c")
        source.excludedKeys = setOf("a")
        h.model.loadFeed(reset = true)
        idle()
        h.model.state.value.items.take(2).map { it.id } shouldBe listOf("a-0", "b-0")
        h.model.state.value.items.map { it.id } shouldContain "c-0"
        h.model.state.value.items.drop(2).none { it.id.startsWith("a-") } shouldBe true
        h.model.state.value.roundRobinOwner!!.token shouldBe owner.token
        source.getFeedCalls shouldBe 0
    }

    @Test
    fun `a replaced owner finishes its active visit once with the old scope`() = roundRobinTest {
        val clock = Clock()
        val source = TestRoundRobinSource(101L, clock).also { it.catalogKeys = listOf("a") }
        val h = newHarness(clock = clock, source = source)
        idle()
        val ownerA = h.model.state.value.roundRobinOwner!!
        val itemA = h.model.state.value.items.first()

        h.model.onRoundRobinVisitStarted(ownerA, itemA)
        h.model.onRoundRobinFirstFrame(ownerA, itemA.id)
        clock.now = 400L
        h.model.onRoundRobinPlaybackSample(ownerA, itemA.id, 400L, eligible = true)

        source.identity = "acct-b"
        source.scanCounts.clear()
        h.model.switchSource(101L)
        idle()

        val ownerB = h.model.state.value.roundRobinOwner!!
        ownerB shouldNotBe ownerA
        ownerB.accountScope shouldBe "acc:101:acct-b"
        val itemB = h.model.state.value.items.first()
        itemB.id shouldBe itemA.id
        // The replaced visit was closed exactly once under account A's immutable scope.
        h.consumption.skipped shouldContain ("acc:101:acct-a" to itemA.id)
        h.consumption.watched.none { it.second == itemA.id } shouldBe true
        h.consumption.writes shouldBe 1

        // Late events from A cannot move B or write B's history.
        clock.now = 2_000L
        h.model.onRoundRobinVisitStarted(ownerA, itemA)
        h.model.onRoundRobinFirstFrame(ownerA, itemA.id)
        h.model.onRoundRobinPlaybackSample(ownerA, itemA.id, 2_000L, eligible = true)
        h.model.onRoundRobinVisitEnded(ownerA, itemA, 2_000L, 0.9f, 30f)
        h.model.onRoundRobinVideoCompleted(ownerA, itemA.id)
        idle()
        h.consumption.writes shouldBe 1
        h.consumption.skipped shouldContain ("acc:101:acct-a" to itemA.id)
        h.model.state.value.roundRobinOwner shouldBe ownerB
        h.model.state.value.items.first().id shouldBe itemB.id

        // B's visit of the SAME item id starts from a clean accumulator.
        h.model.onRoundRobinVisitStarted(ownerB, itemB)
        h.model.onRoundRobinFirstFrame(ownerB, itemB.id)
        h.model.onRoundRobinPlaybackSample(ownerB, itemB.id, 400L, eligible = true)
        idle()
        h.model.onRoundRobinVisitEnded(ownerB, itemB, 400L, 0.01f, 30f)
        idle()
        // Both accounts keep their own row for the same item id: B's skip did not overwrite
        // (or replace) A's record.
        h.consumption.skipped shouldContain ("acc:101:acct-a" to itemA.id)
        h.consumption.skipped shouldContain ("acc:101:acct-b" to itemA.id)
        h.consumption.watched.none { it.second == itemA.id } shouldBe true
        h.consumption.writes shouldBe 2
    }

    @Test
    fun `an accepted async event writes only the scope it was accepted under`() = roundRobinTest {
        val clock = Clock()
        val source = TestRoundRobinSource(101L, clock).also { it.catalogKeys = listOf("a") }
        val h = newHarness(clock = clock, source = source)
        idle()
        val ownerA = h.model.state.value.roundRobinOwner!!
        val itemA = h.model.state.value.items.first()

        // Cross the threshold under A; the durable write is still queued when B replaces A.
        h.model.onRoundRobinVisitStarted(ownerA, itemA)
        h.model.onRoundRobinFirstFrame(ownerA, itemA.id)
        clock.now = 1_200L
        h.model.onRoundRobinPlaybackSample(ownerA, itemA.id, 1_200L, eligible = true)
        h.consumption.writes shouldBe 0

        source.identity = "acct-b"
        source.scanCounts.clear()
        h.model.switchSource(101L)
        idle()

        val ownerB = h.model.state.value.roundRobinOwner!!
        h.consumption.watched shouldContain ("acc:101:acct-a" to itemA.id)
        h.consumption.skipped.none { it.second == itemA.id } shouldBe true
        h.consumption.writes shouldBe 1

        clock.now = 2_000L
        h.model.onRoundRobinVisitStarted(ownerA, itemA)
        h.model.onRoundRobinPlaybackSample(ownerA, itemA.id, 2_000L, eligible = true)
        h.model.onRoundRobinVisitEnded(ownerA, itemA, 2_000L, 0.9f, 30f)
        idle()
        h.consumption.writes shouldBe 1

        // B watches the same item id; its write lands in B's scope, never overwriting A's.
        val itemB = h.model.state.value.items.first()
        h.model.onRoundRobinVisitStarted(ownerB, itemB)
        h.model.onRoundRobinFirstFrame(ownerB, itemB.id)
        clock.now = 3_200L
        h.model.onRoundRobinPlaybackSample(ownerB, itemB.id, 3_200L, eligible = true)
        idle()
        // Both per-(scope, item) rows are present: the accepted A event was not overwritten.
        h.consumption.watched shouldContain ("acc:101:acct-a" to itemA.id)
        h.consumption.watched shouldContain ("acc:101:acct-b" to itemB.id)
        h.consumption.writes shouldBe 2
    }

    @Test
    fun `a pinned account scope survives a transient identity loss across reset and reattach`() =
        roundRobinTest {
            val clock = Clock()
            val source = TestRoundRobinSource(101L, clock).also { it.catalogKeys = listOf("a") }
            val h = newHarness(clock = clock, source = source)
            idle()
            h.model.state.value.roundRobinOwner!!.accountScope shouldBe "acc:101:acct-a"

            // The source can no longer prove its identity exactly while the history reset
            // re-reads the checkpoint and reopens the actor.
            source.identity = null
            source.scanCounts.clear()
            h.model.resetForYouHistory()
            idle()

            // Reset, checkpoint open and the new session all stay on the first proven scope.
            h.consumption.resetScopes shouldBe listOf("acc:101:acct-a")
            h.sessions.openKeys.map { it.accountScopeId }.distinct() shouldBe listOf("acc:101:acct-a")
            h.model.state.value.roundRobinOwner!!.accountScope shouldBe "acc:101:acct-a"
            source.getFeedCalls shouldBe 0
        }

    @Test
    fun `an in-place proven account flip repins the scope a later filter apply and reset use`() =
        roundRobinTest {
            val clock = Clock()
            val source = TestRoundRobinSource(101L, clock).also { it.catalogKeys = listOf("a") }
            val h = newHarness(clock = clock, source = source)
            idle()
            h.model.state.value.roundRobinOwner!!.accountScope shouldBe "acc:101:acct-a"

            // No switchSource/login/logout: only the published owner can tell the host that
            // the coordinator proved another account in place.
            source.identity = "acct-b"
            source.scanCounts.clear()
            source.emitReplayContextChange()
            idle()
            h.model.state.value.roundRobinOwner!!.accountScope shouldBe "acc:101:acct-b"

            // A filter apply re-attaches from the host: it must not reuse the stale pin.
            val opensBeforeFilter = h.sessions.openKeys.size
            h.model.applyFilters()
            idle()
            h.model.state.value.roundRobinOwner!!.accountScope shouldBe "acc:101:acct-b"
            h.sessions.openKeys.drop(opensBeforeFilter).map { it.accountScopeId }.distinct() shouldBe
                listOf("acc:101:acct-b")

            // Same rule for a history reset after a second in-place flip.
            source.identity = "acct-c"
            source.scanCounts.clear()
            source.emitReplayContextChange()
            idle()
            h.model.state.value.roundRobinOwner!!.accountScope shouldBe "acc:101:acct-c"

            val opensBeforeReset = h.sessions.openKeys.size
            h.model.resetForYouHistory()
            idle()
            h.consumption.resetScopes.last() shouldBe "acc:101:acct-c"
            h.model.state.value.roundRobinOwner!!.accountScope shouldBe "acc:101:acct-c"
            h.sessions.openKeys.drop(opensBeforeReset).map { it.accountScopeId }.distinct() shouldBe
                listOf("acc:101:acct-c")
            source.getFeedCalls shouldBe 0
        }

    @Test
    fun `a page composed under owner A cannot call into owner B after an account switch`() =
        roundRobinTest {
            val clock = Clock()
            val source = TestRoundRobinSource(101L, clock).also { it.catalogKeys = listOf("a") }
            val h = newHarness(clock = clock, source = source)
            idle()
            val ownerA = h.model.state.value.roundRobinOwner!!
            val item = h.model.state.value.items.first()
            // The pager captures the owner once, next to the item, when the page is composed.
            val page = ReelsRoundRobinPageCallbacks(h.model, ownerA)

            source.identity = "acct-b"
            source.scanCounts.clear()
            h.model.switchSource(101L)
            idle()
            val ownerB = h.model.state.value.roundRobinOwner!!
            ownerB shouldNotBe ownerA
            val writesBefore = h.consumption.writes

            // The disposed page's player still delivers a full late lifecycle.
            clock.now = 5_000L
            page.onVisitStarted(item)
            page.onFirstFramePresented(item.id)
            page.onPlaybackSample(item.id, 5_000L, eligible = true)
            page.onVisitEnded(item, 5_000L, 0.9f, 30f)
            page.onVideoCompleted(item.id)
            idle()

            h.consumption.writes shouldBe writesBefore
            h.model.state.value.roundRobinOwner shouldBe ownerB
            h.consumption.watched.none { it.second == item.id } shouldBe true
            h.consumption.skipped.none { it.second == item.id } shouldBe true
        }

    @Test
    fun `legacy sources never touch the round robin repository`() = roundRobinTest {
        val roundRobinRepository = TestRoundRobinRepository(
            InMemoryReelsRoundRobinRepository(locallyEligible = { true }),
        )
        val nonCapable = LegacyFeedSource(501L)
        val provided = legacyModel(nonCapable, 501L, roundRobinRepository)
        idle()
        nonCapable.getFeedCalls shouldBe 1
        provided.state.value.items.map { it.id } shouldBe listOf("legacy-1")
        provided.state.value.roundRobinOwner shouldBe null
        roundRobinRepository.openCalls shouldBe 0

        // The default-null constructor keeps working without an RR binding.
        val defaultedSource = LegacyFeedSource(502L)
        val defaulted = legacyModel(defaultedSource, 502L)
        idle()
        defaultedSource.getFeedCalls shouldBe 1
        defaulted.state.value.items.map { it.id } shouldBe listOf("legacy-1")
        defaulted.state.value.roundRobinOwner shouldBe null

        // The same nullable constructor routes a capable source through RR only.
        val rrSource = TestRoundRobinSource(503L, Clock())
        val rr = newHarness(source = rrSource)
        idle()
        rr.model.state.value.roundRobinOwner shouldNotBe null
        rrSource.getFeedCalls shouldBe 0
        rr.roundRobin.openCalls shouldBe 1
    }

    @Test
    fun `candidate mapper preserves every render field and legacy nullability`() {
        val now = Date(5_000L)
        val item = ShortVideoItem(
            id = "v1",
            title = "Title v1",
            author = "Author v1",
            videoUrl = "https://cdn.example/v1.mp4",
            videoUrlHd = "https://cdn.example/v1-hd.mp4",
            posterUrl = "https://cdn.example/v1.jpg",
            posterUrlVertical = "https://cdn.example/v1-vertical.jpg",
            durationSec = 12.5f,
            hasAudio = true,
            tags = listOf("tag-a", "tag-b"),
            webUrl = "https://web.example/v1",
            viewsCount = 7L,
            likesCount = 3L,
            isAuthorVerified = true,
            createdAtEpochSec = 1_600_000_000L,
        )
        val candidate = with(ReelsCandidateMapper) { item.toCandidate("acc:101:acct-a", 101L, now) }

        candidate.accountScopeId shouldBe "acc:101:acct-a"
        candidate.sourceId shouldBe 101L
        candidate.itemId shouldBe item.id
        candidate.title shouldBe item.title
        candidate.author shouldBe item.author
        candidate.videoUrl shouldBe item.videoUrl
        candidate.videoUrlHd shouldBe item.videoUrlHd
        candidate.posterUrl shouldBe item.posterUrl
        candidate.posterUrlVertical shouldBe item.posterUrlVertical
        candidate.webUrl shouldBe item.webUrl
        candidate.durationSec shouldBe 12.5
        candidate.hasAudio shouldBe true
        candidate.createdAtEpochSec shouldBe item.createdAtEpochSec
        candidate.discoveredAt shouldBe now
        candidate.lastValidatedAt shouldBe now
        candidate.availability shouldBe ReelsCandidate.AVAILABILITY_AVAILABLE
        candidate.retryAfter shouldBe null
        candidate.resumePositionMs shouldBe null
        candidate.metadataSchemaVersion shouldBe ReelsForYou.METADATA_SCHEMA_VERSION
        candidate.viewsCount shouldBe 7L
        candidate.likesCount shouldBe 3L
        candidate.isAuthorVerified shouldBe true
        candidate.capturedContextKey shouldBe null

        with(ReelsCandidateMapper) { candidate.toShortVideoItem() } shouldBe item

        // Nullable/normalizing legacy behavior: a missing poster becomes "", broken tags empty.
        val sparse = ShortVideoItem(id = "v2", videoUrl = "", posterUrl = "")
        val sparseCandidate = with(ReelsCandidateMapper) {
            sparse.toCandidate("scope", 101L, now).copy(
                title = null,
                author = null,
                posterUrl = null,
                posterUrlVertical = null,
                webUrl = null,
                tagsJson = "not-json",
                durationSec = null,
                createdAtEpochSec = null,
            )
        }
        val restored = with(ReelsCandidateMapper) { sparseCandidate.toShortVideoItem() }
        restored.id shouldBe "v2"
        restored.title shouldBe null
        restored.author shouldBe null
        restored.posterUrl shouldBe ""
        restored.posterUrlVertical shouldBe null
        restored.webUrl shouldBe null
        restored.durationSec shouldBe null
        restored.tags shouldBe emptyList()
        restored.createdAtEpochSec shouldBe null
        restored.viewsCount shouldBe null
        restored.likesCount shouldBe null
        restored.isAuthorVerified shouldBe false
    }

    @Test
    fun `round robin storage clears transport hints and stamps the captured context`() = roundRobinTest {
        val h = newHarness()
        idle()

        val stored = requireNotNull(h.roundRobin.storedCandidates.firstOrNull { it.itemId == "a-0" }) {
            "no stored payload for a-0: ${h.roundRobin.storedCandidates.map { it.itemId }}"
        }
        stored.accountScopeId shouldBe "acc:101:acct-a"
        stored.sourceId shouldBe 101L
        stored.title shouldBe "Title a-0"
        stored.author shouldBe "Author a-0"
        stored.videoUrl shouldBe ""
        stored.videoUrlHd shouldBe null
        stored.posterUrl shouldBe ""
        stored.posterUrlVertical shouldBe null
        stored.capturedContextKey shouldBe ReelsSessionContext.computeContextKey(
            sourceId = 101L,
            route = "main",
            query = "",
            unseenOnly = true,
            filterFingerprint = ReelsSessionContext.serializeFilters(AnimeFilterList()),
            continuityKey = "tdlib-rr-v1:rr:1",
        )
    }
}
