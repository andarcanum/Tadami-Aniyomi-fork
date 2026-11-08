package eu.kanade.presentation.easteregg.crystal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.easteregg.crystal.CrystalMemorialManager
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Screen
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
        var ceremony by rememberSaveable { mutableStateOf(false) }
        val goBack = {
            if (navigator.lastItem.key == key) navigator.pop()
            Unit
        }
        val goTreasury = {
            if (navigator.lastItem.key == key) {
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
                    TextButton(goBack, Modifier.statusBarsPadding().padding(12.dp)) {
                        Text(labels.back, color = colors.textPrimary)
                    }
                    if (ui.loadFailed) {
                        TextButton(model::reload, Modifier.align(Alignment.Center)) {
                            Text(labels.retry, color = colors.textPrimary)
                        }
                    } else {
                        Text(labels.loading, Modifier.align(Alignment.Center), color = colors.textSecondary)
                    }
                }
            } else if (ui.allowed && !ui.stale) {
                CrystalMemorialScene(
                    labels, goBack, model::complete, { ceremony = true }, model::complete,
                    receiptReady = ui.granted, receiptFailed = ui.grantFailed, portrait = portrait, front = front,
                    reduceMotion = appearance.reduced, soundEnabled = appearance.sound,
                    onSoundChanged = { manager.appearance.soundEnabled().set(it) },
                    previewTimeMs = if (ui.resumeCeremony) {
                        CrystalTimeline.total(
                            appearance.reduced || colors.isEInk,
                        )
                    } else {
                        null
                    },
                )
            }
            if (ceremony && ui.granted) {
                CrystalRewardCeremony(
                    ui.firstGrant,
                    appearance.reduced,
                    onDismiss = {
                        val resume = ui.resumeCeremony
                        model.acknowledgeCeremony {
                            ceremony = false
                            if (resume) goBack()
                        }
                    },
                    onTreasury = {
                        model.acknowledgeCeremony {
                            ceremony = false
                            goTreasury()
                        }
                    },
                )
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
