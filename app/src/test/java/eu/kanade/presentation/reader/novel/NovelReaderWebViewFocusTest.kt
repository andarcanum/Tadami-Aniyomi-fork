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
    fun `webView factory disables native focus to prevent compose writer reentrancy crash`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val webView = createNovelReaderWebView(context)

        // Before fix: WebView defaults to isFocusable=true, which triggers
        // rootViewRequestFocus during removeView → focusSearch into Compose tree
        // → re-entrant compose write → "Cannot start a writer when another writer is pending"
        //
        // After fix: both flags are false so the View system won't request focus
        // into the Compose tree when this View is removed.
        webView.isFocusable shouldBe false
        webView.isFocusableInTouchMode shouldBe false
    }
}
