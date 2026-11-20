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

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vivvvek.seeker.Segment
import eu.kanade.presentation.more.settings.screen.player.custombutton.CustomButtonFetchState
import eu.kanade.presentation.more.settings.screen.player.custombutton.getButtons
import eu.kanade.presentation.theme.playerRippleConfiguration
import eu.kanade.tachiyomi.ui.player.Dialogs
import eu.kanade.tachiyomi.ui.player.LongPressGesture
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.PlayerUpdates
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.Sheets
import eu.kanade.tachiyomi.ui.player.AbLoopState
import eu.kanade.tachiyomi.ui.player.VideoAspect
import eu.kanade.tachiyomi.ui.player.formatBufferingCaption
import eu.kanade.tachiyomi.ui.player.controls.components.BrightnessOverlay
import eu.kanade.tachiyomi.ui.player.controls.components.NextEpisodeCountdownOverlay
import eu.kanade.tachiyomi.ui.player.controls.components.computeNextEpisodeCountdownSeconds
import eu.kanade.tachiyomi.ui.player.controls.components.resolveChapterTitleAtPosition
import eu.kanade.tachiyomi.ui.player.controls.components.BrightnessSlider
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.controls.components.SeekbarWithTimers
import eu.kanade.tachiyomi.ui.player.controls.components.TextPlayerUpdate
import eu.kanade.tachiyomi.ui.player.controls.components.VolumeSlider
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.toFixed
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutOrientation
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutRegion
import eu.kanade.tachiyomi.ui.player.resolveVisibleCustomButton
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Suppress("CompositionLocalAllowlist")
val LocalPlayerButtonsClickEvent = staticCompositionLocalOf { {} }

@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.padding
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
    val gesturePreferences = remember { Injekt.get<GesturePreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }
    val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }
    val interactionSource = remember { MutableInteractionSource() }

    val controlsShown by viewModel.controlsShown.collectAsStateWithLifecycle()
    val areControlsLocked by viewModel.areControlsLocked.collectAsStateWithLifecycle()
    val seekBarShown by viewModel.seekBarShown.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val gestureSeekAmount by viewModel.gestureSeekAmount.collectAsStateWithLifecycle()
    val doubleTapSeekAmount by viewModel.doubleTapSeekAmount.collectAsStateWithLifecycle()
    val seekText by viewModel.seekText.collectAsStateWithLifecycle()
    val currentChapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentBrightness by viewModel.currentBrightness.collectAsStateWithLifecycle()
    val isDynamicSpeedActive by viewModel.isDynamicSpeedActive.collectAsStateWithLifecycle()
    val gesturePlaybackSpeed by viewModel.gesturePlaybackSpeed.collectAsStateWithLifecycle()
    val longPressAction by gesturePreferences.longPressGesture().collectAsStateWithLifecycle()

    val playerTimeToDisappear by playerPreferences.playerTimeToDisappear().collectAsStateWithLifecycle()
    val showCustomButtons by playerPreferences.showCustomButtons().collectAsStateWithLifecycle()
    val playerLayoutConfig by viewModel.playerLayoutConfig.collectAsStateWithLifecycle()
    var isSeeking by remember { mutableStateOf(false) }
    var resetControls by remember { mutableStateOf(true) }

    val customButtons by viewModel.customButtons.collectAsStateWithLifecycle()
    val customButton by viewModel.primaryButton.collectAsStateWithLifecycle()
    val activeLayoutOrientation = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        PlayerLayoutOrientation.Landscape
    } else {
        PlayerLayoutOrientation.Portrait
    }
    val bottomLeftLayoutSlots = remember(playerLayoutConfig, activeLayoutOrientation) {
        playerLayoutConfig.slotsForRegion(
            orientation = activeLayoutOrientation,
            region = PlayerLayoutRegion.BottomLeft,
        ).toSet()
    }
    val bottomRightLayoutSlots = remember(playerLayoutConfig, activeLayoutOrientation) {
        playerLayoutConfig.slotsForRegion(
            orientation = activeLayoutOrientation,
            region = PlayerLayoutRegion.BottomRight,
        ).toSet()
    }
    val chapterSegments = remember(chapters) {
        chapters.map { it.toSegment() }.toImmutableList()
    }
    val currentChapterSegment = remember(currentChapter) {
        currentChapter?.toSegment()
    }
    val controlsBackgroundBrush = remember {
        Brush.verticalGradient(
            Pair(0f, Color.Black),
            Pair(.2f, Color.Transparent),
            Pair(.7f, Color.Transparent),
            Pair(1f, Color.Black),
        )
    }

    LaunchedEffect(
        controlsShown,
        paused,
        isSeeking,
        resetControls,
    ) {
        if (controlsShown && !paused && !isSeeking) {
            delay(playerTimeToDisappear.toLong())
            viewModel.hideControls()
        }
    }

    val transparentOverlay by animateFloatAsState(
        if (controlsShown && !areControlsLocked) .8f else 0f,
        animationSpec = playerControlsExitAnimationSpec(),
        label = "controls_transparent_overlay",
    )
    GestureHandler(
        viewModel = viewModel,
        interactionSource = interactionSource,
    )
    DoubleTapToSeekOvals(doubleTapSeekAmount, seekText, interactionSource)
    CompositionLocalProvider(
        LocalRippleConfiguration provides playerRippleConfiguration,
        LocalPlayerButtonsClickEvent provides { resetControls = !resetControls },
        LocalContentColor provides Color.White,
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
            ConstraintLayout(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        controlsBackgroundBrush,
                        alpha = transparentOverlay,
                    )
                    .padding(horizontal = MaterialTheme.padding.medium),
            ) {
                val (topLeftControls, topRightControls) = createRefs()
                val (volumeSlider, brightnessSlider) = createRefs()
                val unlockControlsButton = createRef()
                val (bottomRightControls, bottomLeftControls) = createRefs()
                val centerControls = createRef()
                val seekbar = createRef()
                val (playerUpdates) = createRefs()
                val speedPill = createRef()
                val nextEpisodeCard = createRef()

                val hasPreviousEpisode by viewModel.hasPreviousEpisode.collectAsStateWithLifecycle()
                val hasNextEpisode by viewModel.hasNextEpisode.collectAsStateWithLifecycle()
                val isBrightnessSliderShown by viewModel.isBrightnessSliderShown.collectAsStateWithLifecycle()
                val isVolumeSliderShown by viewModel.isVolumeSliderShown.collectAsStateWithLifecycle()
                val brightness by viewModel.currentBrightness.collectAsStateWithLifecycle()
                val volume by viewModel.currentVolume.collectAsStateWithLifecycle()
                val mpvVolume by viewModel.currentMPVVolume.collectAsStateWithLifecycle()
                val swapVolumeAndBrightness by gesturePreferences.swapVolumeBrightness().collectAsStateWithLifecycle()
                val reduceMotion by playerPreferences.reduceMotion().collectAsStateWithLifecycle()

                LaunchedEffect(volume, mpvVolume, isVolumeSliderShown) {
                    delay(2000)
                    if (isVolumeSliderShown) viewModel.isVolumeSliderShown.update { false }
                }
                LaunchedEffect(brightness, isBrightnessSliderShown) {
                    delay(2000)
                    if (isBrightnessSliderShown) viewModel.isBrightnessSliderShown.update { false }
                }
                AnimatedVisibility(
                    isBrightnessSliderShown,
                    enter =
                    if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) {
                            if (swapVolumeAndBrightness) -it else it
                        } +
                            fadeIn(
                                playerControlsEnterAnimationSpec(),
                            )
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit =
                    if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) {
                            if (swapVolumeAndBrightness) -it else it
                        } +
                            fadeOut(
                                playerControlsExitAnimationSpec(),
                            )
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(brightnessSlider) {
                        if (swapVolumeAndBrightness) {
                            start.linkTo(parent.start, spacing.medium)
                        } else {
                            end.linkTo(parent.end, spacing.medium)
                        }
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    BrightnessSlider(
                        brightness = brightness,
                        positiveRange = 0f..1f,
                        negativeRange = 0f..0.75f,
                    )
                }

                AnimatedVisibility(
                    isVolumeSliderShown,
                    enter =
                    if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) {
                            if (swapVolumeAndBrightness) it else -it
                        } +
                            fadeIn(
                                playerControlsEnterAnimationSpec(),
                            )
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit =
                    if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) {
                            if (swapVolumeAndBrightness) it else -it
                        } +
                            fadeOut(
                                playerControlsExitAnimationSpec(),
                            )
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(volumeSlider) {
                        if (swapVolumeAndBrightness) {
                            end.linkTo(parent.end, spacing.medium)
                        } else {
                            start.linkTo(parent.start, spacing.medium)
                        }
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    val boostCap by audioPreferences.volumeBoostCap().collectAsStateWithLifecycle()
                    val displayVolumeAsPercentage by playerPreferences.displayVolPer().collectAsStateWithLifecycle()
                    VolumeSlider(
                        volume = volume,
                        mpvVolume = mpvVolume,
                        range = 0..viewModel.maxVolume,
                        boostRange = if (boostCap > 0) 0..audioPreferences.volumeBoostCap().get() else null,
                        displayAsPercentage = displayVolumeAsPercentage,
                    )
                }

                val currentPlayerUpdate by viewModel.playerUpdate.collectAsStateWithLifecycle()
                val aspectRatio by playerPreferences.aspectState().collectAsStateWithLifecycle()
                LaunchedEffect(currentPlayerUpdate, aspectRatio) {
                    if (currentPlayerUpdate is PlayerUpdates.DoubleSpeed || currentPlayerUpdate is PlayerUpdates.None) {
                        return@LaunchedEffect
                    }
                    delay(2000)
                    viewModel.playerUpdate.update { PlayerUpdates.None }
                }
                AnimatedVisibility(
                    currentPlayerUpdate !is PlayerUpdates.None,
                    enter = fadeIn(playerControlsEnterAnimationSpec()),
                    exit = fadeOut(playerControlsExitAnimationSpec()),
                    modifier = Modifier.constrainAs(playerUpdates) {
                        linkTo(parent.start, parent.end)
                        linkTo(parent.top, parent.bottom, bias = 0.2f)
                    },
                ) {
                    when (currentPlayerUpdate) {
                        // is PlayerUpdates.DoubleSpeed -> DoubleSpeedPlayerUpdate()
                        is PlayerUpdates.AspectRatio -> TextPlayerUpdate(stringResource(aspectRatio.titleRes))
                        is PlayerUpdates.ShowText -> TextPlayerUpdate(
                            (currentPlayerUpdate as PlayerUpdates.ShowText).value,
                        )
                        is PlayerUpdates.ShowTextResource -> TextPlayerUpdate(
                            stringResource((currentPlayerUpdate as PlayerUpdates.ShowTextResource).textResource),
                        )
                        else -> {}
                    }
                }

                AnimatedVisibility(
                    controlsShown && areControlsLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.constrainAs(unlockControlsButton) {
                        top.linkTo(parent.top, spacing.medium)
                        start.linkTo(parent.start, spacing.medium)
                    },
                ) {
                    ControlsButton(
                        Icons.Filled.Lock,
                        onClick = { viewModel.unlockControls() },
                    )
                }
                AnimatedVisibility(
                    visible =
                    ((controlsShown && !areControlsLocked && !isDynamicSpeedActive) || gestureSeekAmount != null) ||
                        isLoading ||
                        isLoadingEpisode,
                    enter = fadeIn(playerControlsEnterAnimationSpec()),
                    exit = fadeOut(playerControlsExitAnimationSpec()),
                    modifier = Modifier.constrainAs(centerControls) {
                        end.linkTo(parent.absoluteRight)
                        start.linkTo(parent.absoluteLeft)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    val showLoadingCircle by playerPreferences.showLoadingCircle().collectAsStateWithLifecycle()
                    val bufferingPercent by viewModel.bufferingPercent.collectAsStateWithLifecycle()
                    val cacheSpeedBytesPerSec by viewModel.cacheSpeedBytesPerSec.collectAsStateWithLifecycle()
                    val bufferingCaption = remember(bufferingPercent, cacheSpeedBytesPerSec, isLoading) {
                        if (isLoading) formatBufferingCaption(bufferingPercent, cacheSpeedBytesPerSec) else null
                    }
                    val seekChapterTitle = remember(gestureSeekAmount, chapterSegments) {
                        gestureSeekAmount?.let { (basePos, delta) ->
                            resolveChapterTitleAtPosition(chapterSegments, (basePos + delta).toFloat())
                        }
                    }
                    MiddlePlayerControls(
                        hasPrevious = hasPreviousEpisode,
                        onSkipPrevious = { viewModel.changeEpisode(true) },
                        hasNext = hasNextEpisode,
                        onSkipNext = { viewModel.changeEpisode(false) },
                        isLoading = isLoading,
                        isLoadingEpisode = isLoadingEpisode,
                        bufferingCaption = bufferingCaption,
                        controlsShown = controlsShown,
                        areControlsLocked = areControlsLocked,
                        showLoadingCircle = showLoadingCircle,
                        paused = paused,
                        gestureSeekAmount = gestureSeekAmount,
                        gestureSeekChapterTitle = seekChapterTitle,
                        onPlayPauseClick = viewModel::pauseUnpause,
                        enter = fadeIn(playerControlsEnterAnimationSpec()),
                        exit = fadeOut(playerControlsExitAnimationSpec()),
                    )
                }
                AnimatedVisibility(
                    visible = (controlsShown || seekBarShown) && !areControlsLocked && !isDynamicSpeedActive,
                    enter = if (!reduceMotion) {
                        slideInVertically(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutVertically(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(seekbar) {
                        bottom.linkTo(parent.bottom, spacing.medium)
                    },
                ) {
                    val duration by viewModel.duration.collectAsStateWithLifecycle()
                    val position by viewModel.pos.collectAsStateWithLifecycle()
                    val invertDuration by playerPreferences.invertDuration().collectAsStateWithLifecycle()
                    val readAhead by viewModel.readAhead.collectAsStateWithLifecycle()
                    val preciseSeeking by gesturePreferences.playerSmoothSeek().collectAsStateWithLifecycle()
                    SeekbarWithTimers(
                        position = position,
                        duration = duration,
                        readAheadValue = readAhead,
                        onValueChange = {
                            isSeeking = true
                            viewModel.updatePlayBackPos(it)
                            viewModel.seekTo(it.toInt(), preciseSeeking)
                        },
                        onValueChangeFinished = { isSeeking = false },
                        timersInverted = Pair(false, invertDuration),
                        durationTimerOnCLick = { playerPreferences.invertDuration().set(!invertDuration) },
                        positionTimerOnClick = {},
                        chapters = chapterSegments,
                    )
                }
                val mediaTitle by viewModel.mediaTitle.collectAsStateWithLifecycle()
                val animeTitle by viewModel.animeTitle.collectAsStateWithLifecycle()
                AnimatedVisibility(
                    controlsShown && !areControlsLocked && !isDynamicSpeedActive,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(topLeftControls) {
                        top.linkTo(parent.top, spacing.medium)
                        start.linkTo(parent.start)
                        width = Dimension.fillToConstraints
                        end.linkTo(topRightControls.start)
                    },
                ) {
                    TopLeftPlayerControls(
                        animeTitle = animeTitle,
                        mediaTitle = mediaTitle,
                        onTitleClick = { viewModel.showEpisodeListDialog() },
                        onBackClick = onBackPress,
                    )
                }
                // Top right controls
                val autoPlayEnabled by playerPreferences.autoplayEnabled().collectAsStateWithLifecycle()
                val isEpisodeOnline by viewModel.isEpisodeOnline.collectAsStateWithLifecycle()
                AnimatedVisibility(
                    controlsShown && !areControlsLocked && !isDynamicSpeedActive,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(topRightControls) {
                        top.linkTo(parent.top, spacing.medium)
                        end.linkTo(parent.end)
                    },
                ) {
                    TopRightPlayerControls(
                        autoPlayEnabled = autoPlayEnabled,
                        onToggleAutoPlay = { viewModel.setAutoPlay(it) },
                        onSubtitlesClick = { viewModel.showSheet(Sheets.SubtitleTracks) },
                        onSubtitlesLongClick = { viewModel.showPanel(Panels.SubtitleSettings) },
                        onAudioClick = { viewModel.showSheet(Sheets.AudioTracks) },
                        onAudioLongClick = { viewModel.showPanel(Panels.AudioDelay) },
                        onQualityClick = { viewModel.showSheet(Sheets.QualityTracks) },
                        isEpisodeOnline = isEpisodeOnline,
                        onMoreClick = { viewModel.showSheet(Sheets.More) },
                        onMoreLongClick = { viewModel.showPanel(Panels.VideoFilters) },
                        showScreenshotButton = longPressAction == LongPressGesture.PlaybackSpeed,
                        onScreenshotClick = { viewModel.showSheet(Sheets.Screenshot) },
                    )
                }
                // Shared actions for customizable bottom control slots
                val skipIntroButton by viewModel.skipIntroText.collectAsStateWithLifecycle()
                val customButtonTitle by viewModel.primaryButtonTitle.collectAsStateWithLifecycle()
                val visibleCustomButton = resolveVisibleCustomButton(showCustomButtons, customButton)
                val visibleSkipIntroButton = skipIntroButton
                val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
                val playerActivity = LocalContext.current as PlayerActivity
                val bottomSlotActions = BottomPlayerSlotActions(
                    playbackSpeed = playbackSpeed,
                    customButton = visibleCustomButton,
                    customButtonTitle = customButtonTitle,
                    skipIntroButton = visibleSkipIntroButton,
                    isPipAvailable = playerActivity.isPipSupportedAndEnabled,
                    onLockControls = viewModel::lockControls,
                    onCycleRotation = viewModel::cycleScreenRotations,
                    onPlaybackSpeedTap = { viewModel.showSheet(Sheets.PlaybackSpeed) },
                    onPlaybackSpeedQuickToggle = {
                        val nextSpeed = resolveQuickTogglePlaybackSpeed(playbackSpeed)
                        MPVLib.setPropertyDouble("speed", nextSpeed.toDouble())
                        playerPreferences.playerSpeed().set(nextSpeed)
                    },
                    onPressSkipIntroButton = viewModel::onSkipIntro,
                    onPipClick = {
                        if (!viewModel.isLoadingEpisode.value) {
                            playerActivity.enterPictureInPictureMode(playerActivity.createPipParams())
                        }
                    },
                    onAspectClick = {
                        viewModel.changeVideoAspect(
                            when (aspectRatio) {
                                VideoAspect.Fit -> VideoAspect.Stretch
                                VideoAspect.Stretch -> VideoAspect.Crop
                                VideoAspect.Crop -> VideoAspect.Fit
                            },
                        )
                    },
                )
                // Bottom right controls
                AnimatedVisibility(
                    controlsShown && !areControlsLocked && !isDynamicSpeedActive,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(bottomRightControls) {
                        bottom.linkTo(seekbar.top)
                        end.linkTo(seekbar.end)
                    },
                ) {
                    BottomRightPlayerControls(
                        layoutSlots = bottomRightLayoutSlots,
                        slotActions = bottomSlotActions,
                    )
                }
                // Bottom left controls
                AnimatedVisibility(
                    controlsShown && !areControlsLocked && !isDynamicSpeedActive,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(bottomLeftControls) {
                        bottom.linkTo(seekbar.top)
                        start.linkTo(seekbar.start)
                        width = Dimension.fillToConstraints
                        end.linkTo(bottomRightControls.start)
                    },
                ) {
                    BottomLeftPlayerControls(
                        layoutSlots = bottomLeftLayoutSlots,
                        slotActions = bottomSlotActions,
                        currentChapter = currentChapterSegment,
                        onOpenSheet = viewModel::showSheet,
                    )
                }

                AnimatedVisibility(
                    visible = isDynamicSpeedActive,
                    enter =
                    fadeIn(playerControlsEnterAnimationSpec()) +
                        slideInVertically(playerControlsEnterAnimationSpec()) { -it },
                    exit =
                    fadeOut(playerControlsExitAnimationSpec()) +
                        slideOutVertically(playerControlsExitAnimationSpec()) { -it },
                    modifier = Modifier.constrainAs(speedPill) {
                        top.linkTo(parent.top, spacing.medium)
                        linkTo(parent.start, parent.end)
                    },
                ) {
                    PlaybackSpeedPill(speed = gesturePlaybackSpeed)
                }

                val currentEpisode by viewModel.currentEpisode.collectAsStateWithLifecycle()
                val episodePlaylist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
                val repeatEpisode by viewModel.repeatEpisode.collectAsStateWithLifecycle()
                val countdownPos by viewModel.pos.collectAsStateWithLifecycle()
                val countdownDuration by viewModel.duration.collectAsStateWithLifecycle()
                var dismissedNextOverlayEpisodeId by remember { mutableStateOf<Long?>(null) }
                val nextEpisodeCountdown = computeNextEpisodeCountdownSeconds(
                    positionSec = countdownPos,
                    durationSec = countdownDuration,
                    hasNextEpisode = hasNextEpisode,
                    autoPlayEnabled = autoPlayEnabled,
                    repeatEpisode = repeatEpisode,
                    dismissedEpisodeId = dismissedNextOverlayEpisodeId,
                    currentEpisodeId = currentEpisode?.id,
                )
                val nextEpisodeName = remember(episodePlaylist, currentEpisode?.id) {
                    val idx = viewModel.getCurrentEpisodeIndex()
                    episodePlaylist.getOrNull(idx + 1)?.name
                }
                AnimatedVisibility(
                    visible = nextEpisodeCountdown != null && !areControlsLocked && !nextEpisodeName.isNullOrBlank(),
                    enter = fadeIn(playerControlsEnterAnimationSpec()) +
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { it / 2 },
                    exit = fadeOut(playerControlsExitAnimationSpec()) +
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { it / 2 },
                    modifier = Modifier.constrainAs(nextEpisodeCard) {
                        end.linkTo(parent.end, spacing.medium)
                        bottom.linkTo(parent.bottom, if (controlsShown) 104.dp else 24.dp)
                    },
                ) {
                    if (nextEpisodeCountdown != null && !nextEpisodeName.isNullOrBlank()) {
                        NextEpisodeCountdownOverlay(
                            nextEpisodeTitle = nextEpisodeName,
                            secondsRemaining = nextEpisodeCountdown,
                            onPlayNextNow = { viewModel.changeEpisode(previous = false) },
                            onDismiss = { dismissedNextOverlayEpisodeId = currentEpisode?.id },
                        )
                    }
                }
            }
        }

        val hostActivity = LocalContext.current as PlayerActivity
        PlayerSheetsHost(
            viewModel = viewModel,
            playerPreferences = playerPreferences,
            subtitlePreferences = subtitlePreferences,
            customButtons = customButtons,
            showCustomButtons = showCustomButtons,
            currentChapterSegment = currentChapterSegment,
            chapterSegments = chapterSegments,
            onReloadPlayer = hostActivity::reloadPlayerPreservingProgress,
        )
        val panel by viewModel.panelShown.collectAsStateWithLifecycle()
        PlayerPanels(
            panelShown = panel,
            onDismissRequest = { viewModel.showPanel(Panels.None) },
        )

        val activity = LocalContext.current as PlayerActivity
        val dialog by viewModel.dialogShown.collectAsStateWithLifecycle()
        val anime by viewModel.currentAnime.collectAsStateWithLifecycle()
        val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()

        PlayerDialogs(
            dialogShown = dialog,
            episodeDisplayMode = anime?.displayMode,
            episodeList = playlist,
            currentEpisodeIndex = viewModel.getCurrentEpisodeIndex(),
            dateRelativeTime = viewModel.relativeTime,
            dateFormat = viewModel.dateFormat,
            onBookmarkClicked = viewModel::bookmarkEpisode,
            onFillermarkClicked = viewModel::fillermarkEpisode,
            onEpisodeClicked = {
                viewModel.showDialog(Dialogs.None)
                activity.changeEpisode(it)
            },
            onDismissRequest = { viewModel.showDialog(Dialogs.None) },
        )

        BrightnessOverlay(
            brightness = currentBrightness,
        )
    }
}

@Composable
private fun PlayerSheetsHost(
    viewModel: PlayerViewModel,
    playerPreferences: PlayerPreferences,
    subtitlePreferences: SubtitlePreferences,
    customButtons: CustomButtonFetchState,
    showCustomButtons: Boolean,
    currentChapterSegment: Segment?,
    chapterSegments: ImmutableList<Segment>,
    onReloadPlayer: () -> Unit,
) {
    val sheetShown by viewModel.sheetShown.collectAsStateWithLifecycle()
    val dismissSheet by viewModel.dismissSheet.collectAsStateWithLifecycle()
    val subtitles by viewModel.subtitleTracks.collectAsStateWithLifecycle()
    val selectedSubtitles by viewModel.selectedSubtitles.collectAsStateWithLifecycle()
    val subtitleTranslationUiState by viewModel.subtitleTranslationUiState.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val selectedAudio by viewModel.selectedAudio.collectAsStateWithLifecycle()
    val isLoadingHosters by viewModel.isLoadingHosters.collectAsStateWithLifecycle()
    val hosterState by viewModel.hosterState.collectAsStateWithLifecycle()
    val expandedState by viewModel.hosterExpandedList.collectAsStateWithLifecycle()
    val selectedHosterVideoIndex by viewModel.selectedHosterVideoIndex.collectAsStateWithLifecycle()
    val decoder by viewModel.currentDecoder.collectAsStateWithLifecycle()
    val speed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val sleepTimerTimeRemaining by viewModel.remainingTime.collectAsStateWithLifecycle()
    val aspectRatio by playerPreferences.aspectState().collectAsStateWithLifecycle()
    val videoZoom by viewModel.videoZoom.collectAsStateWithLifecycle()
    val repeatEpisode by viewModel.repeatEpisode.collectAsStateWithLifecycle()
    val abLoopState by viewModel.abLoopState.collectAsStateWithLifecycle()
    val showSubtitles by subtitlePreferences.screenshotSubtitles().collectAsStateWithLifecycle()
    val currentSource by viewModel.currentSource.collectAsStateWithLifecycle()
    val showFailedHosters by playerPreferences.showFailedHosters().collectAsStateWithLifecycle()
    val emptyHosters by playerPreferences.showEmptyHosters().collectAsStateWithLifecycle()

    val immutableSubtitles = remember(subtitles) { subtitles.toImmutableList() }
    val immutableSelectedSubtitles = remember(selectedSubtitles) { selectedSubtitles.toList().toImmutableList() }
    val immutableAudioTracks = remember(audioTracks) { audioTracks.toImmutableList() }
    val immutableButtons = remember(showCustomButtons, customButtons) {
        if (showCustomButtons) {
            customButtons.getButtons().toImmutableList()
        } else {
            persistentListOf()
        }
    }

    PlayerSheets(
        sheetShown = sheetShown,
        subtitles = immutableSubtitles,
        selectedSubtitles = immutableSelectedSubtitles,
        onAddSubtitle = viewModel::addSubtitle,
        onTranslateSubtitle = viewModel::openSubtitleTranslationSheet,
        subtitleTranslationUiState = subtitleTranslationUiState,
        onSelectSubtitleTranslationTrack = viewModel::selectSubtitleTranslationTrack,
        onSelectSubtitleTranslationProvider = viewModel::selectSubtitleTranslationProvider,
        onSubtitleTranslationSourceLanguageChange = viewModel::setSubtitleTranslationSourceLanguage,
        onSubtitleTranslationTargetLanguageChange = viewModel::setSubtitleTranslationTargetLanguage,
        onToggleSubtitleTranslationCache = viewModel::toggleSubtitleTranslationCache,
        onStartSubtitleTranslation = viewModel::startSubtitleTranslation,
        onCancelSubtitleTranslation = viewModel::cancelSubtitleTranslation,
        onSelectSubtitle = viewModel::selectSub,
        audioTracks = immutableAudioTracks,
        selectedAudio = selectedAudio,
        onAddAudio = viewModel::addAudio,
        onSelectAudio = viewModel::selectAudio,
        isLoadingHosters = isLoadingHosters,
        hosterState = hosterState,
        expandedState = expandedState,
        selectedVideoIndex = selectedHosterVideoIndex,
        onClickHoster = viewModel::onHosterClicked,
        onClickVideo = viewModel::onVideoClicked,
        displayHosters = Pair(showFailedHosters, emptyHosters),
        chapter = currentChapterSegment,
        chapters = chapterSegments,
        onSeekToChapter = {
            viewModel.selectChapter(it)
            viewModel.dismissSheet()
            viewModel.unpause()
        },
        decoder = decoder,
        onUpdateDecoder = viewModel::updateDecoder,
        speed = speed,
        onSpeedChange = { MPVLib.setPropertyDouble("speed", it.toFixed(2).toDouble()) },
        sleepTimerTimeRemaining = sleepTimerTimeRemaining,
        onStartSleepTimer = viewModel::startTimer,
        aspectRatio = aspectRatio,
        onSelectAspectRatio = viewModel::changeVideoAspect,
        videoZoom = videoZoom,
        onVideoZoomChange = viewModel::setVideoZoom,
        onResetVideoZoom = viewModel::resetVideoZoom,
        repeatEpisode = repeatEpisode,
        onToggleRepeatEpisode = viewModel::toggleRepeatEpisode,
        abLoopState = abLoopState,
        onCycleAbLoop = viewModel::cycleAbLoop,
        onClearAbLoop = viewModel::clearAbLoop,
        buttons = immutableButtons,
        isLocalSource = currentSource?.id == LocalAnimeSource.ID,
        showSubtitles = showSubtitles,
        onToggleShowSubtitles = { subtitlePreferences.screenshotSubtitles().set(it) },
        cachePath = viewModel.cachePath,
        onSetAsArt = viewModel::setAsArt,
        onShare = { viewModel.shareImage(it, viewModel.pos.value.toInt()) },
        onSave = { viewModel.saveImage(it, viewModel.pos.value.toInt()) },
        takeScreenshot = viewModel::takeScreenshot,
        onDismissScreenshot = {
            viewModel.showSheet(Sheets.None)
            viewModel.unpause()
        },
        onOpenPanel = viewModel::showPanel,
        onReloadPlayer = onReloadPlayer,
        onDismissRequest = { viewModel.showSheet(Sheets.None) },
        dismissSheet = dismissSheet,
    )
}

fun <T> playerControlsExitAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
)

fun <T> playerControlsEnterAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 100,
    easing = LinearOutSlowInEasing,
)

@Composable
fun PlaybackSpeedPill(
    speed: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (speed < 1.0f) {
                FlashingArrows(isForward = false)
            }
            Text(
                text = "${speed}x",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            if (speed > 1.0f) {
                FlashingArrows(isForward = true)
            }
        }
    }
}

@Composable
fun FlashingArrows(
    isForward: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arrows_flash")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrows_alpha",
    )
    Text(
        text = if (isForward) ">>" else "<<",
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = alpha),
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}
