package eu.kanade.presentation.library

import eu.kanade.presentation.library.components.AuroraLibraryListBadgeData
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.AuroraLibraryListStyle

class AuroraLibraryListStyleUiTest {

    @Test
    fun `list style options expose all enum entries in expected order`() {
        auroraLibraryListStyleOptions().map { it.second } shouldBe listOf(
            AuroraLibraryListStyle.Banner,
            AuroraLibraryListStyle.Comfortable,
            AuroraLibraryListStyle.Compact,
        )
    }

    @Test
    fun `compact auto size matches browse source row metrics`() {
        val metrics = resolveAuroraLibraryListRowMetrics(
            style = AuroraLibraryListStyle.Compact,
            sizeSetting = 0,
        )

        metrics.coverWidthDp shouldBe 44
        metrics.coverHeightDp shouldBe 62
        metrics.coverCornerRadiusDp shouldBe 9
        metrics.verticalPaddingDp shouldBe 9
        metrics.horizontalPaddingDp shouldBe 14
        metrics.contentSpacingDp shouldBe 11
        metrics.titleMaxLines shouldBe 1
        metrics.showProgressBar shouldBe false
    }

    @Test
    fun `comfortable auto size enables two-line titles and progress bar`() {
        val metrics = resolveAuroraLibraryListRowMetrics(
            style = AuroraLibraryListStyle.Comfortable,
            sizeSetting = 0,
        )

        metrics.coverWidthDp shouldBe 52
        metrics.coverHeightDp shouldBe 72
        metrics.coverCornerRadiusDp shouldBe 10
        metrics.titleMaxLines shouldBe 2
        metrics.showProgressBar shouldBe true
    }

    @Test
    fun `explicit size tiers scale monotonically and clamp out-of-bounds values`() {
        val compactSizes = (1..AURORA_LIBRARY_LIST_SIZE_MAX).map { tier ->
            resolveAuroraLibraryListRowMetrics(AuroraLibraryListStyle.Compact, tier)
        }
        compactSizes.map { it.coverWidthDp } shouldBe listOf(36, 44, 52, 60, 72)
        compactSizes.map { it.coverHeightDp } shouldBe listOf(50, 62, 72, 84, 100)
        compactSizes.all { !it.showProgressBar } shouldBe true

        val comfortableSizes = (1..AURORA_LIBRARY_LIST_SIZE_MAX).map { tier ->
            resolveAuroraLibraryListRowMetrics(AuroraLibraryListStyle.Comfortable, tier)
        }
        comfortableSizes.all { it.showProgressBar && it.titleMaxLines == 2 } shouldBe true

        resolveAuroraLibraryListRowMetrics(AuroraLibraryListStyle.Compact, -5) shouldBe
            resolveAuroraLibraryListRowMetrics(AuroraLibraryListStyle.Compact, 0)
        resolveAuroraLibraryListRowMetrics(AuroraLibraryListStyle.Compact, 99) shouldBe
            resolveAuroraLibraryListRowMetrics(AuroraLibraryListStyle.Compact, AURORA_LIBRARY_LIST_SIZE_MAX)
    }

    @Test
    fun `size labels format auto and all tiers safely`() {
        formatAuroraLibraryListSizeLabel(-1, "Auto") shouldBe "Auto"
        formatAuroraLibraryListSizeLabel(0, "Auto") shouldBe "Auto"
        formatAuroraLibraryListSizeLabel(1, "Auto") shouldBe "XS"
        formatAuroraLibraryListSizeLabel(2, "Auto") shouldBe "S"
        formatAuroraLibraryListSizeLabel(3, "Auto") shouldBe "M"
        formatAuroraLibraryListSizeLabel(4, "Auto") shouldBe "L"
        formatAuroraLibraryListSizeLabel(5, "Auto") shouldBe "XL"
        formatAuroraLibraryListSizeLabel(42, "Auto") shouldBe "XL"
    }

    @Test
    fun `badge data detects presence across anime manga and novel combinations`() {
        AuroraLibraryListBadgeData().hasAny shouldBe false
        AuroraLibraryListBadgeData(
            downloadLabel = " ",
            unreadCount = 0L,
            isLocal = false,
            language = "",
        ).hasAny shouldBe
            false
        AuroraLibraryListBadgeData(downloadLabel = "12").hasAny shouldBe true
        AuroraLibraryListBadgeData(downloadLabel = "DL").hasAny shouldBe true
        AuroraLibraryListBadgeData(unreadCount = 5L).hasAny shouldBe true
        AuroraLibraryListBadgeData(isLocal = true).hasAny shouldBe true
        AuroraLibraryListBadgeData(language = "en").hasAny shouldBe true
    }
}
