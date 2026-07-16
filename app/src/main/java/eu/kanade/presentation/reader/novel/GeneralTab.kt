@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.presentation.reader.novel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

@Composable
fun GeneralTab(
    settings: eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings,
    sourceId: Long,
    currentWebViewActive: Boolean,
    currentPageReaderActive: Boolean,
    overrideEnabled: Boolean,
    preferences: NovelReaderPreferences,
    onDismissRequest: () -> Unit,
) {
    fun <T> update(
        value: T,
        copyOverride: (NovelReaderOverride, T) -> NovelReaderOverride,
        setGlobal: (T) -> Unit,
        dismissFamily: NovelReaderSettingsFamily? = null,
    ) {
        if (overrideEnabled) {
            preferences.updateSourceOverride(sourceId) { copyOverride(it, value) }
        } else {
            setGlobal(value)
        }
        if (dismissFamily != null && shouldDismissReaderSettingsDialogAfterFamilyChange(dismissFamily)) {
            onDismissRequest()
        }
    }

    val rendererAvailability = remember(
        currentPageReaderActive,
        currentWebViewActive,
        settings.bionicReading,
    ) {
        resolveRendererSettingsAvailability(
            pageReaderEnabled = currentPageReaderActive,
            showWebView = currentWebViewActive,
            bionicReadingEnabled = settings.bionicReading,
        )
    }
    val chapterSwipeControlsEnabled = remember(settings.swipeGestures, currentPageReaderActive) {
        areChapterSwipeControlsEnabled(
            swipeGesturesEnabled = settings.swipeGestures,
            pageReaderEnabled = currentPageReaderActive,
        )
    }
    val pageTransitionEntries = novelPageTransitionStyleEntries()
    val autoScrollChapterEndBehaviorEntries = novelAutoScrollChapterEndBehaviorEntries()
    val pageTurnSpeedEntries = novelPageTurnSpeedEntries()
    val pageTurnIntensityEntries = novelPageTurnIntensityEntries()
    val pageTurnShadowEntries = novelPageTurnShadowIntensityEntries()
    val pageTurnActivationZoneEntries = novelPageTurnActivationZoneEntries()
    val ttsPlacement = remember(settings.ttsEnabled) {
        resolveNovelReaderTtsSettingsPlacementSnapshot(settings.ttsEnabled)
    }
    val showPageTurnTuning = shouldShowPageTurnTuningControls(
        pageReaderEnabled = settings.pageReader,
        style = settings.pageTransitionStyle,
    )
    var pageTurnTuningExpanded by rememberSaveable(settings.pageReader, settings.pageTransitionStyle) {
        mutableStateOf(false)
    }
    var readingBehaviorExpanded by rememberSaveable(sourceId) { mutableStateOf(true) }
    var gesturesExpanded by rememberSaveable(sourceId) { mutableStateOf(false) }
    var translationExpanded by rememberSaveable(sourceId) { mutableStateOf(false) }
    var ttsExpanded by rememberSaveable(sourceId) { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable(sourceId) { mutableStateOf(false) }
    val surfaceStrategy = remember { resolveNovelReaderSettingsSurfaceStrategy() }

    @Composable
    fun rendererSubtitle(
        baseSubtitle: String,
        reason: RendererSettingDisableReason?,
    ): String {
        val reasonText = when (reason) {
            RendererSettingDisableReason.PAGE_MODE ->
                stringResource(AYMR.strings.novel_reader_renderer_disabled_page_mode_summary)
            RendererSettingDisableReason.WEBVIEW_ACTIVE ->
                stringResource(AYMR.strings.novel_reader_renderer_disabled_webview_summary)
            RendererSettingDisableReason.BIONIC_READING ->
                stringResource(AYMR.strings.novel_reader_renderer_disabled_bionic_summary)
            null -> null
        }
        return if (reasonText != null) "$baseSubtitle\n$reasonText" else baseSubtitle
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Text(
            text = stringResource(AYMR.strings.novel_reader_settings_title),
            style = MaterialTheme.typography.titleMedium,
        )
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_override_source),
            subtitle = stringResource(AYMR.strings.novel_reader_override_summary),
            checked = overrideEnabled,
            onCheckedChanged = { enabled ->
                if (enabled) {
                    preferences.enableSourceOverride(sourceId)
                } else {
                    preferences.setSourceOverride(sourceId, null)
                }
            },
        )
        Text(
            text = if (overrideEnabled) {
                stringResource(AYMR.strings.novel_reader_editing_source)
            } else {
                stringResource(AYMR.strings.novel_reader_editing_global)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (surfaceStrategy.globalOnlyFamilies.isNotEmpty()) {
            Text(
                text = stringResource(AYMR.strings.novel_reader_quick_dialog_global_policy_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        NovelReaderAccordionSection(
            title = stringResource(AYMR.strings.novel_reader_section_reading_behavior),
            expanded = readingBehaviorExpanded,
            onToggle = { readingBehaviorExpanded = !readingBehaviorExpanded },
        ) {
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_page_mode),
                subtitle = stringResource(AYMR.strings.novel_reader_page_mode_summary),
                checked = settings.pageReader,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(pageReader = v) },
                        { preferences.pageReader().set(it) },
                        dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                    )
                },
            )
            if (settings.pageReader) {
                SwitchPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_show_page_chapter_title),
                    subtitle = stringResource(AYMR.strings.novel_reader_show_page_chapter_title_summary),
                    checked = settings.showPageChapterTitle,
                    onCheckedChanged = {
                        update(
                            it,
                            { o, v -> o.copy(showPageChapterTitle = v) },
                            { preferences.showPageChapterTitle().set(it) },
                        )
                    },
                )
                ListPreferenceWidget(
                    value = settings.pageTransitionStyle,
                    title = stringResource(AYMR.strings.novel_reader_page_transition_style),
                    subtitle = novelPageTransitionStyleSubtitle(
                        style = settings.pageTransitionStyle,
                        entries = pageTransitionEntries,
                    ),
                    icon = null,
                    entries = pageTransitionEntries,
                    onValueChange = {
                        update(
                            it,
                            { o, v -> o.copy(pageTransitionStyle = v) },
                            { preferences.pageTransitionStyle().set(it) },
                            dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                        )
                    },
                )
                if (settings.pageTransitionStyle == NovelPageTransitionStyle.BOOK_FLIP) {
                    val bookFlipAnimationSpeedEntries = novelBookFlipAnimationSpeedEntries()
                    LnReaderSliderRow(
                        label = stringResource(AYMR.strings.novel_reader_book_flip_animation_speed),
                        valueText = { value ->
                            resolveNovelPageTurnSliderLabel(
                                value = resolveNovelBookFlipAnimationSpeedSliderValue(value.roundToInt()),
                                entries = bookFlipAnimationSpeedEntries,
                            )
                        },
                        committedValue = novelBookFlipAnimationSpeedSliderIndex(
                            settings.bookFlipAnimationSpeed,
                        ).toFloat(),
                        range = 0f..(bookFlipAnimationSpeedEntries.size - 1).toFloat(),
                        steps = bookFlipAnimationSpeedEntries.size - 2,
                        onCommit = { value ->
                            update(
                                resolveNovelBookFlipAnimationSpeedSliderValue(value.roundToInt()),
                                { o, v -> o.copy(bookFlipAnimationSpeed = v) },
                                { preferences.bookFlipAnimationSpeed().set(it) },
                                dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                            )
                        },
                    )
                    LnReaderSliderRow(
                        label = stringResource(AYMR.strings.novel_reader_page_turn_activation_zone),
                        valueText = { value ->
                            resolveNovelPageTurnSliderLabel(
                                value = resolveNovelPageTurnActivationZoneSliderValue(value.roundToInt()),
                                entries = pageTurnActivationZoneEntries,
                            )
                        },
                        committedValue = novelPageTurnActivationZoneSliderIndex(
                            settings.pageTurnActivationZone,
                        ).toFloat(),
                        range = 0f..(pageTurnActivationZoneEntries.size - 1).toFloat(),
                        steps = pageTurnActivationZoneEntries.size - 2,
                        onCommit = { value ->
                            update(
                                resolveNovelPageTurnActivationZoneSliderValue(value.roundToInt()),
                                { o, v -> o.copy(pageTurnActivationZone = v) },
                                { preferences.pageTurnActivationZone().set(it) },
                                dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                            )
                        },
                    )
                }
                if (showPageTurnTuning) {
                    TextPreferenceWidget(
                        title = stringResource(AYMR.strings.novel_reader_page_turn_tuning),
                        subtitle = novelPageTurnTuningSummary(
                            speed = settings.pageTurnSpeed,
                            intensity = settings.pageTurnIntensity,
                            shadowIntensity = settings.pageTurnShadowIntensity,
                            activationZone = settings.pageTurnActivationZone,
                            speedEntries = pageTurnSpeedEntries,
                            intensityEntries = pageTurnIntensityEntries,
                            shadowEntries = pageTurnShadowEntries,
                            activationZoneEntries = pageTurnActivationZoneEntries,
                        ),
                        widget = {
                            Icon(
                                imageVector = if (pageTurnTuningExpanded) {
                                    Icons.Filled.KeyboardArrowDown
                                } else {
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                                },
                                contentDescription = null,
                            )
                        },
                        onPreferenceClick = {
                            pageTurnTuningExpanded = !pageTurnTuningExpanded
                        },
                    )
                    if (pageTurnTuningExpanded) {
                        LnReaderSliderRow(
                            label = stringResource(AYMR.strings.novel_reader_page_turn_speed),
                            valueText = { value ->
                                resolveNovelPageTurnSliderLabel(
                                    value = resolveNovelPageTurnSpeedSliderValue(value.roundToInt()),
                                    entries = pageTurnSpeedEntries,
                                )
                            },
                            committedValue = novelPageTurnSpeedSliderIndex(settings.pageTurnSpeed).toFloat(),
                            range = 0f..(pageTurnSpeedEntries.size - 1).toFloat(),
                            steps = pageTurnSpeedEntries.size - 2,
                            onCommit = { value ->
                                update(
                                    resolveNovelPageTurnSpeedSliderValue(value.roundToInt()),
                                    { o, v -> o.copy(pageTurnSpeed = v) },
                                    { preferences.pageTurnSpeed().set(it) },
                                    dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                                )
                            },
                        )
                        LnReaderSliderRow(
                            label = stringResource(AYMR.strings.novel_reader_page_turn_intensity),
                            valueText = { value ->
                                resolveNovelPageTurnSliderLabel(
                                    value = resolveNovelPageTurnIntensitySliderValue(value.roundToInt()),
                                    entries = pageTurnIntensityEntries,
                                )
                            },
                            committedValue = novelPageTurnIntensitySliderIndex(settings.pageTurnIntensity).toFloat(),
                            range = 0f..(pageTurnIntensityEntries.size - 1).toFloat(),
                            steps = pageTurnIntensityEntries.size - 2,
                            onCommit = { value ->
                                update(
                                    resolveNovelPageTurnIntensitySliderValue(value.roundToInt()),
                                    { o, v -> o.copy(pageTurnIntensity = v) },
                                    { preferences.pageTurnIntensity().set(it) },
                                    dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                                )
                            },
                        )
                        LnReaderSliderRow(
                            label = stringResource(AYMR.strings.novel_reader_page_turn_shadow_intensity),
                            valueText = { value ->
                                resolveNovelPageTurnSliderLabel(
                                    value = resolveNovelPageTurnShadowIntensitySliderValue(value.roundToInt()),
                                    entries = pageTurnShadowEntries,
                                )
                            },
                            committedValue = novelPageTurnShadowIntensitySliderIndex(
                                settings.pageTurnShadowIntensity,
                            ).toFloat(),
                            range = 0f..(pageTurnShadowEntries.size - 1).toFloat(),
                            steps = pageTurnShadowEntries.size - 2,
                            onCommit = { value ->
                                update(
                                    resolveNovelPageTurnShadowIntensitySliderValue(value.roundToInt()),
                                    { o, v -> o.copy(pageTurnShadowIntensity = v) },
                                    { preferences.pageTurnShadowIntensity().set(it) },
                                    dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                                )
                            },
                        )
                        LnReaderSliderRow(
                            label = stringResource(AYMR.strings.novel_reader_page_turn_activation_zone),
                            valueText = { value ->
                                resolveNovelPageTurnSliderLabel(
                                    value = resolveNovelPageTurnActivationZoneSliderValue(value.roundToInt()),
                                    entries = pageTurnActivationZoneEntries,
                                )
                            },
                            committedValue = novelPageTurnActivationZoneSliderIndex(
                                settings.pageTurnActivationZone,
                            ).toFloat(),
                            range = 0f..(pageTurnActivationZoneEntries.size - 1).toFloat(),
                            steps = pageTurnActivationZoneEntries.size - 2,
                            onCommit = { value ->
                                update(
                                    resolveNovelPageTurnActivationZoneSliderValue(value.roundToInt()),
                                    { o, v -> o.copy(pageTurnActivationZone = v) },
                                    { preferences.pageTurnActivationZone().set(it) },
                                    dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                                )
                            },
                        )
                    }
                }
            }
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_prefer_webview_renderer),
                subtitle = rendererSubtitle(
                    baseSubtitle = stringResource(AYMR.strings.novel_reader_prefer_webview_renderer_summary),
                    reason = rendererAvailability.preferWebViewReason,
                ),
                checked = settings.preferWebViewRenderer,
                enabled = rendererAvailability.preferWebViewEnabled,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(preferWebViewRenderer = v) },
                        { preferences.preferWebViewRenderer().set(it) },
                        dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_rich_native_renderer_experimental),
                subtitle = rendererSubtitle(
                    baseSubtitle = stringResource(AYMR.strings.novel_reader_rich_native_renderer_experimental_summary),
                    reason = rendererAvailability.richNativeReason,
                ),
                checked = settings.richNativeRendererExperimental,
                enabled = rendererAvailability.richNativeEnabled,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(richNativeRendererExperimental = v) },
                        { preferences.richNativeRendererExperimental().set(it) },
                        dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                    )
                },
            )
        }
        NovelReaderAccordionSection(
            title = stringResource(AYMR.strings.novel_reader_section_gestures),
            expanded = gesturesExpanded,
            onToggle = { gesturesExpanded = !gesturesExpanded },
        ) {
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_swipe_gestures),
                subtitle = stringResource(AYMR.strings.novel_reader_swipe_gestures_summary),
                checked = settings.swipeGestures,
                onCheckedChanged = {
                    update(it, { o, v -> o.copy(swipeGestures = v) }, { preferences.swipeGestures().set(it) })
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_swipe_to_next),
                checked = settings.swipeToNextChapter,
                enabled = chapterSwipeControlsEnabled,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(swipeToNextChapter = v) },
                        { preferences.swipeToNextChapter().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_swipe_to_prev),
                checked = settings.swipeToPrevChapter,
                enabled = chapterSwipeControlsEnabled,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(swipeToPrevChapter = v) },
                        { preferences.swipeToPrevChapter().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_tap_to_scroll),
                checked = settings.tapToScroll,
                onCheckedChanged = {
                    update(it, { o, v -> o.copy(tapToScroll = v) }, { preferences.tapToScroll().set(it) })
                },
            )
        }
        NovelReaderAccordionSection(
            title = stringResource(AYMR.strings.novel_reader_selected_text_translation_section),
            expanded = translationExpanded,
            onToggle = { translationExpanded = !translationExpanded },
        ) {
            if (overrideEnabled) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_selected_text_translation_global_only_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_text_selection_enabled),
                subtitle = stringResource(AYMR.strings.novel_reader_text_selection_enabled_summary),
                checked = settings.textSelectionEnabled,
                onCheckedChanged = { preferences.textSelectionEnabled().set(it) },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_selected_text_translation_enabled),
                checked = settings.selectedTextTranslationEnabled,
                onCheckedChanged = { preferences.selectedTextTranslationEnabled().set(it) },
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
        var dictionariesExpanded by rememberSaveable { mutableStateOf(false) }
        NovelReaderAccordionSection(
            title = stringResource(AYMR.strings.novel_reader_dictionary_section),
            expanded = dictionariesExpanded,
            onToggle = { dictionariesExpanded = !dictionariesExpanded },
        ) {
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_dictionary_enabled),
                subtitle = stringResource(AYMR.strings.novel_reader_dictionary_enabled_summary),
                checked = settings.novelDictionaryEnabled,
                onCheckedChanged = { preferences.novelDictionaryEnabled().set(it) },
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
                "ru" to "\u0420\u0443\u0441\u0441\u043a\u0438\u0439",
                "ja" to "\u65e5\u672c\u8a9e (Japanese)",
                "zh" to "\u4e2d\u6587 (Chinese)",
                "ko" to "\ud55c\uad6d\uc5b4 (Korean)",
                "es" to "Espa\u00f1ol (Spanish)",
                "fr" to "Fran\u00e7ais (French)",
                "de" to "Deutsch (German)",
                "it" to "Italiano (Italian)",
                "pt" to "Portugu\u00eas (Portuguese)",
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
        NovelReaderAccordionSection(
            title = stringResource(AYMR.strings.novel_reader_tts_section),
            expanded = ttsExpanded,
            onToggle = { ttsExpanded = !ttsExpanded },
        ) {
            if (ttsPlacement.showGeneralEnableToggle) {
                SwitchPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_tts_enabled),
                    subtitle = stringResource(AYMR.strings.novel_reader_tts_enabled_summary),
                    checked = settings.ttsEnabled,
                    onCheckedChanged = {
                        update(it, { o, v -> o.copy(ttsEnabled = v) }, { preferences.ttsEnabled().set(it) })
                    },
                )
            }
        }
        NovelReaderAccordionSection(
            title = stringResource(AYMR.strings.novel_reader_section_advanced),
            expanded = advancedExpanded,
            onToggle = { advancedExpanded = !advancedExpanded },
        ) {
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_volume_buttons),
                subtitle = stringResource(AYMR.strings.novel_reader_volume_buttons_summary),
                checked = settings.useVolumeButtons,
                onCheckedChanged = {
                    update(it, { o, v -> o.copy(useVolumeButtons = v) }, { preferences.useVolumeButtons().set(it) })
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_vertical_seekbar),
                checked = settings.verticalSeekbar,
                onCheckedChanged = {
                    update(it, { o, v -> o.copy(verticalSeekbar = v) }, { preferences.verticalSeekbar().set(it) })
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_prefetch_next_chapter),
                subtitle = stringResource(AYMR.strings.novel_reader_prefetch_next_chapter_summary),
                checked = settings.prefetchNextChapter,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(prefetchNextChapter = v) },
                        { preferences.prefetchNextChapter().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_fullscreen),
                subtitle = stringResource(AYMR.strings.novel_reader_fullscreen_summary),
                checked = settings.fullScreenMode,
                onCheckedChanged = {
                    update(it, { o, v -> o.copy(fullScreenMode = v) }, { preferences.fullScreenMode().set(it) })
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_keep_screen_on),
                subtitle = stringResource(AYMR.strings.novel_reader_keep_screen_on_summary),
                checked = settings.keepScreenOn,
                onCheckedChanged = {
                    update(it, { o, v -> o.copy(keepScreenOn = v) }, { preferences.keepScreenOn().set(it) })
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_show_scroll_percentage),
                checked = settings.showScrollPercentage,
                onCheckedChanged = {
                    update(it, { o, v ->
                        o.copy(showScrollPercentage = v)
                    }, { preferences.showScrollPercentage().set(it) })
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_show_battery_time),
                checked = settings.showBatteryAndTime,
                onCheckedChanged = {
                    update(it, { o, v -> o.copy(showBatteryAndTime = v) }, { preferences.showBatteryAndTime().set(it) })
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_show_kindle_info_block),
                subtitle = stringResource(AYMR.strings.novel_reader_show_kindle_info_block_summary),
                checked = settings.showKindleInfoBlock,
                onCheckedChanged = {
                    update(it, { o, v ->
                        o.copy(showKindleInfoBlock = v)
                    }, { preferences.showKindleInfoBlock().set(it) })
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_show_time_to_end),
                checked = settings.showTimeToEnd,
                enabled = areQuickDialogKindleDependentControlsEnabled(settings.showKindleInfoBlock),
                onCheckedChanged = {
                    update(it, { o, v -> o.copy(showTimeToEnd = v) }, { preferences.showTimeToEnd().set(it) })
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_show_word_count),
                checked = settings.showWordCount,
                enabled = areQuickDialogKindleDependentControlsEnabled(settings.showKindleInfoBlock),
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(showWordCount = v) },
                        { preferences.showWordCount().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_bionic_reading),
                checked = settings.bionicReading,
                onCheckedChanged = {
                    update(it, { o, v -> o.copy(bionicReading = v) }, { preferences.bionicReading().set(it) })
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
            SwitchPreferenceWidget(
                checked = settings.autoScrollAdaptiveDelay,
                title = stringResource(AYMR.strings.novel_reader_auto_scroll_adaptive_delay),
                subtitle = stringResource(AYMR.strings.novel_reader_auto_scroll_adaptive_delay_summary),
                icon = null,
                onCheckedChanged = { checked ->
                    update(
                        checked,
                        { o, v -> o.copy(autoScrollAdaptiveDelay = v) },
                        { preferences.autoScrollAdaptiveDelay().set(checked) },
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
                    update(it.roundToInt(), { o, v ->
                        o.copy(autoScrollOffset = v)
                    }, { preferences.autoScrollOffset().set(it) })
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

@Composable
private fun getNovelReaderTranslationProviderLabel(provider: NovelTranslationProvider): String {
    return when (provider) {
        NovelTranslationProvider.GEMINI ->
            stringResource(AYMR.strings.novel_reader_translation_provider_gemini)
        NovelTranslationProvider.GEMINI_PRIVATE ->
            if (GeminiPrivateBridge.isInstalled()) {
                GeminiPrivateBridge.providerLabel()
            } else {
                stringResource(AYMR.strings.novel_reader_translation_provider_gemini_private)
            }
        NovelTranslationProvider.OPENROUTER ->
            stringResource(AYMR.strings.novel_reader_translation_provider_openrouter)
        NovelTranslationProvider.DEEPSEEK ->
            stringResource(AYMR.strings.novel_reader_translation_provider_deepseek)
        NovelTranslationProvider.MISTRAL ->
            stringResource(AYMR.strings.novel_reader_translation_provider_mistral)
        NovelTranslationProvider.NVIDIA ->
            stringResource(AYMR.strings.novel_reader_translation_provider_nvidia)
        NovelTranslationProvider.OLLAMA_CLOUD ->
            stringResource(AYMR.strings.novel_reader_translation_provider_ollama_cloud)
    }
}

@Composable
private fun NovelReaderAccordionSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(120)) + expandVertically(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(100)) + shrinkVertically(animationSpec = tween(100)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}
