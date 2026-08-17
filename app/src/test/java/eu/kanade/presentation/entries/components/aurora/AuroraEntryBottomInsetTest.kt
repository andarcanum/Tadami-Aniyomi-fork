package eu.kanade.presentation.entries.components.aurora

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraEntryBottomInsetTest {

    @Test
    fun `hero bottom padding defaults to 0 dp with no navigation bar insets`() {
        resolveAuroraHeroBottomPadding(navigationBarsBottom = 0.dp) shouldBe 0.dp
    }

    @Test
    fun `hero bottom padding adds navigation bar bottom inset`() {
        resolveAuroraHeroBottomPadding(navigationBarsBottom = 48.dp) shouldBe 48.dp
        resolveAuroraHeroBottomPadding(navigationBarsBottom = 24.dp) shouldBe 24.dp
    }

    @Test
    fun `hero bottom padding supports custom base offset`() {
        resolveAuroraHeroBottomPadding(navigationBarsBottom = 48.dp, baseBottom = 8.dp) shouldBe 56.dp
    }

    @Test
    fun `fab bottom padding defaults to 20 dp with no navigation bar insets`() {
        resolveAuroraFabBottomPadding(navigationBarsBottom = 0.dp) shouldBe 20.dp
    }

    @Test
    fun `fab bottom padding adds navigation bar bottom inset to base 20 dp`() {
        resolveAuroraFabBottomPadding(navigationBarsBottom = 48.dp) shouldBe 68.dp
        resolveAuroraFabBottomPadding(navigationBarsBottom = 16.dp) shouldBe 36.dp
    }

    @Test
    fun `fab bottom padding supports custom base offset`() {
        resolveAuroraFabBottomPadding(navigationBarsBottom = 48.dp, baseBottom = 12.dp) shouldBe 60.dp
    }
}
