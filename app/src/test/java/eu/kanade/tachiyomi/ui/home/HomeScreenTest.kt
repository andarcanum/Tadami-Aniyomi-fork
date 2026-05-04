package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeScreenTest {

    @Test
    fun `browse badge is hidden when tab is selected`() {
        shouldShowBrowseExtensionBadge(
            selected = true,
            currentCount = 5,
            seenCount = 0,
        ) shouldBe false
    }

    @Test
    fun `browse badge is hidden when count has been seen`() {
        shouldShowBrowseExtensionBadge(
            selected = false,
            currentCount = 5,
            seenCount = 5,
        ) shouldBe false
    }

    @Test
    fun `browse badge shows when unseen count differs`() {
        shouldShowBrowseExtensionBadge(
            selected = false,
            currentCount = 6,
            seenCount = 5,
        ) shouldBe true
    }
}
