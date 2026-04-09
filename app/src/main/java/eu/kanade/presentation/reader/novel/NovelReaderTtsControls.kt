package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelReaderTtsUiState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsEngineDescriptor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsVoiceDescriptor
import eu.kanade.tachiyomi.ui.reader.novel.tts.resolveNovelTtsVoiceSelection
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale
import kotlin.math.roundToInt

internal data class NovelReaderTtsControlSnapshot(
    val showControls: Boolean,
    val primaryActionIsPause: Boolean,
    val hasVoiceSettingsAccess: Boolean,
    val highlightEnabled: Boolean,
)

internal fun resolveNovelReaderTtsControlSnapshot(
    uiState: NovelReaderTtsUiState,
): NovelReaderTtsControlSnapshot {
    return NovelReaderTtsControlSnapshot(
        showControls = uiState.enabled,
        primaryActionIsPause = uiState.isPlaying,
        hasVoiceSettingsAccess = uiState.enabled,
        highlightEnabled = uiState.activeHighlightMode != NovelTtsHighlightMode.OFF,
    )
}

internal data class NovelReaderTtsOptionsSnapshot(
    val selectedEngine: NovelTtsEngineDescriptor?,
    val selectedVoice: NovelTtsVoiceDescriptor?,
    val selectedLocaleTag: String,
    val showLocaleFallback: Boolean,
)

internal data class NovelReaderTtsLanguageOptionSnapshot(
    val localeTag: String,
    val label: String,
    val voiceCount: Int,
)

internal data class NovelReaderTtsVoiceOptionSnapshot(
    val voiceId: String,
    val title: String,
    val subtitle: String?,
    val selected: Boolean,
)

internal data class NovelReaderTtsLanguagePickerSnapshot(
    val languages: List<NovelReaderTtsLanguageOptionSnapshot>,
    val recentLanguages: List<NovelReaderTtsLanguageOptionSnapshot>,
    val activeLanguageTag: String,
    val voices: List<NovelReaderTtsVoiceOptionSnapshot>,
)

internal data class NovelReaderTtsTextSnapshot(
    val unknownLanguage: String = "Unknown language",
    val defaultVoice: String = "Default voice",
    val voiceNumberPattern: String = "Voice %d",
    val networkHint: String = "Network",
    val offlineHint: String = "Offline",
    val notInstalledHint: String = "Not installed",
    val loadingVoices: String = "Loading voices...",
)

internal fun resolveNovelReaderTtsOptionsSnapshot(
    uiState: NovelReaderTtsUiState,
): NovelReaderTtsOptionsSnapshot {
    val selection = resolveNovelTtsVoiceSelection(
        availableVoices = uiState.availableVoices,
        availableLocales = uiState.availableLocales,
        capabilities = uiState.capabilities,
        preferredVoiceId = uiState.selectedVoiceId,
        preferredLocaleTag = uiState.selectedLocaleTag,
    )
    return NovelReaderTtsOptionsSnapshot(
        selectedEngine = uiState.availableEngines.firstOrNull {
            it.packageName == uiState.selectedEnginePackage
        },
        selectedVoice = selection.selectedVoice,
        selectedLocaleTag = selection.selectedLocaleTag,
        showLocaleFallback = selection.showLocaleFallback,
    )
}

internal fun formatNovelReaderTtsLocaleLabel(
    localeTag: String,
    unknownLanguageLabel: String = "Unknown language",
): String {
    val normalizedTag = localeTag.takeIf { it.isNotBlank() } ?: return unknownLanguageLabel
    val locale = runCatching { Locale.forLanguageTag(normalizedTag) }.getOrNull()
    val displayName = locale?.displayName?.takeIf { it.isNotBlank() && it != normalizedTag }
    val titleLocale = locale ?: Locale.getDefault()
    return displayName?.replaceFirstChar { character ->
        if (character.isLowerCase()) {
            character.titlecase(titleLocale)
        } else {
            character.toString()
        }
    } ?: normalizedTag
}

private fun NovelTtsVoiceDescriptor.hasTechnicalName(): Boolean {
    val normalizedName = name.trim().lowercase(Locale.ROOT)
    if (normalizedName.isBlank()) return true
    val normalizedLocale = localeTag.trim().lowercase(Locale.ROOT)
    return normalizedName == normalizedLocale ||
        normalizedName.contains("-x-") ||
        Regex("^[a-z0-9]{2,}(?:[-_][a-z0-9]{2,}){2,}$").matches(normalizedName)
}

private fun resolveNovelReaderTtsVoiceTitle(
    voice: NovelTtsVoiceDescriptor,
    indexInLanguage: Int,
    totalVoicesInLanguage: Int,
    textSnapshot: NovelReaderTtsTextSnapshot,
): String {
    val preferredName = voice.name.trim().takeIf { it.isNotBlank() && !voice.hasTechnicalName() }
    return preferredName ?: if (totalVoicesInLanguage == 1) {
        textSnapshot.defaultVoice
    } else {
        textSnapshot.voiceNumberPattern.format(Locale.getDefault(), indexInLanguage + 1)
    }
}

private fun resolveNovelReaderTtsVoiceSubtitle(
    voice: NovelTtsVoiceDescriptor,
    textSnapshot: NovelReaderTtsTextSnapshot,
): String? {
    val hints = buildList {
        if (voice.requiresNetwork) {
            add(textSnapshot.networkHint)
        } else if (voice.hasTechnicalName()) {
            add(textSnapshot.offlineHint)
        }
        if (!voice.isInstalled) {
            add(textSnapshot.notInstalledHint)
        }
    }
    return hints.takeIf { it.isNotEmpty() }?.joinToString(" - ")
}

internal fun resolveNovelReaderTtsLanguagePickerSnapshot(
    uiState: NovelReaderTtsUiState,
    browsingLanguageTag: String? = null,
    textSnapshot: NovelReaderTtsTextSnapshot = NovelReaderTtsTextSnapshot(),
): NovelReaderTtsLanguagePickerSnapshot {
    val optionsSnapshot = resolveNovelReaderTtsOptionsSnapshot(uiState)
    if (optionsSnapshot.showLocaleFallback) {
        val languages = uiState.availableLocales
            .distinct()
            .sortedBy { localeTag -> formatNovelReaderTtsLocaleLabel(localeTag, textSnapshot.unknownLanguage) }
            .map { localeTag ->
                NovelReaderTtsLanguageOptionSnapshot(
                    localeTag = localeTag,
                    label = formatNovelReaderTtsLocaleLabel(localeTag, textSnapshot.unknownLanguage),
                    voiceCount = 0,
                )
            }
        val recentLanguages = uiState.recentLanguageTags.mapNotNull { recentTag ->
            languages.firstOrNull { it.localeTag == recentTag }
        }
        val activeLanguageTag = browsingLanguageTag
            ?.takeIf { candidate -> languages.any { it.localeTag == candidate } }
            ?: optionsSnapshot.selectedLocaleTag.takeIf { selected ->
                languages.any { it.localeTag == selected }
            }
            ?: languages.firstOrNull()?.localeTag.orEmpty()
        return NovelReaderTtsLanguagePickerSnapshot(
            languages = languages,
            recentLanguages = recentLanguages,
            activeLanguageTag = activeLanguageTag,
            voices = emptyList(),
        )
    }

    val voicesByLanguage = uiState.availableVoices
        .groupBy { voice -> voice.localeTag.takeIf { it.isNotBlank() } ?: uiState.selectedLocaleTag }
    val languages = voicesByLanguage
        .keys
        .sortedBy { localeTag -> formatNovelReaderTtsLocaleLabel(localeTag, textSnapshot.unknownLanguage) }
        .map { localeTag ->
            NovelReaderTtsLanguageOptionSnapshot(
                localeTag = localeTag,
                label = formatNovelReaderTtsLocaleLabel(localeTag, textSnapshot.unknownLanguage),
                voiceCount = voicesByLanguage[localeTag].orEmpty().size,
            )
        }
    val recentLanguages = uiState.recentLanguageTags.mapNotNull { recentTag ->
        languages.firstOrNull { it.localeTag == recentTag }
    }
    val activeLanguageTag = browsingLanguageTag
        ?.takeIf { candidate -> languages.any { it.localeTag == candidate } }
        ?: optionsSnapshot.selectedVoice?.localeTag?.takeIf { selected ->
            languages.any { it.localeTag == selected }
        }
        ?: optionsSnapshot.selectedLocaleTag.takeIf { selected ->
            languages.any { it.localeTag == selected }
        }
        ?: languages.firstOrNull()?.localeTag.orEmpty()
    val voicesForLanguage = voicesByLanguage[activeLanguageTag].orEmpty()
    return NovelReaderTtsLanguagePickerSnapshot(
        languages = languages,
        recentLanguages = recentLanguages,
        activeLanguageTag = activeLanguageTag,
        voices = voicesForLanguage.mapIndexed { index, voice ->
            NovelReaderTtsVoiceOptionSnapshot(
                voiceId = voice.id,
                title = resolveNovelReaderTtsVoiceTitle(
                    voice = voice,
                    indexInLanguage = index,
                    totalVoicesInLanguage = voicesForLanguage.size,
                    textSnapshot = textSnapshot,
                ),
                subtitle = resolveNovelReaderTtsVoiceSubtitle(voice, textSnapshot),
                selected = voice.id == optionsSnapshot.selectedVoice?.id,
            )
        },
    )
}

internal fun filterNovelReaderTtsLanguages(
    languages: List<NovelReaderTtsLanguageOptionSnapshot>,
    query: String,
): List<NovelReaderTtsLanguageOptionSnapshot> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isBlank()) return languages
    return languages.filter { language ->
        language.label.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            language.localeTag.lowercase(Locale.ROOT).contains(normalizedQuery)
    }
}

internal fun resolveNovelReaderTtsSummaryLine(
    uiState: NovelReaderTtsUiState,
    snapshot: NovelReaderTtsOptionsSnapshot,
    textSnapshot: NovelReaderTtsTextSnapshot = NovelReaderTtsTextSnapshot(),
): String? {
    if (uiState.isLoadingVoices) return textSnapshot.loadingVoices
    val selectedVoiceTitle = snapshot.selectedVoice?.let { selectedVoice ->
        val voicesInLanguage = uiState.availableVoices.filter { it.localeTag == selectedVoice.localeTag }
        val indexInLanguage = voicesInLanguage.indexOfFirst { it.id == selectedVoice.id }
            .takeIf { it >= 0 }
            ?: 0
        resolveNovelReaderTtsVoiceTitle(
            voice = selectedVoice,
            indexInLanguage = indexInLanguage,
            totalVoicesInLanguage = voicesInLanguage.size.coerceAtLeast(1),
            textSnapshot = textSnapshot,
        )
    }
    val parts = buildList {
        snapshot.selectedEngine?.label?.takeIf { it.isNotBlank() }?.let(::add)
        selectedVoiceTitle?.takeIf { it.isNotBlank() }?.let(::add)
        snapshot.selectedLocaleTag.takeIf { it.isNotBlank() }?.let {
            add(formatNovelReaderTtsLocaleLabel(it, textSnapshot.unknownLanguage))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" - ")
}

@Composable
private fun rememberNovelReaderTtsTextSnapshot(): NovelReaderTtsTextSnapshot {
    return NovelReaderTtsTextSnapshot(
        unknownLanguage = stringResource(AYMR.strings.novel_reader_tts_unknown_language),
        defaultVoice = stringResource(AYMR.strings.novel_reader_tts_default_voice),
        voiceNumberPattern = stringResource(AYMR.strings.novel_reader_tts_voice_number),
        networkHint = stringResource(AYMR.strings.novel_reader_tts_voice_hint_network),
        offlineHint = stringResource(AYMR.strings.novel_reader_tts_voice_hint_offline),
        notInstalledHint = stringResource(AYMR.strings.novel_reader_tts_voice_hint_not_installed),
        loadingVoices = stringResource(AYMR.strings.novel_reader_tts_loading_voices),
    )
}

@Composable
internal fun NovelReaderTtsControls(
    uiState: NovelReaderTtsUiState,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSetEnginePackage: (String) -> Unit,
    onSetVoiceId: (String) -> Unit,
    onSetLocaleTag: (String) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = resolveNovelReaderTtsControlSnapshot(uiState)
    val optionsSnapshot = resolveNovelReaderTtsOptionsSnapshot(uiState)
    val textSnapshot = rememberNovelReaderTtsTextSnapshot()
    if (!snapshot.showControls) return

    var showOptions by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            uiState.activeUtteranceText?.takeIf { it.isNotBlank() }?.let { utteranceText ->
                Text(
                    text = utteranceText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            resolveNovelReaderTtsSummaryLine(uiState, optionsSnapshot, textSnapshot)?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onStop) {
                Icon(
                    Icons.Outlined.Stop,
                    contentDescription = stringResource(AYMR.strings.novel_reader_tts_action_stop),
                )
            }

            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconButton(onClick = onSkipPrevious) {
                    Icon(
                        Icons.Outlined.SkipPrevious,
                        contentDescription = stringResource(AYMR.strings.novel_reader_tts_action_previous),
                    )
                }

                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        imageVector = if (snapshot.primaryActionIsPause) {
                            Icons.Outlined.Pause
                        } else {
                            Icons.Outlined.PlayArrow
                        },
                        contentDescription = if (snapshot.primaryActionIsPause) {
                            stringResource(AYMR.strings.novel_reader_tts_action_pause)
                        } else {
                            stringResource(AYMR.strings.novel_reader_tts_action_play)
                        },
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(onClick = onSkipNext) {
                    Icon(
                        Icons.Outlined.SkipNext,
                        contentDescription = stringResource(AYMR.strings.novel_reader_tts_action_next),
                    )
                }
            }

            IconButton(onClick = { showOptions = true }) {
                Icon(
                    imageVector = Icons.Outlined.SettingsVoice,
                    contentDescription = stringResource(AYMR.strings.novel_reader_tts_voice_settings),
                )
            }
        }

        uiState.errorMessage?.takeIf { it.isNotBlank() }?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (showOptions) {
        NovelReaderTtsOptionsDialog(
            uiState = uiState,
            onDismiss = { showOptions = false },
            onSetEnginePackage = onSetEnginePackage,
            onSetVoiceId = onSetVoiceId,
            onSetLocaleTag = onSetLocaleTag,
            onSetSpeechRate = onSetSpeechRate,
            onSetPitch = onSetPitch,
        )
    }
}

@Composable
private fun NovelReaderTtsOptionsDialog(
    uiState: NovelReaderTtsUiState,
    onDismiss: () -> Unit,
    onSetEnginePackage: (String) -> Unit,
    onSetVoiceId: (String) -> Unit,
    onSetLocaleTag: (String) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
) {
    val snapshot = resolveNovelReaderTtsOptionsSnapshot(uiState)
    val textSnapshot = rememberNovelReaderTtsTextSnapshot()
    var browsingLanguageTag by remember(
        uiState.selectedEnginePackage,
        uiState.availableVoices,
        uiState.availableLocales,
        uiState.isLoadingVoices,
        snapshot.showLocaleFallback,
    ) {
        mutableStateOf(
            resolveNovelReaderTtsLanguagePickerSnapshot(uiState).activeLanguageTag,
        )
    }
    var languageSearchQuery by remember(
        uiState.selectedEnginePackage,
        snapshot.showLocaleFallback,
    ) {
        mutableStateOf("")
    }
    val languagePicker = resolveNovelReaderTtsLanguagePickerSnapshot(
        uiState = uiState,
        browsingLanguageTag = browsingLanguageTag,
        textSnapshot = textSnapshot,
    )
    val filteredLanguages = filterNovelReaderTtsLanguages(
        languages = languagePicker.languages,
        query = languageSearchQuery,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(AYMR.strings.novel_reader_tts_section)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_tts_engine),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (uiState.availableEngines.isEmpty()) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_tts_no_engines_found),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    uiState.availableEngines.forEach { engine ->
                        NovelReaderTtsSelectableCard(
                            title = engine.label,
                            subtitle = if (engine.isSystemDefault) {
                                stringResource(AYMR.strings.novel_reader_tts_system_default_engine)
                            } else {
                                null
                            },
                            selected = engine.packageName == snapshot.selectedEngine?.packageName,
                            onClick = { onSetEnginePackage(engine.packageName) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (uiState.isLoadingVoices) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_tts_loading_voices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (snapshot.showLocaleFallback) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_tts_language),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (languagePicker.recentLanguages.isNotEmpty()) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_tts_recent_languages),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            languagePicker.recentLanguages.forEach { language ->
                                NovelReaderTtsSelectableCard(
                                    title = language.label,
                                    selected = language.localeTag == languagePicker.activeLanguageTag,
                                    onClick = {
                                        browsingLanguageTag = language.localeTag
                                        languageSearchQuery = ""
                                        onSetLocaleTag(language.localeTag)
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = languageSearchQuery,
                        onValueChange = { languageSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(AYMR.strings.novel_reader_tts_find_language)) },
                    )
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_tts_locale_fallback_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (filteredLanguages.isEmpty()) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_tts_no_languages_available),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            filteredLanguages.forEach { language ->
                                NovelReaderTtsSelectableCard(
                                    title = language.label,
                                    selected = language.localeTag == languagePicker.activeLanguageTag,
                                    onClick = {
                                        browsingLanguageTag = language.localeTag
                                        languageSearchQuery = ""
                                        onSetLocaleTag(language.localeTag)
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_tts_language),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (languagePicker.recentLanguages.isNotEmpty()) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_tts_recent_languages),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            languagePicker.recentLanguages.forEach { language ->
                                NovelReaderTtsSelectableCard(
                                    title = language.label,
                                    subtitle = stringResource(
                                        AYMR.strings.novel_reader_tts_voice_count,
                                        language.voiceCount,
                                    ),
                                    selected = language.localeTag == languagePicker.activeLanguageTag,
                                    onClick = {
                                        browsingLanguageTag = language.localeTag
                                        languageSearchQuery = ""
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = languageSearchQuery,
                        onValueChange = { languageSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(AYMR.strings.novel_reader_tts_find_language)) },
                    )
                    if (filteredLanguages.isEmpty()) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_tts_no_languages_available),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            filteredLanguages.forEach { language ->
                                NovelReaderTtsSelectableCard(
                                    title = language.label,
                                    subtitle = stringResource(
                                        AYMR.strings.novel_reader_tts_voice_count,
                                        language.voiceCount,
                                    ),
                                    selected = language.localeTag == languagePicker.activeLanguageTag,
                                    onClick = {
                                        browsingLanguageTag = language.localeTag
                                        languageSearchQuery = ""
                                    },
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(AYMR.strings.novel_reader_tts_voice),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (languagePicker.voices.isEmpty()) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_tts_no_voices_for_selected_language),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        languagePicker.voices.forEach { voice ->
                            NovelReaderTtsSelectableCard(
                                title = voice.title,
                                subtitle = voice.subtitle,
                                selected = voice.selected,
                                onClick = { onSetVoiceId(voice.voiceId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = stringResource(
                        AYMR.strings.novel_reader_tts_speech_rate_value,
                        (uiState.speechRate * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = uiState.speechRate.coerceIn(0.5f, 2f),
                    onValueChange = onSetSpeechRate,
                    valueRange = 0.5f..2f,
                )
                Text(
                    text = stringResource(
                        AYMR.strings.novel_reader_tts_pitch_value,
                        (uiState.pitch * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = uiState.pitch.coerceIn(0.5f, 2f),
                    onValueChange = onSetPitch,
                    valueRange = 0.5f..2f,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(AYMR.strings.novel_reader_selected_text_translation_action_close))
            }
        },
    )
}

@Composable
private fun NovelReaderTtsSelectableCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
