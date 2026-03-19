package eu.kanade.presentation.entries

data class TitleListFastScrollSpec(
    val thumbAllowed: Boolean,
    val topPaddingPx: Int,
)

internal fun resolveTitleListFastScrollSpec(
    baseTopPaddingPx: Int,
    firstVisibleItemIndex: Int,
    blockStartIndex: Int,
    blockStartOffsetPx: Int?,
): TitleListFastScrollSpec {
    return when {
        blockStartOffsetPx != null -> TitleListFastScrollSpec(
            thumbAllowed = true,
            topPaddingPx = maxOf(baseTopPaddingPx, blockStartOffsetPx),
        )
        firstVisibleItemIndex >= blockStartIndex -> TitleListFastScrollSpec(
            thumbAllowed = true,
            topPaddingPx = baseTopPaddingPx,
        )
        else -> TitleListFastScrollSpec(
            thumbAllowed = false,
            topPaddingPx = baseTopPaddingPx,
        )
    }
}

internal fun shouldShowTitleFastScrollOverlayChrome(
    isThumbDragged: Boolean,
): Boolean {
    return !isThumbDragged
}

internal fun shouldShowTitleFastScrollFloatingActionButton(
    isEligibleToShow: Boolean,
    isThumbDragged: Boolean,
): Boolean {
    return isEligibleToShow && !isThumbDragged
}
