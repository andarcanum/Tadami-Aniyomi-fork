@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import eu.kanade.presentation.reader.settings.AuroraFieldLabel
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraMiniOption
import eu.kanade.presentation.reader.settings.AuroraToggleRow
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import tachiyomi.i18n.aniyomi.AYMR
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

    Column(modifier = Modifier.fillMaxWidth()) {
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_settings_title)) {
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_override_source),
                subtitle = stringResource(AYMR.strings.novel_reader_override_summary),
                checked = overrideEnabled,
                onClick = {
                    if (overrideEnabled) {
                        preferences.setSourceOverride(sourceId, null)
                    } else {
                        preferences.enableSourceOverride(sourceId)
                    }
                },
            )
            NovelGlassHint(
                text = if (overrideEnabled) {
                    stringResource(AYMR.strings.novel_reader_editing_source)
                } else {
                    stringResource(AYMR.strings.novel_reader_editing_global)
                },
            )
            if (surfaceStrategy.globalOnlyFamilies.isNotEmpty()) {
                NovelGlassHint(
                    text = stringResource(AYMR.strings.novel_reader_quick_dialog_global_policy_summary),
                )
            }
        }

        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_reading_behavior)) {
            AuroraFieldLabel(stringResource(AYMR.strings.novel_reader_page_mode))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AuroraMiniOption(
                    selected = !settings.pageReader,
                    onClick = {
                        if (settings.pageReader) {
                            update(
                                false,
                                { o, v -> o.copy(pageReader = v) },
                                { preferences.pageReader().set(it) },
                                dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                            )
                        }
                    },
                    label = stringResource(AYMR.strings.novel_reader_mode_scroll),
                    icon = Icons.Outlined.ViewDay,
                    modifier = Modifier.weight(1f),
                )
                AuroraMiniOption(
                    selected = settings.pageReader,
                    onClick = {
                        if (!settings.pageReader) {
                            update(
                                true,
                                { o, v -> o.copy(pageReader = v) },
                                { preferences.pageReader().set(it) },
                                dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                            )
                        }
                    },
                    label = stringResource(AYMR.strings.novel_reader_mode_pages),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    modifier = Modifier.weight(1f),
                )
            }
            NovelGlassHint(stringResource(AYMR.strings.novel_reader_page_mode_summary))
            if (settings.pageReader) {
                AuroraToggleRow(
                    label = stringResource(AYMR.strings.novel_reader_show_page_chapter_title),
                    subtitle = stringResource(AYMR.strings.novel_reader_show_page_chapter_title_summary),
                    checked = settings.showPageChapterTitle,
                    onClick = {
                        update(
                            !settings.showPageChapterTitle,
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
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_prefer_webview_renderer),
                subtitle = rendererSubtitle(
                    baseSubtitle = stringResource(AYMR.strings.novel_reader_prefer_webview_renderer_summary),
                    reason = rendererAvailability.preferWebViewReason,
                ),
                checked = settings.preferWebViewRenderer,
                enabled = rendererAvailability.preferWebViewEnabled,
                onClick = {
                    update(
                        !settings.preferWebViewRenderer,
                        { o, v -> o.copy(preferWebViewRenderer = v) },
                        { preferences.preferWebViewRenderer().set(it) },
                        dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                    )
                },
            )
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_rich_native_renderer_experimental),
                subtitle = rendererSubtitle(
                    baseSubtitle = stringResource(AYMR.strings.novel_reader_rich_native_renderer_experimental_summary),
                    reason = rendererAvailability.richNativeReason,
                ),
                checked = settings.richNativeRendererExperimental,
                enabled = rendererAvailability.richNativeEnabled,
                onClick = {
                    update(
                        !settings.richNativeRendererExperimental,
                        { o, v -> o.copy(richNativeRendererExperimental = v) },
                        { preferences.richNativeRendererExperimental().set(it) },
                        dismissFamily = NovelReaderSettingsFamily.RENDERER_TUNING,
                    )
                },
            )
        }
    }
}
