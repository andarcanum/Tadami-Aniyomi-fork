package eu.kanade.tachiyomi.ui.updates

import androidx.compose.foundation.pager.PagerState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class UpdatesTabSwitchingTest {

    @Test
    fun `tab switching always uses animateScrollToPage`() {
        runBlocking {
            val pagerState = mockk<PagerState>(relaxed = true)
            every { pagerState.currentPage } returns 0

            switchAuroraUpdatesPage(
                state = pagerState,
                page = 1,
            )

            coVerify(exactly = 1) { pagerState.animateScrollToPage(1) }
            coVerify(exactly = 0) { pagerState.scrollToPage(any()) }
        }
    }
}
