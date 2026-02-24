package eu.kanade.presentation.entries.novel

import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelScreenAuroraLayoutTest {

    @Test
    fun `two pane aurora layout is enabled only for tablet expanded`() {
        shouldUseNovelAuroraTwoPane(AuroraDeviceClass.Phone) shouldBe false
        shouldUseNovelAuroraTwoPane(AuroraDeviceClass.TabletCompact) shouldBe false
        shouldUseNovelAuroraTwoPane(AuroraDeviceClass.TabletExpanded) shouldBe true
    }
}
