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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.presentation.player.components.SliderItem
import eu.kanade.presentation.player.components.SwitchPreference
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

private const val SPEED_PRESET_EPSILON = 0.005f
private const val SPEED_SLIDER_MIN = 0.25f
private const val SPEED_SLIDER_MAX = 6.0f
private const val SPEED_SLIDER_STEP = 0.05f

internal fun snapPlaybackSpeed(value: Float): Float {
    val clamped = value.coerceIn(SPEED_SLIDER_MIN, SPEED_SLIDER_MAX)
    val snapped = (clamped / SPEED_SLIDER_STEP).roundToInt() * SPEED_SLIDER_STEP
    return snapped.toFixed(2)
}

internal fun parseSortedSpeedPresets(rawPresets: Set<String>): List<Float> {
    return rawPresets
        .mapNotNull { it.toFloatOrNull()?.toFixed(2) }
        .distinct()
        .sorted()
}

internal fun removeSpeedPreset(rawPresets: Set<String>, target: Float): Set<String> {
    return rawPresets.filterTo(LinkedHashSet()) { raw ->
        val parsed = raw.toFloatOrNull()
        parsed == null || abs(parsed - target) >= SPEED_PRESET_EPSILON
    }
}

internal fun addSpeedPreset(rawPresets: Set<String>, target: Float): Set<String> {
    val normalized = target.toFixed(2)
    val alreadyPresent = rawPresets.any { raw ->
        val parsed = raw.toFloatOrNull()
        parsed != null && abs(parsed - normalized) < SPEED_PRESET_EPSILON
    }
    return if (alreadyPresent) rawPresets else rawPresets + normalized.toString()
}

@Composable
fun PlaybackSpeedSheet(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = remember { Injekt.get<PlayerPreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }
    PlayerSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = MaterialTheme.padding.medium),
        ) {
            SliderItem(
                label = stringResource(AYMR.strings.player_sheets_speed_slider_label),
                value = speed.coerceIn(SPEED_SLIDER_MIN, SPEED_SLIDER_MAX),
                valueText = stringResource(AYMR.strings.player_speed, speed),
                onChange = { rawValue -> onSpeedChange(snapPlaybackSpeed(rawValue)) },
                max = SPEED_SLIDER_MAX,
                min = SPEED_SLIDER_MIN,
            )
            val playbackSpeedPresets by preferences.speedPresets().collectAsState()
            val sortedPresets = remember(playbackSpeedPresets) {
                parseSortedSpeedPresets(playbackSpeedPresets)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                FilledTonalIconButton(onClick = {
                    preferences.speedPresets().delete()
                }) {
                    Icon(Icons.Default.RestartAlt, null)
                }
                LazyRow(
                    modifier = Modifier
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    items(
                        sortedPresets,
                        key = { it },
                    ) { presetSpeed ->
                        InputChip(
                            selected = abs(speed - presetSpeed) < SPEED_PRESET_EPSILON,
                            onClick = { onSpeedChange(presetSpeed) },
                            label = { Text(stringResource(AYMR.strings.player_speed, presetSpeed)) },
                            modifier = Modifier
                                .animateItem(),
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    null,
                                    modifier = Modifier
                                        .clickable {
                                            preferences.speedPresets().set(
                                                removeSpeedPreset(playbackSpeedPresets, presetSpeed),
                                            )
                                        },
                                )
                            },
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = {
                        preferences.speedPresets().set(addSpeedPreset(playbackSpeedPresets, speed))
                    },
                ) {
                    Icon(Icons.Default.Add, null)
                }
            }
            val pitchCorrection by audioPreferences.enablePitchCorrection().collectAsState()
            SwitchPreference(
                value = pitchCorrection,
                onValueChange = {
                    audioPreferences.enablePitchCorrection().set(it)
                    MPVLib.setPropertyBoolean("audio-pitch-correction", it)
                },
                content = {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(AYMR.strings.pref_audio_pitch_correction_title))
                        Text(
                            text = stringResource(AYMR.strings.pref_audio_pitch_correction_summary),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { preferences.playerSpeed().set(speed) },
                ) {
                    Text(text = stringResource(AYMR.strings.player_sheets_speed_make_default))
                }
                FilledIconButton(
                    onClick = {
                        preferences.playerSpeed().delete()
                        onSpeedChange(1f)
                    },
                ) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null)
                }
            }
        }
    }
}

fun Float.toFixed(precision: Int = 1): Float {
    val factor = 10.0f.pow(precision)
    return (this * factor).roundToInt() / factor
}
