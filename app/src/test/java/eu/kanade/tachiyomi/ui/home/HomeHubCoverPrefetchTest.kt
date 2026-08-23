package eu.kanade.tachiyomi.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * End-to-end smoke over the real Coil loader: the synchronous part of the
 * home-hub prefetch reaches the network right away, while the staggered tail
 * stays deferred until its delay elapses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class HomeHubCoverPrefetchTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        resetSingleton()
        server.shutdown()
    }

    @OptIn(DelicateCoilApi::class)
    private fun installLoader(context: Application) {
        val pngBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO5K6L8AAAAASUVORK5CYII=",
        )
        repeat(PREFETCH_FIRST_BATCH_SIZE) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(pngBytes)),
            )
        }
        SingletonImageLoader.setUnsafe {
            ImageLoader.Builder(context).build()
        }
    }

    @OptIn(DelicateCoilApi::class)
    private fun resetSingleton() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `prefetch runs only the first batch synchronously and defers the rest`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Injekt.addSingleton<Application>(context)
        installLoader(context)
        val covers = List(PREFETCH_FIRST_BATCH_SIZE) { i -> server.url("/cover-$i.png").toString() } +
            listOf(server.url("/deferred.png").toString())

        // Unconfined dispatcher: deferred batches would run immediately if the
        // code did not actually suspend on `delay`.
        prefetchHomeHubCovers(covers, staggerDelayMs = 60_000, scope = backgroundScope)

        // Pump the Robolectric main looper so enqueued first-batch requests can
        // reach the mock server.
        repeat(5) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(100)
        }

        repeat(PREFETCH_FIRST_BATCH_SIZE) {
            assertTrue(
                "first batch request missing",
                server.takeRequest(5, TimeUnit.SECONDS) != null,
            )
        }
        assertNull(
            "deferred batch must stay behind its stagger delay",
            server.takeRequest(500, TimeUnit.MILLISECONDS),
        )
    }
}
