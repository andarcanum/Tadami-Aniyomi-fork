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

package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.components.AuroraCapsuleTabs
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.presentation.player.components.SliderItem
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.player.AbLoopState
import eu.kanade.tachiyomi.ui.player.AudioFilterChainBuilder
import eu.kanade.tachiyomi.ui.player.Decoder
import eu.kanade.tachiyomi.ui.player.DecoderPreset
import eu.kanade.tachiyomi.ui.player.LongPressGesture
import eu.kanade.tachiyomi.ui.player.VideoAspect
import eu.kanade.tachiyomi.ui.player.execute
import eu.kanade.tachiyomi.ui.player.executeLongPress
import eu.kanade.tachiyomi.ui.player.formatVideoZoomPercent
import eu.kanade.tachiyomi.ui.player.resolveMpvStatsPageLabelRes
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioChannels
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MoreSheet(
    selectedDecoder: Decoder,
    onSelectDecoder: (Decoder) -> Unit,
    remainingTime: Int,
    onStartTimer: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    onEnterFiltersPanel: () -> Unit,
    customButtons: ImmutableList<CustomButton>,
    onOpenAudioDelay: () -> Unit = {},
    onOpenSubtitleDelay: () -> Unit = {},
    onOpenSubtitleSettings: () -> Unit = {},
    aspectRatio: VideoAspect = VideoAspect.Fit,
    onSelectAspectRatio: (VideoAspect) -> Unit = {},
    videoZoom: Float = 0f,
    onVideoZoomChange: (Float) -> Unit = {},
    onResetVideoZoom: () -> Unit = {},
    repeatEpisode: Boolean = false,
    onToggleRepeatEpisode: () -> Unit = {},
    abLoopState: AbLoopState = AbLoopState(),
    onCycleAbLoop: () -> Unit = {},
    onClearAbLoop: () -> Unit = {},
    onReloadPlayer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val advancedPreferences = remember { Injekt.get<AdvancedPlayerPreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }
    val decoderPreferences = remember { Injekt.get<DecoderPreferences>() }
    val gesturePreferences = remember { Injekt.get<GesturePreferences>() }
    val decoderPreset by decoderPreferences.decoderPreset().collectAsState()
    val tryHwDecoding by decoderPreferences.tryHWDecoding().collectAsState()
    val gpuNext by decoderPreferences.gpuNext().collectAsState()
    val videoDebanding by decoderPreferences.videoDebanding().collectAsState()
    val anime4kPreset by decoderPreferences.anime4kShaderPreset().collectAsState()
    val motionInterpolation by decoderPreferences.motionInterpolationMode().collectAsState()
    val useYuv420p by decoderPreferences.useYUV420P().collectAsState()
    val hdrMode by decoderPreferences.hdrToneMappingMode().collectAsState()
    val matchRefreshRate by decoderPreferences.matchDisplayRefreshRate().collectAsState()
    val activeDecoderPreset = remember(
        decoderPreset,
        tryHwDecoding,
        gpuNext,
        videoDebanding,
        anime4kPreset,
        motionInterpolation,
        useYuv420p,
    ) {
        DecoderPreset.resolveActive(
            preferred = decoderPreset,
            snapshot = DecoderPreset.snapshotFrom(decoderPreferences),
        )
    }
    var decoderPresetRestartNeeded by rememberSaveable { mutableStateOf(false) }
    var isSleepTimerDialogShown by remember { mutableStateOf(false) }
    val statisticsPage by advancedPreferences.playerStatisticsPage().collectAsState()
    val longPressGesture by gesturePreferences.longPressGesture().collectAsState()
    val audioChannels by audioPreferences.audioChannels().collectAsState()
    val audioNormalization by audioPreferences.audioNormalization().collectAsState()

    val configuration = LocalConfiguration.current
    val wrapFilterChips = shouldWrapMoreSheetFilterChips(configuration.screenWidthDp)
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val pageMaxHeight = if (isLandscape) {
        (configuration.screenHeightDp * 0.56f).coerceAtLeast(180f).dp
    } else {
        (configuration.screenHeightDp * 0.60f).coerceAtLeast(260f).dp
    }

    val tabTitles = listOf(
        stringResource(AYMR.strings.player_quick_tab_playback),
        stringResource(AYMR.strings.player_quick_tab_video),
        stringResource(AYMR.strings.player_quick_tab_audio_subs),
        stringResource(AYMR.strings.player_quick_tab_tools),
    )
    val pagerState = rememberPagerState { tabTitles.size }
    val scope = rememberCoroutineScope()

    if (isSleepTimerDialogShown) {
        TimePickerDialog(
            remainingTime = remainingTime,
            onDismissRequest = { isSleepTimerDialogShown = false },
            onTimeSelect = onStartTimer,
        )
    }

    PlayerSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AYMR.strings.player_sheets_more_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { isSleepTimerDialogShown = true }) {
                    Icon(imageVector = Icons.Outlined.Timer, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (remainingTime == 0) {
                            stringResource(AYMR.strings.timer_title)
                        } else {
                            stringResource(
                                AYMR.strings.timer_remaining,
                                DateUtils.formatElapsedTime(remainingTime.toLong()),
                            )
                        },
                    )
                }
            }

            AuroraCapsuleTabs(
                titles = tabTitles,
                selectedIndex = pagerState.currentPage,
                onSelect = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = pageMaxHeight),
                verticalAlignment = Alignment.Top,
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = MaterialTheme.padding.small),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    when (page) {
                        0 -> {
                            // Playback tab: Aspect ratio, Live Zoom, Repeat & A-B Loop, Quick Sleep Timer
                            SectionLabel(stringResource(AYMR.strings.player_quick_aspect_and_zoom))
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                VideoAspect.entries.forEach { aspect ->
                                    FilterChip(
                                        selected = aspectRatio == aspect,
                                        onClick = { onSelectAspectRatio(aspect) },
                                        label = { Text(text = stringResource(aspect.titleRes)) },
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SliderItem(
                                        label = stringResource(AYMR.strings.player_quick_video_zoom),
                                        value = videoZoom,
                                        valueText = "${formatVideoZoomPercent(videoZoom)}%",
                                        onChange = onVideoZoomChange,
                                        min = -0.4f,
                                        max = 1.0f,
                                    )
                                }
                                IconButton(
                                    onClick = onResetVideoZoom,
                                    enabled = videoZoom != 0f,
                                ) {
                                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null)
                                }
                            }

                            SectionLabel(stringResource(AYMR.strings.player_quick_loop_section))
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                FilterChip(
                                    selected = repeatEpisode,
                                    onClick = onToggleRepeatEpisode,
                                    leadingIcon = { Icon(Icons.Default.RepeatOne, contentDescription = null) },
                                    label = { Text(text = stringResource(AYMR.strings.player_quick_repeat_episode)) },
                                )
                                val abLabel = when {
                                    abLoopState.isLooping -> stringResource(
                                        AYMR.strings.player_quick_ab_loop_active,
                                        Utils.prettyTime(abLoopState.pointA!!.toInt()),
                                        Utils.prettyTime(abLoopState.pointB!!.toInt()),
                                    )
                                    abLoopState.hasPointA -> stringResource(
                                        AYMR.strings.player_quick_ab_loop_set_b,
                                        Utils.prettyTime(abLoopState.pointA!!.toInt()),
                                    )
                                    else -> stringResource(AYMR.strings.player_quick_ab_loop_set_a)
                                }
                                FilterChip(
                                    selected = abLoopState.hasPointA,
                                    onClick = onCycleAbLoop,
                                    label = { Text(text = abLabel) },
                                )
                                if (abLoopState.hasPointA) {
                                    FilterChip(
                                        selected = false,
                                        onClick = onClearAbLoop,
                                        label = { Text(text = stringResource(MR.strings.action_reset)) },
                                    )
                                }
                            }

                            SectionLabel(stringResource(AYMR.strings.timer_title))
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                listOf(15, 30, 60).forEach { minutes ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { onStartTimer(minutes * 60) },
                                        label = { Text(text = "${minutes}m") },
                                    )
                                }
                                FilterChip(
                                    selected = false,
                                    onClick = { isSleepTimerDialogShown = true },
                                    label = { Text(text = stringResource(AYMR.strings.player_quick_timer_custom)) },
                                )
                                if (remainingTime > 0) {
                                    FilterChip(
                                        selected = true,
                                        onClick = { onStartTimer(0) },
                                        label = { Text(text = stringResource(AYMR.strings.timer_cancel_timer)) },
                                    )
                                }
                            }
                        }

                        1 -> {
                            // Video & Decoder tab
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionLabel(stringResource(AYMR.strings.pref_decoder_preset_title))
                                if (activeDecoderPreset == null) {
                                    Text(
                                        text = stringResource(AYMR.strings.player_decoder_preset_custom),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AuroraTheme.colors.accent,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            if (wrapFilterChips) {
                                FlowRow(
                                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    DecoderPreset.entries.forEach { preset ->
                                        FilterChip(
                                            selected = activeDecoderPreset == preset,
                                            onClick = {
                                                val before = DecoderPreset.snapshotFrom(decoderPreferences)
                                                decoderPreferences.decoderPreset().set(preset)
                                                preset.applyTo(decoderPreferences)
                                                if (DecoderPreset.snapshotFrom(decoderPreferences) != before) {
                                                    decoderPresetRestartNeeded = true
                                                }
                                            },
                                            label = { Text(text = stringResource(preset.titleRes)) },
                                        )
                                    }
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    items(DecoderPreset.entries) { preset ->
                                        FilterChip(
                                            selected = activeDecoderPreset == preset,
                                            onClick = {
                                                val before = DecoderPreset.snapshotFrom(decoderPreferences)
                                                decoderPreferences.decoderPreset().set(preset)
                                                preset.applyTo(decoderPreferences)
                                                if (DecoderPreset.snapshotFrom(decoderPreferences) != before) {
                                                    decoderPresetRestartNeeded = true
                                                }
                                            },
                                            label = { Text(text = stringResource(preset.titleRes)) },
                                        )
                                    }
                                }
                            }
                            if (decoderPresetRestartNeeded) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = MaterialTheme.padding.medium,
                                                vertical = MaterialTheme.padding.small,
                                            ),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(AYMR.strings.player_decoder_preset_restart_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f),
                                        )
                                        FilledTonalButton(
                                            onClick = {
                                                decoderPresetRestartNeeded = false
                                                onReloadPlayer()
                                            },
                                        ) {
                                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                                            Text(text = stringResource(AYMR.strings.player_decoder_preset_reload_now))
                                        }
                                    }
                                }
                            }

                            SectionLabel(stringResource(AYMR.strings.player_hwdec_mode))
                            if (wrapFilterChips) {
                                FlowRow(
                                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    Decoder.entries.minus(Decoder.Auto).forEach { decoder ->
                                        FilterChip(
                                            selected = decoder == selectedDecoder,
                                            onClick = { onSelectDecoder(decoder) },
                                            label = { Text(text = decoder.title) },
                                        )
                                    }
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    items(Decoder.entries.minus(Decoder.Auto)) { decoder ->
                                        FilterChip(
                                            selected = decoder == selectedDecoder,
                                            onClick = { onSelectDecoder(decoder) },
                                            label = { Text(text = decoder.title) },
                                        )
                                    }
                                }
                            }

                            FilledTonalButton(
                                onClick = onEnterFiltersPanel,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(imageVector = Icons.Default.Tune, contentDescription = null)
                                Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                                Text(text = stringResource(AYMR.strings.player_sheets_filters_title))
                            }

                            Text(
                                text = "HDR: ${stringResource(
                                    hdrMode.titleRes,
                                )} · AFR: ${if (matchRefreshRate) "ON" else "OFF"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        2 -> {
                            // Audio & Subtitles tab
                            SectionLabel(stringResource(AYMR.strings.pref_audio_channels))
                            if (wrapFilterChips) {
                                FlowRow(
                                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    AudioChannels.entries.forEach { channel ->
                                        FilterChip(
                                            selected = audioChannels == channel,
                                            onClick = {
                                                audioPreferences.audioChannels().set(channel)
                                                AudioFilterChainBuilder.applyProperties(channel, audioNormalization)
                                            },
                                            label = { Text(text = stringResource(channel.titleRes)) },
                                        )
                                    }
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    items(AudioChannels.entries) { channel ->
                                        FilterChip(
                                            selected = audioChannels == channel,
                                            onClick = {
                                                audioPreferences.audioChannels().set(channel)
                                                AudioFilterChainBuilder.applyProperties(channel, audioNormalization)
                                            },
                                            label = { Text(text = stringResource(channel.titleRes)) },
                                        )
                                    }
                                }
                            }

                            FilterChip(
                                selected = audioNormalization,
                                onClick = {
                                    val enabled = !audioNormalization
                                    audioPreferences.audioNormalization().set(enabled)
                                    AudioFilterChainBuilder.applyProperties(audioChannels, enabled)
                                },
                                label = { Text(text = stringResource(AYMR.strings.pref_player_audio_normalization)) },
                            )

                            SectionLabel(stringResource(AYMR.strings.player_quick_sync_and_style))
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                FilledTonalButton(onClick = onOpenAudioDelay) {
                                    Icon(imageVector = Icons.Default.MoreTime, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(AYMR.strings.player_sheets_audio_delay_title))
                                }
                                FilledTonalButton(onClick = onOpenSubtitleDelay) {
                                    Icon(imageVector = Icons.Default.MoreTime, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(AYMR.strings.player_sheets_sub_delay_title))
                                }
                                FilledTonalButton(onClick = onOpenSubtitleSettings) {
                                    Icon(imageVector = Icons.Default.Palette, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(AYMR.strings.player_sheets_subtitles_settings_title))
                                }
                            }
                        }

                        else -> {
                            // Tools tab: Human-readable Stats, Long-press action, Custom buttons
                            SectionLabel(stringResource(AYMR.strings.player_sheets_stats_page_title))
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                for (statsPage in 0 until 6) {
                                    FilterChip(
                                        label = {
                                            Text(text = stringResource(resolveMpvStatsPageLabelRes(statsPage)))
                                        },
                                        onClick = {
                                            if ((statsPage == 0) xor (statisticsPage == 0)) {
                                                MPVLib.command(arrayOf("script-binding", "stats/display-stats-toggle"))
                                            }
                                            if (statsPage != 0) {
                                                MPVLib.command(
                                                    arrayOf("script-binding", "stats/display-page-$statsPage"),
                                                )
                                            }
                                            advancedPreferences.playerStatisticsPage().set(statsPage)
                                        },
                                        selected = statisticsPage == statsPage,
                                    )
                                }
                            }

                            SectionLabel(stringResource(AYMR.strings.pref_long_press_action))
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                LongPressGesture.entries.forEach { gesture ->
                                    FilterChip(
                                        selected = longPressGesture == gesture,
                                        onClick = { gesturePreferences.longPressGesture().set(gesture) },
                                        label = { Text(text = stringResource(gesture.stringRes)) },
                                    )
                                }
                            }

                            if (customButtons.isNotEmpty()) {
                                SectionLabel(stringResource(AYMR.strings.player_sheets_custom_buttons_title))
                                FlowRow(
                                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.mediumSmall),
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                    maxItemsInEachRow = Int.MAX_VALUE,
                                ) {
                                    customButtons.forEach { button ->
                                        val inputChipInteractionSource = remember { MutableInteractionSource() }
                                        Box {
                                            FilterChip(
                                                onClick = {},
                                                label = { Text(text = button.name) },
                                                selected = false,
                                                interactionSource = inputChipInteractionSource,
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .combinedClickable(
                                                        onClick = { button.execute() },
                                                        onLongClick = { button.executeLongPress() },
                                                        interactionSource = inputChipInteractionSource,
                                                        indication = null,
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = AuroraTheme.colors.textSecondary,
        fontWeight = FontWeight.Medium,
    )
}

internal fun shouldWrapMoreSheetFilterChips(screenWidthDp: Int): Boolean {
    return screenWidthDp >= 600
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onTimeSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    remainingTime: Int = 0,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 480.dp)
                .animateContentSize(),
        ) {
            var currentLayoutType by rememberSaveable { mutableIntStateOf(0) }
            val state = rememberTimePickerState(
                initialHour = remainingTime / 3600,
                initialMinute = (remainingTime % 3600) / 60,
                is24Hour = true,
            )

            Column(
                modifier = Modifier
                    .padding(MaterialTheme.padding.large)
                    .width(IntrinsicSize.Min),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Text(
                    text = stringResource(AYMR.strings.timer_picker_pick_time),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (currentLayoutType == 0) {
                    TimePicker(state = state)
                } else {
                    TimeInput(state = state)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { currentLayoutType = if (currentLayoutType == 0) 1 else 0 }) {
                        Icon(
                            imageVector = if (currentLayoutType ==
                                0
                            ) {
                                Icons.Default.KeyboardAlt
                            } else {
                                Icons.Outlined.Schedule
                            },
                            contentDescription = null,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                        if (remainingTime > 0) {
                            TextButton(
                                onClick = {
                                    onTimeSelect(0)
                                    onDismissRequest()
                                },
                            ) {
                                Text(text = stringResource(AYMR.strings.timer_cancel_timer))
                            }
                        }
                        TextButton(onClick = onDismissRequest) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                        TextButton(
                            onClick = {
                                onTimeSelect(state.hour * 3600 + state.minute * 60)
                                onDismissRequest()
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_ok))
                        }
                    }
                }
            }
        }
    }
}
