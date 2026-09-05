package eu.kanade.presentation.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import tachiyomi.presentation.core.util.LocalAppHaptics
import kotlin.math.roundToInt

/**
 * Maps a touch position inside a vertical slider to a value in [valueRange]. The bar runs
 * top-to-bottom: the top edge is [valueRange.start] (first page) and the bottom edge is
 * [valueRange.endInclusive] (last page).
 */
internal fun verticalSliderValueFromPosition(
    positionY: Float,
    height: Float,
    valueRange: ClosedRange<Float>,
): Float {
    if (height <= 0f) return valueRange.start
    val percentage = (positionY / height).coerceIn(0f, 1f)
    return valueRange.start + percentage * (valueRange.endInclusive - valueRange.start)
}

/**
 * Offset of the slider thumb center from the top for [value], so that the start value sits at
 * the top of the bar and the end value at the bottom.
 */
internal fun verticalSliderThumbOffset(
    value: Int,
    height: Float,
    valueRange: ClosedRange<Float>,
): Float {
    val percentage = if (valueRange.endInclusive > valueRange.start) {
        ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    } else {
        0f
    }
    return height * percentage
}

@Composable
fun VerticalSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedRange<Float>,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = activeColor.copy(alpha = 0.3f),
) {
    val haptic = LocalHapticFeedback.current
    // РЕШ-11 revival: `haptic` was declared and never used. Mirror the horizontal slider's tick
    // feedback (TextHandleMove on value change); the initial composition is skipped because
    // lastHapticValue starts equal to value.
    var lastHapticValue by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) {
        if (value != lastHapticValue) {
            lastHapticValue = value
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val newValue = verticalSliderValueFromPosition(offset.y, size.height.toFloat(), valueRange)
                    onValueChange(newValue.roundToInt())
                }
            }
            .pointerInput(valueRange) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    val newValue = verticalSliderValueFromPosition(change.position.y, size.height.toFloat(), valueRange)
                    onValueChange(newValue.roundToInt())
                }
            },
    ) {
        val height = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val valuePercentage = if (valueRange.endInclusive > valueRange.start) {
            ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        } else {
            0f
        }

        val trackWidth = 4.dp
        val thumbSize = 20.dp

        // Track
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(trackWidth)
                .fillMaxHeight()
                .background(inactiveColor, shape = RoundedCornerShape(2.dp)),
        )

        // Active Track (grows from the top, matching the top-to-bottom value mapping)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(trackWidth)
                .fillMaxHeight(valuePercentage)
                .background(activeColor, shape = RoundedCornerShape(2.dp)),
        )

        // Thumb
        val thumbOffsetDp = with(density) {
            val thumbRadiusPx = thumbSize.toPx() / 2
            val rawOffset = verticalSliderThumbOffset(value, height, valueRange)
            (rawOffset - thumbRadiusPx).toDp()
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = thumbOffsetDp)
                .size(thumbSize)
                .background(activeColor, shape = CircleShape),
        )
    }
}

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
    isVertical: Boolean = false,
) {
    val isTabletUi = isTabletUi()
    val horizontalPadding = if (isTabletUi) 24.dp else 8.dp
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val haptic = LocalHapticFeedback.current
    val appHaptics = LocalAppHaptics.current

    // Calculate background alpha based on preference
    val calculatedAlpha = backgroundAlpha / 100f

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
        if (isVertical) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.75f)
                    .width(navigatorHeight.heightDp.dp)
                    .padding(vertical = horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showChapterButtons) {
                    FilledIconButton(
                        enabled = if (isRtl) enabledNext else enabledPrevious,
                        onClick = {
                            appHaptics.tap()
                            if (isRtl) {
                                onNextChapter()
                            } else {
                                onPreviousChapter()
                            }
                        },
                        colors = buttonColor,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SkipPrevious,
                            contentDescription = stringResource(
                                if (isRtl) MR.strings.action_next_chapter else MR.strings.action_previous_chapter,
                            ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (totalPages > 1) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(cornerRadius.dp))
                            .background(backgroundColor)
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (showPageNumbers) {
                            Text(
                                text = currentPage.toString(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        VerticalSlider(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                            value = currentPage,
                            valueRange = 1f..totalPages.toFloat(),
                            onValueChange = f@{
                                if (it == currentPage) return@f
                                onPageIndexChange(it - 1)
                            },
                            activeColor = activeSliderColor,
                            inactiveColor = activeSliderColor.copy(alpha = 0.3f),
                        )

                        if (showPageNumbers) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = totalPages.toString(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (showChapterButtons) {
                    Spacer(Modifier.height(8.dp))
                    FilledIconButton(
                        enabled = if (isRtl) enabledPrevious else enabledNext,
                        onClick = {
                            appHaptics.tap()
                            if (isRtl) {
                                onPreviousChapter()
                            } else {
                                onNextChapter()
                            }
                        },
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
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .then(
                        if (isTabletUi) {
                            Modifier.widthIn(max = 960.dp)
                        } else {
                            Modifier
                        },
                    )
                    .height(navigatorHeight.heightDp.dp)
                    .padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showChapterButtons) {
                    FilledIconButton(
                        enabled = if (isRtl) enabledNext else enabledPrevious,
                        onClick = {
                            appHaptics.tap()
                            if (isRtl) {
                                onNextChapter()
                            } else {
                                onPreviousChapter()
                            }
                        },
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
                        onClick = {
                            appHaptics.tap()
                            if (isRtl) {
                                onPreviousChapter()
                            } else {
                                onNextChapter()
                            }
                        },
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
