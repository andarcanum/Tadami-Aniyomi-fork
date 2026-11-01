package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.reels.anime.repository.ReelsHiddenRepository
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.util.Date

/**
 * Model-level coverage of the user-initiated favorites cleanup (audit A3 remainder): the
 * cold-start guard (an empty source map must never classify every source as missing) and the
 * H7/H8 cascade into the offline store and the hidden-content table. The data-level cascade
 * itself is covered by the repository tests; this pins the SCREEN MODEL contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReelsFavoritesScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cleanup waits for the source subsystem before classifying anything as missing`() = runTest(testDispatcher) {
        val initialized = MutableStateFlow(false)
        val favorites = FakeFavoriteRepository()
        favorites.rows["fav-a" to 501L] = fav("fav-a", 501L)
        val store = FakeOfflineStoreCascade()
        store.stored[501L to "fav-a"] = "file:///offline/501_fav-a.mp4"
        val model = ReelsFavoritesScreenModel(
            repository = favorites,
            sourceManager = managerOf(initialized, feedSource(501L)),
            offlineStore = store,
            hiddenRepository = FakeHiddenRepositoryCascade(),
            albumRepository = FakeAlbumRepositoryCascade(),
        )

        // Cleanup is suspended on the cold-start guard: with the source map still empty it
        // must NOT wipe the list (every source would look "missing").
        val job = async { model.cleanupMissingSources() }
        testDispatcher.scheduler.advanceUntilIdle()
        job.isCompleted shouldBe false
        favorites.rows.keys shouldContainExactly setOf("fav-a" to 501L)
        store.stored.size shouldBe 1

        initialized.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        // 501 IS installed → nothing to remove.
        job.await() shouldBe 0
        favorites.rows.keys shouldContainExactly setOf("fav-a" to 501L)
        store.stored.size shouldBe 1
    }

    @Test
    fun `cleanup removes uninstalled-source rows and cascades to offline copies and hidden entries`() =
        runTest(testDispatcher) {
            val favorites = FakeFavoriteRepository()
            favorites.rows["fav-a" to 501L] = fav("fav-a", 501L)
            favorites.rows["fav-b" to 502L] = fav("fav-b", 502L)
            favorites.rows["fav-c" to 502L] = fav("fav-c", 502L)
            val store = FakeOfflineStoreCascade()
            store.stored[501L to "fav-a"] = "file:///offline/501_fav-a.mp4"
            store.stored[502L to "fav-b"] = "file:///offline/502_fav-b.mp4"
            val hidden = FakeHiddenRepositoryCascade()
            hidden.insert(ReelsHiddenEntry(501L, "video", "keep-me", Date(0), "Kept"))
            hidden.insert(ReelsHiddenEntry(502L, "video", "gone-too", Date(0), "Gone"))
            val albums = FakeAlbumRepositoryCascade()
            albums.insert(album("alb-keep", 501L))
            albums.insert(album("alb-gone", 502L))
            albums.markWatched(502L, "v-gone")
            val model = ReelsFavoritesScreenModel(
                repository = favorites,
                // 502 is not installed anymore: the manager has no entry for it.
                sourceManager = managerOf(MutableStateFlow(true), feedSource(501L)),
                offlineStore = store,
                hiddenRepository = hidden,
                albumRepository = albums,
            )

            val removed = model.cleanupMissingSources()

            removed shouldBe 2
            favorites.rows.keys shouldContainExactly setOf("fav-a" to 501L)
            favorites.deletedSources shouldContainExactly listOf(502L)
            // H8 cascade: the offline copy of the removed source is gone, the installed one stays.
            store.stored.keys shouldContainExactly setOf(501L to "fav-a")
            store.deletedSources shouldContainExactly listOf(502L)
            // H7 cascade: hidden entries of the removed source are gone.
            hidden.entries.keys shouldContainExactly setOf(Triple(501L, "video", "keep-me"))
            hidden.deletedSources shouldContainExactly listOf(502L)
            // Albums cascade: the removed source loses its collection and watched marks.
            albums.albums.keys shouldContainExactly setOf("alb-keep" to 501L)
            albums.deletedSources shouldContainExactly listOf(502L)
            albums.watchedDeletedSources shouldContainExactly listOf(502L)
        }

    @Test
    fun `cleanup with every source installed removes nothing`() = runTest(testDispatcher) {
        val favorites = FakeFavoriteRepository()
        favorites.rows["fav-a" to 501L] = fav("fav-a", 501L)
        val store = FakeOfflineStoreCascade()
        store.stored[501L to "fav-a"] = "file:///offline/501_fav-a.mp4"
        val hidden = FakeHiddenRepositoryCascade()
        val albums = FakeAlbumRepositoryCascade()
        val model = ReelsFavoritesScreenModel(
            repository = favorites,
            sourceManager = managerOf(MutableStateFlow(true), feedSource(501L)),
            offlineStore = store,
            hiddenRepository = hidden,
            albumRepository = albums,
        )

        model.cleanupMissingSources() shouldBe 0
        favorites.rows.size shouldBe 1
        favorites.deletedSources shouldBe emptyList()
        store.deletedSources shouldBe emptyList()
        hidden.deletedSources shouldBe emptyList()
        albums.deletedSources shouldBe emptyList()
    }

    // --- Fixture helpers ---

    private fun fav(videoId: String, sourceId: Long) = ReelsFavorite(
        videoId = videoId,
        sourceId = sourceId,
        title = null,
        author = null,
        videoUrl = "https://example.com/$videoId.mp4",
        videoUrlHd = null,
        posterUrl = "",
        posterUrlVertical = null,
        webUrl = null,
        durationSec = null,
        hasAudio = true,
        addedAt = Date(0),
    )

    private fun feedSource(id: Long): AnimeSource = object : AnimeFeedSource {
        override val id: Long = id
        override val name: String = "Feed $id"
        override val lang: String = "all"

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)
    }

    private fun managerOf(
        initialized: MutableStateFlow<Boolean>,
        vararg sources: AnimeSource,
    ): AnimeSourceManager = object : AnimeSourceManager {
        override val isInitialized: StateFlow<Boolean> = initialized.asStateFlow()
        override val sources: Flow<List<AnimeSource>> = MutableStateFlow(sources.toList())
        override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
        override fun get(sourceKey: Long): AnimeSource? = sources.firstOrNull { it.id == sourceKey }
        override fun getOrStub(sourceKey: Long): AnimeSource = get(sourceKey) ?: sources.first()
        override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
        override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
        override fun getStubSources(): List<StubAnimeSource> = emptyList()
    }

    private class FakeFavoriteRepository : ReelsFavoriteRepository {
        val rows = mutableMapOf<Pair<String, Long>, ReelsFavorite>()
        private val live = MutableStateFlow(rows.values.toList())
        val deletedSources = mutableListOf<Long>()

        override fun subscribeAll(): Flow<List<ReelsFavorite>> = live

        override suspend fun getAll(): List<ReelsFavorite> = rows.values.toList()

        override suspend fun getBySource(sourceId: Long): List<ReelsFavorite> =
            rows.values.filter { it.sourceId == sourceId }

        override suspend fun getIdsBySource(sourceId: Long): List<String> =
            rows.values.filter { it.sourceId == sourceId }.map { it.videoId }

        override suspend fun insert(favorite: ReelsFavorite) {
            rows[favorite.videoId to favorite.sourceId] = favorite
            live.value = rows.values.toList()
        }

        override suspend fun insertAll(favorites: List<ReelsFavorite>) = favorites.forEach { insert(it) }

        override suspend fun delete(videoId: String, sourceId: Long) {
            rows.remove(videoId to sourceId)
            live.value = rows.values.toList()
        }

        override suspend fun deleteBySource(sourceId: Long) {
            deletedSources += sourceId
            rows.keys.filter { it.second == sourceId }.forEach { rows.remove(it) }
            live.value = rows.values.toList()
        }
    }

    private class FakeOfflineStoreCascade : ReelsOfflineStore {
        val stored = mutableMapOf<Pair<Long, String>, String>()
        val deletedSources = mutableListOf<Long>()

        override fun isStored(sourceId: Long, videoId: String): Boolean = (sourceId to videoId) in stored

        override fun localUrl(sourceId: Long, videoId: String): String? = stored[sourceId to videoId]

        override fun storedQuality(sourceId: Long, videoId: String): ReelsStoredVideoQuality? = null

        override fun storedPairs(): Set<Pair<Long, String>> = stored.keys

        override fun usedBytes(): Long = stored.size * 1024L

        override suspend fun download(item: ShortVideoItem, sourceId: Long): ReelsOfflineStore.DownloadResult {
            stored[sourceId to item.id] = "file:///offline/${sourceId}_${item.id}.mp4"
            return ReelsOfflineStore.DownloadResult.SAVED
        }

        override suspend fun downloadSelected(
            snapshot: ReelsQualityDownloadSnapshot,
        ): ReelsOfflineStore.DownloadResult = download(snapshot.item, snapshot.owner.sourceId)

        override suspend fun delete(sourceId: Long, videoId: String): Boolean =
            stored.remove(sourceId to videoId) != null

        override suspend fun deleteBySource(sourceId: Long): Int {
            deletedSources += sourceId
            return stored.keys.filter { it.first == sourceId }.count { stored.remove(it) != null }
        }

        override suspend fun clearAll(): Long {
            val freed = usedBytes()
            stored.clear()
            return freed
        }
    }

    private class FakeHiddenRepositoryCascade : ReelsHiddenRepository {
        val entries = mutableMapOf<Triple<Long, String, String>, ReelsHiddenEntry>()
        val deletedSources = mutableListOf<Long>()

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
            deletedSources += sourceId
            entries.keys.filter { it.first == sourceId }.forEach { entries.remove(it) }
        }

        override suspend fun deleteAll() = entries.clear()
    }

    private fun album(albumId: String, sourceId: Long) = ReelsAlbum(
        sourceId = sourceId,
        albumId = albumId,
        name = "Album $albumId",
        coverUrl = null,
        addedAt = Date(0),
    )

    private class FakeAlbumRepositoryCascade : ReelsAlbumRepository {
        val albums = mutableMapOf<Pair<String, Long>, ReelsAlbum>()
        val watched = mutableMapOf<Long, MutableSet<String>>()
        val deletedSources = mutableListOf<Long>()
        val watchedDeletedSources = mutableListOf<Long>()

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
            deletedSources += sourceId
            albums.keys.filter { it.second == sourceId }.forEach { albums.remove(it) }
            // Mirrors the impl: one repository call clears the source's watched marks too.
            watchedDeletedSources += sourceId
            watched.remove(sourceId)
        }

        override fun subscribeWatched(sourceId: Long): Flow<Set<String>> =
            MutableStateFlow(watched[sourceId].orEmpty())

        override suspend fun markWatched(sourceId: Long, videoId: String) {
            watched.getOrPut(sourceId) { mutableSetOf() } += videoId
        }

        override suspend fun unmarkWatched(sourceId: Long, videoId: String) {
            watched[sourceId]?.remove(videoId)
        }

        override suspend fun deleteWatchedBySource(sourceId: Long) {
            watchedDeletedSources += sourceId
            watched.remove(sourceId)
        }
    }
}
