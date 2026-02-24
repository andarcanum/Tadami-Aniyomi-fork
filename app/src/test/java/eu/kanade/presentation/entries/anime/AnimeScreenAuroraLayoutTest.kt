package eu.kanade.presentation.entries.anime

import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeScreenAuroraLayoutTest {

    @Test
    fun `two pane aurora layout is enabled only for tablet expanded`() {
        shouldUseAnimeAuroraTwoPane(AuroraDeviceClass.Phone) shouldBe false
        shouldUseAnimeAuroraTwoPane(AuroraDeviceClass.TabletCompact) shouldBe false
        shouldUseAnimeAuroraTwoPane(AuroraDeviceClass.TabletExpanded) shouldBe true
    }
}
