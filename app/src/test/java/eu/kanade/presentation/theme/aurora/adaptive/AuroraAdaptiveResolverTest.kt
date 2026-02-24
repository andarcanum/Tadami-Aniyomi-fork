package eu.kanade.presentation.theme.aurora.adaptive

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraAdaptiveResolverTest {

    @Test
    fun `phone spec is used when tablet UI is disabled`() {
        val spec = resolveAuroraAdaptiveSpec(isTabletUi = false, containerWidthDp = 1000)

        spec.deviceClass shouldBe AuroraDeviceClass.Phone
        spec.listMaxWidthDp shouldBe null
        spec.contentHorizontalPaddingDp shouldBe 12
        spec.comfortableGridAdaptiveMinCellDp shouldBe 180
    }

    @Test
    fun `tablet compact spec is used for widths between 600 and 839 dp`() {
        val spec = resolveAuroraAdaptiveSpec(isTabletUi = true, containerWidthDp = 720)

        spec.deviceClass shouldBe AuroraDeviceClass.TabletCompact
        spec.listMaxWidthDp shouldBe 760
        spec.contentHorizontalPaddingDp shouldBe 20
        spec.comfortableGridAdaptiveMinCellDp shouldBe 196
    }

    @Test
    fun `tablet expanded spec is used for widths 840 dp and above`() {
        val spec = resolveAuroraAdaptiveSpec(isTabletUi = true, containerWidthDp = 1024)

        spec.deviceClass shouldBe AuroraDeviceClass.TabletExpanded
        spec.listMaxWidthDp shouldBe 960
        spec.updatesMaxWidthDp shouldBe 1080
        spec.entryMaxWidthDp shouldBe 1120
        spec.coverOnlyGridAdaptiveMinCellDp shouldBe 168
    }

    @Test
    fun `tablet widths below threshold still use phone spec`() {
        val spec = resolveAuroraAdaptiveSpec(isTabletUi = true, containerWidthDp = 580)

        spec.deviceClass shouldBe AuroraDeviceClass.Phone
    }
}
