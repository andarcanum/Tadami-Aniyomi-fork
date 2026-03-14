package eu.kanade.presentation.library.components

import tachiyomi.domain.library.model.LibraryDisplayMode

data class GlowContourLibraryTextSpec(
    val showTextBlock: Boolean,
    val titleMaxLines: Int,
    val subtitleMaxLines: Int,
)

fun resolveGlowContourLibraryTextSpec(
    displayMode: LibraryDisplayMode,
): GlowContourLibraryTextSpec {
    return when (displayMode) {
        LibraryDisplayMode.ComfortableGrid -> GlowContourLibraryTextSpec(
            showTextBlock = true,
            titleMaxLines = 2,
            subtitleMaxLines = 1,
        )
        LibraryDisplayMode.CompactGrid -> GlowContourLibraryTextSpec(
            showTextBlock = true,
            titleMaxLines = 1,
            subtitleMaxLines = 1,
        )
        LibraryDisplayMode.CoverOnlyGrid,
        LibraryDisplayMode.List,
        -> GlowContourLibraryTextSpec(
            showTextBlock = false,
            titleMaxLines = 0,
            subtitleMaxLines = 0,
        )
    }
}
