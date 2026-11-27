package eu.kanade.presentation.library

import androidx.compose.runtime.Immutable
import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.library.model.AuroraLibraryListStyle
import tachiyomi.i18n.MR

internal const val AURORA_LIBRARY_LIST_SIZE_MAX = 5

internal fun auroraLibraryListStyleOptions(): List<Pair<StringResource, AuroraLibraryListStyle>> {
    return listOf(
        MR.strings.pref_aurora_library_list_style_banner to AuroraLibraryListStyle.Banner,
        MR.strings.pref_aurora_library_list_style_comfortable to AuroraLibraryListStyle.Comfortable,
        MR.strings.pref_aurora_library_list_style_compact to AuroraLibraryListStyle.Compact,
    )
}

internal fun formatAuroraLibraryListSizeLabel(
    size: Int,
    autoLabel: String,
): String {
    return when (size.coerceIn(0, AURORA_LIBRARY_LIST_SIZE_MAX)) {
        0 -> autoLabel
        1 -> "XS"
        2 -> "S"
        3 -> "M"
        4 -> "L"
        else -> "XL"
    }
}

@Immutable
internal data class AuroraLibraryListRowMetrics(
    val coverWidthDp: Int,
    val coverHeightDp: Int,
    val coverCornerRadiusDp: Int,
    val verticalPaddingDp: Int,
    val horizontalPaddingDp: Int,
    val contentSpacingDp: Int,
    val titleFontSizeSp: Float,
    val titleMaxLines: Int,
    val showProgressBar: Boolean,
    val continueButtonSizeDp: Int,
    val continueIconSizeDp: Int,
)

internal fun resolveAuroraLibraryListRowMetrics(
    style: AuroraLibraryListStyle,
    sizeSetting: Int,
): AuroraLibraryListRowMetrics {
    val normalizedSize = sizeSetting.coerceIn(0, AURORA_LIBRARY_LIST_SIZE_MAX)
    val isComfortable = style == AuroraLibraryListStyle.Comfortable
    val effectiveTier = if (normalizedSize == 0) {
        if (isComfortable) 3 else 2
    } else {
        normalizedSize
    }

    return when (effectiveTier) {
        1 -> AuroraLibraryListRowMetrics(
            coverWidthDp = 36,
            coverHeightDp = 50,
            coverCornerRadiusDp = 8,
            verticalPaddingDp = 6,
            horizontalPaddingDp = 14,
            contentSpacingDp = 10,
            titleFontSizeSp = 13.5f,
            titleMaxLines = if (isComfortable) 2 else 1,
            showProgressBar = isComfortable,
            continueButtonSizeDp = 28,
            continueIconSizeDp = 15,
        )
        2 -> AuroraLibraryListRowMetrics(
            coverWidthDp = 44,
            coverHeightDp = 62,
            coverCornerRadiusDp = 9,
            verticalPaddingDp = 9,
            horizontalPaddingDp = 14,
            contentSpacingDp = 11,
            titleFontSizeSp = 14f,
            titleMaxLines = if (isComfortable) 2 else 1,
            showProgressBar = isComfortable,
            continueButtonSizeDp = 30,
            continueIconSizeDp = 16,
        )
        3 -> AuroraLibraryListRowMetrics(
            coverWidthDp = 52,
            coverHeightDp = 72,
            coverCornerRadiusDp = 10,
            verticalPaddingDp = 10,
            horizontalPaddingDp = 14,
            contentSpacingDp = 12,
            titleFontSizeSp = 14.5f,
            titleMaxLines = 2,
            showProgressBar = isComfortable,
            continueButtonSizeDp = 32,
            continueIconSizeDp = 17,
        )
        4 -> AuroraLibraryListRowMetrics(
            coverWidthDp = 60,
            coverHeightDp = 84,
            coverCornerRadiusDp = 11,
            verticalPaddingDp = 11,
            horizontalPaddingDp = 14,
            contentSpacingDp = 12,
            titleFontSizeSp = 15f,
            titleMaxLines = 2,
            showProgressBar = isComfortable,
            continueButtonSizeDp = 34,
            continueIconSizeDp = 18,
        )
        else -> AuroraLibraryListRowMetrics(
            coverWidthDp = 72,
            coverHeightDp = 100,
            coverCornerRadiusDp = 12,
            verticalPaddingDp = 12,
            horizontalPaddingDp = 14,
            contentSpacingDp = 13,
            titleFontSizeSp = 15.5f,
            titleMaxLines = 2,
            showProgressBar = isComfortable,
            continueButtonSizeDp = 36,
            continueIconSizeDp = 19,
        )
    }
}
