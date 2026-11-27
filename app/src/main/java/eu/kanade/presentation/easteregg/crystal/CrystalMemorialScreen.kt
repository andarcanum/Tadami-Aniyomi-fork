package eu.kanade.presentation.easteregg.crystal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.easteregg.crystal.CrystalAtmosphereModel
import eu.kanade.domain.easteregg.crystal.CrystalMemorialManager
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class CrystalMemorialScreen(
    private val replay: Boolean = false,
    private val portrait: CrystalPortrait = CrystalPortrait.Warm,
    private val front: CrystalDissolveStyle = CrystalDissolveStyle.Layers,
) : Screen() {
    @Composable
    override fun Content() {
        val manager = remember { Injekt.get<CrystalMemorialManager>() }
        val model = rememberScreenModel { CrystalMemorialScreenModel(manager, replay) }
        val ui by model.state.collectAsStateWithLifecycle()
        val appearance = rememberCrystalAppearance()
        val navigator = LocalNavigator.currentOrThrow
        val assets = LocalContext.current.applicationContext.assets
        val view = LocalView.current
        val appColors = AuroraTheme.colors
        var ceremony by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(assets, appearance.palette, appColors.isEInk, appColors.isDark, appColors.isAmoled) {
            runCatching {
                withContext(Dispatchers.Default) {
                    // Pre-parse jewel SVG paths for the 1D ceremony AND pre-render the 462-shape Living Corner Bitmap
                    // off the UI thread so neither ceremony open nor Lunar Iris portal ever blocks a frame.
                    CrystalJewelPainter.prewarmStaticPaths()
                    val seed = manager.appearance.getOrCreateAtmosphereSeed()
                    val moment = currentCrystalCalendarMoment()
                    val phase = CrystalAtmosphereModel.phaseAt(moment)
                    val weather = CrystalAtmosphereModel.weatherAt(moment, seed)
                    val sceneColors = resolveCrystalLivingColors(
                        phase = phase,
                        weather = weather,
                        palette = appearance.palette,
                        eInk = appColors.isEInk,
                        isDark = appColors.isDark,
                        isAmoled = appColors.isAmoled,
                        colorEInk = appColors.eInkProfile == EInkProfile.COLOR,
                    )
                    CrystalLivingCornerPainter.loadCachedHero(assets, initialColors = sceneColors)
                    CrystalLivingCornerPainter.prewarmHeroSnapshot(
                        assets = assets,
                        widthPx = view.width.takeIf { it > 0 } ?: 1080,
                        colors = sceneColors,
                        phase = phase,
                        weather = weather,
                        palette = appearance.palette,
                        seedHash = seed.hashCode(),
                    )
                }
            }
        }
        val goBack = {
            if (navigator.lastItem.key == key) navigator.pop()
            Unit
        }
        val goTreasury = {
            if (navigator.lastItem.key == key) {
                CrystalPortalTransition.arm()
                navigator.openCrystalCollection()
            }
        }
        LaunchedEffect(ui.loaded, ui.allowed, ui.stale) {
            if (ui.stale || (ui.loaded && !ui.allowed && !ui.loadFailed)) {
                goBack()
            } else if (ui.loaded && ui.allowed) {
                model.onVisible()
            }
        }
        LaunchedEffect(ui.resumeCeremony) { if (ui.resumeCeremony) ceremony = true }
        CrystalArtTheme {
            CrystalSystemBarAppearance()
            val colors = AuroraTheme.colors
            val labels = crystalSceneLabels()
            if (!ui.loaded || ui.loadFailed) {
                Box(Modifier.fillMaxSize().background(colors.background)) {
                    if (ui.loadFailed) {
                        TextButton(goBack, Modifier.statusBarsPadding().padding(12.dp)) {
                            Text(labels.back, color = colors.textPrimary)
                        }
                        TextButton(model::reload, Modifier.align(Alignment.Center)) {
                            Text(labels.retry, color = colors.textPrimary)
                        }
                    }
                }
            } else if (ui.allowed && !ui.stale) {
                val showCeremony = ceremony && ui.granted
                val dismissCeremony = {
                    val resume = ui.resumeCeremony
                    model.acknowledgeCeremony {
                        ceremony = false
                        if (resume) goBack()
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    CrystalMemorialScene(
                        labels,
                        onBack = { if (showCeremony) dismissCeremony() else goBack() },
                        onFinale = model::complete,
                        onContinue = { ceremony = true },
                        onRetryGrant = model::complete,
                        receiptReady = ui.granted,
                        receiptFailed = ui.grantFailed,
                        portrait = portrait,
                        front = front,
                        reduceMotion = appearance.reduced,
                        soundEnabled = appearance.sound,
                        onSoundChanged = { manager.appearance.soundEnabled().set(it) },
                        previewTimeMs = if (ui.resumeCeremony) {
                            CrystalTimeline.total(
                                appearance.reduced || colors.isEInk,
                            )
                        } else {
                            null
                        },
                        ceremonyOpen = showCeremony,
                    )
                    AnimatedVisibility(
                        visible = showCeremony,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(if (appearance.reduced || colors.isEInk) 0 else 360)),
                    ) {
                        CrystalRewardCeremony(
                            firstGrant = ui.firstGrant,
                            reduced = appearance.reduced,
                            onDismiss = dismissCeremony,
                            onTreasury = {
                                model.acknowledgeCeremony {
                                    goTreasury()
                                }
                            },
                            palette = appearance.palette,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun crystalSceneLabels() = CrystalSceneLabels(
    back = stringResource(AYMR.strings.crystal_back), title = stringResource(AYMR.strings.crystal_memorial_title),
    start = stringResource(AYMR.strings.crystal_start), continueLabel = stringResource(AYMR.strings.crystal_continue),
    recognizes = stringResource(
        AYMR.strings.crystal_light_recognizes_you,
    ),
    dedication = stringResource(AYMR.strings.crystal_dedication),
    moveHint = stringResource(AYMR.strings.crystal_move_hint), soundOn = stringResource(AYMR.strings.crystal_sound_on),
    soundOff = stringResource(AYMR.strings.crystal_sound_off), loading = stringResource(AYMR.strings.crystal_loading),
    retry = stringResource(AYMR.strings.crystal_retry), loadFailed = stringResource(AYMR.strings.crystal_load_failed),
    saving = stringResource(AYMR.strings.crystal_saving), saveFailed = stringResource(AYMR.strings.crystal_save_failed),
    startAction = stringResource(AYMR.strings.crystal_release_light),
)

@Composable
private fun CrystalSystemBarAppearance() {
    val view = LocalView.current
    val dark = AuroraTheme.colors.isDark
    val activity = remember(view) { findCrystalActivity(view.context) }
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

private tailrec fun findCrystalActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> findCrystalActivity(context.baseContext)
    else -> null
}
