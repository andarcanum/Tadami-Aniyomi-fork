package eu.kanade.presentation.theme

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.domain.ui.model.EInkThemeMode
import eu.kanade.presentation.easteregg.aurora.AuroraPrimeColors
import eu.kanade.presentation.easteregg.aurora.rememberAuroraPrimeColors
import eu.kanade.presentation.theme.colorscheme.AuroraColorScheme
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import eu.kanade.presentation.theme.colorscheme.CloudflareColorScheme
import eu.kanade.presentation.theme.colorscheme.CottoncandyColorScheme
import eu.kanade.presentation.theme.colorscheme.DoomColorScheme
import eu.kanade.presentation.theme.colorscheme.EventHorizonColorScheme
import eu.kanade.presentation.theme.colorscheme.GreenAppleColorScheme
import eu.kanade.presentation.theme.colorscheme.LavenderColorScheme
import eu.kanade.presentation.theme.colorscheme.MatrixColorScheme
import eu.kanade.presentation.theme.colorscheme.MidnightDuskColorScheme
import eu.kanade.presentation.theme.colorscheme.MochaColorScheme
import eu.kanade.presentation.theme.colorscheme.MonetColorScheme
import eu.kanade.presentation.theme.colorscheme.MonochromeColorScheme
import eu.kanade.presentation.theme.colorscheme.NebulaTideColorScheme
import eu.kanade.presentation.theme.colorscheme.NordColorScheme
import eu.kanade.presentation.theme.colorscheme.OnyxGoldColorScheme
import eu.kanade.presentation.theme.colorscheme.SakuraNoirColorScheme
import eu.kanade.presentation.theme.colorscheme.SapphireColorScheme
import eu.kanade.presentation.theme.colorscheme.StrawberryColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import eu.kanade.presentation.theme.colorscheme.TakoColorScheme
import eu.kanade.presentation.theme.colorscheme.TealTurqoiseColorScheme
import eu.kanade.presentation.theme.colorscheme.TidalWaveColorScheme
import eu.kanade.presentation.theme.colorscheme.VoidRedColorScheme
import eu.kanade.presentation.theme.colorscheme.YinYangColorScheme
import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val appUiFontId = uiPreferences.appUiFontId().get()
    val coverTitleFontId = uiPreferences.coverTitleFontId().get()
    val eInkProfile = uiPreferences.eInkProfile().collectAsState().value
    val eInkThemeMode = uiPreferences.eInkThemeMode().collectAsState().value
    val isDark = resolveEInkThemeIsDark(
        eInkProfile = eInkProfile,
        eInkThemeMode = eInkThemeMode,
        isSystemDarkTheme = isSystemInDarkTheme(),
    )
    BaseTachiyomiTheme(
        appTheme = appTheme ?: uiPreferences.appTheme().get(),
        isAmoled = amoled ?: uiPreferences.themeDarkAmoled().get(),
        eInkProfile = eInkProfile,
        isDark = isDark,
        appUiFontId = appUiFontId,
        coverTitleFontId = coverTitleFontId,
        content = content,
    )
}

@Composable
fun TachiyomiPreviewTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) = BaseTachiyomiTheme(
    appTheme = appTheme,
    isAmoled = isAmoled,
    eInkProfile = EInkProfile.OFF,
    isDark = isSystemInDarkTheme(),
    appUiFontId = UiPreferences.DEFAULT_APP_UI_FONT_ID,
    coverTitleFontId = UiPreferences.DEFAULT_COVER_TITLE_FONT_ID,
    content = content,
)

@Composable
private fun BaseTachiyomiTheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    eInkProfile: EInkProfile,
    isDark: Boolean,
    appUiFontId: String,
    coverTitleFontId: String,
    content: @Composable () -> Unit,
) {
    val isEInkMode = eInkProfile.isEnabled
    val baseColorScheme = getThemeColorScheme(
        appTheme = appTheme,
        isAmoled = isAmoled,
        eInkProfile = eInkProfile,
        isDark = isDark,
    )
    val colorScheme = if (appTheme == AppTheme.AURORA_PRIME) {
        auroraPrimeOverlay(base = baseColorScheme, isAmoled = isAmoled, isDark = isDark)
    } else {
        baseColorScheme
    }
    val appFontFamily = rememberAppFontFamily(appUiFontId)
    val coverTitleFontFamily = rememberAppFontFamily(coverTitleFontId)
    val typography = remember(appFontFamily) {
        Typography().withDefaultFontFamily(appFontFamily)
    }

    val auroraColors = AuroraColors.fromColorScheme(
        colorScheme = colorScheme,
        isDark = isDark,
        isAmoled = isAmoled,
        eInkProfile = eInkProfile,
    )

    CompositionLocalProvider(
        LocalIsEInkMode provides isEInkMode,
        LocalAuroraColors provides auroraColors,
        LocalIsAuroraTheme provides appTheme.isAuroraStyle,
        LocalIsDefaultAppUiFont provides (appUiFontId == UiPreferences.DEFAULT_APP_UI_FONT_ID),
        LocalCoverTitleFontFamily provides coverTitleFontFamily,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

@Composable
@ReadOnlyComposable
private fun getThemeColorScheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    eInkProfile: EInkProfile,
    isDark: Boolean,
): ColorScheme {
    if (eInkProfile == EInkProfile.MONOCHROME) {
        return MonochromeColorScheme.getColorScheme(
            isDark = isDark,
            isAmoled = false,
        )
    }
    val colorScheme = if (appTheme == AppTheme.MONET) {
        MonetColorScheme(LocalContext.current)
    } else {
        colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)
    }
    return colorScheme.getColorScheme(
        isDark,
        isAmoled,
    )
}

internal fun resolveEInkThemeIsDark(
    eInkProfile: EInkProfile,
    eInkThemeMode: EInkThemeMode,
    isSystemDarkTheme: Boolean,
): Boolean {
    return when {
        !eInkProfile.isEnabled -> isSystemDarkTheme
        eInkThemeMode == EInkThemeMode.LIGHT -> false
        eInkThemeMode == EInkThemeMode.DARK -> true
        else -> isSystemDarkTheme
    }
}

private const val RIPPLE_DRAGGED_ALPHA = .1f
private const val RIPPLE_FOCUSED_ALPHA = .1f
private const val RIPPLE_HOVERED_ALPHA = .1f
private const val RIPPLE_PRESSED_ALPHA = .1f

val playerRippleConfiguration
    @Composable get() = RippleConfiguration(
        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
        rippleAlpha = RippleAlpha(
            draggedAlpha = RIPPLE_DRAGGED_ALPHA,
            focusedAlpha = RIPPLE_FOCUSED_ALPHA,
            hoveredAlpha = RIPPLE_HOVERED_ALPHA,
            pressedAlpha = RIPPLE_PRESSED_ALPHA,
        ),
    )

val LocalIsAuroraTheme = staticCompositionLocalOf { false }
val LocalIsEInkMode = staticCompositionLocalOf { false }
val LocalIsDefaultAppUiFont = staticCompositionLocalOf { true }

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.DEFAULT to AuroraColorScheme,
    AppTheme.CLOUDFLARE to CloudflareColorScheme,
    AppTheme.COTTONCANDY to CottoncandyColorScheme,
    AppTheme.DOOM to DoomColorScheme,
    AppTheme.GREEN_APPLE to GreenAppleColorScheme,
    AppTheme.LAVENDER to LavenderColorScheme,
    AppTheme.MATRIX to MatrixColorScheme,
    AppTheme.MIDNIGHT_DUSK to MidnightDuskColorScheme,
    AppTheme.MONOCHROME to MonochromeColorScheme,
    AppTheme.MOCHA to MochaColorScheme,
    AppTheme.SAPPHIRE to SapphireColorScheme,
    AppTheme.NORD to NordColorScheme,
    AppTheme.STRAWBERRY_DAIQUIRI to StrawberryColorScheme,
    AppTheme.TAKO to TakoColorScheme,
    AppTheme.TEALTURQUOISE to TealTurqoiseColorScheme,
    AppTheme.TIDAL_WAVE to TidalWaveColorScheme,
    AppTheme.YINYANG to YinYangColorScheme,
    AppTheme.YOTSUBA to YotsubaColorScheme,
    AppTheme.AURORA to AuroraColorScheme,
    AppTheme.ONYX_GOLD to OnyxGoldColorScheme,
    AppTheme.SAKURA_NOIR to SakuraNoirColorScheme,
    AppTheme.NEBULA_TIDE to NebulaTideColorScheme,
    AppTheme.EVENT_HORIZON to EventHorizonColorScheme,
    AppTheme.VOID_RED to VoidRedColorScheme,
    AppTheme.AURORA_PRIME to AuroraColorScheme,
)

@Composable
private fun auroraPrimeOverlay(base: ColorScheme, isAmoled: Boolean, isDark: Boolean): ColorScheme {
    val context = LocalContext.current
    val manager = remember { eu.kanade.domain.easteregg.aurora.AuroraHeartManager.get(context) }
    val payload = remember { manager.unlockedPayload() }
    val powerSave = remember {
        (context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true
    }
    val live = rememberAuroraPrimeColors(payload, animated = !powerSave) ?: return base
    return applyAuroraPrimeOverlay(
        base = base,
        live = live,
        isAmoled = isAmoled,
        isDark = isDark,
    )
}

/**
 * AURORA_PRIME overlay: living accents always; night surfaces only in dark mode.
 *
 * Payload themeColors ship dark-only background/surface (night sky). Applying them
 * under light theme mode mixed light surfaceContainer* with forced dark bg and
 * pale on* text — the broken "dark-in-light" look. Light mode keeps base light
 * surfaces and only tints accents/outlines.
 */
internal fun applyAuroraPrimeOverlay(
    base: ColorScheme,
    live: AuroraPrimeColors,
    isAmoled: Boolean,
    isDark: Boolean,
): ColorScheme {
    val withAccents = base.copy(
        primary = live.primary,
        onPrimary = Color.Black,
        primaryContainer = live.primary.copy(alpha = 0.2f),
        onPrimaryContainer = live.primary,
        secondary = live.secondary,
        onSecondary = Color.White,
        secondaryContainer = live.secondary.copy(alpha = 0.2f),
        onSecondaryContainer = live.secondary,
        tertiary = live.accent,
        onTertiary = Color.Black,
        tertiaryContainer = live.accent.copy(alpha = 0.2f),
        onTertiaryContainer = live.accent,
        surfaceTint = live.primary,
        outline = live.primary.copy(alpha = if (isDark) 0.5f else 0.35f),
        outlineVariant = live.primary.copy(alpha = if (isDark) 0.2f else 0.15f),
    )

    if (!isDark) {
        // Light: keep bright surfaces from AuroraColorScheme.lightScheme
        return withAccents
    }

    return withAccents.copy(
        // При AMOLED фон/поверхности оставляем чёрными (так делает BaseColorScheme)
        background = if (isAmoled) base.background else live.background,
        onBackground = Color(0xFFDCEBFF),
        surface = if (isAmoled) base.surface else live.surface,
        onSurface = Color(0xFFDCEBFF),
        surfaceVariant = (if (isAmoled) base.surface else live.surface).copy(alpha = 0.8f),
        onSurfaceVariant = Color(0xCCDCEBFF),
    )
}
