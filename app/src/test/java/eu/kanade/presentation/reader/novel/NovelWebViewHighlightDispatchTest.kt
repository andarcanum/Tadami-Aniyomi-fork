package eu.kanade.presentation.reader.novel

import android.app.Application
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.SelectedTextAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NovelWebViewHighlightDispatchTest {
    @Test
    fun `highlight menu dispatches persistence action rather than only showing a toast`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = NovelReaderWebView(context)
        try {
            view.localSelection = NovelSelectedTextSelection(
                sessionId = 1L,
                renderer = NovelSelectedTextRenderer.WEBVIEW,
                text = "Quote",
                anchor = NovelSelectedTextAnchor(0, 0, 100, 20),
            )
            var dispatched: NovelSelectedTextSelection? = null
            view.onSelectedTextSelectionChanged = { dispatched = it }
            val item = PopupMenu(context, View(context)).menu.add(Menu.NONE, MENU_ID_HIGHLIGHT, 0, "Highlight")
            val method = NovelReaderWebView::class.java.getDeclaredMethod(
                "handleReaderSelectionAction",
                MenuItem::class.java,
                ActionMode::class.java,
            ).apply { isAccessible = true }
            assertTrue(method.invoke(view, item, null) as Boolean)
            assertEquals(SelectedTextAction.HIGHLIGHT, dispatched?.triggerAction)
            assertEquals("Quote", dispatched?.text)
        } finally {
            view.destroy()
        }
    }
}
