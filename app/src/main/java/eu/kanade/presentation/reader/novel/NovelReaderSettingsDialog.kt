package eu.kanade.presentation.reader.novel

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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
        SwitchPreferenceWidget(
            title = stringResource(AYMR.strings.novel_reader_auto_scroll),
            checked = settings.autoScroll,
            onCheckedChanged = { update(it, { o, v -> o.copy(autoScroll = v) }, { preferences.autoScroll().set(it) }) },
        )
        LnReaderSliderRow(
            label = stringResource(AYMR.strings.novel_reader_auto_scroll_interval),
            valueText = settings.autoScrollInterval.toString(),
            value = settings.autoScrollInterval.toFloat(),
            range = 1f..60f,
            steps = 58,
            enabled = settings.autoScroll,
            onChange = {
                update(
                    it.toInt(),
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
            enabled = settings.autoScroll,
            onChange = {
                update(it.toInt(), { o, v -> o.copy(autoScrollOffset = v) }, { preferences.autoScrollOffset().set(it) })
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
            title = stringResource(AYMR.strings.novel_reader_bionic_reading),
            checked = settings.bionicReading,
            onCheckedChanged = {
                update(it, { o, v -> o.copy(bionicReading = v) }, { preferences.bionicReading().set(it) })
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

@Composable
private fun ReadingTab(
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

    val selectedTheme = currentTheme(settings.backgroundColor.orEmpty(), settings.textColor.orEmpty())
    val isPreset = selectedTheme != null && novelReaderPresetThemes.contains(selectedTheme)
    val isCustom = selectedTheme != null && settings.customThemes.contains(selectedTheme)
    val colorTiles = remember(settings.customThemes) {
        (settings.customThemes + novelReaderPresetThemes).distinctBy { "${it.backgroundColor}:${it.textColor}" }
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

        FontExamplesRow(
            selected = settings.fontFamily,
            onSelect = { font ->
                update(font, { o, v -> o.copy(fontFamily = v) }, { preferences.fontFamily().set(it) })
            },
        )

        ThemeModeRow(
            selected = settings.theme,
            onSelect = { mode -> update(mode, { o, v -> o.copy(theme = v) }, { preferences.theme().set(it) }) },
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
                        val newThemes = listOf(selectedTheme) + settings.customThemes.filterNot { it == selectedTheme }
                        update(newThemes, { o, v -> o.copy(customThemes = v) }, { preferences.customThemes().set(it) })
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
                        update(newThemes, { o, v -> o.copy(customThemes = v) }, { preferences.customThemes().set(it) })
                    },
            ) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_delete_custom_theme),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
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

private fun parseColor(value: String): Color? {
    return runCatching { Color(AndroidColor.parseColor(value)) }.getOrNull()
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
