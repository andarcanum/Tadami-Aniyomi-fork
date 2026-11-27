package eu.kanade.presentation.reader.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressHudLogic

/**
 * Ultra-thin ambient reading-progress hairline anchored to the bottom edge of the reader viewport
 * when menus are hidden. Mirrors fill direction automatically in RTL manga mode and eliminates
 * animated transitions on E-Ink panels.
 */
@Composable
fun ReaderProgressHairline(
    currentPage: Int,
    totalPages: Int,
    isRtl: Boolean,
    sliderColor: Int = 0,
    modifier: Modifier = Modifier,
) {
    if (currentPage <= 0 || totalPages <= 0) return

    val aurora = AuroraTheme.colors
    val targetFraction = ReaderProgressHudLogic.computeProgressFraction(
        currentPage = currentPage,
        totalPages = totalPages,
    )
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (aurora.isEInk) snap() else tween(durationMillis = 180),
        label = "readerProgressHairline",
    )

    val baseColor = when {
        aurora.isEInk -> aurora.textPrimary
        sliderColor != 0 -> Color(sliderColor)
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = if (aurora.isEInk) {
        aurora.textSecondary.copy(alpha = 0.25f)
    } else {
        baseColor.copy(alpha = 0.16f)
    }
    val fillColor = if (aurora.isEInk) {
        baseColor
    } else {
        baseColor.copy(alpha = 0.78f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .align(if (isRtl) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(animatedFraction)
                .fillMaxHeight()
                .background(fillColor),
        )
    }
}
