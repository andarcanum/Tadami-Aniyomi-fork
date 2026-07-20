package eu.kanade.presentation.reader.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.entries.components.aurora.AuroraGlassCtaSurface
import eu.kanade.presentation.entries.components.aurora.AuroraHeroCtaMode
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Slider
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState

private val sliderColors = listOf(
    0 to AYMR.strings.reader_navigator_slider_color_theme,
    0xFFE91E63.toInt() to AYMR.strings.reader_navigator_slider_color_pink,
    0xFFF44336.toInt() to AYMR.strings.reader_navigator_slider_color_red,
    0xFFFF9800.toInt() to AYMR.strings.reader_navigator_slider_color_orange,
    0xFFFFEB3B.toInt() to AYMR.strings.reader_navigator_slider_color_yellow,
    0xFF4CAF50.toInt() to AYMR.strings.reader_navigator_slider_color_green,
    0xFF2196F3.toInt() to AYMR.strings.reader_navigator_slider_color_blue,
    0xFF9C27B0.toInt() to AYMR.strings.reader_navigator_slider_color_purple,
    0xFF00BCD4.toInt() to AYMR.strings.reader_navigator_slider_color_turquoise,
    0xFF795548.toInt() to AYMR.strings.reader_navigator_slider_color_brown,
    0xFF607D8B.toInt() to AYMR.strings.reader_navigator_slider_color_gray,
)

/**
 * Studio glass panel for the manga reader page slider (полоса прокрутки).
 *
 * Matches nickname / greeting studio: frosted card over dim scrim, neutral
 * [NavigatorStudioPreviewStage], live controls, sticky cancel / done actions.
 * Prefs apply immediately so the preview stays in sync.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigatorSettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
) {
    val appHaptics = LocalAppHaptics.current
    val colors = AuroraTheme.colors
    val useAdaptivePaletteLayout = shouldUseAdaptiveNavigatorPaletteLayout(
        LocalConfiguration.current.screenWidthDp,
    )

    val showNavigator by screenModel.preferences.showNavigator().collectAsState()
    val showPageNumbers by screenModel.preferences.navigatorShowPageNumbers().collectAsState()
    val showChapterButtons by screenModel.preferences.navigatorShowChapterButtons().collectAsState()
    val showTickMarks by screenModel.preferences.navigatorShowTickMarks().collectAsState()

    val sliderColorPref = screenModel.preferences.navigatorSliderColor()
    val sliderColor by sliderColorPref.collectAsState()

    val backgroundAlphaPref = screenModel.preferences.navigatorBackgroundAlpha()
    val backgroundAlpha by backgroundAlphaPref.collectAsState()

    val heightPref = screenModel.preferences.navigatorHeight()
    val height by heightPref.collectAsState()

    val cornerRadiusPref = screenModel.preferences.navigatorCornerRadius()
    val cornerRadius by cornerRadiusPref.collectAsState()

    val bottomBarPositionPref = screenModel.preferences.bottomBarPosition()
    val bottomBarPosition by bottomBarPositionPref.collectAsState()

    val cardShape = RoundedCornerShape(28.dp)
    val previewShape = RoundedCornerShape(18.dp)
    val cardFrostBase = when {
        colors.isEInk -> colors.surface
        colors.isDark ->
            Color.White.copy(alpha = 0.10f).compositeOver(colors.background.copy(alpha = 0.90f))
        else -> Color.White.copy(alpha = 0.92f)
    }

    fun dismiss() {
        appHaptics.tap()
        onDismissRequest()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(onBack = onDismissRequest)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(
                            alpha = when {
                                colors.isEInk -> 0.55f
                                colors.isDark -> 0.45f
                                else -> 0.30f
                            },
                        ),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = cardShape,
                        ambientColor = Color.Black.copy(alpha = 0.40f),
                        spotColor = Color.Black.copy(alpha = 0.28f),
                    )
                    .clip(cardShape)
                    .background(cardFrostBase)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(MR.strings.pref_navigator_settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(AYMR.strings.reader_navigator_preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )

                    NavigatorStudioPreviewStage(
                        shape = previewShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp),
                    ) {
                        if (showNavigator) {
                            NavigatorPreview(
                                showPageNumbers = showPageNumbers,
                                showChapterButtons = showChapterButtons,
                                sliderColor = sliderColor,
                                backgroundAlpha = backgroundAlpha,
                                navigatorHeight = height,
                                cornerRadius = cornerRadius,
                                showTickMarks = showTickMarks,
                            )
                        } else {
                            Text(
                                text = stringResource(MR.strings.pref_show_navigator),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    AuroraToggleRow(
                        label = stringResource(MR.strings.pref_show_navigator),
                        pref = screenModel.preferences.showNavigator(),
                    )

                    if (showNavigator) {
                        AuroraToggleRow(
                            label = stringResource(MR.strings.pref_navigator_show_page_numbers),
                            pref = screenModel.preferences.navigatorShowPageNumbers(),
                        )
                        AuroraToggleRow(
                            label = stringResource(MR.strings.pref_navigator_show_chapter_buttons),
                            pref = screenModel.preferences.navigatorShowChapterButtons(),
                        )
                        AuroraToggleRow(
                            label = stringResource(MR.strings.pref_navigator_show_tick_marks),
                            pref = screenModel.preferences.navigatorShowTickMarks(),
                        )

                        Spacer(Modifier.height(8.dp))

                        AuroraFieldLabel(stringResource(MR.strings.pref_navigator_slider_color))
                        if (useAdaptivePaletteLayout) {
                            AdaptiveNavigatorColorPalette(
                                sliderColor = sliderColor,
                                onColorSelected = {
                                    appHaptics.tap()
                                    sliderColorPref.set(it)
                                },
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    sliderColors.take(6).forEach { (colorValue, _) ->
                                        ColorCircle(
                                            color = if (colorValue == 0) {
                                                colors.accent
                                            } else {
                                                Color(colorValue)
                                            },
                                            isSelected = sliderColor == colorValue,
                                            isThemeColor = colorValue == 0,
                                            onClick = {
                                                appHaptics.tap()
                                                sliderColorPref.set(colorValue)
                                            },
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    sliderColors.drop(6).forEach { (colorValue, _) ->
                                        ColorCircle(
                                            color = if (colorValue == 0) {
                                                colors.accent
                                            } else {
                                                Color(colorValue)
                                            },
                                            isSelected = sliderColor == colorValue,
                                            isThemeColor = colorValue == 0,
                                            onClick = {
                                                appHaptics.tap()
                                                sliderColorPref.set(colorValue)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        AuroraSliderRow(
                            label = stringResource(MR.strings.pref_navigator_background_alpha),
                            value = backgroundAlpha,
                            valueRange = 0..100,
                            valueText = "$backgroundAlpha%",
                            onChange = { backgroundAlphaPref.set(it) },
                        )

                        AuroraFieldLabel(stringResource(MR.strings.pref_navigator_height))
                        AuroraChipFlow {
                            ReaderPreferences.NavigatorHeight.entries.forEach { heightOption ->
                                AuroraChip(
                                    selected = height == heightOption,
                                    onClick = {
                                        appHaptics.tap()
                                        heightPref.set(heightOption)
                                    },
                                    label = stringResource(heightOption.titleRes),
                                )
                            }
                        }

                        AuroraFieldLabel(stringResource(MR.strings.pref_bottom_bar_position))
                        AuroraChipFlow {
                            ReaderPreferences.BottomBarPosition.entries.forEach { positionOption ->
                                AuroraChip(
                                    selected = bottomBarPosition == positionOption,
                                    onClick = {
                                        appHaptics.tap()
                                        bottomBarPositionPref.set(positionOption)
                                    },
                                    label = stringResource(positionOption.titleRes),
                                )
                            }
                        }

                        AuroraSliderRow(
                            label = stringResource(MR.strings.pref_navigator_corner_radius),
                            value = cornerRadius,
                            valueRange = 0..32,
                            valueText = "${cornerRadius}dp",
                            onChange = { cornerRadiusPref.set(it) },
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (colors.isDark) {
                                    Color.White.copy(alpha = 0.08f)
                                } else {
                                    Color.Black.copy(alpha = 0.05f)
                                },
                            )
                            .clickable(onClick = ::dismiss)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(MR.strings.action_cancel),
                            color = colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    val applyInteraction = remember { MutableInteractionSource() }
                    AuroraGlassCtaSurface(
                        mode = AuroraHeroCtaMode.Aurora,
                        onClick = ::dismiss,
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        interactionSource = applyInteraction,
                        modifier = Modifier.fillMaxWidth(),
                    ) { contentColor ->
                        Text(
                            text = stringResource(MR.strings.action_ok),
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Neutral studio preview stage — same language as nickname / greeting
 * [eu.kanade.tachiyomi.ui.home.StudioPreviewStage]: soft glass fill + thin rim.
 */
@Composable
private fun NavigatorStudioPreviewStage(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = AuroraTheme.colors
    val fill = when {
        colors.isEInk -> colors.surface
        colors.isDark -> Color.White.copy(alpha = 0.06f)
        else -> Color.Black.copy(alpha = 0.04f)
    }
    val rim = if (colors.isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, rim, shape)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

internal fun shouldUseAdaptiveNavigatorPaletteLayout(screenWidthDp: Int): Boolean = screenWidthDp >= 600

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdaptiveNavigatorColorPalette(
    sliderColor: Int,
    onColorSelected: (Int) -> Unit,
) {
    val colors = AuroraTheme.colors
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 6,
    ) {
        sliderColors.forEach { (colorValue, _) ->
            ColorCircle(
                color = if (colorValue == 0) colors.accent else Color(colorValue),
                isSelected = sliderColor == colorValue,
                isThemeColor = colorValue == 0,
                onClick = { onColorSelected(colorValue) },
            )
        }
    }
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
    val colors = AuroraTheme.colors
    val calculatedAlpha = backgroundAlpha / 100f
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = calculatedAlpha)
    val buttonColor = IconButtonDefaults.filledIconButtonColors(
        containerColor = backgroundColor,
        disabledContainerColor = backgroundColor,
    )

    val activeSliderColor = if (sliderColor == 0) {
        colors.accent
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
            .height(navigatorHeight.heightDp.dp),
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
                    tint = colors.textPrimary,
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
                    color = colors.textPrimary,
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
                    color = colors.textPrimary,
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
                    tint = colors.textPrimary,
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
    val colors = AuroraTheme.colors
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, colors.textPrimary, CircleShape)
                } else {
                    Modifier.border(1.dp, colors.textSecondary.copy(alpha = 0.35f), CircleShape)
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isThemeColor) {
            Text(
                text = "T",
                style = MaterialTheme.typography.labelMedium,
                color = colors.background,
            )
        }
    }
}
