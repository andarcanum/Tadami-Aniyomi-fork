/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.player.components.LeftSideOvalShape
import eu.kanade.presentation.player.components.RightSideOvalShape
import eu.kanade.presentation.theme.playerRippleConfiguration
import eu.kanade.tachiyomi.ui.player.LongPressGesture
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.PlayerUpdates
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.Sheets
import eu.kanade.tachiyomi.ui.player.controls.components.DoubleTapSeekTriangles
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun GestureHandler(
    viewModel: PlayerViewModel,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
    val gesturePreferences = remember { Injekt.get<GesturePreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }
    val longPressAction by gesturePreferences.longPressGesture().collectAsStateWithLifecycle()
    val isDynamicSpeedActive by viewModel.isDynamicSpeedActive.collectAsStateWithLifecycle()

    val panelShown by viewModel.panelShown.collectAsStateWithLifecycle()
    val allowGesturesInPanels by playerPreferences.allowGestures().collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val position by viewModel.pos.collectAsStateWithLifecycle()
    val controlsShown by viewModel.controlsShown.collectAsStateWithLifecycle()
    val areControlsLocked by viewModel.areControlsLocked.collectAsStateWithLifecycle()
    val seekAmount by viewModel.doubleTapSeekAmount.collectAsStateWithLifecycle()
    val isSeekingForwards by viewModel.isSeekingForwards.collectAsStateWithLifecycle()
    var isDoubleTapSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(seekAmount) {
        delay(800)
        isDoubleTapSeeking = false
        viewModel.updateSeekAmount(0)
        viewModel.updateSeekText(null)
        delay(100)
        viewModel.hideSeekBar()
    }

    val gestureVolumeBrightness = gesturePreferences.gestureVolumeBrightness().get()
    val swapVolumeBrightness by gesturePreferences.swapVolumeBrightness().collectAsStateWithLifecycle()
    val seekGesture by gesturePreferences.gestureHorizontalSeek().collectAsStateWithLifecycle()
    val preciseSeeking by gesturePreferences.playerSmoothSeek().collectAsStateWithLifecycle()
    val showSeekbar by gesturePreferences.showSeekBar().collectAsStateWithLifecycle()
    var isLongPressing by remember { mutableStateOf(false) }
    val currentVolume by viewModel.currentVolume.collectAsStateWithLifecycle()
    val currentMPVVolume by viewModel.currentMPVVolume.collectAsStateWithLifecycle()
    val currentBrightness by viewModel.currentBrightness.collectAsStateWithLifecycle()
    val volumeBoostingCap = audioPreferences.volumeBoostCap().get()
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeGestures)
            .pointerInput(longPressAction) {
                if (areControlsLocked || longPressAction != LongPressGesture.PlaybackSpeed) return@pointerInput
                awaitPointerEventScope {
                    var startingX = 0f
                    var hasDragged = false
                    val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val changes = event.changes
                        val downChange = changes.firstOrNull { it.pressed && !it.previousPressed }
                        if (downChange != null) {
                            startingX = downChange.position.x
                            hasDragged = false
                        }
                        if (viewModel.isDynamicSpeedActive.value) {
                            val activeChange = changes.firstOrNull { it.pressed }
                            if (activeChange != null) {
                                val currentX = activeChange.position.x
                                val deltaX = currentX - startingX
                                val presetStep = 40.dp.toPx()
                                val stepsShifted = (deltaX / presetStep).toInt()
                                val baseIndex = 5 // 2.0f is at presets[5]
                                val newIndex = (baseIndex + stepsShifted).coerceIn(0, presets.size - 1)
                                val targetSpeed = presets[newIndex]
                                if (viewModel.gesturePlaybackSpeed.value != targetSpeed) {
                                    hasDragged = true
                                    viewModel.gesturePlaybackSpeed.update { targetSpeed }
                                    MPVLib.setPropertyDouble("speed", targetSpeed.toDouble())
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                activeChange.consume()
                            }
                            val upChange = changes.firstOrNull { !it.pressed && it.previousPressed }
                            if (upChange != null || changes.all { !it.pressed }) {
                                viewModel.isDynamicSpeedActive.update { false }
                                isLongPressing = false
                                viewModel.playerUpdate.update { PlayerUpdates.None }
                                if (hasDragged) {
                                    // User slid to a chosen speed - KEEP IT!
                                } else {
                                    // User just released without sliding - restore original speed!
                                    MPVLib.setPropertyDouble(
                                        "speed",
                                        viewModel.preGesturePlaybackSpeed.value.toDouble(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(longPressAction) {
                val originalSpeed = viewModel.playbackSpeed.value
                detectTapGestures(
                    onTap = {
                        if (controlsShown) viewModel.hideControls() else viewModel.showControls()
                    },
                    onDoubleTap = {
                        if (areControlsLocked || isDoubleTapSeeking) return@detectTapGestures
                        if (it.x > size.width * 3 / 5) {
                            if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                            viewModel.handleRightDoubleTap()
                            isDoubleTapSeeking = true
                        } else if (it.x < size.width * 2 / 5) {
                            if (isSeekingForwards) viewModel.updateSeekAmount(0)
                            viewModel.handleLeftDoubleTap()
                            isDoubleTapSeeking = true
                        } else {
                            viewModel.handleCenterDoubleTap()
                        }
                    },
                    onPress = {
                        if (panelShown != Panels.None && !allowGesturesInPanels) {
                            viewModel.panelShown.update { Panels.None }
                        }
                        val press = PressInteraction.Press(
                            it.copy(x = if (it.x > size.width * 3 / 5) it.x - size.width * 0.6f else it.x),
                        )
                        if (!areControlsLocked && isDoubleTapSeeking && seekAmount != 0) {
                            if (it.x > size.width * 3 / 5) {
                                if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                                viewModel.handleRightDoubleTap()
                            } else if (it.x < size.width * 2 / 5) {
                                if (isSeekingForwards) viewModel.updateSeekAmount(0)
                                viewModel.handleLeftDoubleTap()
                            } else {
                                viewModel.handleCenterDoubleTap()
                            }
                        } else {
                            isDoubleTapSeeking = false
                        }
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        if (isLongPressing) {
                            isLongPressing = false
                            if (longPressAction == LongPressGesture.PlaybackSpeed) {
                                // Handled in outer Initial pointerInput
                            } else {
                                MPVLib.setPropertyDouble("speed", originalSpeed.toDouble())
                            }
                            viewModel.playerUpdate.update { PlayerUpdates.None }
                        }
                        interactionSource.emit(PressInteraction.Release(press))
                    },
                    onLongPress = {
                        if (areControlsLocked) return@detectTapGestures
                        if (!isLongPressing) {
                            if (longPressAction == LongPressGesture.Screenshot) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                isLongPressing = true
                                viewModel.pause()
                                viewModel.sheetShown.update { Sheets.Screenshot }
                            } else if (longPressAction == LongPressGesture.PlaybackSpeed) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                isLongPressing = true
                                viewModel.preGesturePlaybackSpeed.update { viewModel.playbackSpeed.value }
                                viewModel.gesturePlaybackSpeed.update { 2.0f }
                                viewModel.isDynamicSpeedActive.update { true }
                                viewModel.hideControls()
                                MPVLib.setPropertyDouble("speed", 2.0)
                            }
                        }
                    },
                )
            }
            .pointerInput(areControlsLocked, isDynamicSpeedActive) {
                if (!seekGesture || areControlsLocked || isDynamicSpeedActive) return@pointerInput
                var startingPosition = position.toInt()
                var startingX = 0f
                var wasPlayerAlreadyPause = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        startingPosition = position.toInt()
                        startingX = it.x
                        wasPlayerAlreadyPause = viewModel.paused.value
                        viewModel.pause()
                    },
                    onDragEnd = {
                        viewModel.gestureSeekAmount.update { null }
                        viewModel.hideSeekBar()
                        if (!wasPlayerAlreadyPause) viewModel.unpause()
                    },
                ) { change, dragAmount ->
                    if (position <= 0f && dragAmount < 0) return@detectHorizontalDragGestures
                    if (position >= duration && dragAmount > 0) return@detectHorizontalDragGestures
                    calculateNewHorizontalGestureValue(startingPosition, startingX, change.position.x, 0.15f).let {
                        viewModel.gestureSeekAmount.update { _ ->
                            Pair(
                                startingPosition,
                                (it - startingPosition)
                                    .coerceIn(0 - startingPosition, (duration - startingPosition).toInt()),
                            )
                        }
                        viewModel.seekTo(it.coerceIn(0, duration.toInt()), preciseSeeking)
                    }

                    if (showSeekbar) viewModel.showSeekBar()
                }
            }
            .pointerInput(areControlsLocked) {
                if (!gestureVolumeBrightness || areControlsLocked) return@pointerInput
                var startingY = 0f
                var mpvVolumeStartingY = 0f
                var originalVolume = currentVolume
                var originalMPVVolume = currentMPVVolume
                var originalBrightness = currentBrightness
                val brightnessGestureSens = 0.001f
                val volumeGestureSens = 0.001f * viewModel.maxVolume
                val mpvVolumeGestureSens = 0.001f * volumeBoostingCap
                val isIncreasingVolumeBoost: (Float) -> Boolean = {
                    volumeBoostingCap > 0 &&
                        currentVolume == viewModel.maxVolume &&
                        currentMPVVolume - 100 < volumeBoostingCap &&
                        it < 0
                }
                val isDecreasingVolumeBoost: (Float) -> Boolean = {
                    volumeBoostingCap > 0 &&
                        currentVolume == viewModel.maxVolume &&
                        currentMPVVolume - 100 in 1..volumeBoostingCap &&
                        it > 0
                }
                detectVerticalDragGestures(
                    onDragEnd = { startingY = 0f },
                    onDragStart = {
                        startingY = 0f
                        mpvVolumeStartingY = 0f
                        originalVolume = currentVolume
                        originalMPVVolume = currentMPVVolume
                        originalBrightness = currentBrightness
                    },
                ) { change, amount ->
                    val changeVolume: () -> Unit = {
                        if (isIncreasingVolumeBoost(amount) || isDecreasingVolumeBoost(amount)) {
                            if (mpvVolumeStartingY == 0f) {
                                startingY = 0f
                                originalVolume = currentVolume
                                mpvVolumeStartingY = change.position.y
                            }
                            viewModel.changeMPVVolumeTo(
                                calculateNewVerticalGestureValue(
                                    originalMPVVolume,
                                    mpvVolumeStartingY,
                                    change.position.y,
                                    mpvVolumeGestureSens,
                                )
                                    .coerceIn(100..volumeBoostingCap + 100),
                            )
                        } else {
                            if (startingY == 0f) {
                                mpvVolumeStartingY = 0f
                                originalMPVVolume = currentMPVVolume
                                startingY = change.position.y
                            }
                            viewModel.changeVolumeTo(
                                calculateNewVerticalGestureValue(
                                    originalVolume,
                                    startingY,
                                    change.position.y,
                                    volumeGestureSens,
                                ),
                            )
                        }
                        viewModel.displayVolumeSlider()
                    }
                    val changeBrightness: () -> Unit = {
                        if (startingY == 0f) startingY = change.position.y
                        viewModel.changeBrightnessTo(
                            calculateNewVerticalGestureValue(
                                originalBrightness,
                                startingY,
                                change.position.y,
                                brightnessGestureSens,
                            ),
                        )
                        viewModel.displayBrightnessSlider()
                    }
                    if (swapVolumeBrightness) {
                        if (change.position.x > size.width / 2) changeBrightness() else changeVolume()
                    } else {
                        if (change.position.x < size.width / 2) changeBrightness() else changeVolume()
                    }
                }
            },
    )
}

@Composable
fun DoubleTapToSeekOvals(
    amount: Int,
    text: String?,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (amount == 0) 0f else 0.2f, label = "double_tap_animation_alpha")
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (amount > 0) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides playerRippleConfiguration,
        ) {
            if (amount != 0 || text != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.4f), // 2 fifths
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(if (amount > 0) RightSideOvalShape else LeftSideOvalShape)
                            .background(Color.White.copy(alpha))
                            .indication(interactionSource, ripple()),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DoubleTapSeekTriangles(isForward = amount > 0)
                        Text(
                            text = text ?: pluralStringResource(AYMR.plurals.seconds, amount, amount),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

fun calculateNewVerticalGestureValue(originalValue: Int, startingY: Float, newY: Float, sensitivity: Float): Int {
    return originalValue + ((startingY - newY) * sensitivity).toInt()
}

fun calculateNewVerticalGestureValue(originalValue: Float, startingY: Float, newY: Float, sensitivity: Float): Float {
    return originalValue + ((startingY - newY) * sensitivity)
}

fun calculateNewHorizontalGestureValue(originalValue: Int, startingX: Float, newX: Float, sensitivity: Float): Int {
    return originalValue + ((newX - startingX) * sensitivity).toInt()
}

fun calculateNewHorizontalGestureValue(originalValue: Float, startingX: Float, newX: Float, sensitivity: Float): Float {
    return originalValue + ((newX - startingX) * sensitivity)
}
