package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.domain.ui.model.HomeHeaderLayoutElement
import eu.kanade.domain.ui.model.HomeHeaderLayoutSpec
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_SHAPE
import eu.kanade.presentation.more.settings.LocalSettingsUiStyle
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.settingsAccentColor
import eu.kanade.presentation.more.settings.settingsCardBorderColor
import eu.kanade.presentation.more.settings.settingsCardContainerColor
import eu.kanade.presentation.more.settings.settingsDialogContainerColor
import eu.kanade.presentation.more.settings.settingsSubtitleColor
import eu.kanade.presentation.more.settings.settingsTitleColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

class HomeHeaderLayoutEditorScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val prefs = remember { Injekt.get<UserProfilePreferences>() }
        val uiStyle = rememberResolvedSettingsUiStyle()
        val isAurora = uiStyle == SettingsUiStyle.Aurora
        val accent = settingsAccentColor()
        val auroraColors = AuroraTheme.colors
        val titleColor = settingsTitleColor()
        val subtitleColor = settingsSubtitleColor()

        CompositionLocalProvider(LocalSettingsUiStyle provides uiStyle) {
            val initialLayout = remember { prefs.getHomeHeaderLayoutOrDefault() }
            var workingLayout by remember { mutableStateOf(initialLayout) }
            var selectedElement by remember { mutableStateOf(HomeHeaderLayoutElement.Nickname) }
            var showGrid by rememberSaveable { mutableStateOf(true) }
            var showOnlySelectedOverlay by rememberSaveable { mutableStateOf(false) }
            var showHomeGreeting by rememberSaveable { mutableStateOf(prefs.showHomeGreeting().get()) }
            var showHomeStreak by rememberSaveable { mutableStateOf(prefs.showHomeStreak().get()) }
            var greetingAlignRight by rememberSaveable { mutableStateOf(prefs.homeHeaderGreetingAlignRight().get()) }
            var nicknameAlignRight by rememberSaveable { mutableStateOf(prefs.homeHeaderNicknameAlignRight().get()) }
            var showResetConfirm by rememberSaveable { mutableStateOf(false) }
            val visibleElements = remember(showHomeGreeting, showHomeStreak) {
                homeHeaderLayoutEditorVisibleElements(
                    showGreeting = showHomeGreeting,
                    showStreak = showHomeStreak,
                )
            }

            LaunchedEffect(visibleElements, selectedElement) {
                if (selectedElement !in visibleElements && visibleElements.isNotEmpty()) {
                    selectedElement = visibleElements.first()
                }
            }

            if (showResetConfirm) {
                AlertDialog(
                    onDismissRequest = { showResetConfirm = false },
                    containerColor = if (isAurora) {
                        settingsDialogContainerColor()
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    title = {
                        Text(
                            text = stringResource(AYMR.strings.home_header_layout_editor_reset_confirm_title),
                            color = if (isAurora) {
                                auroraColors.textPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(AYMR.strings.home_header_layout_editor_reset_confirm_message),
                            color = if (isAurora) {
                                auroraColors.textSecondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                workingLayout = HomeHeaderLayoutSpec.default()
                                showResetConfirm = false
                            },
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.home_header_layout_editor_reset_confirm_action),
                                color = if (isAurora) accent else MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirm = false }) {
                            Text(
                                text = stringResource(AYMR.strings.home_header_layout_editor_cancel),
                                color = if (isAurora) accent else MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }

            SettingsScaffold(
                title = stringResource(AYMR.strings.home_header_layout_editor_title),
                uiStyle = uiStyle,
                onBackPressed = navigator::pop,
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(AYMR.strings.home_header_layout_editor_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtitleColor,
                    )
                    Spacer(Modifier.height(8.dp))
                    HomeHeaderLayoutEditorSwitchRow(
                        title = stringResource(AYMR.strings.home_header_layout_editor_show_grid),
                        checked = showGrid,
                        isAurora = isAurora,
                        onCheckedChange = { showGrid = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    HomeHeaderLayoutEditorSwitchRow(
                        title = stringResource(AYMR.strings.pref_show_home_greeting),
                        checked = showHomeGreeting,
                        isAurora = isAurora,
                        onCheckedChange = { showHomeGreeting = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    HomeHeaderLayoutEditorSwitchRow(
                        title = stringResource(AYMR.strings.pref_show_home_streak),
                        checked = showHomeStreak,
                        isAurora = isAurora,
                        onCheckedChange = { showHomeStreak = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    HomeHeaderLayoutEditorSwitchRow(
                        title = stringResource(AYMR.strings.home_header_layout_editor_greeting_align_right),
                        checked = greetingAlignRight,
                        isAurora = isAurora,
                        onCheckedChange = { greetingAlignRight = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    HomeHeaderLayoutEditorSwitchRow(
                        title = stringResource(AYMR.strings.home_header_layout_editor_nickname_align_right),
                        checked = nicknameAlignRight,
                        isAurora = isAurora,
                        onCheckedChange = { nicknameAlignRight = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    HomeHeaderLayoutEditorSwitchRow(
                        title = stringResource(AYMR.strings.home_header_layout_editor_only_selected_overlay),
                        checked = showOnlySelectedOverlay,
                        isAurora = isAurora,
                        onCheckedChange = { showOnlySelectedOverlay = it },
                    )
                    Spacer(Modifier.height(12.dp))

                    HomeHeaderLayoutEditorCanvas(
                        layout = workingLayout,
                        selectedElement = selectedElement,
                        showGrid = showGrid,
                        showOnlySelectedOverlay = showOnlySelectedOverlay,
                        showGreeting = showHomeGreeting,
                        showStreak = showHomeStreak,
                        greetingAlignRight = greetingAlignRight,
                        nicknameAlignRight = nicknameAlignRight,
                        onSelectedElementChange = { selectedElement = it },
                        onLayoutChange = { workingLayout = it },
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            AYMR.strings.home_header_layout_editor_selected,
                            homeHeaderLayoutElementLabel(selectedElement),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = titleColor,
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.home_header_layout_editor_reset),
                                color = if (isAurora) accent else MaterialTheme.colorScheme.primary,
                            )
                        }
                        TextButton(
                            onClick = navigator::pop,
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.home_header_layout_editor_cancel),
                                color = if (isAurora) accent else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Button(
                            contentPadding = PaddingValues(
                                horizontal = 10.dp,
                                vertical = ButtonDefaults.ContentPadding.calculateTopPadding(),
                            ),
                            onClick = {
                                prefs.setHomeHeaderLayout(workingLayout)
                                prefs.showHomeGreeting().set(showHomeGreeting)
                                prefs.showHomeStreak().set(showHomeStreak)
                                prefs.homeHeaderGreetingAlignRight().set(greetingAlignRight)
                                prefs.homeHeaderNicknameAlignRight().set(nicknameAlignRight)
                                navigator.pop()
                            },
                            colors = if (isAurora) {
                                ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = auroraColors.textPrimary,
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.home_header_layout_editor_save),
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeaderLayoutEditorSwitchRow(
    title: String,
    checked: Boolean,
    isAurora: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val accent = settingsAccentColor()
    val titleColor = settingsTitleColor()
    val containerColor = settingsCardContainerColor()
    val borderColor = settingsCardBorderColor()
    val rowShape = if (isAurora) AURORA_SETTINGS_CARD_SHAPE else RoundedCornerShape(0.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isAurora) {
                    Modifier
                        .clip(rowShape)
                        .background(containerColor)
                        .then(
                            if (borderColor.alpha > 0f) {
                                Modifier.border(1.dp, borderColor, rowShape)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = titleColor,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = if (isAurora) {
                SwitchDefaults.colors(
                    checkedThumbColor = accent,
                    checkedTrackColor = accent.copy(alpha = 0.5f),
                )
            } else {
                SwitchDefaults.colors()
            },
        )
    }
}

@Composable
private fun HomeHeaderLayoutEditorCanvas(
    layout: HomeHeaderLayoutSpec,
    selectedElement: HomeHeaderLayoutElement,
    showGrid: Boolean,
    showOnlySelectedOverlay: Boolean,
    showGreeting: Boolean,
    showStreak: Boolean,
    greetingAlignRight: Boolean,
    nicknameAlignRight: Boolean,
    onSelectedElementChange: (HomeHeaderLayoutElement) -> Unit,
    onLayoutChange: (HomeHeaderLayoutSpec) -> Unit,
) {
    val density = LocalDensity.current
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora
    val elementSizes = remember { defaultHomeHeaderElementPixelSizes() }
    val latestLayout by rememberUpdatedState(layout)
    val guideColumns = 12
    val guideRows = 6
    val visibleElements = remember(showGreeting, showStreak) {
        homeHeaderLayoutEditorVisibleElements(showGreeting = showGreeting, showStreak = showStreak)
    }
    val previewGreetingBaseTextStyle = MaterialTheme.typography.labelMedium
    val previewGreetingText = stringResource(AYMR.strings.home_header_layout_editor_preview_greeting)
    val decoratedPreviewGreetingText = remember(previewGreetingText) {
        decorateGreetingText(previewGreetingText, GreetingDecorationPreset.Auto)
    }
    val previewGreetingTextStyle = remember(previewGreetingBaseTextStyle) {
        previewGreetingBaseTextStyle.copy(
            fontSize = 12.sp,
            lineHeight = 15.sp,
            lineBreak = LineBreak.Heading,
        )
    }
    val textMeasurer = rememberTextMeasurer()
    val containerShape = if (isAurora) AURORA_SETTINGS_CARD_SHAPE else RoundedCornerShape(16.dp)
    val containerColor = if (isAurora) {
        settingsCardContainerColor()
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val borderColor = if (isAurora) {
        settingsCardBorderColor()
    } else {
        MaterialTheme.colorScheme.outline
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(containerColor)
            .then(
                if (borderColor.alpha > 0f) {
                    Modifier.border(1.dp, borderColor, containerShape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        val canvasWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val designWidthPx = layout.canvas.width.coerceAtLeast(1f)
        val previewMeasureScaleX = canvasWidthPx / designWidthPx
        val greetingMeasureWidthPx =
            (
                elementSizes.getValue(
                    HomeHeaderLayoutElement.Greeting,
                ).width * previewMeasureScaleX
                ).toInt().coerceAtLeast(1)
        val preMeasuredGreetingLayout = remember(
            decoratedPreviewGreetingText,
            previewGreetingTextStyle,
            greetingMeasureWidthPx,
        ) {
            textMeasurer.measure(
                text = AnnotatedString(decoratedPreviewGreetingText),
                style = previewGreetingTextStyle,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = greetingMeasureWidthPx),
            )
        }
        val measuredGreetingLineCount = if (showGreeting) {
            if (preMeasuredGreetingLayout.hasVisualOverflow) {
                4
            } else {
                preMeasuredGreetingLayout.lineCount.coerceAtLeast(1)
            }
        } else {
            1
        }
        val greetingLineLimit = resolveGreetingLineLimit(measuredGreetingLineCount)
        val previewCanvasHeightDp = resolveHomeHeaderCanvasHeightDp(greetingLineLimit).dp
        val previewCanvasHeightPx = with(density) { previewCanvasHeightDp.toPx() }.coerceAtLeast(1f)
        val designHeightPx = resolveHomeHeaderCanvasHeightDp(greetingLineLimit).toFloat().coerceAtLeast(1f)
        val scaleX = canvasWidthPx / designWidthPx
        val scaleY = previewCanvasHeightPx / designHeightPx
        val effectiveElementSizes = remember(greetingLineLimit) {
            elementSizes.mapValues { (element, size) ->
                if (element == HomeHeaderLayoutElement.Greeting) {
                    size.copy(height = resolveGreetingSlotHeightPx(greetingLineLimit))
                } else {
                    size
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewCanvasHeightDp),
        ) {
            HomeHeaderLayoutLivePreview(
                modifier = Modifier.matchParentSize(),
                layoutSpec = layout,
                showGreeting = showGreeting,
                showStreak = showStreak,
                greetingAlignRight = greetingAlignRight,
                nicknameAlignRight = nicknameAlignRight,
            )

            if (showGrid) {
                val gridColor = if (isAurora) {
                    AuroraTheme.colors.textSecondary.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                }
                Canvas(Modifier.matchParentSize()) {
                    for (col in 1 until guideColumns) {
                        val x = col * size.width / guideColumns
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    }
                    for (row in 1 until guideRows) {
                        val y = row * size.height / guideRows
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                }
            }

            visibleElements.forEach { element ->
                val elementSize = effectiveElementSizes.getValue(element)
                val point = resolveHomeHeaderEffectivePoint(
                    element = element,
                    layoutSpec = layout,
                    elementSizes = effectiveElementSizes,
                    canvasWidth = designWidthPx,
                    canvasHeight = designHeightPx,
                    showGreeting = showGreeting,
                )
                val xPx = point.x * scaleX
                val yPx = point.y * scaleY
                val widthDp = with(density) { (elementSize.width * scaleX).toDp() }
                val heightDp = with(density) { (elementSize.height * scaleY).toDp() }

                val latestOnLayoutChange by rememberUpdatedState(onLayoutChange)
                val latestOnSelectedChange by rememberUpdatedState(onSelectedElementChange)
                val latestSelected by rememberUpdatedState(selectedElement)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                        .width(widthDp)
                        .height(heightDp)
                        .pointerInput(element, scaleX, scaleY, designWidthPx, designHeightPx) {
                            var dragStart = point
                            var dragAccum = Offset.Zero
                            detectDragGestures(
                                onDragStart = {
                                    dragStart = HomeHeaderPixelPoint(
                                        x = latestLayout.positionOf(element).x,
                                        y = latestLayout.positionOf(element).y,
                                    )
                                    dragAccum = Offset.Zero
                                    latestOnSelectedChange(element)
                                },
                                onDragEnd = {
                                    dragAccum = Offset.Zero
                                },
                                onDragCancel = {
                                    dragAccum = Offset.Zero
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                dragAccum += dragAmount
                                val deltaDesign = HomeHeaderPixelPoint(
                                    x = if (scaleX == 0f) 0f else dragAccum.x / scaleX,
                                    y = if (scaleY == 0f) 0f else dragAccum.y / scaleY,
                                )
                                val candidate = clampHomeHeaderPixelPoint(
                                    point = HomeHeaderPixelPoint(
                                        x = dragStart.x + deltaDesign.x,
                                        y = dragStart.y + deltaDesign.y,
                                    ),
                                    elementSize = elementSize,
                                    canvasWidth = designWidthPx,
                                    canvasHeight = designHeightPx,
                                )
                                latestOnLayoutChange(
                                    latestLayout.withPosition(
                                        element = element,
                                        x = candidate.x,
                                        y = candidate.y,
                                    ),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val isSelected = latestSelected == element
                    if (!showOnlySelectedOverlay || isSelected) {
                        EditorLayoutElementOverlay(
                            element = element,
                            selected = isSelected,
                        )
                    }
                }
            }
        }
    }
}

internal fun homeHeaderLayoutEditorVisibleElements(
    showGreeting: Boolean,
    showStreak: Boolean,
): List<HomeHeaderLayoutElement> {
    return HomeHeaderLayoutElement.entries.filter { element ->
        when (element) {
            HomeHeaderLayoutElement.Greeting -> showGreeting
            HomeHeaderLayoutElement.Streak -> showStreak
            HomeHeaderLayoutElement.Nickname,
            HomeHeaderLayoutElement.Avatar,
            -> true
        }
    }
}

@Composable
private fun EditorLayoutElementOverlay(
    element: HomeHeaderLayoutElement,
    selected: Boolean,
) {
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora
    val accent = settingsAccentColor()
    val border = if (selected) {
        if (isAurora) accent.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary
    } else if (isAurora) {
        AuroraTheme.colors.textSecondary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    val chipBg = if (isAurora) {
        AuroraTheme.colors.glass.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    val chipText = if (isAurora) AuroraTheme.colors.textPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp)),
    ) {
        if (selected) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(chipBg, RoundedCornerShape(999.dp))
                    .border(1.dp, border.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = homeHeaderLayoutElementLabel(element),
                    color = chipText,
                    style = MaterialTheme.typography.labelSmall,
                )
                Icon(
                    imageVector = Icons.Filled.DragIndicator,
                    contentDescription = null,
                    tint = chipText,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun homeHeaderLayoutElementLabel(element: HomeHeaderLayoutElement): String {
    return when (element) {
        HomeHeaderLayoutElement.Greeting -> stringResource(AYMR.strings.home_header_layout_element_greeting)
        HomeHeaderLayoutElement.Nickname -> stringResource(AYMR.strings.home_header_layout_element_nickname)
        HomeHeaderLayoutElement.Avatar -> stringResource(AYMR.strings.home_header_layout_element_avatar)
        HomeHeaderLayoutElement.Streak -> stringResource(AYMR.strings.home_header_layout_element_streak)
    }
}
