@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraToggleRow
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
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
    onDismissRequest: () -> Unit = {},
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

    val pageTransitionEntries = novelPageTransitionStyleEntries()
    val pageTurnSpeedEntries = novelPageTurnSpeedEntries()
    val pageTurnIntensityEntries = novelPageTurnIntensityEntries()
    val pageTurnShadowEntries = novelPageTurnShadowIntensityEntries()
    val pageTurnActivationZoneEntries = novelPageTurnActivationZoneEntries()
    val showPageTurnTuning = shouldShowPageTurnTuningControls(
        pageReaderEnabled = settings.pageReader,
        style = settings.pageTransitionStyle,
    )
    var pageTurnTuningExpanded by rememberSaveable(settings.pageReader, settings.pageTransitionStyle) {
        mutableStateOf(false)
    }

    val chapterSwipeControlsEnabled = remember(currentPageReaderActive) {
        areChapterSwipeControlsEnabled(
            pageReaderEnabled = currentPageReaderActive,
        )
    }
    val autoScrollChapterEndBehaviorEntries = novelAutoScrollChapterEndBehaviorEntries()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Стили перелистывания страниц (если режим страниц включен)
        if (settings.pageReader) {
            AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_page_transition_style)) {
                val transitionOptions = listOf(
                    NovelPageTransitionStyle.SLIDE to
                        stringResource(AYMR.strings.novel_reader_page_transition_style_slide),
                    NovelPageTransitionStyle.BOOK_FLIP to
                        stringResource(AYMR.strings.novel_reader_page_transition_style_book_flip),
                    NovelPageTransitionStyle.CURL to
                        stringResource(AYMR.strings.novel_reader_page_transition_style_curl),
                    NovelPageTransitionStyle.INSTANT to
                        stringResource(AYMR.strings.novel_reader_page_transition_style_instant),
                    NovelPageTransitionStyle.DEPTH to
                        stringResource(AYMR.strings.novel_reader_page_transition_style_depth),
                )
                NovelChipStrip(
                    options = transitionOptions.map { (style, label) ->
                        label to (settings.pageTransitionStyle == style)
                    },
                    onSelectIndex = { index ->
                        val selectedStyle = transitionOptions[index].first
                        update(
                            selectedStyle,
                            { o, v -> o.copy(pageTransitionStyle = v) },
                            { preferences.pageTransitionStyle().set(it) },
                            dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                        )
                    },
                )

                if (settings.pageTransitionStyle == NovelPageTransitionStyle.BOOK_FLIP) {
                    val bookFlipAnimationSpeedEntries = novelBookFlipAnimationSpeedEntries()
                    LnReaderSliderRow(
                        label = stringResource(AYMR.strings.novel_reader_page_turn_speed),
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
                } else if (settings.pageTransitionStyle != NovelPageTransitionStyle.INSTANT) {
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
                }

                AuroraToggleRow(
                    label = stringResource(AYMR.strings.novel_reader_page_edge_shadow),
                    subtitle = stringResource(AYMR.strings.novel_reader_page_edge_shadow_summary),
                    checked = settings.pageEdgeShadow,
                    onClick = {
                        update(
                            !settings.pageEdgeShadow,
                            { o, v -> o.copy(pageEdgeShadow = v) },
                            { preferences.pageEdgeShadow().set(it) },
                        )
                    },
                )
                if (settings.pageEdgeShadow) {
                    LnReaderSliderRow(
                        label = stringResource(AYMR.strings.novel_reader_page_edge_shadow_alpha),
                        valueText = { "${(it * 100).roundToInt()}%" },
                        committedValue = settings.pageEdgeShadowAlpha,
                        range = 0.05f..1f,
                        steps = 18,
                        onCommit = {
                            update(it, { o, v ->
                                o.copy(pageEdgeShadowAlpha = v)
                            }, { preferences.pageEdgeShadowAlpha().set(it) })
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
                            committedValue = novelPageTurnIntensitySliderIndex(
                                settings.pageTurnIntensity,
                            ).toFloat(),
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
        }

        // Жесты и навигация
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
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_custom_tap_zones),
                subtitle = stringResource(AYMR.strings.novel_reader_custom_tap_zones_summary),
                checked = settings.customTapZones,
                onClick = {
                    update(
                        !settings.customTapZones,
                        { o, v -> o.copy(customTapZones = v) },
                        { preferences.customTapZones().set(it) },
                    )
                },
            )
            if (settings.customTapZones) {
                NovelReaderTapZonesEditor(
                    serializedActions = settings.tapZoneActions,
                    onSerializedActionsChange = { serialized ->
                        update(
                            serialized,
                            { o, v -> o.copy(tapZoneActions = v) },
                            { preferences.tapZoneActions().set(it) },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
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
                label = stringResource(AYMR.strings.novel_reader_seamless_chapter_transition),
                subtitle = stringResource(AYMR.strings.novel_reader_seamless_chapter_transition_summary),
                checked = settings.seamlessChapterTransition,
                onClick = {
                    update(
                        !settings.seamlessChapterTransition,
                        { o, v -> o.copy(seamlessChapterTransition = v) },
                        { preferences.seamlessChapterTransition().set(it) },
                    )
                },
            )
        }

        // Расширенная автопрокрутка
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_auto_scroll)) {
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
        }

        // Экран и оверлеи
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_display)) {
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
