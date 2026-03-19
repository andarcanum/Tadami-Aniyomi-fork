package eu.kanade.presentation.more.stats.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

internal data class StatsAuroraStatItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

internal data class StatsAuroraProgressData(
    val fraction: Float,
    val color: Color,
)
