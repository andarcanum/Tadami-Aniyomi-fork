package eu.kanade.presentation.more.settings.screen

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.presentation.reader.novel.NOVEL_READER_BACKGROUND_PRESET_AGED_PAGE_ID
import eu.kanade.presentation.reader.novel.NOVEL_READER_BACKGROUND_PRESET_CRUMPLED_SHEET_ID
import eu.kanade.presentation.reader.novel.NOVEL_READER_BACKGROUND_PRESET_DARK_WOOD_ID
import eu.kanade.presentation.reader.novel.NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID
import eu.kanade.presentation.reader.novel.NOVEL_READER_BACKGROUND_PRESET_NIGHT_VELVET_ID
import eu.kanade.presentation.reader.novel.autoScrollSpeedToInterval
import eu.kanade.presentation.reader.novel.clearNovelReaderCustomBackground
import eu.kanade.presentation.reader.novel.intervalToAutoScrollSpeed
import eu.kanade.presentation.reader.novel.novelReaderBackgroundPresets
import eu.kanade.presentation.reader.novel.novelReaderFonts
import eu.kanade.presentation.reader.novel.novelReaderPresetThemes
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderChapterDiskCache
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderChapterDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderParagraphSpacing
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import android.graphics.Color as AndroidColor

object SettingsNovelReaderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_category_novel_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val prefs = remember { Injekt.get<NovelReaderPreferences>() }
        return listOf(
            getDisplayGroup(prefs),
            getThemeGroup(prefs),
            getNavigationGroup(prefs),
            getAccessibilityGroup(prefs),
            getAdvancedGroup(prefs),
        )
    }

    @Composable
    private fun getDisplayGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val fontSizePref = prefs.fontSize()
        val fontSize by fontSizePref.collectAsState()
        val lineHeightPref = prefs.lineHeight()
        val lineHeight by lineHeightPref.collectAsState()
        val marginPref = prefs.margin()
        val margin by marginPref.collectAsState()
        val fontFamilyPref = prefs.fontFamily()
        val selectedFontFamily by fontFamilyPref.collectAsState()
        val geminiEnabled by prefs.geminiEnabled().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.geminiEnabled(),
                    title = stringResource(AYMR.strings.novel_reader_gemini_enabled),
                    subtitle = stringResource(AYMR.strings.novel_reader_gemini_enabled_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.geminiPromptMode(),
                    entries = persistentMapOf(
                        GeminiPromptMode.CLASSIC to stringResource(
                            AYMR.strings.novel_reader_gemini_prompt_mode_classic,
                        ),
                        GeminiPromptMode.ADULT_18 to stringResource(AYMR.strings.novel_reader_gemini_prompt_mode_adult),
                    ),
                    title = stringResource(AYMR.strings.novel_reader_gemini_prompt_mode),
                    enabled = geminiEnabled,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = fontSize,
                    title = stringResource(AYMR.strings.novel_reader_font_size),
                    subtitle = "${fontSize}sp",
                    valueRange = 12..28,
                    onValueChanged = {
                        fontSizePref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (lineHeight * 10).toInt(),
                    title = stringResource(AYMR.strings.novel_reader_line_height),
                    subtitle = String.format("%.1f", lineHeight),
                    valueRange = 12..20,
                    onValueChanged = {
                        lineHeightPref.set(it / 10f)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = margin,
                    title = stringResource(AYMR.strings.novel_reader_margins),
                    subtitle = "${margin}dp",
                    valueRange = 0..50,
                    onValueChanged = {
                        marginPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.textAlign(),
                    entries = TextAlign.entries
                        .associate { it to getTextAlignString(it) }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.novel_reader_text_align),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.paragraphSpacing(),
                    entries = persistentMapOf(
                        NovelReaderParagraphSpacing.COMPACT to
                            stringResource(AYMR.strings.novel_reader_paragraph_spacing_compact),
                        NovelReaderParagraphSpacing.NORMAL to
                            stringResource(AYMR.strings.novel_reader_paragraph_spacing_normal),
                        NovelReaderParagraphSpacing.SPACIOUS to
                            stringResource(AYMR.strings.novel_reader_paragraph_spacing_spacious),
                    ),
                    title = stringResource(AYMR.strings.novel_reader_paragraph_spacing),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.forceParagraphIndent(),
                    title = stringResource(AYMR.strings.novel_reader_force_paragraph_indent),
                    subtitle = stringResource(AYMR.strings.novel_reader_force_paragraph_indent_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.preserveSourceTextAlignInNative(),
                    title = stringResource(AYMR.strings.novel_reader_preserve_source_text_align_native),
                    subtitle = stringResource(AYMR.strings.novel_reader_preserve_source_text_align_native_summary),
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.novel_reader_font_family),
                ) {
                    BasePreferenceWidget(
                        title = stringResource(AYMR.strings.novel_reader_font_family),
                        subcomponent = {
                            NovelReaderFontPreviewRow(
                                selectedFontId = selectedFontFamily,
                                onSelect = { fontFamilyPref.set(it) },
                            )
                        },
                    )
                },
            ),
        )
    }

    @Composable
    private fun getThemeGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val appearanceModePref = prefs.appearanceMode()
        val appearanceMode by appearanceModePref.collectAsState()
        val bgPref = prefs.backgroundColor()
        val bg by bgPref.collectAsState()
        val textPref = prefs.textColor()
        val text by textPref.collectAsState()
        val backgroundSourcePref = prefs.backgroundSource()
        val backgroundSource by backgroundSourcePref.collectAsState()
        val backgroundPresetIdPref = prefs.backgroundPresetId()
        val backgroundPresetId by backgroundPresetIdPref.collectAsState()
        val customBackgroundPathPref = prefs.customBackgroundPath()
        val customBackgroundPath by customBackgroundPathPref.collectAsState()
        val customThemesPref = prefs.customThemes()
        val customThemes by customThemesPref.collectAsState()

        val currentTheme = currentTheme(bg, text)
        val isPreset = currentTheme != null && novelReaderPresetThemes.contains(currentTheme)
        val isCustom = currentTheme != null && customThemes.contains(currentTheme)

        val items = mutableListOf<Preference.PreferenceItem<out Any>>(
            Preference.PreferenceItem.ListPreference(
                preference = appearanceModePref,
                entries = persistentMapOf(
                    eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode.THEME to
                        stringResource(AYMR.strings.novel_reader_appearance_mode_theme),
                    eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode.BACKGROUND to
                        stringResource(AYMR.strings.novel_reader_appearance_mode_background),
                ),
                title = stringResource(AYMR.strings.novel_reader_appearance_mode),
            ),
        )

        if (appearanceMode == eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode.THEME) {
            items += listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.theme(),
                    entries = persistentMapOf(
                        NovelReaderTheme.SYSTEM to stringResource(AYMR.strings.novel_reader_theme_system),
                        NovelReaderTheme.LIGHT to stringResource(AYMR.strings.novel_reader_theme_light),
                        NovelReaderTheme.DARK to stringResource(AYMR.strings.novel_reader_theme_dark),
                    ),
                    title = stringResource(AYMR.strings.novel_reader_theme),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.backgroundTexture(),
                    entries = persistentMapOf(
                        NovelReaderBackgroundTexture.NONE to
                            stringResource(AYMR.strings.novel_reader_background_texture_none),
                        NovelReaderBackgroundTexture.PAPER_GRAIN to
                            stringResource(AYMR.strings.novel_reader_background_texture_paper_grain),
                        NovelReaderBackgroundTexture.LINEN to
                            stringResource(AYMR.strings.novel_reader_background_texture_linen),
                        NovelReaderBackgroundTexture.PARCHMENT to
                            stringResource(AYMR.strings.novel_reader_background_texture_parchment),
                    ),
                    title = stringResource(AYMR.strings.novel_reader_background_texture),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.oledEdgeGradient(),
                    title = stringResource(AYMR.strings.novel_reader_oled_edge_gradient),
                    subtitle = stringResource(AYMR.strings.novel_reader_oled_edge_gradient_summary),
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.novel_reader_theme_presets),
                ) {
                    BasePreferenceWidget(
                        title = stringResource(AYMR.strings.novel_reader_theme_presets),
                        subcomponent = {
                            NovelReaderThemePresetRow(
                                selectedTheme = currentTheme,
                                onSelect = { preset ->
                                    bgPref.set(preset.backgroundColor)
                                    textPref.set(preset.textColor)
                                },
                            )
                        },
                    )
                },
                Preference.PreferenceItem.EditTextInfoPreference(
                    preference = bgPref,
                    title = stringResource(AYMR.strings.novel_reader_background_color),
                    subtitle = "%s",
                    dialogSubtitle = stringResource(AYMR.strings.novel_reader_color_input_hint),
                    validate = ::isValidColorOrBlank,
                ),
                Preference.PreferenceItem.EditTextInfoPreference(
                    preference = textPref,
                    title = stringResource(AYMR.strings.novel_reader_text_color),
                    subtitle = "%s",
                    dialogSubtitle = stringResource(AYMR.strings.novel_reader_color_input_hint),
                    validate = ::isValidColorOrBlank,
                ),
            )

            if (currentTheme != null && !isPreset && !isCustom) {
                items += Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_save_custom_theme),
                    subtitle = "${currentTheme.backgroundColor} / ${currentTheme.textColor}",
                    onClick = {
                        customThemesPref.set(listOf(currentTheme) + customThemes.filterNot { it == currentTheme })
                    },
                )
            }

            if (currentTheme != null && isCustom) {
                items += Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_delete_custom_theme),
                    subtitle = "${currentTheme.backgroundColor} / ${currentTheme.textColor}",
                    onClick = {
                        customThemesPref.set(customThemes.filterNot { it == currentTheme })
                    },
                )
            }
        } else {
            items += Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.novel_reader_theme),
                subtitle = stringResource(AYMR.strings.novel_reader_theme_controls_disabled_summary),
            )
            items += Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.novel_reader_background_presets),
            ) {
                BasePreferenceWidget(
                    title = stringResource(AYMR.strings.novel_reader_background_presets),
                    subcomponent = {
                        NovelReaderBackgroundPresetRow(
                            selectedPresetId = backgroundPresetId,
                            onSelect = { presetId ->
                                backgroundSourcePref.set(
                                    eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource.PRESET,
                                )
                                backgroundPresetIdPref.set(presetId)
                            },
                        )
                    },
                )
            }
            if (customBackgroundPath.isNotBlank()) {
                items += Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_background_upload),
                    subtitle = customBackgroundPath,
                    onClick = {
                        backgroundSourcePref.set(
                            eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource.CUSTOM,
                        )
                    },
                )
                items += Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_background_remove_custom),
                    onClick = {
                        clearNovelReaderCustomBackground(customBackgroundPath)
                        customBackgroundPathPref.set("")
                        if (backgroundSource ==
                            eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource.CUSTOM
                        ) {
                            backgroundSourcePref.set(
                                eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource.PRESET,
                            )
                        }
                    },
                )
            } else {
                items += Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_background_upload),
                    subtitle = stringResource(AYMR.strings.novel_reader_background_upload_hint),
                )
            }
            items += Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.novel_reader_background_texture),
                subtitle = stringResource(AYMR.strings.novel_reader_background_controls_disabled_summary),
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_theme_settings),
            preferenceItems = items.toList().toImmutableList(),
        )
    }

    @Composable
    private fun getNavigationGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val autoScrollIntervalPref = prefs.autoScrollInterval()
        val autoScrollInterval by autoScrollIntervalPref.collectAsState()
        val autoScrollSpeed = intervalToAutoScrollSpeed(autoScrollInterval)
        val autoScrollOffsetPref = prefs.autoScrollOffset()
        val autoScrollOffset by autoScrollOffsetPref.collectAsState()
        val cacheReadChaptersPref = prefs.cacheReadChapters()
        val cacheReadChapters by cacheReadChaptersPref.collectAsState()
        val cacheReadChaptersUnlimitedPref = prefs.cacheReadChaptersUnlimited()
        val cacheReadChaptersUnlimited by cacheReadChaptersUnlimitedPref.collectAsState()
        val chapterCacheRefreshTick = remember { mutableIntStateOf(0) }
        val chapterCacheStats by produceState(
            initialValue = NovelReaderChapterDiskCacheStore.stats(),
            cacheReadChapters,
            cacheReadChaptersUnlimited,
            chapterCacheRefreshTick.intValue,
        ) {
            if (!cacheReadChaptersUnlimited) {
                NovelReaderChapterDiskCacheStore.trimToCurrentLimits()
            }
            value = NovelReaderChapterDiskCacheStore.stats()
        }
        val chapterCacheLimitSizeText = remember(context) {
            Formatter.formatFileSize(context, NovelReaderChapterDiskCache.DEFAULT_MAX_TOTAL_BYTES)
        }
        val chapterCacheSizeText = remember(chapterCacheStats.totalBytes, context) {
            Formatter.formatFileSize(context, chapterCacheStats.totalBytes)
        }
        val chapterCacheSummary = if (cacheReadChaptersUnlimited) {
            stringResource(
                AYMR.strings.novel_reader_chapter_cache_size_summary_unlimited,
                chapterCacheSizeText,
                chapterCacheStats.entryCount.toString(),
            )
        } else {
            stringResource(
                AYMR.strings.novel_reader_chapter_cache_size_summary_limited,
                chapterCacheSizeText,
                chapterCacheStats.entryCount.toString(),
                chapterCacheLimitSizeText,
                NovelReaderChapterDiskCache.DEFAULT_MAX_ENTRIES.toString(),
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_navigation),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.useVolumeButtons(),
                    title = stringResource(AYMR.strings.novel_reader_volume_buttons),
                    subtitle = stringResource(AYMR.strings.novel_reader_volume_buttons_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.verticalSeekbar(),
                    title = stringResource(AYMR.strings.novel_reader_vertical_seekbar),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.swipeGestures(),
                    title = stringResource(AYMR.strings.novel_reader_swipe_gestures),
                    subtitle = stringResource(AYMR.strings.novel_reader_swipe_gestures_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.swipeToNextChapter(),
                    title = stringResource(AYMR.strings.novel_reader_swipe_to_next),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.swipeToPrevChapter(),
                    title = stringResource(AYMR.strings.novel_reader_swipe_to_prev),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.tapToScroll(),
                    title = stringResource(AYMR.strings.novel_reader_tap_to_scroll),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.pageReader(),
                    title = stringResource(AYMR.strings.novel_reader_page_mode),
                    subtitle = stringResource(AYMR.strings.novel_reader_page_mode_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.preferWebViewRenderer(),
                    title = stringResource(AYMR.strings.novel_reader_prefer_webview_renderer),
                    subtitle = stringResource(AYMR.strings.novel_reader_prefer_webview_renderer_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.richNativeRendererExperimental(),
                    title = stringResource(AYMR.strings.novel_reader_rich_native_renderer_experimental),
                    subtitle = stringResource(AYMR.strings.novel_reader_rich_native_renderer_experimental_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = autoScrollSpeed,
                    title = stringResource(AYMR.strings.novel_reader_auto_scroll_speed),
                    subtitle = autoScrollSpeed.toString(),
                    valueRange = 1..100,
                    enabled = true,
                    onValueChanged = {
                        autoScrollIntervalPref.set(autoScrollSpeedToInterval(it.coerceIn(1, 100)))
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = autoScrollOffset,
                    title = stringResource(AYMR.strings.novel_reader_auto_scroll_offset),
                    subtitle = autoScrollOffset.toString(),
                    valueRange = 0..2000,
                    enabled = true,
                    onValueChanged = {
                        autoScrollOffsetPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.prefetchNextChapter(),
                    title = stringResource(AYMR.strings.novel_reader_prefetch_next_chapter),
                    subtitle = stringResource(AYMR.strings.novel_reader_prefetch_next_chapter_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.cacheReadChapters(),
                    title = stringResource(AYMR.strings.novel_reader_cache_read_chapters),
                    subtitle = stringResource(AYMR.strings.novel_reader_cache_read_chapters_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = cacheReadChaptersUnlimitedPref,
                    title = stringResource(AYMR.strings.novel_reader_cache_read_chapters_unlimited),
                    subtitle = stringResource(AYMR.strings.novel_reader_cache_read_chapters_unlimited_summary),
                    enabled = cacheReadChapters,
                    onValueChanged = { enabled ->
                        if (!enabled) {
                            NovelReaderChapterDiskCacheStore.trimToCurrentLimits(unlimitedOverride = false)
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_chapter_cache_size),
                    subtitle = chapterCacheSummary,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_reader_clear_chapter_cache),
                    subtitle = stringResource(AYMR.strings.novel_reader_clear_chapter_cache_summary),
                    enabled = chapterCacheStats.entryCount > 0,
                    onClick = {
                        NovelReaderChapterDiskCacheStore.clear()
                        chapterCacheRefreshTick.intValue++
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getAccessibilityGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val showKindleInfoBlockPref = prefs.showKindleInfoBlock()
        val showKindleInfoBlock by showKindleInfoBlockPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_accessibility),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.fullScreenMode(),
                    title = stringResource(AYMR.strings.novel_reader_fullscreen),
                    subtitle = stringResource(AYMR.strings.novel_reader_fullscreen_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.keepScreenOn(),
                    title = stringResource(AYMR.strings.novel_reader_keep_screen_on),
                    subtitle = stringResource(AYMR.strings.novel_reader_keep_screen_on_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.showScrollPercentage(),
                    title = stringResource(AYMR.strings.novel_reader_show_scroll_percentage),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.showBatteryAndTime(),
                    title = stringResource(AYMR.strings.novel_reader_show_battery_time),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showKindleInfoBlockPref,
                    title = stringResource(AYMR.strings.novel_reader_show_kindle_info_block),
                    subtitle = stringResource(AYMR.strings.novel_reader_show_kindle_info_block_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.showTimeToEnd(),
                    title = stringResource(AYMR.strings.novel_reader_show_time_to_end),
                    enabled = showKindleInfoBlock,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.showWordCount(),
                    title = stringResource(AYMR.strings.novel_reader_show_word_count),
                    enabled = showKindleInfoBlock,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.bionicReading(),
                    title = stringResource(AYMR.strings.novel_reader_bionic_reading),
                ),
            ),
        )
    }

    @Composable
    private fun getAdvancedGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_advanced),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.MultiLineEditTextPreference(
                    preference = prefs.customCSS(),
                    title = stringResource(AYMR.strings.novel_reader_custom_css),
                    subtitle = stringResource(AYMR.strings.novel_reader_custom_css_hint),
                    canBeBlank = true,
                ),
                Preference.PreferenceItem.MultiLineEditTextPreference(
                    preference = prefs.customJS(),
                    title = stringResource(AYMR.strings.novel_reader_custom_js),
                    subtitle = stringResource(AYMR.strings.novel_reader_custom_js_hint),
                    canBeBlank = true,
                ),
            ),
        )
    }

    @Composable
    private fun getTextAlignString(textAlign: TextAlign): String {
        return when (textAlign) {
            TextAlign.SOURCE -> stringResource(AYMR.strings.novel_reader_text_align_source)
            TextAlign.LEFT -> stringResource(AYMR.strings.novel_reader_text_align_left)
            TextAlign.CENTER -> stringResource(AYMR.strings.novel_reader_text_align_center)
            TextAlign.JUSTIFY -> stringResource(AYMR.strings.novel_reader_text_align_justify)
            TextAlign.RIGHT -> stringResource(AYMR.strings.novel_reader_text_align_right)
        }
    }

    private fun currentTheme(backgroundColor: String, textColor: String): NovelReaderColorTheme? {
        if (backgroundColor.isBlank() || textColor.isBlank()) return null
        if (!isValidColorOrBlank(backgroundColor) || !isValidColorOrBlank(textColor)) return null
        return NovelReaderColorTheme(backgroundColor = backgroundColor, textColor = textColor)
    }

    private fun isValidColorOrBlank(value: String): Boolean {
        if (value.isBlank()) return true
        return value.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))
    }
}

@Composable
private fun NovelReaderFontPreviewRow(
    selectedFontId: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(novelReaderFonts, key = { it.id }) { option ->
            val fontFamily = option.fontResId?.let { FontFamily(Font(it)) }
            val isSelected = option.id == selectedFontId
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.clickable { onSelect(option.id) },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Aa",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = fontFamily),
                    )
                    Text(
                        text = option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = fontFamily),
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelReaderThemePresetRow(
    selectedTheme: NovelReaderColorTheme?,
    onSelect: (NovelReaderColorTheme) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(novelReaderPresetThemes, key = { "${it.backgroundColor}:${it.textColor}" }) { theme ->
            NovelReaderThemePreviewTile(
                theme = theme,
                selected = selectedTheme == theme,
                onClick = { onSelect(theme) },
            )
        }
    }
}

@Composable
private fun NovelReaderBackgroundPresetRow(
    selectedPresetId: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(novelReaderBackgroundPresets, key = { it.id }) { preset ->
            val selected = preset.id == selectedPresetId
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.clickable { onSelect(preset.id) },
            ) {
                Column(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(width = 148.dp, height = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Image(
                        painter = painterResource(id = preset.imageResId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(height = 92.dp, width = 136.dp),
                    )
                    Text(
                        text = readerBackgroundPresetTitle(preset.id),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = readerBackgroundPresetDescription(preset.id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun readerBackgroundPresetTitle(presetId: String): String {
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
private fun readerBackgroundPresetDescription(presetId: String): String {
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

@Composable
private fun NovelReaderThemePreviewTile(
    theme: NovelReaderColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = parseNovelReaderPreviewColor(theme.backgroundColor)
        ?: MaterialTheme.colorScheme.surface
    val foreground = parseNovelReaderPreviewColor(theme.textColor)
        ?: MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 32.dp)
                    .background(color = background, shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Aa",
                    color = foreground,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(color = background, shape = CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(color = foreground, shape = CircleShape),
                )
            }
        }
    }
}

private fun parseNovelReaderPreviewColor(value: String): Color? {
    return runCatching { Color(AndroidColor.parseColor(value)) }.getOrNull()
}
