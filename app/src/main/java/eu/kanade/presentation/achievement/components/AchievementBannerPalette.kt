package eu.kanade.presentation.achievement.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

internal fun mutedUnlockBannerGradient(
    accent: Color,
    surface: Color,
): List<Color> {
    val softenedSurface = accent.copy(alpha = 0.16f).compositeOver(surface)
    return listOf(
        accent.copy(alpha = 0.54f).compositeOver(softenedSurface),
        accent.copy(alpha = 0.62f).compositeOver(surface.copy(alpha = 0.92f)),
        accent.copy(alpha = 0.5f).compositeOver(Color.Black.copy(alpha = 0.18f)),
    )
}

internal fun mutedGroupBannerGradient(
    accent: Color,
    secondary: Color,
    surface: Color,
): List<Color> {
    val softenedSurface = accent.copy(alpha = 0.2f).compositeOver(surface)
    return listOf(
        accent.copy(alpha = 0.56f).compositeOver(softenedSurface),
        secondary.copy(alpha = 0.5f).compositeOver(surface.copy(alpha = 0.94f)),
        accent.copy(alpha = 0.48f).compositeOver(Color.Black.copy(alpha = 0.24f)),
    )
}
