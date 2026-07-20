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
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraToggleRow
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

@Composable
fun BehaviorTab(
    settings: eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings,
    sourceId: Long,
    currentPageReaderActive: Boolean,
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

    val chapterSwipeControlsEnabled = remember(settings.swipeGestures, currentPageReaderActive) {
        areChapterSwipeControlsEnabled(
            swipeGesturesEnabled = settings.swipeGestures,
            pageReaderEnabled = currentPageReaderActive,
        )
    }
    val autoScrollChapterEndBehaviorEntries = novelAutoScrollChapterEndBehaviorEntries()
    val ttsPlacement = remember(settings.ttsEnabled) {
        resolveNovelReaderTtsSettingsPlacementSnapshot(settings.ttsEnabled)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_gestures)) {
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_swipe_gestures),
                subtitle = stringResource(AYMR.strings.novel_reader_swipe_gestures_summary),
                checked = settings.swipeGestures,
                onClick = {
                    update(
                        !settings.swipeGestures,
                        { o, v -> o.copy(swipeGestures = v) },
                        { preferences.swipeGestures().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_swipe_to_next),
                checked = settings.swipeToNextChapter,
                enabled = chapterSwipeControlsEnabled,
                onClick = {
                    update(
                        !settings.swipeToNextChapter,
                        { o, v -> o.copy(swipeToNextChapter = v) },
                        { preferences.swipeToNextChapter().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_swipe_to_prev),
                checked = settings.swipeToPrevChapter,
                enabled = chapterSwipeControlsEnabled,
                onClick = {
                    update(
                        !settings.swipeToPrevChapter,
                        { o, v -> o.copy(swipeToPrevChapter = v) },
                        { preferences.swipeToPrevChapter().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_tap_to_scroll),
                checked = settings.tapToScroll,
                onClick = {
                    update(
                        !settings.tapToScroll,
                        { o, v -> o.copy(tapToScroll = v) },
                        { preferences.tapToScroll().set(it) },
                    )
                },
            )
        }

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
            val dictionaryLanguages = kotlinx.collections.immutable.persistentMapOf(
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
            val dictionaryTargetLanguages = kotlinx.collections.immutable.persistentMapOf(
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
            ListPreferenceWidget(
                value = settings.novelDictionaryTargetLanguage,
                title = stringResource(AYMR.strings.novel_reader_dictionary_target_language),
                subtitle = dictionaryTargetLanguages[settings.novelDictionaryTargetLanguage]
                    ?: settings.novelDictionaryTargetLanguage,
                icon = null,
                entries = dictionaryTargetLanguages,
                onValueChange = {
                    preferences.novelDictionaryTargetLanguage().set(it)
                },
            )
        }

        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_tts_section)) {
            if (ttsPlacement.showGeneralEnableToggle) {
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
            }
        }

        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_advanced)) {
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_volume_buttons),
                subtitle = stringResource(AYMR.strings.novel_reader_volume_buttons_summary),
                checked = settings.useVolumeButtons,
                onClick = {
                    update(
                        !settings.useVolumeButtons,
                        { o, v -> o.copy(useVolumeButtons = v) },
                        { preferences.useVolumeButtons().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_vertical_seekbar),
                checked = settings.verticalSeekbar,
                onClick = {
                    update(
                        !settings.verticalSeekbar,
                        { o, v -> o.copy(verticalSeekbar = v) },
                        { preferences.verticalSeekbar().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_prefetch_next_chapter),
                subtitle = stringResource(AYMR.strings.novel_reader_prefetch_next_chapter_summary),
                checked = settings.prefetchNextChapter,
                onClick = {
                    update(
                        !settings.prefetchNextChapter,
                        { o, v -> o.copy(prefetchNextChapter = v) },
                        { preferences.prefetchNextChapter().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_fullscreen),
                subtitle = stringResource(AYMR.strings.novel_reader_fullscreen_summary),
                checked = settings.fullScreenMode,
                onClick = {
                    update(
                        !settings.fullScreenMode,
                        { o, v -> o.copy(fullScreenMode = v) },
                        { preferences.fullScreenMode().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_keep_screen_on),
                subtitle = stringResource(AYMR.strings.novel_reader_keep_screen_on_summary),
                checked = settings.keepScreenOn,
                onClick = {
                    update(
                        !settings.keepScreenOn,
                        { o, v -> o.copy(keepScreenOn = v) },
                        { preferences.keepScreenOn().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_show_scroll_percentage),
                checked = settings.showScrollPercentage,
                onClick = {
                    update(
                        !settings.showScrollPercentage,
                        { o, v -> o.copy(showScrollPercentage = v) },
                        { preferences.showScrollPercentage().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_show_battery_time),
                checked = settings.showBatteryAndTime,
                onClick = {
                    update(
                        !settings.showBatteryAndTime,
                        { o, v -> o.copy(showBatteryAndTime = v) },
                        { preferences.showBatteryAndTime().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_show_kindle_info_block),
                subtitle = stringResource(AYMR.strings.novel_reader_show_kindle_info_block_summary),
                checked = settings.showKindleInfoBlock,
                onClick = {
                    update(
                        !settings.showKindleInfoBlock,
                        { o, v -> o.copy(showKindleInfoBlock = v) },
                        { preferences.showKindleInfoBlock().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_show_time_to_end),
                checked = settings.showTimeToEnd,
                enabled = areQuickDialogKindleDependentControlsEnabled(settings.showKindleInfoBlock),
                onClick = {
                    update(
                        !settings.showTimeToEnd,
                        { o, v -> o.copy(showTimeToEnd = v) },
                        { preferences.showTimeToEnd().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_show_word_count),
                checked = settings.showWordCount,
                enabled = areQuickDialogKindleDependentControlsEnabled(settings.showKindleInfoBlock),
                onClick = {
                    update(
                        !settings.showWordCount,
                        { o, v -> o.copy(showWordCount = v) },
                        { preferences.showWordCount().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_bionic_reading),
                checked = settings.bionicReading,
                onClick = {
                    update(
                        !settings.bionicReading,
                        { o, v -> o.copy(bionicReading = v) },
                        { preferences.bionicReading().set(it) },
                    )
                },
            )
            ListPreferenceWidget(
                value = settings.autoScrollChapterEndBehavior,
                title = stringResource(AYMR.strings.novel_reader_auto_scroll_chapter_end_behavior),
                subtitle = autoScrollChapterEndBehaviorEntries[settings.autoScrollChapterEndBehavior],
                icon = null,
                entries = autoScrollChapterEndBehaviorEntries,
                onValueChange = {
                    update(
                        it,
                        { o, v -> o.copy(autoScrollChapterEndBehavior = v) },
                        { preferences.autoScrollChapterEndBehavior().set(it) },
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_auto_scroll_adaptive_delay),
                subtitle = stringResource(AYMR.strings.novel_reader_auto_scroll_adaptive_delay_summary),
                checked = settings.autoScrollAdaptiveDelay,
                onClick = {
                    update(
                        !settings.autoScrollAdaptiveDelay,
                        { o, v -> o.copy(autoScrollAdaptiveDelay = v) },
                        { preferences.autoScrollAdaptiveDelay().set(it) },
                    )
                },
            )
            if (settings.autoScrollChapterEndBehavior != NovelAutoScrollChapterEndBehavior.StopAtEnd) {
                val endPauseLabel = stringResource(AYMR.strings.novel_reader_auto_scroll_end_pause_value)
                LnReaderSliderRow(
                    label = stringResource(AYMR.strings.novel_reader_auto_scroll_end_pause),
                    valueText = {
                        endPauseLabel.replace(
                            "%1\$d",
                            it.roundToInt().toString(),
                        ).replace("%d", it.roundToInt().toString())
                    },
                    committedValue = (settings.autoScrollEndPauseMs / 1000f).coerceIn(0f, 10f),
                    range = 0f..10f,
                    steps = 10,
                    enabled = true,
                    onCommit = {
                        val seconds = it.roundToInt().coerceIn(0, 10)
                        update(
                            seconds * 1000L,
                            { o, v -> o.copy(autoScrollEndPauseMs = v) },
                            { preferences.autoScrollEndPauseMs().set(it) },
                        )
                    },
                )
            }
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_auto_scroll_speed),
                valueText = { it.roundToInt().toString() },
                committedValue = intervalToAutoScrollSpeed(settings.autoScrollInterval).toFloat(),
                range = 1f..100f,
                steps = 98,
                enabled = true,
                onCommit = {
                    val speed = it.roundToInt().coerceIn(1, 100)
                    update(
                        autoScrollSpeedToInterval(speed),
                        { o, v -> o.copy(autoScrollInterval = v) },
                        { preferences.autoScrollInterval().set(it) },
                    )
                },
            )
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_auto_scroll_offset),
                valueText = { it.roundToInt().toString() },
                committedValue = settings.autoScrollOffset.toFloat(),
                range = 0f..2000f,
                steps = 1999,
                enabled = true,
                onCommit = {
                    update(
                        it.roundToInt(),
                        { o, v -> o.copy(autoScrollOffset = v) },
                        { preferences.autoScrollOffset().set(it) },
                    )
                },
            )
            EditTextPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_custom_css),
                subtitle = stringResource(AYMR.strings.novel_reader_custom_css_hint),
                icon = null,
                value = settings.customCSS,
                onConfirm = {
                    update(it, { o, v -> o.copy(customCSS = v) }, { preferences.customCSS().set(it) })
                    true
                },
                singleLine = false,
                canBeBlank = true,
                formatSubtitle = false,
            )
            EditTextPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_custom_js),
                subtitle = stringResource(AYMR.strings.novel_reader_custom_js_hint),
                icon = null,
                value = settings.customJS,
                onConfirm = {
                    update(it, { o, v -> o.copy(customJS = v) }, { preferences.customJS().set(it) })
                    true
                },
                singleLine = false,
                canBeBlank = true,
                formatSubtitle = false,
            )
        }
    }
}

@Composable
internal fun novelAutoScrollChapterEndBehaviorEntries() = persistentMapOf(
    NovelAutoScrollChapterEndBehavior.StopAtEnd to
        stringResource(AYMR.strings.novel_reader_auto_scroll_chapter_end_stop),
    NovelAutoScrollChapterEndBehavior.AdvanceAndStop to
        stringResource(AYMR.strings.novel_reader_auto_scroll_chapter_end_advance_stop),
    NovelAutoScrollChapterEndBehavior.ContinuousReading to
        stringResource(AYMR.strings.novel_reader_auto_scroll_chapter_end_continuous),
)
