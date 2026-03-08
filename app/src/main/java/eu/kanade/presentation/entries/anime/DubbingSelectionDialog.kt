package eu.kanade.presentation.entries.anime

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.entries.anime.AvailablePlaybackDubbings
import eu.kanade.tachiyomi.ui.player.PlaybackPlayerPreference
import eu.kanade.tachiyomi.ui.player.PlaybackSelectionPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DubbingSelectionDialog(
    availableDubbings: AvailablePlaybackDubbings,
    currentPreferences: PlaybackSelectionPreferences,
    onDismissRequest: () -> Unit,
    onConfirm: (PlaybackSelectionPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val availableCdnDubbings = availableDubbings.cdn
    val availableKodikDubbings = availableDubbings.kodik
    val availableAllohaDubbings = availableDubbings.alloha
    val playerOptions = buildList {
        add(PlaybackPlayerPreference.AUTO)
        if (availableCdnDubbings.isNotEmpty()) add(PlaybackPlayerPreference.CDN)
        if (availableKodikDubbings.isNotEmpty()) add(PlaybackPlayerPreference.KODIK)
        if (availableAllohaDubbings.isNotEmpty()) add(PlaybackPlayerPreference.ALLOHA)
    }
    val autoEffectivePlayer = when {
        availableCdnDubbings.isNotEmpty() -> PlaybackPlayerPreference.CDN
        availableKodikDubbings.isNotEmpty() -> PlaybackPlayerPreference.KODIK
        availableAllohaDubbings.isNotEmpty() -> PlaybackPlayerPreference.ALLOHA
        else -> PlaybackPlayerPreference.CDN
    }
    var selectedPlayer by remember(playerOptions, currentPreferences.preferredPlayer) {
        mutableStateOf(
            currentPreferences.preferredPlayer.takeIf { it in playerOptions } ?: playerOptions.first(),
        )
    }
    var selectedDubbingCdn by remember {
        mutableStateOf(currentPreferences.preferredDubbingCdn.ifBlank { availableCdnDubbings.firstOrNull() ?: "" })
    }
    var selectedDubbingKodik by remember {
        mutableStateOf(currentPreferences.preferredDubbingKodik.ifBlank { availableKodikDubbings.firstOrNull() ?: "" })
    }
    var selectedDubbingAlloha by remember {
        mutableStateOf(
            currentPreferences.preferredDubbingAlloha.ifBlank {
                availableAllohaDubbings.firstOrNull() ?: ""
            },
        )
    }
    var selectedQualityCdn by remember { mutableStateOf(currentPreferences.preferredQualityCdn.ifBlank { "best" }) }
    var selectedQualityKodik by remember { mutableStateOf(currentPreferences.preferredQualityKodik.ifBlank { "best" }) }
    var selectedQualityAlloha by remember {
        mutableStateOf(currentPreferences.preferredQualityAlloha.ifBlank { "best" })
    }

    val qualityOptions = listOf("best", "1080p", "720p", "480p", "360p")
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val effectivePlayer = if (selectedPlayer == PlaybackPlayerPreference.AUTO) autoEffectivePlayer else selectedPlayer
    val selectedDubbing = when (effectivePlayer) {
        PlaybackPlayerPreference.CDN, PlaybackPlayerPreference.AUTO -> selectedDubbingCdn
        PlaybackPlayerPreference.KODIK -> selectedDubbingKodik
        PlaybackPlayerPreference.ALLOHA -> selectedDubbingAlloha
    }
    val activeDubbings = when (effectivePlayer) {
        PlaybackPlayerPreference.CDN, PlaybackPlayerPreference.AUTO -> availableCdnDubbings
        PlaybackPlayerPreference.KODIK -> availableKodikDubbings
        PlaybackPlayerPreference.ALLOHA -> availableAllohaDubbings
    }
    val selectedQuality = when (effectivePlayer) {
        PlaybackPlayerPreference.CDN, PlaybackPlayerPreference.AUTO -> selectedQualityCdn
        PlaybackPlayerPreference.KODIK -> selectedQualityKodik
        PlaybackPlayerPreference.ALLOHA -> selectedQualityAlloha
    }

    AdaptiveSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    vertical = TabbedDialogPaddings.Vertical,
                    horizontal = TabbedDialogPaddings.Horizontal,
                )
                .fillMaxWidth(),
        ) {
            Text(
                modifier = Modifier.padding(bottom = 16.dp, top = 8.dp),
                text = "Playback Preferences",
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = "Player",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            playerOptions.forEach { player ->
                DubbingRadioItem(
                    text = when (player) {
                        PlaybackPlayerPreference.AUTO -> "Auto"
                        PlaybackPlayerPreference.CDN -> "CDN"
                        PlaybackPlayerPreference.KODIK -> "Kodik"
                        PlaybackPlayerPreference.ALLOHA -> "Alloha"
                    },
                    selected = selectedPlayer == player,
                    onClick = { selectedPlayer = player },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dubbing",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            items(activeDubbings) { dubbing ->
                                DubbingRadioItem(
                                    text = dubbing,
                                    selected = selectedDubbing == dubbing,
                                    onClick = {
                                        when (effectivePlayer) {
                                            PlaybackPlayerPreference.CDN, PlaybackPlayerPreference.AUTO -> {
                                                selectedDubbingCdn = dubbing
                                            }
                                            PlaybackPlayerPreference.KODIK -> {
                                                selectedDubbingKodik = dubbing
                                            }
                                            PlaybackPlayerPreference.ALLOHA -> {
                                                selectedDubbingAlloha = dubbing
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    VerticalDivider()

                    Column(modifier = Modifier.weight(0.6f)) {
                        Text(
                            text = "Quality",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            items(qualityOptions) { quality ->
                                DubbingRadioItem(
                                    text = if (quality == "best") "Best Available" else quality,
                                    selected = selectedQuality == quality,
                                    onClick = {
                                        when (effectivePlayer) {
                                            PlaybackPlayerPreference.CDN, PlaybackPlayerPreference.AUTO -> {
                                                selectedQualityCdn = quality
                                            }
                                            PlaybackPlayerPreference.KODIK -> {
                                                selectedQualityKodik = quality
                                            }
                                            PlaybackPlayerPreference.ALLOHA -> {
                                                selectedQualityAlloha = quality
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Dubbing",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    activeDubbings.forEach { dubbing ->
                        DubbingRadioItem(
                            text = dubbing,
                            selected = selectedDubbing == dubbing,
                            onClick = {
                                when (effectivePlayer) {
                                    PlaybackPlayerPreference.CDN, PlaybackPlayerPreference.AUTO -> {
                                        selectedDubbingCdn = dubbing
                                    }
                                    PlaybackPlayerPreference.KODIK -> {
                                        selectedDubbingKodik = dubbing
                                    }
                                    PlaybackPlayerPreference.ALLOHA -> {
                                        selectedDubbingAlloha = dubbing
                                    }
                                }
                            },
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text(
                        text = "Quality",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    qualityOptions.forEach { quality ->
                        DubbingRadioItem(
                            text = if (quality == "best") "Best Available" else quality,
                            selected = selectedQuality == quality,
                            onClick = {
                                when (effectivePlayer) {
                                    PlaybackPlayerPreference.CDN, PlaybackPlayerPreference.AUTO -> {
                                        selectedQualityCdn = quality
                                    }
                                    PlaybackPlayerPreference.KODIK -> {
                                        selectedQualityKodik = quality
                                    }
                                    PlaybackPlayerPreference.ALLOHA -> {
                                        selectedQualityAlloha = quality
                                    }
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(MR.strings.action_cancel))
                }
                Button(
                    onClick = {
                        onConfirm(
                            PlaybackSelectionPreferences(
                                preferredPlayer = selectedPlayer,
                                preferredDubbingCdn = selectedDubbingCdn,
                                preferredDubbingKodik = selectedDubbingKodik,
                                preferredDubbingAlloha = selectedDubbingAlloha,
                                preferredQualityCdn = selectedQualityCdn,
                                preferredQualityKodik = selectedQualityKodik,
                                preferredQualityAlloha = selectedQualityAlloha,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(MR.strings.action_save))
                }
            }
        }
    }
}

@Composable
private fun DubbingRadioItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
