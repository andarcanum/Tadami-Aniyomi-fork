package eu.kanade.tachiyomi.network

import android.webkit.CookieManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Constructing [AndroidCookieJar] must not initialize the WebView CookieManager.
 *
 * On Android 16 WebViewFactory.isWebViewSupported() dereferences ActivityThread.currentApplication(),
 * which is null while the app is still starting: any early background-thread construction of the
 * jar (DI graph warmup, resumed workers) crashed with
 * "Application.getPackageManager() on a null object reference".
 * The manager may only be resolved lazily on the first real cookie operation.
 */
class AndroidCookieJarTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `construction does not touch the WebView CookieManager`() {
        AndroidCookieJar()

        verify(exactly = 0) { CookieManager.getInstance() }
    }

    @Test
    fun `first cookie operation resolves the manager exactly once`() {
        val jar = AndroidCookieJar()
        val url = "https://example.org/".toHttpUrl()

        jar.saveFromResponse(url, listOf(cookie("a", "1")))
        jar.loadForRequest(url)
        jar.loadForRequest(url)

        // Lazy init on first use, cached afterwards - never re-resolved per operation.
        verify(exactly = 1) { CookieManager.getInstance() }
    }

    @Test
    fun `loadForRequest parses cookies from the manager`() {
        val manager = mockk<CookieManager>()
        every { CookieManager.getInstance() } returns manager
        every { manager.getCookie("https://example.org/") } returns "a=1; b=2"
        val jar = AndroidCookieJar()

        jar.loadForRequest("https://example.org/".toHttpUrl())
            .map { "${it.name}=${it.value}" } shouldBe listOf("a=1", "b=2")
    }

    private fun cookie(name: String, value: String): okhttp3.Cookie =
        okhttp3.Cookie.Builder().name(name).value(value).domain("example.org").build()
}
