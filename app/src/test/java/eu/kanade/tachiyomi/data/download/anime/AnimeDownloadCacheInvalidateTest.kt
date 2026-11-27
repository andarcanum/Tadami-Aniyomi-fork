package eu.kanade.tachiyomi.data.download.anime

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.storage.service.StorageManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AnimeDownloadCacheInvalidateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `invalidateCache during in-flight renewal allows follow-up renewal to execute`() = runTest {
        val context = mockk<Context>(relaxed = true) {
            every { cacheDir } returns tempFolder.newFolder("cache")
        }
        val provider = mockk<AnimeDownloadProvider>(relaxed = true)
        val extInitialized = MutableStateFlow(false)
        val srcInitialized = MutableStateFlow(false)
        val extensionManager = mockk<AnimeExtensionManager> {
            every { isInitialized } returns extInitialized
        }
        val source = mockk<AnimeHttpSource>(relaxed = true) {
            every { id } returns 1L
        }
        val sourceManager = mockk<AnimeSourceManager> {
            every { isInitialized } returns srcInitialized
            every { getOnlineSources() } returns listOf(source)
            every { getStubSources() } returns emptyList()
        }
        val storageManager = mockk<StorageManager> {
            every { getDownloadsDirectory() } returns null
            every { changes } returns MutableSharedFlow()
        }

        val cache = AnimeDownloadCache(
            context = context,
            provider = provider,
            sourceManager = sourceManager,
            extensionManager = extensionManager,
            storageManager = storageManager,
            scope = backgroundScope,
        )
        runCurrent()

        // Start first renewal; it suspends waiting for extensions/sources initialization.
        cache.getTotalDownloadCount() shouldBe 0
        runCurrent()

        // Invalidate mid-flight: cancels job1 and schedules job2 without job1's finally
        // overwriting lastRenew with a retry backoff timestamp.
        cache.invalidateCache()
        runCurrent()

        extInitialized.value = true
        srcInitialized.value = true
        runCurrent()
        advanceUntilIdle()

        verify(exactly = 1) { sourceManager.getOnlineSources() }
        // Subsequent lookup sees fresh lastRenew from job2 and does not trigger another scan.
        cache.getTotalDownloadCount() shouldBe 0
        advanceUntilIdle()
        verify(exactly = 1) { sourceManager.getOnlineSources() }
    }
}
