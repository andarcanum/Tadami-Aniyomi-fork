package eu.kanade.presentation.entries.manga

import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaScreenAuroraLayoutTest {

    @Test
    fun `two pane aurora layout is enabled only for tablet expanded`() {
        shouldUseMangaAuroraTwoPane(AuroraDeviceClass.Phone) shouldBe false
        shouldUseMangaAuroraTwoPane(AuroraDeviceClass.TabletCompact) shouldBe false
        shouldUseMangaAuroraTwoPane(AuroraDeviceClass.TabletExpanded) shouldBe true
    }

    @Test
    fun `fast scroller uses pane scoped placement only in two pane manga layout`() {
        shouldUseMangaAuroraPaneScopedFastScroller(useTwoPaneLayout = false) shouldBe false
        shouldUseMangaAuroraPaneScopedFastScroller(useTwoPaneLayout = true) shouldBe true
    }
}
