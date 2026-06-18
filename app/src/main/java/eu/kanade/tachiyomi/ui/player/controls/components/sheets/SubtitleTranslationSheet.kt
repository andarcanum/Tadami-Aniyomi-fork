package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.tachiyomi.ui.player.subtitle.translation.PlayerSubtitleTranslationTrack
import eu.kanade.tachiyomi.ui.player.subtitle.translation.PlayerSubtitleTranslationTrackKind
import eu.kanade.tachiyomi.ui.player.subtitle.translation.PlayerSubtitleTranslationUiState
import eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationProviderId
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SubtitleTranslationSheet(
    state: PlayerSubtitleTranslationUiState,
    onSelectTrack: (Int) -> Unit,
    onSelectProvider: (SubtitleTranslationProviderId) -> Unit,
    onSourceLanguageChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onToggleCache: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSheet(onDismissRequest) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            TrackSheetTitle(title = stringResource(AYMR.strings.player_sheets_translate_subtitles))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.padding.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    Text(
                        text = stringResource(
                            AYMR.strings.player_subtitle_translation_from_to,
                            state.sourceLanguage.ifBlank { "auto" },
                            state.targetLanguage.ifBlank { "default" },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (state.providerId == SubtitleTranslationProviderId.Ai) {
                            stringResource(AYMR.strings.pref_player_subtitle_translation_allow_ai_summary)
                        } else {
                            stringResource(AYMR.strings.pref_player_subtitle_translation_summary)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                OutlinedTextField(
                    value = state.sourceLanguage,
                    onValueChange = onSourceLanguageChange,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isTranslating,
                    singleLine = true,
                    label = { Text(stringResource(AYMR.strings.pref_player_subtitle_translation_source_lang)) },
                )
                OutlinedTextField(
                    value = state.targetLanguage,
                    onValueChange = onTargetLanguageChange,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isTranslating,
                    singleLine = true,
                    label = { Text(stringResource(AYMR.strings.pref_player_subtitle_translation_target_lang)) },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                Text(
                    text = stringResource(AYMR.strings.pref_player_subtitle_translation_provider),
                    style = MaterialTheme.typography.titleSmall,
                )
                ProviderRow(
                    title = stringResource(AYMR.strings.player_subtitle_translation_provider_google),
                    selected = state.providerId == SubtitleTranslationProviderId.Google,
                    enabled = !state.isTranslating,
                    onClick = { onSelectProvider(SubtitleTranslationProviderId.Google) },
                )
                ProviderRow(
                    title = stringResource(AYMR.strings.player_subtitle_translation_provider_ai),
                    selected = state.providerId == SubtitleTranslationProviderId.Ai,
                    enabled = !state.isTranslating && state.allowAi,
                    onClick = { onSelectProvider(SubtitleTranslationProviderId.Ai) },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isTranslating) { onToggleCache(!state.useCache) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.useCache,
                    onCheckedChange = onToggleCache,
                    enabled = !state.isTranslating,
                )
                Text(text = stringResource(AYMR.strings.pref_player_subtitle_translation_cache))
            }

            Text(
                text = stringResource(AYMR.strings.player_subtitle_translation_select_track),
                style = MaterialTheme.typography.titleSmall,
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                items(state.tracks) { track ->
                    SubtitleTranslationTrackRow(
                        track = track,
                        selected = state.selectedTrackId == track.id,
                        enabled = track.enabled && !state.isTranslating,
                        onClick = { onSelectTrack(track.id) },
                    )
                }
            }

            state.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        AYMR.strings.player_subtitle_translation_progress,
                        progress.percent,
                        progress.translated,
                        progress.total,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (state.isTranslating) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(AYMR.strings.player_subtitle_translation_cancel))
                    }
                } else {
                    Button(
                        onClick = onStart,
                        enabled = state.selectedTrack?.enabled == true,
                    ) {
                        Text(stringResource(AYMR.strings.player_subtitle_translation_start))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.padding.small,
                vertical = MaterialTheme.padding.extraSmall,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun SubtitleTranslationTrackRow(
    track: PlayerSubtitleTranslationTrack,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.padding.small,
                vertical = MaterialTheme.padding.small,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = track.title.ifBlank { track.language.ifBlank { track.kind.name } })
                val subtitle = when {
                    track.kind == PlayerSubtitleTranslationTrackKind.Embedded ->
                        track.disabledReason
                            ?: stringResource(AYMR.strings.player_subtitle_translation_embedded_unavailable)
                    track.language.isNotBlank() -> track.language
                    else -> track.url.orEmpty()
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }
}
