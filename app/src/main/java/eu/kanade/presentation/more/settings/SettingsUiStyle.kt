package eu.kanade.presentation.more.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
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
internal val AURORA_SETTINGS_CARD_SHAPE: Shape = RoundedCornerShape(20.dp)

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
        AuroraTheme.colors.glass
    } else {
        Color.Transparent
    }
}

@Composable
fun settingsCardBorderColor(): Color {
    return if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        AuroraTheme.colors.accent.copy(alpha = 0.16f)
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
