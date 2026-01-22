package eu.kanade.presentation.reader.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.material.Slider
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

private val sliderColors = listOf(
    0 to "Тема",
    0xFFE91E63.toInt() to "Розовый",
    0xFFF44336.toInt() to "Красный",
    0xFFFF9800.toInt() to "Оранжевый",
    0xFFFFEB3B.toInt() to "Жёлтый",
    0xFF4CAF50.toInt() to "Зелёный",
    0xFF2196F3.toInt() to "Синий",
    0xFF9C27B0.toInt() to "Фиолетовый",
    0xFF00BCD4.toInt() to "Бирюзовый",
    0xFF795548.toInt() to "Коричневый",
    0xFF607D8B.toInt() to "Серый",
)

@Composable
fun NavigatorSettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
) {
    val showNavigator by screenModel.preferences.showNavigator().collectAsState()
    val showPageNumbers by screenModel.preferences.navigatorShowPageNumbers().collectAsState()
    val showChapterButtons by screenModel.preferences.navigatorShowChapterButtons().collectAsState()

    val sliderColorPref = screenModel.preferences.navigatorSliderColor()
    val sliderColor by sliderColorPref.collectAsState()

    val backgroundAlphaPref = screenModel.preferences.navigatorBackgroundAlpha()
    val backgroundAlpha by backgroundAlphaPref.collectAsState()

    val heightPref = screenModel.preferences.navigatorHeight()
    val height by heightPref.collectAsState()

    val cornerRadiusPref = screenModel.preferences.navigatorCornerRadius()
    val cornerRadius by cornerRadiusPref.collectAsState()

    val showTickMarks by screenModel.preferences.navigatorShowTickMarks().collectAsState()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(MR.strings.pref_navigator_settings))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Preview of the navigator
                if (showNavigator) {
                    Text(
                        text = "Предпросмотр:",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    NavigatorPreview(
                        showPageNumbers = showPageNumbers,
                        showChapterButtons = showChapterButtons,
                        sliderColor = sliderColor,
                        backgroundAlpha = backgroundAlpha,
                        navigatorHeight = height,
                        cornerRadius = cornerRadius,
                        showTickMarks = showTickMarks,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                CheckboxItem(
                    label = stringResource(MR.strings.pref_show_navigator),
                    pref = screenModel.preferences.showNavigator(),
                )

                if (showNavigator) {
                    Spacer(modifier = Modifier.height(8.dp))

                    CheckboxItem(
                        label = stringResource(MR.strings.pref_navigator_show_page_numbers),
                        pref = screenModel.preferences.navigatorShowPageNumbers(),
                    )

                    CheckboxItem(
                        label = stringResource(MR.strings.pref_navigator_show_chapter_buttons),
                        pref = screenModel.preferences.navigatorShowChapterButtons(),
                    )

                    CheckboxItem(
                        label = stringResource(MR.strings.pref_navigator_show_tick_marks),
                        pref = screenModel.preferences.navigatorShowTickMarks(),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Slider color selector
                    Text(
                        text = stringResource(MR.strings.pref_navigator_slider_color),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sliderColors.take(6).forEach { (colorValue, _) ->
                            ColorCircle(
                                color = if (colorValue == 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(colorValue)
                                },
                                isSelected = sliderColor == colorValue,
                                isThemeColor = colorValue == 0,
                                onClick = { sliderColorPref.set(colorValue) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sliderColors.drop(6).forEach { (colorValue, _) ->
                            ColorCircle(
                                color = if (colorValue == 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(colorValue)
                                },
                                isSelected = sliderColor == colorValue,
                                isThemeColor = colorValue == 0,
                                onClick = { sliderColorPref.set(colorValue) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SliderItem(
                        value = backgroundAlpha,
                        valueRange = 0..100,
                        label = stringResource(MR.strings.pref_navigator_background_alpha),
                        valueText = "$backgroundAlpha%",
                        onChange = { backgroundAlphaPref.set(it) },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsChipRow(MR.strings.pref_navigator_height) {
                        ReaderPreferences.NavigatorHeight.entries.map { heightOption ->
                            FilterChip(
                                selected = height == heightOption,
                                onClick = { heightPref.set(heightOption) },
                                label = { Text(stringResource(heightOption.titleRes)) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SliderItem(
                        value = cornerRadius,
                        valueRange = 0..32,
                        label = stringResource(MR.strings.pref_navigator_corner_radius),
                        valueText = "${cornerRadius}dp",
                        onChange = { cornerRadiusPref.set(it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}

@Composable
private fun NavigatorPreview(
    showPageNumbers: Boolean,
    showChapterButtons: Boolean,
    sliderColor: Int,
    backgroundAlpha: Int,
    navigatorHeight: ReaderPreferences.NavigatorHeight,
    cornerRadius: Int,
    showTickMarks: Boolean = false,
) {
    val calculatedAlpha = backgroundAlpha / 100f
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = calculatedAlpha)
    val buttonColor = IconButtonDefaults.filledIconButtonColors(
        containerColor = backgroundColor,
        disabledContainerColor = backgroundColor,
    )

    val activeSliderColor = if (sliderColor == 0) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(sliderColor)
    }
    val sliderColorScheme = SliderDefaults.colors(
        thumbColor = activeSliderColor,
        activeTrackColor = activeSliderColor,
        inactiveTrackColor = activeSliderColor.copy(alpha = 0.3f),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(navigatorHeight.heightDp.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showChapterButtons) {
            FilledIconButton(
                enabled = true,
                onClick = {},
                colors = buttonColor,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipPrevious,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(cornerRadius.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showPageNumbers) {
                Text(
                    text = "5",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Slider(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                value = 5,
                valueRange = 1..10,
                onValueChange = {},
                colors = sliderColorScheme,
                steps = if (showTickMarks) 8 else 0,
            )

            if (showPageNumbers) {
                Text(
                    text = "10",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (showChapterButtons) {
            FilledIconButton(
                enabled = true,
                onClick = {},
                colors = buttonColor,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    isThemeColor: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isThemeColor) {
            Text(
                text = "T",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
