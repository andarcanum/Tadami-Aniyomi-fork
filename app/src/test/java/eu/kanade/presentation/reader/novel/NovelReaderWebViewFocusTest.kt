package eu.kanade.presentation.reader.novel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NovelReaderWebViewFocusTest {

    @Test
    fun `webView factory keeps native focus for selection drag handles without stealing tap focus`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val webView = createNovelReaderWebView(context)

        // History: the view was once created unfocusable to prevent a compose writer
        // reentrancy crash ("Cannot start a writer when another writer is pending") on
        // removeView. That also disabled Chromium's selection drag handles, which only
        // show for a focusable WebView, so the current contract is:
        // - isFocusable = true — required for long-press selection drag handles;
        // - isFocusableInTouchMode = false — taps must not steal key/input focus from
        //   the reader.
        webView.isFocusable shouldBe true
        webView.isFocusableInTouchMode shouldBe false
    }
}
