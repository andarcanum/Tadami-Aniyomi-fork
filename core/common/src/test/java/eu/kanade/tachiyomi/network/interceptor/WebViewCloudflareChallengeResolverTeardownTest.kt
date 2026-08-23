package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.network.AndroidCookieJar
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import okhttp3.Cookie
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Regression: the WebView teardown ran after the latch waits, outside any finally block.
 * `CountDownLatch.await` throws InterruptedException when the OkHttp worker thread is
 * interrupted, so everything after the wait -- including the WebView destroy -- was skipped,
 * leaking a multi-megabyte WebView per occurrence (an OOM driver on 0.60).
 *
 * Robolectric's main looper is paused: the create/teardown runnables stay queued until the
 * test runs them, so we can observe whether destroy() was ever scheduled after an interrupt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class WebViewCloudflareChallengeResolverTeardownTest {

    private fun newRequest(): Request = Request.Builder().url("https://cf.example.com/page").build()

    @Test
    fun `interrupted solve still destroys the webview`() {
        val request = newRequest()
        val created = mutableListOf<WebView>()

        val cookieJar = mockk<AndroidCookieJar> {
            every { get(request.url) } returns emptyList<Cookie>()
        }

        // Mirror production: create/teardown run on the main looper (paused under Robolectric).
        val mainExecutor = Executor { runnable -> Handler(Looper.getMainLooper()).post(runnable) }

        val resolver = WebViewCloudflareChallengeResolver(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            mainExecutor = mainExecutor,
            createWebView = {
                // Spy the real WebView so destroy()/stopLoading() calls are verifiable.
                spyk(WebView(ApplicationProvider.getApplicationContext<Context>()))
                    .also { created.add(it) }
            },
            parseHeaders = { emptyMap() },
            isWebViewOutdated = { false },
        )

        var workerFailure: Throwable? = null
        val worker = Thread {
            try {
                resolver.resolve(request, null)
            } catch (t: Throwable) {
                workerFailure = t
            }
        }
        worker.start()

        // Run the queued create-task, then wait until the worker parks in the latch await.
        val deadline = System.currentTimeMillis() + 5_000
        while (created.isEmpty() || worker.state != Thread.State.TIMED_WAITING) {
            Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()
            check(System.currentTimeMillis() < deadline) {
                "resolver never parked: created=${created.size}, state=$worker.state, failure=$workerFailure"
            }
            Thread.sleep(10)
        }

        // Interrupt while parked in latch.await: resolve() must still tear the WebView down.
        worker.interrupt()
        worker.join(5_000)

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { created.single().destroy() }
    }
}
