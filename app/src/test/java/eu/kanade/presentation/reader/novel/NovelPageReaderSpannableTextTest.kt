package eu.kanade.presentation.reader.novel

import android.app.Application
import android.text.style.ClickableSpan
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NovelPageReaderSpannableTextTest {

    @Test
    fun `spannable builder preserves rich url annotations as clickable spans`() {
        val annotated = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(text = "Hello "),
                NovelRichTextSegment(text = "link", linkUrl = "https://example.org"),
            ),
        )

        val spannable = buildNovelPageReaderSpannableText(text = annotated)

        val clickableSpans = spannable.getSpans(0, spannable.length, ClickableSpan::class.java)
        assertEquals(1, clickableSpans.size)
    }

    @Test
    fun `spannable url click span dispatches callback`() {
        val annotated = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(text = "Hello "),
                NovelRichTextSegment(text = "link", linkUrl = "https://example.org"),
            ),
        )
        var clickedUrl: String? = null

        val spannable = buildNovelPageReaderSpannableText(
            text = annotated,
            onUrlClick = { clickedUrl = it },
        )

        val clickableSpan = spannable.getSpans(0, spannable.length, ClickableSpan::class.java).single()
        clickableSpan.onClick(TextView(ApplicationProvider.getApplicationContext()))

        assertTrue(clickedUrl == "https://example.org")
    }
}
