package eu.kanade.presentation.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Slider
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ChapterNavigator(
    isRtl: Boolean,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,
    // Navigator customization options
    showPageNumbers: Boolean = true,
    showChapterButtons: Boolean = true,
    sliderColor: Int = 0,
    backgroundAlpha: Int = 90,
    navigatorHeight: ReaderPreferences.NavigatorHeight = ReaderPreferences.NavigatorHeight.NORMAL,
    cornerRadius: Int = 24,
    showTickMarks: Boolean = false,
) {
    val isTabletUi = isTabletUi()
    val horizontalPadding = if (isTabletUi) 24.dp else 8.dp
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val haptic = LocalHapticFeedback.current

    // Calculate background alpha based on preference
    val calculatedAlpha = backgroundAlpha / 100f
    val isDarkTheme = isSystemInDarkTheme()

    // Match with toolbar background color set in ReaderActivity
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = calculatedAlpha)
    val buttonColor = IconButtonDefaults.filledIconButtonColors(
        containerColor = backgroundColor,
        disabledContainerColor = backgroundColor,
    )

    // Slider color - use theme primary if sliderColor is 0
    val activeSliderColor = if (sliderColor == 0) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(sliderColor)
    }
    val sliderColors = SliderDefaults.colors(
        thumbColor = activeSliderColor,
        activeTrackColor = activeSliderColor,
        inactiveTrackColor = activeSliderColor.copy(alpha = 0.3f),
    )

    // We explicitly handle direction based on the reader viewer rather than the system direction
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(navigatorHeight.heightDp.dp)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showChapterButtons) {
                FilledIconButton(
                    enabled = if (isRtl) enabledNext else enabledPrevious,
                    onClick = if (isRtl) onNextChapter else onPreviousChapter,
                    colors = buttonColor,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipPrevious,
                        contentDescription = stringResource(
                            if (isRtl) MR.strings.action_next_chapter else MR.strings.action_previous_chapter,
                        ),
                    )
                }
            }

            if (totalPages > 1) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(cornerRadius.dp))
                            .background(backgroundColor)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showPageNumbers) {
                            Box(contentAlignment = Alignment.CenterEnd) {
                                Text(text = currentPage.toString())
                                // Taking up full length so the slider doesn't shift when 'currentPage' length changes
                                Text(text = totalPages.toString(), color = Color.Transparent)
                            }
                        }

                        val interactionSource = remember { MutableInteractionSource() }
                        val sliderDragged by interactionSource.collectIsDraggedAsState()
                        LaunchedEffect(currentPage) {
                            if (sliderDragged) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }

                        Slider(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            value = currentPage,
                            valueRange = 1..totalPages,
                            onValueChange = f@{
                                if (it == currentPage) return@f
                                onPageIndexChange(it - 1)
                            },
                            interactionSource = interactionSource,
                            colors = sliderColors,
                            steps = if (showTickMarks) totalPages - 2 else 0,
                        )

                        if (showPageNumbers) {
                            Text(text = totalPages.toString())
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (showChapterButtons) {
                FilledIconButton(
                    enabled = if (isRtl) enabledPrevious else enabledNext,
                    onClick = if (isRtl) onPreviousChapter else onNextChapter,
                    colors = buttonColor,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = stringResource(
                            if (isRtl) MR.strings.action_previous_chapter else MR.strings.action_next_chapter,
                        ),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ChapterNavigatorPreview() {
    var currentPage by remember { mutableIntStateOf(1) }
    TachiyomiPreviewTheme {
        ChapterNavigator(
            isRtl = false,
            onNextChapter = {},
            enabledNext = true,
            onPreviousChapter = {},
            enabledPrevious = true,
            currentPage = currentPage,
            totalPages = 10,
            onPageIndexChange = { currentPage = (it + 1) },
        )
    }
}
