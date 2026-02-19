package eu.kanade.tachiyomi.ui.updates

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UpdatesTabSubtitleVisibilityTest {

    @Test
    fun `aurora updates subtitle is hidden for all tabs`() {
        shouldShowAuroraUpdatesSubtitle(0) shouldBe false
        shouldShowAuroraUpdatesSubtitle(1) shouldBe false
        shouldShowAuroraUpdatesSubtitle(2) shouldBe false
    }
}
