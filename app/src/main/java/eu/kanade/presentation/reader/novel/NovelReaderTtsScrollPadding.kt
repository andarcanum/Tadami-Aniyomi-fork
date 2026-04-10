package eu.kanade.presentation.reader.novel

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun resolveNovelReaderTtsScrollTopPadding(
    hasActiveTtsSession: Boolean,
    statusBarTopPadding: Dp,
    extraGap: Dp = 24.dp,
): Dp {
    if (!hasActiveTtsSession) return 0.dp

    // Keep the currently spoken paragraph below the cutout / status bar edge.
    return statusBarTopPadding + extraGap
}
