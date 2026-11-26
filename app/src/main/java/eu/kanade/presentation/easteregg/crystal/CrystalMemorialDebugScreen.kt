package eu.kanade.presentation.easteregg.crystal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.tadami.aurora.BuildConfig
import eu.kanade.domain.easteregg.crystal.CrystalCampaignController
import eu.kanade.domain.easteregg.crystal.CrystalCampaignEvaluation
import eu.kanade.domain.easteregg.crystal.CrystalMemorialManager
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.easteregg.crystal.CrystalMedia
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState
import tachiyomi.domain.easteregg.crystal.CrystalReleasePolicy
import tachiyomi.domain.easteregg.crystal.CrystalTitleInput
import tachiyomi.domain.easteregg.crystal.CrystalTitleMatcher
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class CrystalMemorialDebugScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        if (!BuildConfig.DEBUG || BuildConfig.BUILD_TYPE != "debug") {
            LaunchedEffect(Unit) { if (navigator.lastItem.key == key) navigator.pop() }
            return
        }
        val manager = remember { Injekt.get<CrystalMemorialManager>() }
        val campaign by produceState<CrystalCampaignController?>(null) {
            try {
                value = withContext(Dispatchers.IO) { Injekt.get<CrystalCampaignController>() }
            } catch (
                e: CancellationException,
            ) {
                throw e
            } catch (_: Exception) { }
        }
        val scanConditions = rememberCrystalCampaignConditions()
        val latestScanConditions by rememberUpdatedState(scanConditions)
        var scan by remember { mutableStateOf<CrystalCampaignEvaluation?>(null) }
        var scanFailed by remember { mutableStateOf(false) }
        val appearance = rememberCrystalAppearance()
        val scope = rememberCoroutineScope()
        val colors = AuroraTheme.colors
        var busy by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var portrait by rememberSaveable { mutableStateOf(CrystalPortrait.Warm) }
        var front by rememberSaveable { mutableStateOf(CrystalDissolveStyle.Layers) }
        var showPreview by rememberSaveable { mutableStateOf(false) }
        var fraction by rememberSaveable { mutableFloatStateOf(0f) }
        var playing by remember { mutableStateOf(false) }
        var playStart by remember { mutableFloatStateOf(0f) }
        var playbackKey by remember { mutableStateOf(0) }
        val time = rememberCrystalTime(showPreview && playing, playbackKey)
        val total = CrystalTimeline.total(appearance.reduced || colors.isEInk)
        val shownFraction = if (playing) ((playStart + time.value) % total) / total else fraction
        var title by rememberSaveable { mutableStateOf("") }
        var media by rememberSaveable { mutableStateOf(CrystalMedia.ANIME) }
        val matcher = remember { CrystalTitleMatcher() }
        val match = remember(title, media) { matcher.match(CrystalTitleInput(media, title)) }
        var scenario by rememberSaveable { mutableStateOf(0) }
        val simulated = if (scenario ==
            2
        ) {
            CrystalMemorialState(grantedAt = 1, grantedXp = 999)
        } else {
            CrystalMemorialState()
        }
        val decision = CrystalReleasePolicy.evaluate(if (scenario == 0) 100 else 101, true, setOf(100), simulated)
        Column(
            Modifier.fillMaxSize().background(colors.background).padding(WindowInsets.safeDrawing.asPaddingValues()),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { navigator.pop() }) { Text(stringResource(AYMR.strings.crystal_back)) }
                Text(
                    stringResource(AYMR.strings.crystal_debug_title),
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = {
                    if (!busy) {
                        busy = true
                        failed = false
                        scope.launch {
                            try {
                                manager.resetAndPrepareDebug()
                                CrystalDebugReleaseSimulation.trigger(portrait, front)
                                navigator.popUntilRoot()
                            } catch (
                                e: CancellationException,
                            ) {
                                throw e
                            } catch (_: Exception) {
                                failed = true
                            } finally {
                                busy = false
                            }
                        }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(AYMR.strings.crystal_debug_simulate_release))
                }
                Button(onClick = {
                    if (!busy) {
                        busy = true
                        failed = false
                        scope.launch {
                            try {
                                manager.resetAndPrepareDebug()
                                if (navigator.lastItem.key ==
                                    key
                                ) {
                                    navigator.push(CrystalMemorialScreen(false, portrait, front))
                                }
                            } catch (
                                e: CancellationException,
                            ) {
                                throw e
                            } catch (_: Exception) {
                                failed = true
                            } finally {
                                busy =
                                    false
                            }
                        }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(AYMR.strings.crystal_debug_reset))
                }
                TextButton(
                    onClick = { navigator.push(CrystalMemorialScreen(true, portrait, front)) },
                    enabled = appearance.receipt.isGranted && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(AYMR.strings.crystal_debug_replay))
                }
                if (failed) Text(stringResource(AYMR.strings.crystal_debug_reset_failed), color = colors.error)
                Text(
                    "generation=" + appearance.receipt.generation + " · eligible=" +
                        appearance.receipt.eligibilityReason +
                        " · granted=" + appearance.receipt.grantedXp + " · ceremony=" +
                        appearance.receipt.hasPendingCeremony,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
                CrystalDebugToggle(stringResource(AYMR.strings.crystal_reduce_motion), appearance.reduced) {
                    manager.appearance.reduceMotion().set(it)
                }
                CrystalDebugToggle(stringResource(AYMR.strings.crystal_sound_on), appearance.sound) {
                    manager.appearance.soundEnabled().set(it)
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    CrystalPortrait.entries.forEach { choice ->
                        TextButton(onClick = { portrait = choice }) {
                            Text(
                                stringResource(
                                    when (choice) {
                                        CrystalPortrait.Warm -> AYMR.strings.crystal_portrait_warm
                                        CrystalPortrait.Soft -> AYMR.strings.crystal_portrait_soft
                                        CrystalPortrait.Photo -> AYMR.strings.crystal_portrait_photo
                                    },
                                ),
                                fontWeight = if (portrait == choice) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    CrystalDissolveStyle.entries.forEach { choice ->
                        TextButton(onClick = { front = choice }) {
                            Text(
                                stringResource(
                                    when (choice) {
                                        CrystalDissolveStyle.Layers -> AYMR.strings.crystal_front_layers
                                        CrystalDissolveStyle.Heart -> AYMR.strings.crystal_front_heart
                                        CrystalDissolveStyle.Aurora -> AYMR.strings.crystal_front_aurora
                                    },
                                ),
                                fontWeight = if (front == choice) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    showPreview = !showPreview
                    playing = false
                }) { Text(stringResource(AYMR.strings.crystal_debug_preview)) }
                if (showPreview) {
                    Text(
                        ((shownFraction * total / 1000f * 10).toInt() / 10f).toString() +
                            " / " + (total / 1000f) + " s",
                        color = colors.textSecondary,
                    )
                    Slider(value = shownFraction, onValueChange = {
                        playing = false
                        fraction = it
                    })
                    TextButton(onClick = {
                        if (playing) {
                            fraction = shownFraction
                            playing = false
                        } else {
                            playStart = fraction * total
                            playbackKey++
                            playing = true
                        }
                    }) {
                        Text(
                            stringResource(
                                if (playing) AYMR.strings.crystal_debug_pause else AYMR.strings.crystal_debug_resume,
                            ),
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(
                            404f / 860f,
                        ).clip(RoundedCornerShape(28.dp)).border(1.dp, colors.divider, RoundedCornerShape(28.dp)),
                    ) {
                        CrystalArtTheme {
                            CrystalMemorialScene(
                                crystalSceneLabels(), {
                                    showPreview = false
                                    playing = false
                                }, {}, {}, {}, true, false,
                                portrait = portrait, front = front, reduceMotion = appearance.reduced,
                                soundEnabled = appearance.sound, onSoundChanged = {
                                    manager.appearance.soundEnabled().set(it)
                                },
                                previewTimeMs =
                                shownFraction * total,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val current = campaign
                        if (!busy && current != null) {
                            busy = true
                            scanFailed = false
                            scope.launch {
                                try {
                                    scan =
                                        current.checkSavedProgressForDebug(
                                            latestScanConditions.withLivePrivacyAndRestore(),
                                        ) {
                                            latestScanConditions.withLivePrivacyAndRestore()
                                        }
                                } catch (
                                    e: CancellationException,
                                ) {
                                    throw e
                                } catch (_: Exception) {
                                    scanFailed = true
                                } finally {
                                    busy =
                                        false
                                }
                            }
                        }
                    },
                    enabled = !busy && campaign != null && scanConditions.canEvaluate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(AYMR.strings.crystal_debug_scan_progress))
                }
                if (!scanConditions.canEvaluate) {
                    Text(
                        stringResource(AYMR.strings.crystal_debug_scan_waiting),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                if (scanFailed) {
                    Text(
                        stringResource(AYMR.strings.crystal_debug_scan_failed),
                        color = colors.error,
                        fontSize = 12.sp,
                    )
                }
                scan?.let { result ->
                    val progress = result.progress
                    if (progress == null ||
                        progress.entries.isEmpty()
                    ) {
                        Text(
                            stringResource(AYMR.strings.crystal_debug_scan_empty),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    progress?.entries?.forEach { entry ->
                        val adaptation = entry.titleMatch.adaptation
                        Text(
                            (adaptation?.work?.name ?: entry.titleMatch.reason.name) + " · " +
                                (adaptation?.media?.name ?: "—") +
                                " · " + entry.distinctCompleted + "/" + entry.requiredCount,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    if (progress?.isEligible == true && result.state.isEligible) {
                        Text(
                            stringResource(AYMR.strings.crystal_debug_scan_ready),
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                        )
                        TextButton(
                            onClick = {
                                navigator.push(CrystalMemorialScreen(replay = appearance.receipt.isGranted))
                            },
                            enabled = !busy && appearance.receipt.isEligible,
                        ) {
                            Text(stringResource(AYMR.strings.crystal_gift_open))
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                        AYMR.strings.crystal_debug_release,
                        AYMR.strings.crystal_debug_skipped,
                        AYMR.strings.crystal_debug_claimed,
                    ).forEachIndexed {
                            index,
                            label,
                        ->
                        TextButton(onClick = { scenario = index }) {
                            Text(
                                stringResource(label),
                                fontWeight = if (scenario ==
                                    index
                                ) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        }
                    }
                }
                Text(
                    "releaseEligibility=" + decision.shouldMakeReleaseEligible + " · titleRoute=" +
                        decision.mayUseTitleUnlock +
                        " · offer=" +
                        decision.shouldOfferPending,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
                OutlinedTextField(title, {
                    title = it
                }, Modifier.fillMaxWidth(), label = {
                    Text(stringResource(AYMR.strings.crystal_debug_title_input))
                }, singleLine = true)
                Row {
                    CrystalMedia.entries.forEach { item ->
                        TextButton(onClick = { media = item }) {
                            Text(
                                item.name,
                                fontWeight = if (media ==
                                    item
                                ) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        }
                    }
                }
                Text(
                    match.reason.name + " · " + (match.adaptation?.work?.name ?: "—"),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun CrystalDebugToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = AuroraTheme.colors.textPrimary)
        Switch(checked, onChange)
    }
}
