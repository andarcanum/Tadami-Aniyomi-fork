package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import rx.Observable
import java.io.IOException

class DiscoveryMangaMetadataCompatibilityTest {
    private open class BaseSource : CatalogueSource {
        override val id = 7L
        override val name = "Fixture"
        override val lang = "ru"
        override val supportsLatest = false
        override fun getFilterList() = FilterList()
        override suspend fun getChapterList(manga: SManga): List<SChapter> =
            error("metadata-only lookup must not fetch chapters")
    }

    private class UpdateOnlySource : BaseSource() {
        var updates = 0
        var legacyCalls = 0
        var receivedUrl: String? = null
        override suspend fun getMangaDetails(manga: SManga): SManga {
            legacyCalls++
            throw UnsupportedOperationException("legacy details are not implemented")
        }

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            updates++
            fetchDetails shouldBe true
            fetchChapters shouldBe false
            chapters shouldBe emptyList()
            receivedUrl = manga.url
            // Like LibGroup.Manga.toSManga, this details object intentionally omits URL and title.
            return SMangaUpdate(SManga.create().apply { genre = "Фэнтези, Система" }, emptyList())
        }
    }

    private fun entry() = SManga.create().apply {
        url = "/opaque--42"
        title = "Original Title"
    }

    @Test
    fun `modern update-only source works without ever invoking legacy details`(): Unit = runTest {
        val source = UpdateOnlySource()
        val details = fetchDiscoveryMangaDetails(source, entry())
        details.genre shouldBe "Фэнтези, Система"
        details.url shouldBe "/opaque--42"
        details.title shouldBe "Original Title"
        source.receivedUrl shouldBe "/opaque--42"
        source.updates shouldBe 1
        source.legacyCalls shouldBe 0
    }

    @Test
    fun `the former direct call fails on the same modern fixture`(): Unit = runTest {
        shouldThrow<UnsupportedOperationException> { UpdateOnlySource().getMangaDetails(entry()) }
    }

    @Test
    fun `legacy suspend details remains supported through the host update bridge`(): Unit = runTest {
        var detailsCalls = 0
        val source = object : BaseSource() {
            override suspend fun getMangaDetails(manga: SManga): SManga {
                detailsCalls++
                return SManga.create().apply { genre = "Fantasy" }
            }
        }
        fetchDiscoveryMangaDetails(source, entry()).genre shouldBe "Fantasy"
        detailsCalls shouldBe 1
    }

    @Test
    @Suppress("OVERRIDE_DEPRECATION")
    fun `legacy Rx details remains supported through both default bridges`(): Unit = runTest {
        var detailsCalls = 0
        val source = object : BaseSource() {
            @Deprecated("Legacy fixture")
            override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
                detailsCalls++
                return Observable.just(SManga.create().apply { genre = "Fantasy" })
            }
        }
        fetchDiscoveryMangaDetails(source, entry()).genre shouldBe "Fantasy"
        detailsCalls shouldBe 1
    }

    @Test
    fun `source errors and cancellation propagate rather than fabricate matching metadata`(): Unit = runTest {
        val source = object : BaseSource() {
            override suspend fun getMangaUpdate(
                manga: SManga,
                chapters: List<SChapter>,
                fetchDetails: Boolean,
                fetchChapters: Boolean,
            ): SMangaUpdate = throw IOException("details failed")
        }
        shouldThrow<IOException> { fetchDiscoveryMangaDetails(source, entry()) }
        val cancelled = object : BaseSource() {
            override suspend fun getMangaUpdate(
                manga: SManga,
                chapters: List<SChapter>,
                fetchDetails: Boolean,
                fetchChapters: Boolean,
            ): SMangaUpdate = throw CancellationException("cancelled")
        }
        shouldThrow<CancellationException> { fetchDiscoveryMangaDetails(cancelled, entry()) }
    }
}
