package tachiyomi.presentation.core.components

fun resolveFastScrollerTrackTopPadding(
    previousTrackTopPadding: Float?,
    liveTrackTopPadding: Float,
    isThumbDragged: Boolean,
): Float {
    return if (isThumbDragged) {
        previousTrackTopPadding ?: liveTrackTopPadding
    } else {
        liveTrackTopPadding
    }
}
