@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.reader.settings.AuroraFieldLabel
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraMiniOption
import eu.kanade.presentation.reader.settings.AuroraToggleRow
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.roundToInt

@Composable
fun GeneralTab(
    settings: eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings,
    sourceId: Long,
    overrideEnabled: Boolean,
    preferences: NovelReaderPreferences,
    onDismissRequest: () -> Unit,
    bookModeActive: Boolean = false,
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

    val bookModeHeadingsPref = preferences.bookModeShowChapterHeadings()
    val bookModeHeadings by bookModeHeadingsPref.collectAsState()
    val aurora = AuroraTheme.colors
    val selectedColorTheme = resolveNovelReaderColorTheme(
        settings.backgroundColor.orEmpty(),
        settings.textColor.orEmpty(),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Scope control (Общие vs Для источника)
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_settings_title)) {
            AuroraToggleRow(
                label = stringResource(AYMR.strings.novel_reader_override_source),
                subtitle = if (overrideEnabled) {
                    stringResource(AYMR.strings.novel_reader_editing_source)
                } else {
                    stringResource(AYMR.strings.novel_reader_override_summary)
                },
                checked = overrideEnabled,
                onClick = {
                    if (overrideEnabled) {
                        preferences.setSourceOverride(sourceId, null)
                    } else {
                        preferences.enableSourceOverride(sourceId)
                    }
                },
            )
        }

        // Режим чтения
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_reading_behavior)) {
            if (bookModeActive) {
                AuroraToggleRow(
                    label = stringResource(AYMR.strings.novel_reader_book_mode_show_chapter_headings),
                    subtitle = stringResource(AYMR.strings.novel_reader_book_mode_show_chapter_headings_summary),
                    checked = bookModeHeadings,
                    onClick = { bookModeHeadingsPref.set(!bookModeHeadings) },
                )
            }
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
                    label = stringResource(AYMR.strings.novel_reader_two_page_landscape),
                    subtitle = stringResource(AYMR.strings.novel_reader_two_page_landscape_summary),
                    checked = settings.twoPageLandscape,
                    onClick = {
                        update(
                            !settings.twoPageLandscape,
                            { o, v -> o.copy(twoPageLandscape = v) },
                            { preferences.twoPageLandscape().set(it) },
                        )
                    },
                )
                if (settings.twoPageLandscape) {
                    AuroraToggleRow(
                        label = stringResource(AYMR.strings.novel_reader_spread_cutout_guard),
                        subtitle = stringResource(AYMR.strings.novel_reader_spread_cutout_guard_summary),
                        checked = settings.spreadCutoutGuard,
                        // Device-level setting: the cutout-guard is a property of the display, not
                        // of a source, so it only ever toggles the global preference.
                        onClick = { preferences.spreadCutoutGuard().set(!settings.spreadCutoutGuard) },
                    )
                }
            }
        }

        // Внешний вид страницы (Размер текста + Тема / Фон)
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_appearance)) {
            // Слайдер размера шрифта (стиль Apple Books / Kindle с буквами A по краям)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_font_size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = aurora.textPrimary,
                    )
                    Text(
                        text = "${settings.fontSize} sp",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = aurora.accent,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
                            val newSize = (settings.fontSize - 1).coerceIn(12, 28)
                            update(newSize, { o, v -> o.copy(fontSize = v) }, { preferences.fontSize().set(it) })
                        },
                        enabled = settings.fontSize > 12,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text(
                            text = "A",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (settings.fontSize >
                                12
                            ) {
                                aurora.textPrimary
                            } else {
                                aurora.textSecondary.copy(alpha = 0.35f)
                            },
                        )
                    }

                    var draftFontSize by rememberSaveable { mutableFloatStateOf(settings.fontSize.toFloat()) }
                    LaunchedEffect(settings.fontSize) {
                        draftFontSize = settings.fontSize.toFloat()
                    }

                    Slider(
                        value = draftFontSize,
                        onValueChange = { draftFontSize = it },
                        onValueChangeFinished = {
                            val committed = draftFontSize.roundToInt().coerceIn(12, 28)
                            if (committed != settings.fontSize) {
                                update(committed, { o, v -> o.copy(fontSize = v) }, { preferences.fontSize().set(it) })
                            }
                        },
                        valueRange = 12f..28f,
                        steps = 15,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = aurora.accent,
                            activeTrackColor = aurora.accent,
                            inactiveTrackColor = if (aurora.isDark) {
                                Color.White.copy(
                                    alpha = 0.12f,
                                )
                            } else {
                                Color.Black.copy(alpha = 0.08f)
                            },
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                        ),
                    )

                    IconButton(
                        onClick = {
                            val newSize = (settings.fontSize + 1).coerceIn(12, 28)
                            update(newSize, { o, v -> o.copy(fontSize = v) }, { preferences.fontSize().set(it) })
                        },
                        enabled = settings.fontSize < 28,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text(
                            text = "A",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (settings.fontSize <
                                28
                            ) {
                                aurora.textPrimary
                            } else {
                                aurora.textSecondary.copy(alpha = 0.35f)
                            },
                        )
                    }
                }
            }

            // Сегментированный переключатель: Тема vs Фон
            AuroraFieldLabel(stringResource(AYMR.strings.novel_reader_appearance_mode))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AuroraMiniOption(
                    selected = settings.appearanceMode == NovelReaderAppearanceMode.THEME,
                    onClick = {
                        update(
                            NovelReaderAppearanceMode.THEME,
                            { o, v -> o.copy(appearanceMode = v) },
                            { preferences.appearanceMode().set(it) },
                        )
                    },
                    label = stringResource(AYMR.strings.novel_reader_appearance_mode_theme),
                    icon = Icons.Outlined.Palette,
                    modifier = Modifier.weight(1f),
                )
                AuroraMiniOption(
                    selected = settings.appearanceMode == NovelReaderAppearanceMode.BACKGROUND,
                    onClick = {
                        update(
                            NovelReaderAppearanceMode.BACKGROUND,
                            { o, v -> o.copy(appearanceMode = v) },
                            { preferences.appearanceMode().set(it) },
                        )
                    },
                    label = stringResource(AYMR.strings.novel_reader_appearance_mode_background),
                    icon = Icons.Outlined.Image,
                    modifier = Modifier.weight(1f),
                )
            }

            // Быстрые пресеты темы или фона
            if (settings.appearanceMode == NovelReaderAppearanceMode.THEME) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(novelReaderPresetThemes) { theme ->
                        val isSelected = selectedColorTheme == theme
                        val parsedBg = remember(theme.backgroundColor) { parseHexColor(theme.backgroundColor) }
                        val parsedFg = remember(theme.textColor) { parseHexColor(theme.textColor) }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    update(
                                        NovelReaderAppearanceMode.THEME,
                                        { o, v -> o.copy(appearanceMode = v) },
                                        { preferences.appearanceMode().set(it) },
                                    )
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
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) aurora.accent.copy(alpha = 0.18f) else Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    aurora.accent
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.25f,
                                    )
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(parsedBg)
                                        .border(1.dp, parsedFg.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "A",
                                        color = parsedFg,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(novelReaderBackgroundPresets) { preset ->
                        val isSelected = settings.backgroundSource == NovelReaderBackgroundSource.PRESET &&
                            settings.backgroundPresetId == preset.id
                        val shape = RoundedCornerShape(12.dp)

                        Surface(
                            modifier = Modifier
                                .width(76.dp)
                                .height(54.dp)
                                .clip(shape)
                                .clickable {
                                    update(
                                        NovelReaderAppearanceMode.BACKGROUND,
                                        { o, v -> o.copy(appearanceMode = v) },
                                        { preferences.appearanceMode().set(it) },
                                    )
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
                            shape = shape,
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) aurora.accent else auroraRimColor(),
                            ),
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Image(
                                    painter = painterResource(id = preset.imageResId),
                                    contentDescription = preset.id,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(aurora.accent),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (aurora.isDark) aurora.background else Color.White,
                                            modifier = Modifier.size(11.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Экспресс-переключатели
        AuroraGlassSection(title = stringResource(AYMR.strings.novel_reader_section_express_switches)) {
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
            }
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
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorInt = when (cleanHex.length) {
            6 -> (0xFF000000 or cleanHex.toLong(16)).toInt()
            8 -> cleanHex.toLong(16).toInt()
            else -> android.graphics.Color.DKGRAY
        }
        Color(colorInt)
    } catch (_: Exception) {
        Color.DarkGray
    }
}
