package eu.kanade.tachiyomi.data.library.manga

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.manga.fetchMangaChaptersForUpdate
import eu.kanade.tachiyomi.util.manga.getMangaUpdateLocked
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class MangaBackgroundSourceUpdateTest {

    @Test
    fun `library chapter-only updates share the foreground source lock`() = runTest {
        val update = assertGuardedBackgroundBoundary(
            sourceId = 6001L,
            expectedRequest = UpdateRequest(fetchDetails = false, fetchChapters = true),
        ) { source, manga ->
            source.fetchMangaLibraryUpdate(manga, fetchDetails = false)
        }

        update.manga.url shouldBe "/test/background-entry"
        update.chapters.map { it.url } shouldBe listOf("/test/background-entry/chapter-1")
    }

    @Test
    fun `library metadata-and-chapter updates share the foreground source lock`() = runTest {
        val update = assertGuardedBackgroundBoundary(
            sourceId = 6002L,
            expectedRequest = UpdateRequest(fetchDetails = true, fetchChapters = true),
        ) { source, manga ->
            source.fetchMangaLibraryUpdate(manga, fetchDetails = true)
        }

        update.manga.url shouldBe "/test/background-entry"
        update.chapters.map { it.url } shouldBe listOf("/test/background-entry/chapter-1")
    }

    @Test
    fun `metadata-only updates share the foreground source lock`() = runTest {
        val manga = assertGuardedBackgroundBoundary(
            sourceId = 6003L,
            expectedRequest = UpdateRequest(fetchDetails = true, fetchChapters = false),
        ) { source, request ->
            source.fetchMangaMetadataUpdate(request)
        }

        manga.url shouldBe "/test/background-entry"
        manga.title shouldBe "Background entry"
    }

    @Test
    fun `shared deep-link and migration chapter boundary uses the foreground source lock`() = runTest {
        val chapters = assertGuardedBackgroundBoundary(
            sourceId = 6004L,
            expectedRequest = UpdateRequest(fetchDetails = false, fetchChapters = true),
        ) { source, request ->
            source.fetchMangaChaptersForUpdate(request)
        }

        chapters.map { it.url } shouldBe listOf("/test/background-entry/chapter-1")
    }

    private suspend fun <T> CoroutineScope.assertGuardedBackgroundBoundary(
        sourceId: Long,
        expectedRequest: UpdateRequest,
        backgroundUpdate: suspend (MangaSource, SManga) -> T,
    ): T {
        val foregroundEntered = CompletableDeferred<Unit>()
        val releaseForeground = CompletableDeferred<Unit>()
        val source = SingleFlightSource(sourceId, foregroundEntered, releaseForeground)
        val request = SManga.create().apply {
            url = "/test/background-entry"
            title = "Background entry"
            initialized = true
        }
        val foreground = async(start = CoroutineStart.UNDISPATCHED) {
            source.getMangaUpdateLocked(request, fetchDetails = true, fetchChapters = true)
        }
        try {
            foregroundEntered.await()
            val background = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { backgroundUpdate(source, request) }
            }

            releaseForeground.complete(Unit)
            foreground.await()
            val result = background.await()
            // Both callers must succeed: a raw outgoing boundary reaches the guarded source too early.
            result.isSuccess shouldBe true
            source.requests.toList() shouldBe listOf(
                UpdateRequest(fetchDetails = true, fetchChapters = true),
                expectedRequest,
            )
            return result.getOrThrow()
        } finally {
            releaseForeground.complete(Unit)
            foreground.await()
        }
    }

    private data class UpdateRequest(
        val fetchDetails: Boolean,
        val fetchChapters: Boolean,
        val url: String = "/test/background-entry",
        val title: String = "Background entry",
        val chapterUrls: List<String> = emptyList(),
    )

    private class SingleFlightSource(
        override val id: Long,
        private val foregroundEntered: CompletableDeferred<Unit>,
        private val releaseForeground: CompletableDeferred<Unit>,
    ) : MangaSource {
        override val name: String = "SingleFlightSource"
        override val lang: String = "en"

        val requests = CopyOnWriteArrayList<UpdateRequest>()
        private val inFlight = mutableSetOf<String>()
        private val acceptedCalls = AtomicInteger(0)
        private val responseChapter = SChapter.create().apply {
            url = "/test/background-entry/chapter-1"
            name = "Chapter 1"
            chapter_number = 1f
            date_upload = 100L
            scanlator = "Main"
        }

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            synchronized(inFlight) {
                check(inFlight.add(manga.url)) { "getMangaUpdate rejected overlapping requests for ${manga.url}" }
            }
            try {
                requests += UpdateRequest(
                    fetchDetails = fetchDetails,
                    fetchChapters = fetchChapters,
                    url = manga.url,
                    title = manga.title,
                    chapterUrls = chapters.map { it.url },
                )
                if (acceptedCalls.incrementAndGet() == 1) {
                    foregroundEntered.complete(Unit)
                    releaseForeground.await()
                }
                return SMangaUpdate(manga, if (fetchChapters) listOf(responseChapter) else chapters)
            } finally {
                synchronized(inFlight) { inFlight.remove(manga.url) }
            }
        }

        override suspend fun getMangaDetails(manga: SManga): SManga = error("Combined update API required")
        override suspend fun getChapterList(manga: SManga): List<SChapter> = error("Combined update API required")
        override suspend fun getPageList(chapter: SChapter): List<Page> = error("Not used")
    }
}
