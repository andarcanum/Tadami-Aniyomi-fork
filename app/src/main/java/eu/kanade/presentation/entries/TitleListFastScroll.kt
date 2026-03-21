package eu.kanade.presentation.entries

data class TitleListFastScrollSpec(
    val thumbAllowed: Boolean,
    val topPaddingPx: Int,
)

internal data class TitleFastScrollOverlayAccumulator(
    val prevIndex: Int,
    val prevOffset: Int,
    val revealed: Boolean,
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
    isExpandedList: Boolean,
    isReverseScrolling: Boolean,
): Boolean {
    if (isThumbDragged) return false
    if (!isExpandedList) return true
    return isReverseScrolling
}

internal fun resolveTitleFastScrollOverlayRevealState(
    currentRevealState: Boolean,
    isExpandedList: Boolean,
    isScrolling: Boolean,
    movedForward: Boolean,
): Boolean {
    if (!isExpandedList) return false
    if (movedForward) return false
    return currentRevealState || isScrolling
}

internal fun reduceTitleFastScrollOverlayAccumulator(
    current: TitleFastScrollOverlayAccumulator,
    isExpandedList: Boolean,
    isScrolling: Boolean,
    index: Int,
    offset: Int,
): TitleFastScrollOverlayAccumulator {
    val movedForward = index > current.prevIndex || (index == current.prevIndex && offset > current.prevOffset)
    return TitleFastScrollOverlayAccumulator(
        prevIndex = index,
        prevOffset = offset,
        revealed = resolveTitleFastScrollOverlayRevealState(
            currentRevealState = current.revealed,
            isExpandedList = isExpandedList,
            isScrolling = isScrolling,
            movedForward = movedForward,
        ),
    )
}

internal fun shouldShowTitleFastScrollFloatingActionButton(
    isEligibleToShow: Boolean,
    isThumbDragged: Boolean,
): Boolean {
    return isEligibleToShow && !isThumbDragged
}
