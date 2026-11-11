package eu.kanade.tachiyomi.util.manga

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import rx.Observable
import java.util.concurrent.atomic.AtomicInteger

class MangaSourceUpdateLockTest {

    @Test
    fun `concurrent getMangaUpdateLocked calls for same manga are serialized without error`() = runTest {
        val source = GuardedMangaSource(id = 100L)
        val manga = SManga.create().apply {
            url = "/test/entry-1"
            title = "Entry 1"
        }

        // Fire 5 concurrent calls for the exact same manga url
        val deferreds = (1..5).map {
            async {
                source.getMangaUpdateLocked(
                    manga = manga,
                    chapters = emptyList(),
                    fetchDetails = true,
                    fetchChapters = true,
                )
            }
        }

        val results = deferreds.awaitAll()
        results.size shouldBe 5
        source.inFlightViolations.get() shouldBe 0
        source.totalCalls.get() shouldBe 5
    }

    @Test
    fun `concurrent getMangaUpdateLocked calls for different manga do not block each other`() = runTest {
        val source = GuardedMangaSource(id = 200L)
        val mangaA = SManga.create().apply { url = "/test/entry-a" }
        val mangaB = SManga.create().apply { url = "/test/entry-b" }

        val deferredA = async {
            source.getMangaUpdateLocked(mangaA, fetchDetails = true, fetchChapters = false)
        }
        val deferredB = async {
            source.getMangaUpdateLocked(mangaB, fetchDetails = true, fetchChapters = false)
        }

        val results = listOf(deferredA.await(), deferredB.await())
        results.size shouldBe 2
        source.inFlightViolations.get() shouldBe 0
        source.totalCalls.get() shouldBe 2
    }

    private class GuardedMangaSource(override val id: Long) : MangaSource {
        override val name: String = "GuardedSource"
        override val lang: String = "en"

        val inFlightViolations = AtomicInteger(0)
        val totalCalls = AtomicInteger(0)
        private val inFlight = mutableSetOf<String>()

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            synchronized(inFlight) {
                if (!inFlight.add(manga.url)) {
                    inFlightViolations.incrementAndGet()
                    error("getMangaUpdate must not be called concurrently for same manga")
                }
            }
            try {
                delay(50)
                totalCalls.incrementAndGet()
                return SMangaUpdate(manga = manga, chapters = chapters)
            } finally {
                synchronized(inFlight) {
                    inFlight.remove(manga.url)
                }
            }
        }

        override suspend fun getMangaDetails(manga: SManga): SManga = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

        @Deprecated("Not used")
        override fun fetchMangaDetails(manga: SManga): Observable<SManga> = throw IllegalStateException()

        @Deprecated("Not used")
        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = throw IllegalStateException()

        @Deprecated("Not used")
        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = throw IllegalStateException()
    }
}
