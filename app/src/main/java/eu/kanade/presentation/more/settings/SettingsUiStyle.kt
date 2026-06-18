package eu.kanade.presentation.more.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class SettingsUiStyle {
    Classic,
    Aurora,
}

enum class SettingsPaneContext {
    SinglePane,
    TwoPanePrimary,
    TwoPaneSecondary,
}

val LocalSettingsPaneContext = compositionLocalOf { SettingsPaneContext.SinglePane }
val LocalSettingsUiStyle = compositionLocalOf { SettingsUiStyle.Classic }

internal const val AURORA_SETTINGS_DIALOG_ALPHA = 0.92f
internal const val AURORA_SETTINGS_SELECTION_BACKGROUND_ALPHA = 0.25f
internal const val AURORA_SETTINGS_SELECTION_BORDER_ALPHA = 0.42f
internal val AURORA_SETTINGS_CARD_CORNER_RADIUS = 16.dp
internal val AURORA_SETTINGS_CARD_VERTICAL_PADDING = 6.dp
internal val AURORA_SETTINGS_CARD_HORIZONTAL_INSET = 16.dp
internal val AURORA_SETTINGS_CARD_SHAPE: Shape = RoundedCornerShape(AURORA_SETTINGS_CARD_CORNER_RADIUS)

fun resolveSettingsUiStyle(
    isAuroraTheme: Boolean,
    paneContext: SettingsPaneContext,
): SettingsUiStyle {
    return if (isAuroraTheme) {
        when (paneContext) {
            SettingsPaneContext.SinglePane,
            SettingsPaneContext.TwoPanePrimary,
            SettingsPaneContext.TwoPaneSecondary,
            -> SettingsUiStyle.Aurora
        }
    } else {
        SettingsUiStyle.Classic
    }
}

internal fun resolveAuroraDialogContainerColor(surface: Color): Color {
    return surface.copy(alpha = AURORA_SETTINGS_DIALOG_ALPHA)
}

internal fun resolveAuroraSelectionBackgroundColor(accent: Color): Color {
    return accent.copy(alpha = AURORA_SETTINGS_SELECTION_BACKGROUND_ALPHA)
}

internal fun resolveAuroraSelectionBorderColor(accent: Color): Color {
    return accent.copy(alpha = AURORA_SETTINGS_SELECTION_BORDER_ALPHA)
}

@Composable
fun Modifier.auroraCardStyle(
    colors: AuroraColors = AuroraTheme.colors,
    shape: Shape = AURORA_SETTINGS_CARD_SHAPE,
    applyShadow: Boolean = false,
    applyModifierBackgroundInDark: Boolean = false,
    applyDarkRimLight: Boolean = true,
): Modifier {
    return if (!colors.isDark && !colors.isEInk) {
        this
            .drawBehind {
                val radius = AURORA_SETTINGS_CARD_CORNER_RADIUS.toPx()
                val cornerRadius = CornerRadius(radius, radius)

                val neutralOffsetY = 3.dp.toPx()
                val warmOffsetY = 5.dp.toPx()

                val neutralInset = 1.dp.toPx()
                val warmInset = 3.dp.toPx()

                // Очень мягкая нейтральная подложка снизу.
                // Это НЕ elevation/shadow, а обычная отрисовка за карточкой.
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.035f),
                    topLeft = Offset(
                        x = neutralInset,
                        y = neutralOffsetY,
                    ),
                    size = Size(
                        width = size.width - neutralInset * 2,
                        height = size.height,
                    ),
                    cornerRadius = cornerRadius,
                )

                // Тёплый ореол под цвет темы.
                // На бежевом фоне выглядит мягче, чем серая тень.
                drawRoundRect(
                    color = colors.accent.copy(alpha = 0.025f),
                    topLeft = Offset(
                        x = warmInset,
                        y = warmOffsetY,
                    ),
                    size = Size(
                        width = size.width - warmInset * 2,
                        height = size.height,
                    ),
                    cornerRadius = cornerRadius,
                )
            }
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.78f),
                        Color.White.copy(alpha = 0.68f),
                        Color.White.copy(alpha = 0.60f),
                    ),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.75f),
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.12f),
                    ),
                ),
                shape = shape,
            )
    } else if (colors.isDark && !colors.isEInk) {
        val baseModifier = if (applyModifierBackgroundInDark) {
            this.background(
                color = resolveAuroraMoreCardContainerColor(colors),
                shape = shape,
            )
        } else {
            this
        }
        if (applyDarkRimLight) {
            val borderBrush = auroraMenuRimLightBrush(colors)
            baseModifier.border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape,
            )
        } else {
            baseModifier
        }
    } else if (applyModifierBackgroundInDark) {
        this.background(
            color = resolveAuroraMoreCardContainerColor(colors),
            shape = shape,
        )
    } else {
        this
    }
}

internal fun resolveAuroraCardBorderColor(
    colors: AuroraColors,
): Color {
    return when (colors.eInkProfile) {
        EInkProfile.MONOCHROME -> if (colors.isDark) {
            Color(0xFF404040)
        } else {
            Color(0xFFBDBDBD)
        }
        EInkProfile.COLOR -> colors.divider
        EInkProfile.OFF -> if (colors.isDark) {
            Color.Transparent
        } else {
            Color(0xFFD7E3F1)
        }
    }
}

@Composable
fun rememberResolvedSettingsUiStyle(
    paneContext: SettingsPaneContext = LocalSettingsPaneContext.current,
): SettingsUiStyle {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val theme by uiPreferences.appTheme().collectAsState()
    return remember(theme.isAuroraStyle, paneContext) {
        resolveSettingsUiStyle(
            isAuroraTheme = theme.isAuroraStyle,
            paneContext = paneContext,
        )
    }
}

@Composable
fun settingsCardContainerColor(): Color {
    return if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        resolveAuroraMoreCardContainerColor(AuroraTheme.colors)
    } else {
        Color.Transparent
    }
}

@Composable
fun settingsCardBorderColor(): Color {
    return if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        resolveAuroraCardBorderColor(AuroraTheme.colors)
    } else {
        Color.Transparent
    }
}

@Composable
fun settingsTitleColor(): Color {
    return if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        AuroraTheme.colors.textPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
}

@Composable
fun settingsSubtitleColor(): Color {
    return if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        AuroraTheme.colors.textSecondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun settingsAccentColor(): Color {
    return if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        AuroraTheme.colors.accent
    } else {
        MaterialTheme.colorScheme.primary
    }
}

@Composable
fun settingsDialogContainerColor(): Color {
    return if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        resolveAuroraDialogContainerColor(AuroraTheme.colors.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }
}

@Composable
fun settingsSelectionBackgroundColor(): Color {
    return if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        resolveAuroraSelectionBackgroundColor(AuroraTheme.colors.accent)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
fun settingsSelectionBorderColor(): Color {
    return if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        resolveAuroraSelectionBorderColor(AuroraTheme.colors.accent)
    } else {
        Color.Transparent
    }
}
