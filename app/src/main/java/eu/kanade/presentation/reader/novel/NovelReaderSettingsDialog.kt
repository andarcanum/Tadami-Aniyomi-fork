package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AniviewSegmentedControlCompact
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun NovelReaderSettingsDialog(
    sourceId: Long,
    onDismissRequest: () -> Unit,
) {
    val preferences = remember { Injekt.get<NovelReaderPreferences>() }
    val fontSizePref = remember { preferences.fontSize() }
    val lineHeightPref = remember { preferences.lineHeight() }
    val marginPref = remember { preferences.margin() }
    val themePref = remember { preferences.theme() }
    val overridesPref = remember { preferences.sourceOverrides() }

    val fontSize by fontSizePref.changes().collectAsStateWithLifecycle(initialValue = fontSizePref.get())
    val lineHeight by lineHeightPref.changes().collectAsStateWithLifecycle(initialValue = lineHeightPref.get())
    val margin by marginPref.changes().collectAsStateWithLifecycle(initialValue = marginPref.get())
    val theme by themePref.changes().collectAsStateWithLifecycle(initialValue = themePref.get())
    val overrides by overridesPref.changes().collectAsStateWithLifecycle(initialValue = overridesPref.get())

    val sourceOverride = overrides[sourceId]
    val overrideEnabled = sourceOverride != null

    val effectiveFontSize = sourceOverride?.fontSize ?: fontSize
    val effectiveLineHeight = sourceOverride?.lineHeight ?: lineHeight
    val effectiveMargin = sourceOverride?.margin ?: margin
    val effectiveTheme = sourceOverride?.theme ?: theme

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
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
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            )

            SettingsSlider(
                label = stringResource(AYMR.strings.novel_reader_font_size),
                value = effectiveFontSize.toFloat(),
                valueText = "${effectiveFontSize}px",
                valueRange = 12f..28f,
                steps = 15,
                onValueChange = { newValue ->
                    val updated = newValue.toInt()
                    if (overrideEnabled) {
                        preferences.updateSourceOverride(sourceId) {
                            it.copy(fontSize = updated)
                        }
                    } else {
                        fontSizePref.set(updated)
                    }
                },
            )

            SettingsSlider(
                label = stringResource(AYMR.strings.novel_reader_line_height),
                value = effectiveLineHeight,
                valueText = String.format("%.1f", effectiveLineHeight),
                valueRange = 1.2f..2.0f,
                steps = 7,
                onValueChange = { newValue ->
                    val updated = (newValue * 10).toInt() / 10f
                    if (overrideEnabled) {
                        preferences.updateSourceOverride(sourceId) {
                            it.copy(lineHeight = updated)
                        }
                    } else {
                        lineHeightPref.set(updated)
                    }
                },
            )

            SettingsSlider(
                label = stringResource(AYMR.strings.novel_reader_margins),
                value = effectiveMargin.toFloat(),
                valueText = "${effectiveMargin}dp",
                valueRange = 8f..32f,
                steps = 23,
                onValueChange = { newValue ->
                    val updated = newValue.toInt()
                    if (overrideEnabled) {
                        preferences.updateSourceOverride(sourceId) {
                            it.copy(margin = updated)
                        }
                    } else {
                        marginPref.set(updated)
                    }
                },
            )

            ThemeSelector(
                theme = effectiveTheme,
                onSelect = { selected ->
                    if (overrideEnabled) {
                        preferences.updateSourceOverride(sourceId) {
                            it.copy(theme = selected)
                        }
                    } else {
                        themePref.set(selected)
                    }
                },
            )

            Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
        )
    }
}

@Composable
private fun ThemeSelector(
    theme: NovelReaderTheme,
    onSelect: (NovelReaderTheme) -> Unit,
) {
    val labels = listOf(
        stringResource(AYMR.strings.novel_reader_theme_system),
        stringResource(AYMR.strings.novel_reader_theme_light),
        stringResource(AYMR.strings.novel_reader_theme_dark),
    )
    val selectedIndex = when (theme) {
        NovelReaderTheme.SYSTEM -> 0
        NovelReaderTheme.LIGHT -> 1
        NovelReaderTheme.DARK -> 2
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = stringResource(AYMR.strings.novel_reader_theme),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
        )
        AniviewSegmentedControlCompact(
            items = labels,
            selectedIndex = selectedIndex,
            onItemSelected = { index ->
                val selected = when (index) {
                    1 -> NovelReaderTheme.LIGHT
                    2 -> NovelReaderTheme.DARK
                    else -> NovelReaderTheme.SYSTEM
                }
                onSelect(selected)
            },
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
        )
    }
}
