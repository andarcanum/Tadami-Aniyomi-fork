@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraNavRow
import eu.kanade.presentation.reader.settings.AuroraToggleRow
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.replace.NovelTextReplaceRulesScreen
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

@Composable
fun ToolsTab(
    settings: eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings,
    sourceId: Long,
    overrideEnabled: Boolean,
    preferences: NovelReaderPreferences,
) {
    fun <T> update(
        value: T,
        copyOverride: (NovelReaderOverride, T) -> NovelReaderOverride,
        setGlobal: (T) -> Unit,
    ) {
        if (overrideEnabled) {
            preferences.updateSourceOverride(sourceId) { copyOverride(it, value) }
        } else {
            setGlobal(value)
        }
    }

    val dictionaryLanguages = persistentMapOf(
        "en" to "English",
        "ru" to "Русский",
        "ja" to "日本語 (Japanese)",
        "zh" to "中文 (Chinese)",
        "ko" to "한국어 (Korean)",
        "es" to "Español (Spanish)",
        "fr" to "Français (French)",
        "de" to "Deutsch (German)",
        "it" to "Italiano (Italian)",
        "pt" to "Português (Portuguese)",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Озвучка текста (TTS)
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_tts_section)) {
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_tts_enabled),
                subtitle = stringResource(AYMR.strings.novel_reader_tts_enabled_summary),
                checked = settings.ttsEnabled,
                onClick = {
                    update(
                        !settings.ttsEnabled,
                        { o, v -> o.copy(ttsEnabled = v) },
                        { preferences.ttsEnabled().set(it) },
                    )
                },
            )
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_tts_speech_rate),
                valueText = { formatTtsPercentage(it / 100f) },
                committedValue = settings.ttsSpeechRate * 100f,
                range = 50f..200f,
                steps = 149,
                enabled = true,
                onCommit = {
                    update(
                        it / 100f,
                        { o, v -> o.copy(ttsSpeechRate = v) },
                        { preferences.ttsSpeechRate().set(it) },
                    )
                },
            )
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_tts_pitch),
                valueText = { formatTtsPercentage(it / 100f) },
                committedValue = settings.ttsPitch * 100f,
                range = 50f..200f,
                steps = 149,
                enabled = true,
                onCommit = {
                    update(
                        it / 100f,
                        { o, v -> o.copy(ttsPitch = v) },
                        { preferences.ttsPitch().set(it) },
                    )
                },
            )
            ListPreferenceWidget(
                value = settings.ttsHighlightMode,
                title = stringResource(AYMR.strings.novel_reader_tts_highlight_mode),
                subtitle = stringResource(AYMR.strings.novel_reader_tts_highlight_mode_summary),
                icon = null,
                entries = NovelTtsHighlightMode.entries.associateWith { getTtsHighlightModeLabel(it) },
                onValueChange = {
                    update(
                        it,
                        { o, v -> o.copy(ttsHighlightMode = v) },
                        { preferences.ttsHighlightMode().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_tts_word_highlight_enabled),
                subtitle = stringResource(AYMR.strings.novel_reader_tts_word_highlight_enabled_summary),
                checked = settings.ttsWordHighlightEnabled,
                onClick = {
                    update(
                        !settings.ttsWordHighlightEnabled,
                        { o, v -> o.copy(ttsWordHighlightEnabled = v) },
                        { preferences.ttsWordHighlightEnabled().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_tts_auto_advance_chapter),
                checked = settings.ttsAutoAdvanceChapter,
                onClick = {
                    update(
                        !settings.ttsAutoAdvanceChapter,
                        { o, v -> o.copy(ttsAutoAdvanceChapter = v) },
                        { preferences.ttsAutoAdvanceChapter().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_tts_follow_along),
                subtitle = stringResource(AYMR.strings.novel_reader_tts_follow_along_summary),
                checked = settings.ttsFollowAlong,
                onClick = {
                    update(
                        !settings.ttsFollowAlong,
                        { o, v -> o.copy(ttsFollowAlong = v) },
                        { preferences.ttsFollowAlong().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_tts_pause_on_manual_navigation),
                checked = settings.ttsPauseOnManualNavigation,
                onClick = {
                    update(
                        !settings.ttsPauseOnManualNavigation,
                        { o, v -> o.copy(ttsPauseOnManualNavigation = v) },
                        { preferences.ttsPauseOnManualNavigation().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_tts_keep_screen_on_during_playback),
                checked = settings.ttsKeepScreenOnDuringPlayback,
                onClick = {
                    update(
                        !settings.ttsKeepScreenOnDuringPlayback,
                        { o, v -> o.copy(ttsKeepScreenOnDuringPlayback = v) },
                        { preferences.ttsKeepScreenOnDuringPlayback().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_tts_prefer_translated_text),
                subtitle = stringResource(AYMR.strings.novel_reader_tts_prefer_translated_text_summary),
                checked = settings.ttsPreferTranslatedText,
                onClick = {
                    update(
                        !settings.ttsPreferTranslatedText,
                        { o, v -> o.copy(ttsPreferTranslatedText = v) },
                        { preferences.ttsPreferTranslatedText().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_tts_read_chapter_title),
                checked = settings.ttsReadChapterTitle,
                onClick = {
                    update(
                        !settings.ttsReadChapterTitle,
                        { o, v -> o.copy(ttsReadChapterTitle = v) },
                        { preferences.ttsReadChapterTitle().set(it) },
                    )
                },
            )
        }

        // Перевод выделенного текста
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_selected_text_translation_section)) {
            if (overrideEnabled) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_selected_text_translation_global_only_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_text_selection_enabled),
                subtitle = stringResource(AYMR.strings.novel_reader_text_selection_enabled_summary),
                checked = settings.textSelectionEnabled,
                onClick = { preferences.textSelectionEnabled().set(!settings.textSelectionEnabled) },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_selected_text_translation_enabled),
                checked = settings.selectedTextTranslationEnabled,
                onClick = {
                    preferences.selectedTextTranslationEnabled().set(!settings.selectedTextTranslationEnabled)
                },
            )
            ListPreferenceWidget(
                value = settings.selectedTextTranslationTargetLanguage,
                title = stringResource(AYMR.strings.novel_reader_selected_text_translation_target_language),
                subtitle = dictionaryLanguages[settings.selectedTextTranslationTargetLanguage]
                    ?: settings.selectedTextTranslationTargetLanguage,
                icon = null,
                entries = dictionaryLanguages,
                onValueChange = {
                    preferences.selectedTextTranslationTargetLanguage().set(it)
                },
            )
        }

        // Словарь
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_dictionary_section)) {
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_dictionary_enabled),
                subtitle = stringResource(AYMR.strings.novel_reader_dictionary_enabled_summary),
                checked = settings.novelDictionaryEnabled,
                onClick = { preferences.novelDictionaryEnabled().set(!settings.novelDictionaryEnabled) },
            )
            var dictionaryQuickAccess by remember {
                mutableStateOf(preferences.novelDictionaryQuickAccess().get())
            }
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_dictionary_quick_access),
                subtitle = stringResource(AYMR.strings.novel_reader_dictionary_quick_access_summary),
                checked = dictionaryQuickAccess,
                onClick = {
                    dictionaryQuickAccess = !dictionaryQuickAccess
                    preferences.novelDictionaryQuickAccess().set(dictionaryQuickAccess)
                },
            )
            val dictionarySourceEntries = persistentMapOf(
                "ONLINE" to stringResource(AYMR.strings.novel_reader_dictionary_source_online),
                "OFFLINE" to stringResource(AYMR.strings.novel_reader_dictionary_source_offline),
                "OFFLINE_FIRST" to stringResource(AYMR.strings.novel_reader_dictionary_source_offline_first),
                "ONLINE_FIRST" to stringResource(AYMR.strings.novel_reader_dictionary_source_online_first),
            )
            var dictionarySourceMode by remember { mutableStateOf(preferences.novelDictionarySource().get()) }
            ListPreferenceWidget(
                value = dictionarySourceMode,
                title = stringResource(AYMR.strings.novel_reader_dictionary_source_mode),
                subtitle = dictionarySourceEntries[dictionarySourceMode] ?: dictionarySourceMode,
                icon = null,
                entries = dictionarySourceEntries,
                onValueChange = {
                    dictionarySourceMode = it
                    preferences.novelDictionarySource().set(it)
                },
            )
            ListPreferenceWidget(
                value = settings.novelDictionaryTargetLanguage,
                title = stringResource(AYMR.strings.novel_reader_dictionary_target_language),
                subtitle = dictionaryLanguages[settings.novelDictionaryTargetLanguage]
                    ?: settings.novelDictionaryTargetLanguage,
                icon = null,
                entries = dictionaryLanguages,
                onValueChange = {
                    preferences.novelDictionaryTargetLanguage().set(it)
                },
            )
        }

        // Правила текста (Словарь замен / Regex)
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_text_rules)) {
            val navigator = LocalNavigator.currentOrThrow
            AuroraNavRow(
                label = stringResource(AYMR.strings.novel_reader_text_replace),
                onClick = { navigator.push(NovelTextReplaceRulesScreen()) },
            )
        }
    }
}

@Composable
private fun getTtsHighlightModeLabel(mode: NovelTtsHighlightMode): String {
    return when (mode) {
        NovelTtsHighlightMode.AUTO -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_auto)
        NovelTtsHighlightMode.EXACT -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_exact)
        NovelTtsHighlightMode.ESTIMATED -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_estimated)
        NovelTtsHighlightMode.OFF -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_off)
    }
}

private fun formatTtsPercentage(value: Float): String {
    return "${(value * 100).roundToInt()}%"
}
