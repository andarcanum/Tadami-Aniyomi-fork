package eu.kanade.presentation.reader.novel

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelReaderTtsScrollPaddingTest {

    @Test
    fun `tts scroll padding is zero without active session`() {
        assertEquals(
            0.dp,
            resolveNovelReaderTtsScrollTopPadding(
                hasActiveTtsSession = false,
                statusBarTopPadding = 16.dp,
            ),
        )
    }

    @Test
    fun `tts scroll padding keeps active paragraph below status bar`() {
        assertEquals(
            40.dp,
            resolveNovelReaderTtsScrollTopPadding(
                hasActiveTtsSession = true,
                statusBarTopPadding = 16.dp,
            ),
        )
    }
}
