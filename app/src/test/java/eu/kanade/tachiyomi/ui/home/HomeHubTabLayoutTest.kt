package eu.kanade.tachiyomi.ui.home

import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHubTabLayoutTest {

    @Test
    fun `wrapped homehub section layout is enabled only on tablet classes`() {
        shouldUseHomeHubWrappedSections(AuroraDeviceClass.Phone) shouldBe false
        shouldUseHomeHubWrappedSections(AuroraDeviceClass.TabletCompact) shouldBe true
        shouldUseHomeHubWrappedSections(AuroraDeviceClass.TabletExpanded) shouldBe true
    }
}
