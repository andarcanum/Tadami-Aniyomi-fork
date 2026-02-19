package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.presentation.reader.novel.novelReaderFonts
import eu.kanade.presentation.reader.novel.novelReaderPresetThemes
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme
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

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_display),
            preferenceItems = persistentListOf(
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
        val bgPref = prefs.backgroundColor()
        val bg by bgPref.collectAsState()
        val textPref = prefs.textColor()
        val text by textPref.collectAsState()
        val customThemesPref = prefs.customThemes()
        val customThemes by customThemesPref.collectAsState()

        val currentTheme = currentTheme(bg, text)
        val isPreset = currentTheme != null && novelReaderPresetThemes.contains(currentTheme)
        val isCustom = currentTheme != null && customThemes.contains(currentTheme)

        val items = mutableListOf<Preference.PreferenceItem<out Any>>(
            Preference.PreferenceItem.ListPreference(
                preference = prefs.theme(),
                entries = persistentMapOf(
                    NovelReaderTheme.SYSTEM to stringResource(AYMR.strings.novel_reader_theme_system),
                    NovelReaderTheme.LIGHT to stringResource(AYMR.strings.novel_reader_theme_light),
                    NovelReaderTheme.DARK to stringResource(AYMR.strings.novel_reader_theme_dark),
                ),
                title = stringResource(AYMR.strings.novel_reader_theme),
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

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_theme_settings),
            preferenceItems = items.toList().toImmutableList(),
        )
    }

    @Composable
    private fun getNavigationGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val autoScrollPref = prefs.autoScroll()
        val autoScroll by autoScrollPref.collectAsState()
        val autoScrollIntervalPref = prefs.autoScrollInterval()
        val autoScrollInterval by autoScrollIntervalPref.collectAsState()
        val autoScrollOffsetPref = prefs.autoScrollOffset()
        val autoScrollOffset by autoScrollOffsetPref.collectAsState()

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
                    preference = autoScrollPref,
                    title = stringResource(AYMR.strings.novel_reader_auto_scroll),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = autoScrollInterval,
                    title = stringResource(AYMR.strings.novel_reader_auto_scroll_interval),
                    subtitle = autoScrollInterval.toString(),
                    valueRange = 1..60,
                    enabled = autoScroll,
                    onValueChanged = {
                        autoScrollIntervalPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = autoScrollOffset,
                    title = stringResource(AYMR.strings.novel_reader_auto_scroll_offset),
                    subtitle = autoScrollOffset.toString(),
                    valueRange = 0..2000,
                    enabled = autoScroll,
                    onValueChanged = {
                        autoScrollOffsetPref.set(it)
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getAccessibilityGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
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
