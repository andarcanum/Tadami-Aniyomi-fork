package eu.kanade.presentation.theme.aurora.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import eu.kanade.presentation.util.isTabletUi

enum class AuroraDeviceClass {
    Phone,
    TabletCompact,
    TabletExpanded,
}

data class AuroraAdaptiveSpec(
    val deviceClass: AuroraDeviceClass,
    val contentHorizontalPaddingDp: Int,
    val listMaxWidthDp: Int?,
    val updatesMaxWidthDp: Int?,
    val entryMaxWidthDp: Int?,
    val comfortableGridAdaptiveMinCellDp: Int,
    val coverOnlyGridAdaptiveMinCellDp: Int,
    val compactGridAdaptiveMinCellDp: Int,
)

fun resolveAuroraAdaptiveSpec(
    isTabletUi: Boolean,
    containerWidthDp: Int,
): AuroraAdaptiveSpec {
    val deviceClass = when {
        !isTabletUi || containerWidthDp < 600 -> AuroraDeviceClass.Phone
        containerWidthDp < 840 -> AuroraDeviceClass.TabletCompact
        else -> AuroraDeviceClass.TabletExpanded
    }

    return when (deviceClass) {
        AuroraDeviceClass.Phone -> AuroraAdaptiveSpec(
            deviceClass = deviceClass,
            contentHorizontalPaddingDp = 12,
            listMaxWidthDp = null,
            updatesMaxWidthDp = null,
            entryMaxWidthDp = null,
            comfortableGridAdaptiveMinCellDp = 180,
            coverOnlyGridAdaptiveMinCellDp = 140,
            compactGridAdaptiveMinCellDp = 140,
        )
        AuroraDeviceClass.TabletCompact -> AuroraAdaptiveSpec(
            deviceClass = deviceClass,
            contentHorizontalPaddingDp = 20,
            listMaxWidthDp = 760,
            updatesMaxWidthDp = 860,
            entryMaxWidthDp = 920,
            comfortableGridAdaptiveMinCellDp = 196,
            coverOnlyGridAdaptiveMinCellDp = 154,
            compactGridAdaptiveMinCellDp = 154,
        )
        AuroraDeviceClass.TabletExpanded -> AuroraAdaptiveSpec(
            deviceClass = deviceClass,
            contentHorizontalPaddingDp = 24,
            listMaxWidthDp = 960,
            updatesMaxWidthDp = 1080,
            entryMaxWidthDp = 1120,
            comfortableGridAdaptiveMinCellDp = 212,
            coverOnlyGridAdaptiveMinCellDp = 168,
            compactGridAdaptiveMinCellDp = 168,
        )
    }
}

@Composable
@ReadOnlyComposable
fun rememberAuroraAdaptiveSpec(
    containerWidthDp: Int = LocalConfiguration.current.screenWidthDp,
): AuroraAdaptiveSpec {
    return resolveAuroraAdaptiveSpec(
        isTabletUi = isTabletUi(),
        containerWidthDp = containerWidthDp,
    )
}
