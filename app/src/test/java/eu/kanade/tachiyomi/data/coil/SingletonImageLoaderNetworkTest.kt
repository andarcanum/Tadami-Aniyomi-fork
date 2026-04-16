package eu.kanade.tachiyomi.data.coil

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SingletonImageLoaderNetworkTest {

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        resetSingleton()
        server.shutdown()
    }

    @Test
    fun `singleton image loader supports remote image urls`() = runTest {
        val pngBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO5K6L8AAAAASUVORK5CYII=",
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(pngBytes)),
        )

        val context = ApplicationProvider.getApplicationContext<Application>()
        configureSingletonWithNetworkFetcher(context)
        val request = ImageRequest.Builder(context)
            .data(server.url("/cover.png").toString())
            .build()

        val result = SingletonImageLoader.get(context).execute(request)
        val failure = (result as? ErrorResult)?.throwable?.stackTraceToString()
        assertTrue(result is SuccessResult, "Expected SuccessResult but got ${result::class.simpleName}: $failure")
    }

    @OptIn(DelicateCoilApi::class)
    private fun configureSingletonWithNetworkFetcher(context: Application) {
        SingletonImageLoader.setUnsafe {
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() }))
                }
                .build()
        }
    }

    @OptIn(DelicateCoilApi::class)
    private fun resetSingleton() {
        SingletonImageLoader.reset()
    }
}
