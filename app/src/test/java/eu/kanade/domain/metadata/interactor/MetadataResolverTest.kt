package eu.kanade.domain.metadata.interactor

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.metadata.cache.ExternalMetadataCache
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource

class MetadataResolverTest {

    @Test
    fun `returns cached metadata without querying provider`() = runTest {
        val cache = FakeExternalMetadataCache()
        val cached = sampleMetadata(searchQuery = "My Anime")
        cache.cached = cached

        val adapter = FakeMetadataAdapter(
            contentType = MetadataContentType.ANIME,
            source = MetadataSource.ANILIST,
        )
        val resolver = MetadataResolver(cache, adapter)

        val result = resolver.await(sampleTarget())

        result shouldBe cached
        adapter.searchCalls shouldBe 0
        adapter.fetchByIdCalls shouldBe 0
    }

    @Test
    fun `prefers tracked remote id before search`() = runTest {
        val cache = FakeExternalMetadataCache()
        val adapter = FakeMetadataAdapter(
            contentType = MetadataContentType.ANIME,
            source = MetadataSource.SHIKIMORI,
            tracks = listOf(TestTrack(remoteId = 42L)),
            fetchByIdResult = sampleRemote(remoteId = 42L, name = "Tracked title"),
        )
        val resolver = MetadataResolver(cache, adapter)

        val result = resolver.await(sampleTarget())

        result?.remoteId shouldBe 42L
        result?.isManualMatch shouldBe true
        result?.searchQuery shouldBe "tracking:42"
        adapter.fetchByIdCalls shouldBe 1
        adapter.searchCalls shouldBe 0
        cache.upserted.size shouldBe 1
    }

    @Test
    fun `falls back to normalized title search`() = runTest {
        val cache = FakeExternalMetadataCache()
        val adapter = FakeMetadataAdapter(
            contentType = MetadataContentType.MANGA,
            source = MetadataSource.ANILIST,
            searchResults = mapOf(
                "My Manga" to listOf(sampleRemote(name = "My Manga")),
            ),
        )
        val resolver = MetadataResolver(cache, adapter)

        val result = resolver.await(
            MetadataTarget(
                mediaId = 3L,
                title = "My Manga Season 2",
                description = null,
            ),
        )

        result?.searchQuery shouldBe "My Manga"
        result?.remoteId shouldBe 777L
        result?.isManualMatch shouldBe false
        adapter.searchCalls shouldBe 1
        cache.upserted.size shouldBe 1
    }

    @Test
    fun `prefers exact alias match over first search result`() = runTest {
        val cache = FakeExternalMetadataCache()
        val adapter = FakeMetadataAdapter(
            contentType = MetadataContentType.MANGA,
            source = MetadataSource.SHIKIMORI,
            searchResults = mapOf(
                "Колорист" to listOf(
                    sampleRemote(
                        remoteId = 111L,
                        name = "Wrong Result",
                    ),
                    sampleRemote(
                        remoteId = 222L,
                        name = "Colorist",
                        alternativeTitles = listOf("Колорист"),
                    ),
                ),
            ),
        )
        val resolver = MetadataResolver(cache, adapter)

        val result = resolver.await(
            MetadataTarget(
                mediaId = 4L,
                title = "Колорист",
                description = null,
            ),
        )

        result?.remoteId shouldBe 222L
        result?.searchQuery shouldBe "Колорист"
        adapter.searchCalls shouldBe 1
        cache.upserted.size shouldBe 1
    }

    @Test
    fun `prefers primary title exact match over alias exact match`() = runTest {
        val cache = FakeExternalMetadataCache()
        val adapter = FakeMetadataAdapter(
            contentType = MetadataContentType.MANGA,
            source = MetadataSource.SHIKIMORI,
            searchResults = mapOf(
                "Colorist" to listOf(
                    sampleRemote(
                        remoteId = 111L,
                        name = "Colorist Deluxe",
                        alternativeTitles = listOf("Colorist"),
                    ),
                    sampleRemote(
                        remoteId = 222L,
                        name = "Colorist",
                    ),
                ),
            ),
        )
        val resolver = MetadataResolver(cache, adapter)

        val result = resolver.await(
            MetadataTarget(
                mediaId = 8L,
                title = "Colorist",
                description = null,
            ),
        )

        result?.remoteId shouldBe 222L
        result?.searchQuery shouldBe "Colorist"
        adapter.searchCalls shouldBe 1
        cache.upserted.size shouldBe 1
    }

    @Test
    fun `accepts a single low confidence result instead of dropping it`() = runTest {
        val cache = FakeExternalMetadataCache()
        val adapter = FakeMetadataAdapter(
            contentType = MetadataContentType.MANGA,
            source = MetadataSource.SHIKIMORI,
            searchResults = mapOf(
                "Boku no Hero Academia" to listOf(
                    sampleRemote(
                        remoteId = 333L,
                        name = "My Hero Academia",
                    ),
                ),
            ),
        )
        val resolver = MetadataResolver(cache, adapter)

        val result = resolver.await(
            MetadataTarget(
                mediaId = 9L,
                title = "Boku no Hero Academia",
                description = null,
            ),
        )

        result?.remoteId shouldBe 333L
        result?.searchQuery shouldBe "Boku no Hero Academia"
        adapter.searchCalls shouldBe 1
        cache.upserted.size shouldBe 1
    }

    @Test
    fun `rejects ambiguous equal-scoring results`() = runTest {
        val cache = FakeExternalMetadataCache()
        val adapter = FakeMetadataAdapter(
            contentType = MetadataContentType.MANGA,
            source = MetadataSource.SHIKIMORI,
            searchResults = mapOf(
                "Shared Title" to listOf(
                    sampleRemote(remoteId = 301L, name = "Shared Title"),
                    sampleRemote(remoteId = 302L, name = "Shared Title"),
                ),
            ),
        )
        val resolver = MetadataResolver(cache, adapter)

        val result = resolver.await(
            MetadataTarget(
                mediaId = 5L,
                title = "Shared Title",
                description = null,
            ),
        )

        result.shouldBeNull()
        cache.upserted.size shouldBe 1
        cache.upserted.single().remoteId shouldBe null
    }

    @Test
    fun `bypasses stale cache when cached query no longer matches current candidates`() = runTest {
        val cache = FakeExternalMetadataCache()
        cache.cached = sampleMetadata(searchQuery = "Old Query")
        val adapter = FakeMetadataAdapter(
            contentType = MetadataContentType.MANGA,
            source = MetadataSource.ANILIST,
            searchResults = mapOf(
                "My Anime" to listOf(sampleRemote(name = "My Anime")),
            ),
        )
        val resolver = MetadataResolver(cache, adapter)

        val result = resolver.await(
            MetadataTarget(
                mediaId = 6L,
                title = "My Anime",
                description = null,
            ),
        )

        result?.remoteId shouldBe 777L
        adapter.searchCalls shouldBe 1
        cache.upserted.size shouldBe 1
    }

    @Test
    fun `stores not found metadata when provider returns nothing`() = runTest {
        val cache = FakeExternalMetadataCache()
        val adapter = FakeMetadataAdapter(
            contentType = MetadataContentType.MANGA,
            source = MetadataSource.SHIKIMORI,
        )
        val resolver = MetadataResolver(cache, adapter)

        val result = resolver.await(sampleTarget(title = "Unknown Title"))

        result.shouldBeNull()
        cache.upserted.size shouldBe 1
        cache.upserted.single().mediaId shouldBe 1L
        cache.upserted.single().source shouldBe MetadataSource.SHIKIMORI
        cache.upserted.single().contentType shouldBe MetadataContentType.MANGA
    }

    private fun sampleTarget(
        title: String = "My Anime Season 2",
    ) = MetadataTarget(
        mediaId = 1L,
        title = title,
        description = "Original: My Manga",
    )

    private fun sampleMetadata(
        searchQuery: String,
        updatedAt: Long = System.currentTimeMillis(),
    ) = ExternalMetadata(
        contentType = MetadataContentType.ANIME,
        source = MetadataSource.ANILIST,
        mediaId = 1L,
        remoteId = 777L,
        score = 8.5,
        format = "TV",
        status = "RELEASING",
        coverUrl = "https://example.org/cover.jpg",
        coverUrlFallback = "https://example.org/cover-medium.jpg",
        searchQuery = searchQuery,
        updatedAt = updatedAt,
        isManualMatch = false,
    )

    private fun sampleRemote(
        remoteId: Long = 777L,
        name: String,
        alternativeTitles: List<String> = emptyList(),
    ) = TestRemote(
        remoteId = remoteId,
        title = name,
        alternativeTitles = alternativeTitles,
        score = 8.5,
        format = "TV",
        status = "RELEASING",
        coverUrl = "https://example.org/cover.jpg",
    )

    private data class TestTrack(val remoteId: Long)

    private data class TestRemote(
        val remoteId: Long,
        val title: String,
        val alternativeTitles: List<String>,
        val score: Double?,
        val format: String?,
        val status: String?,
        val coverUrl: String?,
    )

    private class FakeExternalMetadataCache : ExternalMetadataCache {
        var cached: ExternalMetadata? = null
        val upserted = mutableListOf<ExternalMetadata>()

        override suspend fun get(
            contentType: MetadataContentType,
            mediaId: Long,
            source: MetadataSource,
        ): ExternalMetadata? {
            return cached?.takeIf {
                it.contentType == contentType &&
                    it.mediaId == mediaId &&
                    it.source == source
            }
        }

        override suspend fun upsert(metadata: ExternalMetadata) {
            upserted += metadata
            cached = metadata
        }

        override suspend fun delete(
            contentType: MetadataContentType,
            mediaId: Long,
            source: MetadataSource,
        ) = Unit

        override suspend fun clearAll() = Unit

        override suspend fun deleteStaleEntries() = Unit
    }

    private class FakeMetadataAdapter(
        override val contentType: MetadataContentType,
        override val source: MetadataSource,
        private val tracks: List<TestTrack> = emptyList(),
        private val fetchByIdResult: TestRemote? = null,
        private val searchResults: Map<String, List<TestRemote>> = emptyMap(),
    ) : MetadataAdapter<TestTrack, TestRemote> {
        var fetchByIdCalls = 0
        var searchCalls = 0

        override val trackerId: Long = 99L

        override suspend fun getTracks(mediaId: Long): List<TestTrack> = tracks

        override fun trackTrackerId(track: TestTrack): Long = trackerId

        override fun trackRemoteId(track: TestTrack): Long = track.remoteId

        override fun remoteId(remote: TestRemote): Long = remote.remoteId

        override fun candidateTitles(remote: TestRemote): List<String> {
            return buildList {
                add(remote.title)
                addAll(remote.alternativeTitles)
            }
        }

        override suspend fun fetchById(remoteId: Long): TestRemote? {
            fetchByIdCalls += 1
            return fetchByIdResult
        }

        override suspend fun search(query: String): List<TestRemote> {
            searchCalls += 1
            return searchResults[query].orEmpty()
        }

        override suspend fun map(
            target: MetadataTarget,
            remote: TestRemote,
            searchQuery: String,
            isManualMatch: Boolean,
        ): ExternalMetadata {
            return ExternalMetadata(
                contentType = contentType,
                source = source,
                mediaId = target.mediaId,
                remoteId = remote.remoteId,
                score = remote.score,
                format = remote.format,
                status = remote.status,
                coverUrl = remote.coverUrl,
                coverUrlFallback = remote.coverUrl,
                searchQuery = searchQuery,
                updatedAt = 100L,
                isManualMatch = isManualMatch,
            )
        }

        override fun isNotAuthenticated(error: Throwable): Boolean = false
    }
}
