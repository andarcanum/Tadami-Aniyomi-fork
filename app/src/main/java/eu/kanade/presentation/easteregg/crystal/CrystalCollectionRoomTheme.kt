package eu.kanade.presentation.easteregg.crystal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.domain.easteregg.crystal.CrystalAppearancePreferences
import eu.kanade.domain.easteregg.crystal.CrystalAtmosphereModel
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalAuroraColors
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** A copy installed only around this destination; the app's selected theme is never changed. */
internal fun crystalCollectionRoomColors(
    current: AuroraColors,
    scene: CrystalLivingCornerColors,
): AuroraColors = current.copy(
    accent = if (current.isEInk) Color(scene.heading) else Color(scene.palette.gold),
    accentVariant = if (current.isEInk) Color(scene.heading) else Color(scene.palette.rose),
    background = Color(scene.floor),
    surface = Color(scene.wall),
    gradientStart = Color(scene.wall),
    gradientEnd = Color(scene.floor),
    glass = Color(scene.wall).copy(alpha = if (current.isEInk) 1f else .9f),
    cardBackground = Color(scene.floor),
    textPrimary = Color(scene.heading),
    textSecondary = Color(scene.muted),
    textOnAccent = if (current.isEInk) Color(scene.wall) else Color(0xFF263842),
    divider = Color(scene.trim).copy(alpha = if (current.isEInk) 1f else .35f),
    isDark = if (current.isEInk) current.isDark else true,
)

@Composable
internal fun CrystalCollectionRoomTheme(content: @Composable (CrystalLivingCornerColors) -> Unit) {
    val current = AuroraTheme.colors
    val appearance = rememberCrystalAppearance()
    val preferences = remember { Injekt.get<CrystalAppearancePreferences>() }
    val seed = remember(preferences, appearance.receipt.isEligible, appearance.receipt.isGranted) {
        if (appearance.receipt.isEligible || appearance.receipt.isGranted) {
            preferences.getOrCreateAtmosphereSeed()
        } else {
            preferences.atmosphereSeed().get().ifEmpty { "crystal-unopened" }
        }
    }
    val policy = rememberCrystalMotionPolicy()
    val focused by rememberLivingWindowFocus()
    val navigator = LocalNavigator.current
    val moment by rememberLivingMoment(
        policy.resumed && focused && navigator?.lastItem == CrystalCollectionScreen,
    )
    val phase = CrystalAtmosphereModel.phaseAt(moment)
    val weather = CrystalAtmosphereModel.weatherAt(moment, seed)
    val scene = remember(phase, weather, appearance.palette, current) {
        resolveCrystalLivingColors(
            phase,
            weather,
            appearance.palette,
            current.isEInk,
            current.isDark,
            current.isAmoled,
            current.eInkProfile == EInkProfile.COLOR,
        )
    }
    val room = remember(current, scene) { crystalCollectionRoomColors(current, scene) }
    val scheme = MaterialTheme.colorScheme.copy(
        primary = room.accent,
        onPrimary = room.textOnAccent,
        primaryContainer = room.surface,
        onPrimaryContainer = room.textPrimary,
        background = room.background,
        onBackground = room.textPrimary,
        surface = room.surface,
        onSurface = room.textPrimary,
        surfaceVariant = room.cardBackground,
        onSurfaceVariant = room.textSecondary,
        outline = room.divider,
    )
    CompositionLocalProvider(LocalAuroraColors provides room) {
        CrystalCollectionRoomSystemBars(room.isDark)
        MaterialTheme(colorScheme = scheme) { content(scene) }
    }
}

@Composable
private fun CrystalCollectionRoomSystemBars(dark: Boolean) {
    val view = LocalView.current
    val activity = remember(view) { crystalCollectionActivity(view.context) }
    DisposableEffect(activity, dark) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {
            if (status != null) controller?.isAppearanceLightStatusBars = status
            if (navigation != null) controller?.isAppearanceLightNavigationBars = navigation
        }
    }
}

private tailrec fun crystalCollectionActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> crystalCollectionActivity(context.baseContext)
    else -> null
}
