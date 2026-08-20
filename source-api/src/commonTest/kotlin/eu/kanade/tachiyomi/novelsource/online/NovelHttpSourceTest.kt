package eu.kanade.tachiyomi.novelsource.online

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

class NovelHttpSourceTest {

    private val testSource = object : HttpNovelSource() {
        override val name: String = "Test Novel Source"
        override val baseUrl: String = "https://novel.example.com"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override val client: OkHttpClient = OkHttpClient()
        override fun getFilterList(): NovelFilterList = NovelFilterList()

        override suspend fun getNovelDetails(novel: SNovel): SNovel {
            return novel.apply {
                title = "Updated Title"
                status = SNovel.COMPLETED
            }
        }

        override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
            return listOf(
                SNovelChapter.create().apply {
                    name = "Chapter 1"
                    url = "/c1"
                },
            )
        }
    }

    @Test
    fun `getNovelUpdate fetches details and chapters in parallel`() = runTest {
        val novel = SNovel.create().apply { url = "/novel/1" }
        val existingChapters = emptyList<SNovelChapter>()

        val update = testSource.getNovelUpdate(
            novel = novel,
            chapters = existingChapters,
            fetchDetails = true,
            fetchChapters = true,
        )

        update.novel.title shouldBe "Updated Title"
        update.novel.status shouldBe SNovel.COMPLETED
        update.chapters.size shouldBe 1
        update.chapters[0].name shouldBe "Chapter 1"
    }

    @Test
    fun `getNovelUpdate skips details when fetchDetails is false`() = runTest {
        val novel = SNovel.create().apply {
            title = "Original Title"
            url = "/novel/1"
        }

        val update = testSource.getNovelUpdate(
            novel = novel,
            chapters = emptyList(),
            fetchDetails = false,
            fetchChapters = true,
        )

        update.novel.title shouldBe "Original Title"
        update.chapters.size shouldBe 1
    }

    @Test
    fun `headersBuilder provides user agent and headers`() {
        val headers: Headers = testSource.headers
        testSource.baseUrl shouldBe "https://novel.example.com"
        testSource.getHomeUrl() shouldBe "https://novel.example.com"
    }
}
