package eu.kanade.presentation.reader.novel

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderParagraphSpacing
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@Composable
fun NovelReaderSettingsDialog(
    sourceId: Long,
    onDismissRequest: () -> Unit,
) {
    val preferences = remember { Injekt.get<NovelReaderPreferences>() }
    val sourceOverrides = remember { preferences.sourceOverrides() }
    val overrides by sourceOverrides.changes().collectAsStateWithLifecycle(initialValue = sourceOverrides.get())
    val overrideEnabled = overrides[sourceId] != null
    val settingsFlow = remember(sourceId) { preferences.settingsFlow(sourceId) }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = preferences.resolveSettings(sourceId))

    val tabTitles = persistentListOf(
        stringResource(AYMR.strings.novel_reader_tab_general),
        stringResource(AYMR.strings.novel_reader_tab_reading),
    )

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = tabTitles,
        modifier = Modifier.fillMaxHeight(0.4f),
    ) { page ->
        when (page) {
            0 -> {
                GeneralTab(
                    settings = settings,
                    sourceId = sourceId,
                    overrideEnabled = overrideEnabled,
                    preferences = preferences,
                )
            }
            else -> {
                ReadingTab(
                    settings = settings,
                    sourceId = sourceId,
                    overrideEnabled = overrideEnabled,
                    preferences = preferences,
                )
            }
        }
    }
}

@Composable
private fun GeneralTab(
    settings: eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings,
    sourceId: Long,
    overrideEnabled: Boolean,
    preferences: NovelReaderPreferences,
) {
    var showGeminiSettings by remember { mutableStateOf(false) }

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

    LaunchedEffect(settings.translationProvider) {
        if (settings.translationProvider == NovelTranslationProvider.AIRFORCE) {
            update(
                NovelTranslationProvider.GEMINI,
                { o, v -> o.copy(translationProvider = v) },
                { preferences.translationProvider().set(it) },
            )
        }
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

        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_page_mode),
            subtitle = stringResource(AYMR.strings.novel_reader_page_mode_summary),
            checked = settings.pageReader,
            onCheckedChanged = { update(it, { o, v -> o.copy(pageReader = v) }, { preferences.pageReader().set(it) }) },
        )
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_prefer_webview_renderer),
            subtitle = stringResource(AYMR.strings.novel_reader_prefer_webview_renderer_summary),
            checked = settings.preferWebViewRenderer,
            onCheckedChanged = {
                update(
                    it,
                    { o, v -> o.copy(preferWebViewRenderer = v) },
                    { preferences.preferWebViewRenderer().set(it) },
                )
            },
        )
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_rich_native_renderer_experimental),
            subtitle = stringResource(AYMR.strings.novel_reader_rich_native_renderer_experimental_summary),
            checked = settings.richNativeRendererExperimental,
            onCheckedChanged = {
                update(
                    it,
                    { o, v -> o.copy(richNativeRendererExperimental = v) },
                    { preferences.richNativeRendererExperimental().set(it) },
                )
            },
        )
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
        LnReaderSliderRow(
            label = stringResource(AYMR.strings.novel_reader_auto_scroll_speed),
            valueText = intervalToAutoScrollSpeed(settings.autoScrollInterval).toString(),
            value = intervalToAutoScrollSpeed(settings.autoScrollInterval).toFloat(),
            range = 1f..100f,
            steps = 98,
            enabled = true,
            onChange = {
                val speed = it.toInt().coerceIn(1, 100)
                update(
                    autoScrollSpeedToInterval(speed),
                    { o, v -> o.copy(autoScrollInterval = v) },
                    { preferences.autoScrollInterval().set(it) },
                )
            },
        )
        LnReaderSliderRow(
            label = stringResource(AYMR.strings.novel_reader_auto_scroll_offset),
            valueText = settings.autoScrollOffset.toString(),
            value = settings.autoScrollOffset.toFloat(),
            range = 0f..2000f,
            steps = 1999,
            enabled = true,
            onChange = {
                update(it.toInt(), { o, v -> o.copy(autoScrollOffset = v) }, { preferences.autoScrollOffset().set(it) })
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
                update(it, { o, v -> o.copy(showScrollPercentage = v) }, { preferences.showScrollPercentage().set(it) })
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
                update(it, { o, v -> o.copy(showKindleInfoBlock = v) }, { preferences.showKindleInfoBlock().set(it) })
            },
        )
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_show_time_to_end),
            checked = settings.showTimeToEnd,
            onCheckedChanged = {
                if (!settings.showKindleInfoBlock) return@SwitchPreferenceWidget
                update(it, { o, v -> o.copy(showTimeToEnd = v) }, { preferences.showTimeToEnd().set(it) })
            },
        )
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_show_word_count),
            checked = settings.showWordCount,
            onCheckedChanged = {
                if (!settings.showKindleInfoBlock) return@SwitchPreferenceWidget
                update(it, { o, v -> o.copy(showWordCount = v) }, { preferences.showWordCount().set(it) })
            },
        )
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_bionic_reading),
            checked = settings.bionicReading,
            onCheckedChanged = {
                update(it, { o, v -> o.copy(bionicReading = v) }, { preferences.bionicReading().set(it) })
            },
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showGeminiSettings = !showGeminiSettings },
        ) {
            Text(
                text = if (showGeminiSettings) {
                    stringResource(AYMR.strings.novel_reader_ai_translator_hide)
                } else {
                    stringResource(AYMR.strings.novel_reader_ai_translator_show)
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (showGeminiSettings) {
            val privateProviderLabel = if (GeminiPrivateBridge.isInstalled()) {
                GeminiPrivateBridge.providerLabel()
            } else {
                "Gemini Private"
            }
            Text(
                text = stringResource(AYMR.strings.novel_reader_translation_provider),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    NovelTranslationProvider.GEMINI to
                        stringResource(AYMR.strings.novel_reader_translation_provider_gemini),
                    NovelTranslationProvider.GEMINI_PRIVATE to privateProviderLabel,
                    NovelTranslationProvider.OPENROUTER to
                        stringResource(AYMR.strings.novel_reader_translation_provider_openrouter),
                    NovelTranslationProvider.DEEPSEEK to
                        stringResource(AYMR.strings.novel_reader_translation_provider_deepseek),
                ).forEach { option ->
                    val selected = settings.translationProvider == option.first
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.clickable {
                            update(
                                option.first,
                                { o, v -> o.copy(translationProvider = v) },
                                { preferences.translationProvider().set(it) },
                            )
                        },
                    ) {
                        Text(
                            text = if (selected) "* ${option.second}" else option.second,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_translation_auto_english_title),
                subtitle = stringResource(AYMR.strings.novel_reader_translation_auto_english_summary),
                checked = settings.geminiAutoTranslateEnglishSource,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(geminiAutoTranslateEnglishSource = v) },
                        { preferences.geminiAutoTranslateEnglishSource().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_translation_prefetch_next_title),
                subtitle = stringResource(AYMR.strings.novel_reader_translation_prefetch_next_summary),
                checked = settings.geminiPrefetchNextChapterTranslation,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(geminiPrefetchNextChapterTranslation = v) },
                        { preferences.geminiPrefetchNextChapterTranslation().set(it) },
                    )
                },
            )
            if (settings.translationProvider == NovelTranslationProvider.OPENROUTER) {
                EditTextPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_openrouter_base_url),
                    subtitle = "%s",
                    icon = null,
                    value = settings.openRouterBaseUrl,
                    onConfirm = {
                        update(
                            it,
                            { o, v -> o.copy(openRouterBaseUrl = v) },
                            { preferences.openRouterBaseUrl().set(it) },
                        )
                        true
                    },
                    canBeBlank = false,
                )
                EditTextPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_openrouter_api_key),
                    subtitle = "%s",
                    icon = null,
                    value = settings.openRouterApiKey,
                    onConfirm = {
                        update(
                            it,
                            { o, v -> o.copy(openRouterApiKey = v) },
                            { preferences.openRouterApiKey().set(it) },
                        )
                        true
                    },
                    canBeBlank = false,
                )
                EditTextPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_openrouter_model),
                    subtitle = "%s",
                    icon = null,
                    value = settings.openRouterModel,
                    onConfirm = {
                        update(
                            it,
                            { o, v -> o.copy(openRouterModel = v) },
                            { preferences.openRouterModel().set(it) },
                        )
                        true
                    },
                    canBeBlank = false,
                )
            }
            if (settings.translationProvider == NovelTranslationProvider.DEEPSEEK) {
                EditTextPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_deepseek_base_url),
                    subtitle = "%s",
                    icon = null,
                    value = settings.deepSeekBaseUrl,
                    onConfirm = {
                        update(
                            it,
                            { o, v -> o.copy(deepSeekBaseUrl = v) },
                            { preferences.deepSeekBaseUrl().set(it) },
                        )
                        true
                    },
                    canBeBlank = false,
                )
                EditTextPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_deepseek_api_key),
                    subtitle = "%s",
                    icon = null,
                    value = settings.deepSeekApiKey,
                    onConfirm = {
                        update(
                            it,
                            { o, v -> o.copy(deepSeekApiKey = v) },
                            { preferences.deepSeekApiKey().set(it) },
                        )
                        true
                    },
                    canBeBlank = false,
                )
                EditTextPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_deepseek_model),
                    subtitle = "%s",
                    icon = null,
                    value = settings.deepSeekModel,
                    onConfirm = {
                        update(
                            it,
                            { o, v -> o.copy(deepSeekModel = v) },
                            { preferences.deepSeekModel().set(it) },
                        )
                        true
                    },
                    canBeBlank = false,
                )
            }
        }

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

@Composable
private fun ReadingTab(
    settings: eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings,
    sourceId: Long,
    overrideEnabled: Boolean,
    preferences: NovelReaderPreferences,
) {
    val context = LocalContext.current

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

    val selectedTheme = currentTheme(settings.backgroundColor.orEmpty(), settings.textColor.orEmpty())
    val isPreset = selectedTheme != null && novelReaderPresetThemes.contains(selectedTheme)
    val isCustom = selectedTheme != null && settings.customThemes.contains(selectedTheme)
    val colorTiles = remember(settings.customThemes) {
        (settings.customThemes + novelReaderPresetThemes).distinctBy { "${it.backgroundColor}:${it.textColor}" }
    }
    val importFailedMessage = stringResource(AYMR.strings.novel_reader_background_custom_import_failed)
    val appearanceControlState = remember(settings.appearanceMode) {
        resolveAppearanceControlState(settings.appearanceMode)
    }
    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val importResult = importNovelReaderCustomBackground(context, uri)
        val importedPath = importResult.getOrNull()
        if (importedPath.isNullOrBlank()) {
            Toast.makeText(
                context,
                importFailedMessage,
                Toast.LENGTH_SHORT,
            ).show()
            return@rememberLauncherForActivityResult
        }
        update(
            NovelReaderAppearanceMode.BACKGROUND,
            { o, v -> o.copy(appearanceMode = v) },
            { preferences.appearanceMode().set(it) },
        )
        update(
            NovelReaderBackgroundSource.CUSTOM,
            { o, v -> o.copy(backgroundSource = v) },
            { preferences.backgroundSource().set(it) },
        )
        update(
            importedPath,
            { o, v -> o.copy(customBackgroundPath = v) },
            { preferences.customBackgroundPath().set(it) },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        LnReaderSliderRow(
            label = stringResource(AYMR.strings.novel_reader_font_size),
            valueText = "${settings.fontSize}sp",
            value = settings.fontSize.toFloat(),
            range = 12f..28f,
            steps = 15,
            onChange = { update(it.toInt(), { o, v -> o.copy(fontSize = v) }, { preferences.fontSize().set(it) }) },
        )
        LnReaderSliderRow(
            label = stringResource(AYMR.strings.novel_reader_line_height),
            valueText = String.format("%.1f", settings.lineHeight),
            value = settings.lineHeight,
            range = 1.2f..2f,
            steps = 7,
            onChange = { update(it, { o, v -> o.copy(lineHeight = v) }, { preferences.lineHeight().set(it) }) },
        )
        Text(
            text = stringResource(AYMR.strings.novel_reader_paragraph_spacing),
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(NovelReaderParagraphSpacing.entries) { option ->
                val selected = settings.paragraphSpacing == option
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.clickable {
                        update(
                            option,
                            { o, v -> o.copy(paragraphSpacing = v) },
                            { preferences.paragraphSpacing().set(it) },
                        )
                    },
                ) {
                    Text(
                        text = when (option) {
                            NovelReaderParagraphSpacing.COMPACT ->
                                stringResource(AYMR.strings.novel_reader_paragraph_spacing_compact)
                            NovelReaderParagraphSpacing.NORMAL ->
                                stringResource(AYMR.strings.novel_reader_paragraph_spacing_normal)
                            NovelReaderParagraphSpacing.SPACIOUS ->
                                stringResource(AYMR.strings.novel_reader_paragraph_spacing_spacious)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        LnReaderSliderRow(
            label = stringResource(AYMR.strings.novel_reader_margins),
            valueText = "${settings.margin}dp",
            value = settings.margin.toFloat(),
            range = 0f..50f,
            steps = 49,
            onChange = { update(it.toInt(), { o, v -> o.copy(margin = v) }, { preferences.margin().set(it) }) },
        )

        AlignButtonsRow(
            selected = settings.textAlign,
            onSelect = { align ->
                update(align, { o, v -> o.copy(textAlign = v) }, { preferences.textAlign().set(it) })
            },
        )
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_force_paragraph_indent),
            subtitle = stringResource(AYMR.strings.novel_reader_force_paragraph_indent_summary),
            checked = settings.forceParagraphIndent,
            onCheckedChanged = {
                update(
                    it,
                    { o, v -> o.copy(forceParagraphIndent = v) },
                    { preferences.forceParagraphIndent().set(it) },
                )
            },
        )
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_force_bold_text),
            subtitle = stringResource(AYMR.strings.novel_reader_force_bold_text_summary),
            checked = settings.forceBoldText,
            onCheckedChanged = {
                update(it, { o, v -> o.copy(forceBoldText = v) }, { preferences.forceBoldText().set(it) })
            },
        )
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_force_italic_text),
            subtitle = stringResource(AYMR.strings.novel_reader_force_italic_text_summary),
            checked = settings.forceItalicText,
            onCheckedChanged = {
                update(it, { o, v -> o.copy(forceItalicText = v) }, { preferences.forceItalicText().set(it) })
            },
        )
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_text_shadow),
            subtitle = stringResource(AYMR.strings.novel_reader_text_shadow_summary),
            checked = settings.textShadow,
            onCheckedChanged = {
                update(it, { o, v -> o.copy(textShadow = v) }, { preferences.textShadow().set(it) })
            },
        )
        if (settings.textShadow) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                EditTextPreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_text_shadow_color),
                    subtitle = stringResource(AYMR.strings.novel_reader_text_shadow_color_summary),
                    icon = null,
                    value = settings.textShadowColor.orEmpty(),
                    onConfirm = { value ->
                        if (!isValidColorOrBlank(value)) return@EditTextPreferenceWidget false
                        update(value.trim(), { o, v ->
                            o.copy(textShadowColor = v)
                        }, { preferences.textShadowColor().set(it) })
                        true
                    },
                    canBeBlank = true,
                )
                LnReaderSliderRow(
                    label = stringResource(AYMR.strings.novel_reader_text_shadow_blur),
                    valueText = String.format("%.1f", settings.textShadowBlur),
                    value = settings.textShadowBlur,
                    range = 0f..20f,
                    steps = 39,
                    onChange = {
                        update(it, { o, v -> o.copy(textShadowBlur = v) }, { preferences.textShadowBlur().set(it) })
                    },
                )
                LnReaderSliderRow(
                    label = stringResource(AYMR.strings.novel_reader_text_shadow_x),
                    valueText = String.format("%.1f", settings.textShadowX),
                    value = settings.textShadowX,
                    range = -20f..20f,
                    steps = 79,
                    onChange = {
                        update(it, { o, v -> o.copy(textShadowX = v) }, { preferences.textShadowX().set(it) })
                    },
                )
                LnReaderSliderRow(
                    label = stringResource(AYMR.strings.novel_reader_text_shadow_y),
                    valueText = String.format("%.1f", settings.textShadowY),
                    value = settings.textShadowY,
                    range = -20f..20f,
                    steps = 79,
                    onChange = {
                        update(it, { o, v -> o.copy(textShadowY = v) }, { preferences.textShadowY().set(it) })
                    },
                )
            }
        }

        FontExamplesRow(
            selected = settings.fontFamily,
            onSelect = { font ->
                update(font, { o, v -> o.copy(fontFamily = v) }, { preferences.fontFamily().set(it) })
            },
        )

        Text(
            text = stringResource(AYMR.strings.novel_reader_appearance_mode),
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(NovelReaderAppearanceMode.entries) { mode ->
                val selected = settings.appearanceMode == mode
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.clickable {
                        update(
                            mode,
                            { o, v -> o.copy(appearanceMode = v) },
                            { preferences.appearanceMode().set(it) },
                        )
                    },
                ) {
                    Text(
                        text = when (mode) {
                            NovelReaderAppearanceMode.THEME ->
                                stringResource(AYMR.strings.novel_reader_appearance_mode_theme)
                            NovelReaderAppearanceMode.BACKGROUND ->
                                stringResource(AYMR.strings.novel_reader_appearance_mode_background)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        if (appearanceControlState.themeControlsEnabled) {
            ThemeModeRow(
                selected = settings.theme,
                onSelect = { mode ->
                    val selection = resolveThemeModeSelection(mode)
                    update(selection.theme, { o, v -> o.copy(theme = v) }, { preferences.theme().set(it) })
                    update(
                        selection.backgroundColor,
                        { o, v -> o.copy(backgroundColor = v) },
                        { preferences.backgroundColor().set(it) },
                    )
                    update(
                        selection.textColor,
                        { o, v -> o.copy(textColor = v) },
                        { preferences.textColor().set(it) },
                    )
                },
            )
            Text(
                text = stringResource(AYMR.strings.novel_reader_background_texture),
                style = MaterialTheme.typography.bodyMedium,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(NovelReaderBackgroundTexture.entries) { option ->
                    val selected = settings.backgroundTexture == option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.clickable {
                            update(
                                option,
                                { o, v -> o.copy(backgroundTexture = v) },
                                { preferences.backgroundTexture().set(it) },
                            )
                        },
                    ) {
                        Text(
                            text = when (option) {
                                NovelReaderBackgroundTexture.NONE ->
                                    stringResource(AYMR.strings.novel_reader_background_texture_none)
                                NovelReaderBackgroundTexture.PAPER_GRAIN ->
                                    stringResource(AYMR.strings.novel_reader_background_texture_paper_grain)
                                NovelReaderBackgroundTexture.LINEN ->
                                    stringResource(AYMR.strings.novel_reader_background_texture_linen)
                                NovelReaderBackgroundTexture.PARCHMENT ->
                                    stringResource(AYMR.strings.novel_reader_background_texture_parchment)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_native_texture_strength),
                valueText = "${settings.nativeTextureStrengthPercent}%",
                value = settings.nativeTextureStrengthPercent.toFloat(),
                range = 0f..200f,
                steps = 199,
                onChange = { value ->
                    val rounded = value.roundToInt()
                    update(
                        rounded,
                        { o, v -> o.copy(nativeTextureStrengthPercent = v) },
                        { preferences.nativeTextureStrengthPercent().set(it) },
                    )
                },
            )
            Text(
                text = stringResource(AYMR.strings.novel_reader_native_texture_strength_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_oled_edge_gradient),
                subtitle = stringResource(AYMR.strings.novel_reader_oled_edge_gradient_summary),
                checked = settings.oledEdgeGradient,
                onCheckedChanged = {
                    update(it, { o, v -> o.copy(oledEdgeGradient = v) }, { preferences.oledEdgeGradient().set(it) })
                },
            )
            Text(
                text = stringResource(AYMR.strings.novel_reader_theme_presets),
                style = MaterialTheme.typography.titleSmall,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(colorTiles) { theme ->
                    ThemeTile(
                        theme = theme,
                        selected = selectedTheme == theme,
                        onClick = {
                            update(
                                theme.backgroundColor,
                                { o, v -> o.copy(backgroundColor = v) },
                                { preferences.backgroundColor().set(it) },
                            )
                            update(
                                theme.textColor,
                                { o, v -> o.copy(textColor = v) },
                                { preferences.textColor().set(it) },
                            )
                        },
                    )
                }
            }

            EditTextPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_background_color),
                subtitle = "%s",
                icon = null,
                value = settings.backgroundColor.orEmpty(),
                onConfirm = { value ->
                    if (!isValidColorOrBlank(value)) return@EditTextPreferenceWidget false
                    update(value, { o, v -> o.copy(backgroundColor = v) }, { preferences.backgroundColor().set(it) })
                    true
                },
                canBeBlank = true,
            )

            EditTextPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_text_color),
                subtitle = "%s",
                icon = null,
                value = settings.textColor.orEmpty(),
                onConfirm = { value ->
                    if (!isValidColorOrBlank(value)) return@EditTextPreferenceWidget false
                    update(value, { o, v -> o.copy(textColor = v) }, { preferences.textColor().set(it) })
                    true
                },
                canBeBlank = true,
            )

            if (selectedTheme != null && !isPreset && !isCustom) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newThemes =
                                listOf(selectedTheme) + settings.customThemes.filterNot { it == selectedTheme }
                            update(newThemes, { o, v ->
                                o.copy(customThemes = v)
                            }, { preferences.customThemes().set(it) })
                        },
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_save_custom_theme),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            if (selectedTheme != null && isCustom) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newThemes = settings.customThemes.filterNot { it == selectedTheme }
                            update(newThemes, { o, v ->
                                o.copy(customThemes = v)
                            }, { preferences.customThemes().set(it) })
                        },
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_delete_custom_theme),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        } else {
            Text(
                text = stringResource(AYMR.strings.novel_reader_theme_controls_disabled_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (appearanceControlState.backgroundControlsEnabled) {
            Text(
                text = stringResource(AYMR.strings.novel_reader_background_presets),
                style = MaterialTheme.typography.titleSmall,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(novelReaderBackgroundPresets) { preset ->
                    val selected =
                        settings.backgroundSource == NovelReaderBackgroundSource.PRESET &&
                            settings.backgroundPresetId == preset.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .size(width = 170.dp, height = 175.dp)
                            .clickable {
                                update(
                                    NovelReaderBackgroundSource.PRESET,
                                    { o, v -> o.copy(backgroundSource = v) },
                                    { preferences.backgroundSource().set(it) },
                                )
                                update(
                                    preset.id,
                                    { o, v -> o.copy(backgroundPresetId = v) },
                                    { preferences.backgroundPresetId().set(it) },
                                )
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Image(
                                painter = painterResource(id = preset.imageResId),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .size(height = 90.dp, width = 154.dp),
                            )
                            Text(
                                text = backgroundPresetTitle(preset.id),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = backgroundPresetDescription(preset.id),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { backgroundPicker.launch("image/*") },
            ) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_background_upload),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Text(
                text = stringResource(AYMR.strings.novel_reader_background_upload_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.customBackgroundPath.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (settings.backgroundSource == NovelReaderBackgroundSource.CUSTOM) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = settings.customBackgroundPath,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clearNovelReaderCustomBackground(settings.customBackgroundPath)
                            update(
                                "",
                                { o, v -> o.copy(customBackgroundPath = v) },
                                { preferences.customBackgroundPath().set(it) },
                            )
                            update(
                                NovelReaderBackgroundSource.PRESET,
                                { o, v -> o.copy(backgroundSource = v) },
                                { preferences.backgroundSource().set(it) },
                            )
                        },
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_background_remove_custom),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        } else {
            Text(
                text = stringResource(AYMR.strings.novel_reader_background_controls_disabled_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_page_edge_shadow),
            subtitle = stringResource(AYMR.strings.novel_reader_page_edge_shadow_summary),
            checked = settings.pageEdgeShadow,
            onCheckedChanged = {
                update(it, { o, v -> o.copy(pageEdgeShadow = v) }, { preferences.pageEdgeShadow().set(it) })
            },
        )
        if (settings.pageEdgeShadow) {
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_page_edge_shadow_alpha),
                valueText = "${(settings.pageEdgeShadowAlpha * 100).toInt()}%",
                value = settings.pageEdgeShadowAlpha,
                range = 0.05f..1f,
                steps = 18,
                onChange = {
                    update(it, { o, v ->
                        o.copy(pageEdgeShadowAlpha = v)
                    }, { preferences.pageEdgeShadowAlpha().set(it) })
                },
            )
        }
    }
}

@Composable
private fun LnReaderSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            valueRange = range,
            steps = steps,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
            ),
        )
    }
}

@Composable
private fun AlignButtonsRow(
    selected: TextAlign,
    onSelect: (TextAlign) -> Unit,
) {
    val options = listOf(
        Triple(
            TextAlign.SOURCE,
            Icons.Outlined.Public,
            stringResource(AYMR.strings.novel_reader_text_align_source),
        ),
        Triple(
            TextAlign.LEFT,
            Icons.AutoMirrored.Filled.FormatAlignLeft,
            stringResource(AYMR.strings.novel_reader_text_align_left),
        ),
        Triple(
            TextAlign.CENTER,
            Icons.Filled.FormatAlignCenter,
            stringResource(AYMR.strings.novel_reader_text_align_center),
        ),
        Triple(
            TextAlign.JUSTIFY,
            Icons.Filled.FormatAlignJustify,
            stringResource(AYMR.strings.novel_reader_text_align_justify),
        ),
        Triple(
            TextAlign.RIGHT,
            Icons.AutoMirrored.Filled.FormatAlignRight,
            stringResource(AYMR.strings.novel_reader_text_align_right),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(AYMR.strings.novel_reader_text_align), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, icon, description) ->
                val isSelected = value == selected
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onSelect(value) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = description)
                    }
                }
            }
        }
    }
}

@Composable
private fun FontExamplesRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(AYMR.strings.novel_reader_font_family),
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(novelReaderFonts) { option ->
                val fontFamily = option.fontResId?.let { FontFamily(Font(it)) }
                val isSelected = option.id == selected
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.clickable { onSelect(option.id) },
                ) {
                    Text(
                        text = option.label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = fontFamily),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeRow(
    selected: NovelReaderTheme,
    onSelect: (NovelReaderTheme) -> Unit,
) {
    val options = listOf(
        NovelReaderTheme.SYSTEM to stringResource(AYMR.strings.novel_reader_theme_system),
        NovelReaderTheme.LIGHT to stringResource(AYMR.strings.novel_reader_theme_light),
        NovelReaderTheme.DARK to stringResource(AYMR.strings.novel_reader_theme_dark),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(AYMR.strings.novel_reader_theme), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, title) ->
                val isSelected = value == selected
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.clickable { onSelect(value) },
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeTile(
    theme: NovelReaderColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = parseColor(theme.backgroundColor) ?: MaterialTheme.colorScheme.surface
    val foreground = parseColor(theme.textColor) ?: MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = CircleShape,
            )
            .padding(3.dp)
            .background(color = background, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "A",
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun backgroundPresetTitle(presetId: String): String {
    return when (presetId) {
        NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_linen_paper_title)
        NOVEL_READER_BACKGROUND_PRESET_AGED_PAGE_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_aged_page_title)
        NOVEL_READER_BACKGROUND_PRESET_CRUMPLED_SHEET_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_crumpled_sheet_title)
        NOVEL_READER_BACKGROUND_PRESET_NIGHT_VELVET_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_night_velvet_title)
        NOVEL_READER_BACKGROUND_PRESET_DARK_WOOD_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_dark_wood_title)
        else -> presetId
    }
}

@Composable
private fun backgroundPresetDescription(presetId: String): String {
    return when (presetId) {
        NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_linen_paper_description)
        NOVEL_READER_BACKGROUND_PRESET_AGED_PAGE_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_aged_page_description)
        NOVEL_READER_BACKGROUND_PRESET_CRUMPLED_SHEET_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_crumpled_sheet_description)
        NOVEL_READER_BACKGROUND_PRESET_NIGHT_VELVET_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_night_velvet_description)
        NOVEL_READER_BACKGROUND_PRESET_DARK_WOOD_ID ->
            stringResource(AYMR.strings.novel_reader_background_preset_dark_wood_description)
        else -> ""
    }
}

private fun parseColor(value: String): Color? {
    return runCatching { Color(AndroidColor.parseColor(value)) }.getOrNull()
}

private fun currentTheme(backgroundColor: String, textColor: String): NovelReaderColorTheme? {
    if (backgroundColor.isBlank() || textColor.isBlank()) return null
    if (!isValidColorOrBlank(backgroundColor) || !isValidColorOrBlank(textColor)) return null
    return NovelReaderColorTheme(backgroundColor = backgroundColor, textColor = textColor)
}

internal data class ThemeModeSelection(
    val theme: NovelReaderTheme,
    val backgroundColor: String,
    val textColor: String,
)

internal data class AppearanceControlState(
    val themeControlsEnabled: Boolean,
    val backgroundControlsEnabled: Boolean,
)

internal fun resolveAppearanceControlState(
    appearanceMode: NovelReaderAppearanceMode,
): AppearanceControlState {
    return when (appearanceMode) {
        NovelReaderAppearanceMode.THEME -> AppearanceControlState(
            themeControlsEnabled = true,
            backgroundControlsEnabled = false,
        )
        NovelReaderAppearanceMode.BACKGROUND -> AppearanceControlState(
            themeControlsEnabled = false,
            backgroundControlsEnabled = true,
        )
    }
}

internal fun resolveThemeModeSelection(theme: NovelReaderTheme): ThemeModeSelection {
    // Base mode selection must restore fallback theme colors from reader screen logic.
    return ThemeModeSelection(
        theme = theme,
        backgroundColor = "",
        textColor = "",
    )
}

private fun isValidColorOrBlank(value: String): Boolean {
    if (value.isBlank()) return true
    return value.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))
}
