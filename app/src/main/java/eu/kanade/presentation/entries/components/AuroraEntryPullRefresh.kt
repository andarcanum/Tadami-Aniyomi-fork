package eu.kanade.presentation.entries.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.delay

private const val AURORA_ENTRY_HOLD_REFRESH_TRIGGER_THRESHOLD = 1.0f
private const val AURORA_ENTRY_HOLD_REFRESH_RESET_THRESHOLD = 0.1f
private const val AURORA_ENTRY_HOLD_REFRESH_DELAY_MS = 360L

internal fun shouldStartAuroraEntryHoldRefresh(
    distanceFraction: Float,
    refreshing: Boolean,
    hasTriggeredForCurrentPull: Boolean,
): Boolean {
    return !refreshing &&
        !hasTriggeredForCurrentPull &&
        distanceFraction >= AURORA_ENTRY_HOLD_REFRESH_TRIGGER_THRESHOLD
}

internal fun shouldResetAuroraEntryHoldRefreshLatch(distanceFraction: Float): Boolean {
    return distanceFraction <= AURORA_ENTRY_HOLD_REFRESH_RESET_THRESHOLD
}

internal fun shouldTriggerAuroraEntryRefresh(
    refreshing: Boolean,
    hasTriggeredForCurrentPull: Boolean,
): Boolean {
    return !refreshing && !hasTriggeredForCurrentPull
}

internal fun normalizeAuroraGlobalSearchQuery(title: String): String? {
    return title.trim().takeIf { it.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuroraEntryHoldToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    holdDelayMillis: Long = AURORA_ENTRY_HOLD_REFRESH_DELAY_MS,
    content: @Composable () -> Unit,
) {
    val pullRefreshState = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    var hasTriggeredForCurrentPull by remember { mutableStateOf(false) }

    fun triggerRefresh(playHapticFeedback: Boolean) {
        if (!shouldTriggerAuroraEntryRefresh(
                refreshing = refreshing,
                hasTriggeredForCurrentPull = hasTriggeredForCurrentPull,
            )
        ) {
            return
        }

        hasTriggeredForCurrentPull = true
        if (playHapticFeedback) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onRefresh()
    }

    LaunchedEffect(
        enabled,
        refreshing,
        pullRefreshState.distanceFraction,
        hasTriggeredForCurrentPull,
        holdDelayMillis,
    ) {
        if (!enabled) return@LaunchedEffect

        if (shouldResetAuroraEntryHoldRefreshLatch(pullRefreshState.distanceFraction)) {
            hasTriggeredForCurrentPull = false
            return@LaunchedEffect
        }

        if (!shouldStartAuroraEntryHoldRefresh(
                distanceFraction = pullRefreshState.distanceFraction,
                refreshing = refreshing,
                hasTriggeredForCurrentPull = hasTriggeredForCurrentPull,
            )
        ) {
            return@LaunchedEffect
        }

        delay(holdDelayMillis)

        if (!shouldStartAuroraEntryHoldRefresh(
                distanceFraction = pullRefreshState.distanceFraction,
                refreshing = refreshing,
                hasTriggeredForCurrentPull = hasTriggeredForCurrentPull,
            )
        ) {
            return@LaunchedEffect
        }

        triggerRefresh(playHapticFeedback = true)
    }

    val colors = AuroraTheme.colors

    Box(
        modifier = modifier.pullToRefresh(
            state = pullRefreshState,
            isRefreshing = refreshing,
            enabled = enabled,
            onRefresh = { triggerRefresh(playHapticFeedback = false) },
        ),
    ) {
        content()

        PullToRefreshDefaults.Indicator(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(indicatorPadding),
            isRefreshing = refreshing,
            state = pullRefreshState,
            containerColor = colors.surface.copy(alpha = 0.88f),
            color = colors.accent,
        )
    }
}
