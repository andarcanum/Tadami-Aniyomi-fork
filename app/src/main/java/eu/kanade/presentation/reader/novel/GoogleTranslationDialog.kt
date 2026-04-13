package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationPhase
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GoogleTranslationDialog(
    readerSettings: NovelReaderSettings,
    isTranslating: Boolean,
    translationProgress: Int,
    translationPhase: TranslationPhase = TranslationPhase.IDLE,
    isVisible: Boolean,
    hasCache: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
    onToggleVisibility: () -> Unit,
    onClear: () -> Unit,
    onSetAutoStart: (Boolean) -> Unit,
    onSetSourceLang: (String) -> Unit,
    onSetTargetLang: (String) -> Unit,
    onSetMlKitPreferOffline: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedResume = onResume
    var sourceLang by remember(readerSettings.googleTranslationSourceLang) {
        mutableStateOf(readerSettings.googleTranslationSourceLang)
    }
    var targetLang by remember(readerSettings.googleTranslationTargetLang) {
        mutableStateOf(readerSettings.googleTranslationTargetLang)
    }
    LaunchedEffect(readerSettings.googleTranslationSourceLang) {
        sourceLang = readerSettings.googleTranslationSourceLang
    }
    LaunchedEffect(readerSettings.googleTranslationTargetLang) {
        targetLang = readerSettings.googleTranslationTargetLang
    }
    var sourceExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    var showMlKitModelsDialog by remember { mutableStateOf(false) }
    var autoStartDraft by remember {
        mutableStateOf(readerSettings.googleTranslationAutoStart)
    }
    var previousAutoStartCommitted by remember {
        mutableStateOf<Boolean?>(null)
    }
    LaunchedEffect(readerSettings.googleTranslationAutoStart) {
        val synced = syncGoogleTranslationToggleDraft(
            committedValue = readerSettings.googleTranslationAutoStart,
            previousCommittedValue = previousAutoStartCommitted,
            currentDraftValue = autoStartDraft,
        )
        previousAutoStartCommitted = synced.committedValue
        autoStartDraft = synced.draftValue
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(AYMR.strings.novel_reader_google_translate))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GoogleTranslationLanguageField(
                    value = sourceLang,
                    onValueChange = {
                        sourceLang = it
                        onSetSourceLang(it)
                    },
                    label = stringResource(AYMR.strings.novel_reader_google_translate_source),
                    placeholder = "auto",
                    expanded = sourceExpanded,
                    onExpandedChange = { sourceExpanded = it },
                    enabled = !isTranslating,
                )

                GoogleTranslationLanguageField(
                    value = targetLang,
                    onValueChange = {
                        targetLang = it
                        onSetTargetLang(it)
                    },
                    label = stringResource(AYMR.strings.novel_reader_google_translate_target),
                    placeholder = "ru",
                    expanded = targetExpanded,
                    onExpandedChange = { targetExpanded = it },
                    enabled = !isTranslating,
                )

                Text(stringResource(AYMR.strings.novel_reader_google_translate_backend_simple))

                if (onSetMlKitPreferOffline != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_mlkit_prefer_offline),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = readerSettings.mlKitPreferOffline,
                            onCheckedChange = { onSetMlKitPreferOffline(it) },
                            enabled = !isTranslating,
                        )
                    }
                }

                if (onSetMlKitPreferOffline != null) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_mlkit_prefer_offline_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { showMlKitModelsDialog = true },
                        enabled = !isTranslating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_mlkit_manage_models),
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_google_translate_auto_start),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    )
                    Switch(
                        checked = autoStartDraft,
                        onCheckedChange = {
                            autoStartDraft = it
                            onSetAutoStart(it)
                        },
                        enabled = !isTranslating,
                    )
                }

                if (isTranslating) {
                    LinearProgressIndicator(
                        progress = { translationProgress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (translationPhase == TranslationPhase.PREPARING_MODEL) {
                            stringResource(AYMR.strings.novel_reader_translation_preparing_model)
                        } else {
                            stringResource(AYMR.strings.novel_reader_google_translate_progress, translationProgress)
                        },
                    )
                }

                if (hasCache) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onToggleVisibility,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 56.dp),
                        ) {
                            Text(
                                if (isVisible) {
                                    stringResource(AYMR.strings.novel_reader_google_translate_original)
                                } else {
                                    stringResource(AYMR.strings.novel_reader_google_translate_translated)
                                },
                                maxLines = 2,
                            )
                        }
                        OutlinedButton(
                            onClick = onClear,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 56.dp),
                        ) {
                            Text(
                                stringResource(AYMR.strings.novel_reader_google_translate_clear),
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        isTranslating -> {
                            Button(
                                onClick = onStop,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 56.dp),
                            ) {
                                Text(
                                    stringResource(AYMR.strings.novel_reader_google_translate_stop),
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        hasCache && !isVisible -> {
                            Button(
                                onClick = onToggleVisibility,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 56.dp),
                            ) {
                                Text(
                                    stringResource(AYMR.strings.novel_reader_google_translate_show_translation),
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        hasCache && isVisible -> {
                            Button(
                                onClick = onToggleVisibility,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 56.dp),
                            ) {
                                Text(
                                    stringResource(AYMR.strings.novel_reader_google_translate_hide_translation),
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        else -> {
                            Button(
                                onClick = onStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 56.dp),
                            ) {
                                Text(
                                    stringResource(AYMR.strings.novel_reader_google_translate_start),
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    if (hasCache && !isTranslating) {
                        OutlinedButton(
                            onClick = {
                                onClear()
                                onStart()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 56.dp),
                        ) {
                            Text(
                                stringResource(AYMR.strings.novel_reader_google_translate_retranslate),
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_close))
            }
        },
    )

    if (showMlKitModelsDialog) {
        MlKitTranslationModelsDialog(
            onDismiss = { showMlKitModelsDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoogleTranslationLanguageField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    val suggestions = remember(value) {
        googleTranslationLanguageSuggestions(value)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = {
            if (enabled && suggestions.isNotEmpty()) {
                onExpandedChange(!expanded)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                onExpandedChange(it.isNotBlank() && googleTranslationLanguageSuggestions(it).isNotEmpty())
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && suggestions.isNotEmpty())
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
        )

        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { onExpandedChange(false) },
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = {
                        Text("${suggestion.canonicalName} (${suggestion.code})")
                    },
                    onClick = {
                        onValueChange(suggestion.canonicalName)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

data class GoogleTranslationToggleDraftState(
    val committedValue: Boolean,
    val draftValue: Boolean,
)

fun syncGoogleTranslationToggleDraft(
    committedValue: Boolean,
    previousCommittedValue: Boolean?,
    currentDraftValue: Boolean,
): GoogleTranslationToggleDraftState {
    val draftValue = if (previousCommittedValue == null || committedValue != previousCommittedValue) {
        committedValue
    } else {
        currentDraftValue
    }
    return GoogleTranslationToggleDraftState(
        committedValue = committedValue,
        draftValue = draftValue,
    )
}

data class GoogleTranslationLanguageSuggestion(
    val canonicalName: String,
    val code: String,
)

private data class GoogleTranslationLanguageEntry(
    val canonicalName: String,
    val code: String,
    val aliases: List<String>,
)

private val googleTranslationLanguageEntries = listOf(
    GoogleTranslationLanguageEntry("Auto", "auto", listOf("auto", "авто", "automatic", "автоматически")),
    GoogleTranslationLanguageEntry("English", "en", listOf("english", "eng", "англ", "английский", "en")),
    GoogleTranslationLanguageEntry("Russian", "ru", listOf("russian", "рус", "русский", "ru")),
    GoogleTranslationLanguageEntry("Japanese", "ja", listOf("japanese", "jpn", "япон", "японский", "ja")),
    GoogleTranslationLanguageEntry("Chinese", "zh-CN", listOf("chinese", "china", "китай", "китайский", "zh")),
    GoogleTranslationLanguageEntry("Korean", "ko", listOf("korean", "корей", "корейский", "ko")),
    GoogleTranslationLanguageEntry("German", "de", listOf("german", "deutsch", "нем", "немецкий", "de")),
    GoogleTranslationLanguageEntry("French", "fr", listOf("french", "франц", "французский", "fr")),
    GoogleTranslationLanguageEntry("Spanish", "es", listOf("spanish", "espanol", "испан", "испанский", "es")),
    GoogleTranslationLanguageEntry("Italian", "it", listOf("italian", "итал", "итальянский", "it")),
    GoogleTranslationLanguageEntry("Portuguese", "pt", listOf("portuguese", "португ", "португальский", "pt")),
    GoogleTranslationLanguageEntry("Ukrainian", "uk", listOf("ukrainian", "украин", "украинский", "uk")),
    GoogleTranslationLanguageEntry("Polish", "pl", listOf("polish", "поль", "польский", "pl")),
    GoogleTranslationLanguageEntry("Turkish", "tr", listOf("turkish", "турец", "турецкий", "tr")),
    GoogleTranslationLanguageEntry("Arabic", "ar", listOf("arabic", "араб", "арабский", "ar")),
    GoogleTranslationLanguageEntry("Hindi", "hi", listOf("hindi", "хинди", "hi")),
    GoogleTranslationLanguageEntry("Thai", "th", listOf("thai", "тай", "тайский", "th")),
    GoogleTranslationLanguageEntry("Vietnamese", "vi", listOf("vietnamese", "вьет", "вьетнамский", "vi")),
    GoogleTranslationLanguageEntry("Indonesian", "id", listOf("indonesian", "индонез", "индонезийский", "id")),
)

fun googleTranslationLanguageSuggestions(input: String): List<GoogleTranslationLanguageSuggestion> {
    val normalizedInput = input.trim().lowercase()
    if (normalizedInput.isBlank()) return emptyList()

    return googleTranslationLanguageEntries
        .asSequence()
        .filter { entry ->
            entry.canonicalName.lowercase().contains(normalizedInput) ||
                entry.code.lowercase().startsWith(normalizedInput) ||
                entry.aliases.any { alias -> alias.lowercase().contains(normalizedInput) }
        }
        .map { entry ->
            GoogleTranslationLanguageSuggestion(
                canonicalName = entry.canonicalName,
                code = entry.code,
            )
        }
        .take(8)
        .toList()
}
