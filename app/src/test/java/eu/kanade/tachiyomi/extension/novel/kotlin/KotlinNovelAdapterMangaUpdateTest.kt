package eu.kanade.tachiyomi.extension.novel.kotlin

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * tachiyomix 1.6 sources (KeiSource-based) serve details and chapters exclusively through
 * getMangaUpdate, reject the legacy fetch hooks and reject concurrent getMangaUpdate calls
 * for the same manga. The adapter must route through getMangaUpdate and serialize the
 * parallel details/chapters refresh per manga.
 */
class KotlinNovelAdapterMangaUpdateTest {

    private class KeiStyleSource : HttpSource() {
        override val name = "Kei Style"
        override val lang = "en"
        override val baseUrl = "https://example.com"
        override val supportsLatest = true

        private val inFlight = ConcurrentHashMap<String, Int>()
        var updateCalls = 0
            private set

        @Suppress("SameParameterValue")
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            val current = inFlight.merge(manga.url, 1, Int::plus)!!
            try {
                check(current == 1) { "getMangaUpdate must not be called concurrently for same manga" }
                Thread.sleep(2)
                updateCalls++
                return SMangaUpdate(
                    manga = if (fetchDetails) manga.apply { author = "details-author" } else manga,
                    chapters = if (fetchChapters) {
                        listOf(
                            SChapter.create().apply {
                                url = "/c1"
                                name = "Chapter 1"
                            },
                        )
                    } else {
                        chapters
                    },
                )
            } finally {
                inFlight.merge(manga.url, -1, Int::plus)
            }
        }

        override fun getFilterList() = FilterList()
    }

    @Test
    fun `details and chapters route through getMangaUpdate serialized per manga`() = runBlocking<Unit> {
        val source = KeiStyleSource()
        val adapter = source.asKotlinNovelCatalogueSource("plugin-id") as NovelCatalogueSource
        val novel = SNovel.create().apply {
            url = "/novel/x"
            title = "X"
        }

        val details: List<SNovel>
        val chapters: List<List<SNovelChapter>>
        coroutineScope {
            val detailJobs = mutableListOf<Deferred<SNovel>>()
            val chapterJobs = mutableListOf<Deferred<List<SNovelChapter>>>()
            repeat(16) {
                detailJobs += async(Dispatchers.Default) { adapter.getNovelDetails(novel) }
                chapterJobs += async(Dispatchers.Default) { adapter.getChapterList(novel) }
            }
            details = detailJobs.awaitAll()
            chapters = chapterJobs.awaitAll()
        }

        details.forEach { it.author shouldBe "details-author" }
        chapters.forEach { chapterList ->
            chapterList shouldHaveSize 1
            chapterList.first().name shouldBe "Chapter 1"
        }
        source.updateCalls shouldBe 32
    }
}
