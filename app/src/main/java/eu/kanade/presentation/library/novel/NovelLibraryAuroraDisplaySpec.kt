package eu.kanade.presentation.library.novel

import eu.kanade.presentation.theme.aurora.adaptive.AuroraAdaptiveSpec
import tachiyomi.domain.library.model.LibraryDisplayMode

internal data class NovelLibraryAuroraDisplaySpec(
    val isList: Boolean,
    val fixedColumns: Int?,
    val adaptiveMinCellDp: Int?,
    val showMetadata: Boolean,
    val useCompactGridEntryStyle: Boolean,
    val gridCardAspectRatio: Float,
    val gridCoverHeightFraction: Float,
    val gridHorizontalSpacingDp: Int,
    val gridVerticalSpacingDp: Int,
)

internal fun resolveNovelLibraryAuroraDisplaySpec(
    displayMode: LibraryDisplayMode,
    columns: Int,
    auroraAdaptiveSpec: AuroraAdaptiveSpec? = null,
): NovelLibraryAuroraDisplaySpec {
    return when (displayMode) {
        LibraryDisplayMode.List -> NovelLibraryAuroraDisplaySpec(
            isList = true,
            fixedColumns = 1,
            adaptiveMinCellDp = null,
            showMetadata = true,
            useCompactGridEntryStyle = false,
            gridCardAspectRatio = 0.6f,
            gridCoverHeightFraction = 0.68f,
            gridHorizontalSpacingDp = 8,
            gridVerticalSpacingDp = 12,
        )
        LibraryDisplayMode.ComfortableGrid -> NovelLibraryAuroraDisplaySpec(
            isList = false,
            fixedColumns = columns.takeIf { it > 0 },
            adaptiveMinCellDp = (auroraAdaptiveSpec?.comfortableGridAdaptiveMinCellDp ?: 180).takeIf { columns <= 0 },
            showMetadata = true,
            useCompactGridEntryStyle = false,
            gridCardAspectRatio = 0.66f,
            gridCoverHeightFraction = 0.68f,
            gridHorizontalSpacingDp = 12,
            gridVerticalSpacingDp = 16,
        )
        LibraryDisplayMode.CoverOnlyGrid -> NovelLibraryAuroraDisplaySpec(
            isList = false,
            fixedColumns = columns.takeIf { it > 0 },
            adaptiveMinCellDp = (auroraAdaptiveSpec?.coverOnlyGridAdaptiveMinCellDp ?: 140).takeIf { columns <= 0 },
            showMetadata = false,
            useCompactGridEntryStyle = false,
            gridCardAspectRatio = 0.6f,
            gridCoverHeightFraction = 1f,
            gridHorizontalSpacingDp = 8,
            gridVerticalSpacingDp = 12,
        )
        LibraryDisplayMode.CompactGrid -> NovelLibraryAuroraDisplaySpec(
            isList = false,
            fixedColumns = columns.takeIf { it > 0 },
            adaptiveMinCellDp = (auroraAdaptiveSpec?.compactGridAdaptiveMinCellDp ?: 140).takeIf { columns <= 0 },
            showMetadata = true,
            useCompactGridEntryStyle = true,
            gridCardAspectRatio = 0.56f,
            gridCoverHeightFraction = 0.68f,
            gridHorizontalSpacingDp = 8,
            gridVerticalSpacingDp = 12,
        )
    }
}
