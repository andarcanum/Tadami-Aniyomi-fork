package tachiyomi.presentation.core.components

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class FastScrollerItemInfo(
    val index: Int,
    val top: Int,
    val size: Int,
    val isStickyHeader: Boolean = false,
) {
    val bottom: Int = top + size
}

data class FastScrollerTrackBounds(
    val heightPx: Float,
    val effectiveTrackHeightPx: Float,
    val minThumbOffsetY: Float,
    val maxThumbOffsetY: Float,
)

data class FastScrollerListProgress(
    val topItem: FastScrollerItemInfo,
    val bottomItem: FastScrollerItemInfo,
    val previousSections: Float,
    val remainingSections: Float,
    val scrollableSections: Float,
)

data class FastScrollerListEstimateState(
    val previousScrollableSections: Float? = null,
    val estimateConfidence: Float? = null,
)

data class FastScrollerListEstimateResolution(
    val maxRemainingSections: Float,
    val nextState: FastScrollerListEstimateState,
)

data class FastScrollerGridProgress(
    val avgSizePerRow: Float,
    val scrollOffset: Int,
    val scrollRange: Int,
    val totalItemsCount: Int,
    val columnCount: Int,
)

data class FastScrollerScrollRequest(
    val index: Int,
    val scrollOffset: Int,
)

fun resolveFastScrollerTrackBounds(
    contentHeightPx: Float,
    thumbTopPadding: Float,
    thumbBottomPadding: Float,
    afterContentPaddingPx: Int,
    thumbHeightPx: Float,
): FastScrollerTrackBounds {
    val heightPx = contentHeightPx - thumbTopPadding - thumbBottomPadding - afterContentPaddingPx
    val trackHeightPx = heightPx - thumbHeightPx
    val effectiveTrackHeightPx = max(0f, trackHeightPx)
    return FastScrollerTrackBounds(
        heightPx = heightPx,
        effectiveTrackHeightPx = effectiveTrackHeightPx,
        minThumbOffsetY = thumbTopPadding,
        maxThumbOffsetY = thumbTopPadding + effectiveTrackHeightPx,
    )
}

fun shouldEnableFastScrollerDrag(
    isThumbVisible: Boolean,
    isThumbDragged: Boolean,
    isScrollInProgress: Boolean,
): Boolean {
    return isThumbVisible && (isThumbDragged || !isScrollInProgress)
}

fun computeFastScrollerListProgress(
    visibleItems: List<FastScrollerItemInfo>,
    totalItemsCount: Int,
    scrollHeightPx: Float,
): FastScrollerListProgress? {
    if (visibleItems.isEmpty() || totalItemsCount == 0) return null
    val topItem = visibleItems.firstOrNull {
        it.bottom >= 0 && !it.isStickyHeader
    } ?: visibleItems.first()
    val bottomItem = visibleItems.lastOrNull {
        it.top <= scrollHeightPx && !it.isStickyHeader
    } ?: visibleItems.last()

    val topHiddenProportion = -1f * topItem.top / topItem.size.coerceAtLeast(1)
    val bottomHiddenProportion = (bottomItem.bottom - scrollHeightPx) / bottomItem.size.coerceAtLeast(1)
    val previousSections = topHiddenProportion + topItem.index
    val remainingSections = bottomHiddenProportion + (totalItemsCount - (bottomItem.index + 1))
    return FastScrollerListProgress(
        topItem = topItem,
        bottomItem = bottomItem,
        previousSections = previousSections,
        remainingSections = remainingSections,
        scrollableSections = previousSections + remainingSections,
    )
}

fun resolveFastScrollerListEstimateState(
    previousState: FastScrollerListEstimateState?,
    progress: FastScrollerListProgress,
    anyScrollInProgress: Boolean,
): FastScrollerListEstimateResolution {
    val previousScrollableSections = previousState?.previousScrollableSections ?: progress.scrollableSections
    val layoutChanged = !anyScrollInProgress && abs(previousScrollableSections - progress.scrollableSections) > 0.1f
    val estimateConfidence = when {
        previousState?.estimateConfidence == null -> progress.remainingSections
        layoutChanged -> progress.remainingSections
        else -> previousState.estimateConfidence
    }
    val maxRemainingSections = max(estimateConfidence, progress.scrollableSections)
    return FastScrollerListEstimateResolution(
        maxRemainingSections = maxRemainingSections,
        nextState = FastScrollerListEstimateState(
            previousScrollableSections = progress.scrollableSections,
            estimateConfidence = max(estimateConfidence, progress.remainingSections),
        ),
    )
}

fun resolveFastScrollerListThumbOffset(
    progress: FastScrollerListProgress,
    maxRemainingSections: Float,
    trackBounds: FastScrollerTrackBounds,
): Float {
    if (trackBounds.effectiveTrackHeightPx <= 0f || maxRemainingSections < 0.5f) {
        return trackBounds.minThumbOffsetY
    }
    val proportion = (1f - progress.remainingSections / maxRemainingSections).coerceIn(0f, 1f)
    return trackBounds.effectiveTrackHeightPx * proportion + trackBounds.minThumbOffsetY
}

fun resolveFastScrollerListScrollRequest(
    thumbOffsetY: Float,
    trackBounds: FastScrollerTrackBounds,
    maxRemainingSections: Float,
    totalItemsCount: Int,
    visibleItems: List<FastScrollerItemInfo>,
    scrollHeightPx: Float,
): FastScrollerScrollRequest? {
    if (totalItemsCount == 0 || trackBounds.effectiveTrackHeightPx <= 0f || maxRemainingSections < 0.5f) {
        return null
    }
    val progress = computeFastScrollerListProgress(
        visibleItems = visibleItems,
        totalItemsCount = totalItemsCount,
        scrollHeightPx = scrollHeightPx,
    ) ?: return null
    val thumbProportion = ((thumbOffsetY - trackBounds.minThumbOffsetY) / trackBounds.effectiveTrackHeightPx)
        .coerceIn(0f, 1f)
    if (thumbProportion <= 0.001f) {
        return FastScrollerScrollRequest(index = 0, scrollOffset = 0)
    }

    val scrollRemainingSections = (1f - thumbProportion) * maxRemainingSections
    val currentSection = totalItemsCount - scrollRemainingSections
    val scrollSectionIndex = currentSection.toInt().coerceAtMost(totalItemsCount)
    val visibleRange = abs(progress.bottomItem.index - progress.topItem.index) + 1
    val estimatedSectionSizePx = abs(progress.bottomItem.bottom - progress.topItem.top).toFloat() /
        visibleRange.coerceAtLeast(1)
    val expectedScrollItem = visibleItems.find { it.index == scrollSectionIndex }
    val sectionSizePx = expectedScrollItem?.size?.toFloat() ?: estimatedSectionSizePx
    val scrollRelativeOffset = sectionSizePx * (currentSection - scrollSectionIndex)
    val scrollSectionOffset = (scrollRelativeOffset - scrollHeightPx).roundToInt()
    val scrollItemIndex = scrollSectionIndex.coerceIn(0, totalItemsCount - 1)
    val scrollItemOffset = scrollSectionOffset + ((scrollSectionIndex - scrollItemIndex) * sectionSizePx).roundToInt()
    return FastScrollerScrollRequest(
        index = scrollItemIndex,
        scrollOffset = scrollItemOffset,
    )
}

fun computeFastScrollerGridProgress(
    visibleItems: List<FastScrollerItemInfo>,
    totalItemsCount: Int,
    columnCount: Int,
): FastScrollerGridProgress? {
    if (visibleItems.isEmpty() || totalItemsCount == 0 || columnCount <= 0) return null
    val startChild = visibleItems.first()
    val endChild = visibleItems.last()
    val laidOutArea = (endChild.bottom - startChild.top).coerceAtLeast(0)
    val laidOutRows = (1 + abs(endChild.index - startChild.index) / columnCount).coerceAtLeast(1)
    val avgSizePerRow = laidOutArea.toFloat() / laidOutRows
    val rowsBefore = min(startChild.index, endChild.index).coerceAtLeast(0) / columnCount
    val scrollOffset = (rowsBefore * avgSizePerRow - startChild.top).roundToInt()
    val totalRows = 1 + (totalItemsCount - 1) / columnCount
    val endSpacing = avgSizePerRow - endChild.size
    val scrollRange = (endSpacing + avgSizePerRow * totalRows).roundToInt()
    return FastScrollerGridProgress(
        avgSizePerRow = avgSizePerRow,
        scrollOffset = scrollOffset,
        scrollRange = scrollRange,
        totalItemsCount = totalItemsCount,
        columnCount = columnCount,
    )
}

fun resolveFastScrollerGridThumbOffset(
    progress: FastScrollerGridProgress,
    trackBounds: FastScrollerTrackBounds,
): Float {
    if (trackBounds.effectiveTrackHeightPx <= 0f) return trackBounds.minThumbOffsetY
    val extraScrollRange = (progress.scrollRange.toFloat() - trackBounds.heightPx).coerceAtLeast(1f)
    val proportion = (progress.scrollOffset.toFloat() / extraScrollRange).coerceAtMost(1f).coerceAtLeast(0f)
    return trackBounds.effectiveTrackHeightPx * proportion + trackBounds.minThumbOffsetY
}

fun resolveFastScrollerGridScrollRequest(
    thumbOffsetY: Float,
    progress: FastScrollerGridProgress,
    trackBounds: FastScrollerTrackBounds,
    viewportHeightPx: Float,
): FastScrollerScrollRequest? {
    if (progress.totalItemsCount == 0 || progress.columnCount <= 0 || trackBounds.effectiveTrackHeightPx <= 0f) {
        return null
    }
    val scrollRatio = ((thumbOffsetY - trackBounds.minThumbOffsetY) / trackBounds.effectiveTrackHeightPx)
        .coerceIn(0f, 1f)
    val scrollAmt = scrollRatio * (progress.scrollRange.toFloat() - viewportHeightPx).coerceAtLeast(1f)
    val rowNumber = (scrollAmt / progress.avgSizePerRow).toInt()
    val rowOffset = scrollAmt - rowNumber * progress.avgSizePerRow
    val itemIndex = (progress.columnCount * rowNumber).coerceIn(0, progress.totalItemsCount - 1)
    return FastScrollerScrollRequest(
        index = itemIndex,
        scrollOffset = rowOffset.roundToInt(),
    )
}
