package eu.kanade.domain.entries.novel.interactor

import eu.kanade.domain.entries.rating.EntryRatingCache
import eu.kanade.tachiyomi.network.HttpException
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import eu.kanade.tachiyomi.novelsource.NovelSource

class NovelRatingFetcherTest {
    companion object {
        private lateinit var ratingCache: EntryRatingCache

        @JvmStatic
        @BeforeAll
        fun setupInjektBindings() {
            ratingCache = mockk(relaxed = true)
            Injekt.addSingleton(fullType<EntryRatingCache>(), ratingCache)
        }
    }

    @Test
    fun `await falls back to cached value when rating request fails`() {
        clearMocks(ratingCache)
        val source = mockk<NovelSource>()
        val novel = Novel.create().copy(
            id = 1L,
            url = "https://example.org/novel/1",
            title = "Example",
            initialized = true,
        )

        every { source.name } returns "Example Source"
        coEvery {
            ratingCache.resolve(
                contentType = any(),
                sourceName = any(),
                url = any(),
                forceRefresh = any(),
                loader = any(),
            )
        } throws HttpException(404)
        every {
            ratingCache.peek(
                contentType = any(),
                sourceName = any(),
                url = any(),
            )
        } returns 4.2f

        runBlocking {
            NovelRatingFetcher().await(source, novel) shouldBe 4.2f
        }
    }

    @Test
    fun `await returns null when rating request fails and cache is empty`() {
        clearMocks(ratingCache)
        val source = mockk<NovelSource>()
        val novel = Novel.create().copy(
            id = 2L,
            url = "https://example.org/novel/2",
            title = "Example 2",
            initialized = true,
        )

        every { source.name } returns "Example Source 2"
        coEvery {
            ratingCache.resolve(
                contentType = any(),
                sourceName = any(),
                url = any(),
                forceRefresh = any(),
                loader = any(),
            )
        } throws HttpException(404)
        every {
            ratingCache.peek(
                contentType = any(),
                sourceName = any(),
                url = any(),
            )
        } returns null

        runBlocking {
            NovelRatingFetcher().await(source, novel) shouldBe null
        }
    }
}
