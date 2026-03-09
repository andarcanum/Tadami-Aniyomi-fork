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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

class HomeHeaderLayoutEditorScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val prefs = remember { Injekt.get<UserProfilePreferences>() }
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
                title = { Text(stringResource(AYMR.strings.home_header_layout_editor_reset_confirm_title)) },
                text = { Text(stringResource(AYMR.strings.home_header_layout_editor_reset_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            workingLayout = HomeHeaderLayoutSpec.default()
                            showResetConfirm = false
                        },
                    ) {
                        Text(stringResource(AYMR.strings.home_header_layout_editor_reset_confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text(stringResource(AYMR.strings.home_header_layout_editor_cancel))
                    }
                },
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(AYMR.strings.home_header_layout_editor_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
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
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        text = stringResource(AYMR.strings.home_header_layout_editor_show_grid),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = showGrid,
                        onCheckedChange = { showGrid = it },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        text = stringResource(AYMR.strings.pref_show_home_greeting),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = showHomeGreeting,
                        onCheckedChange = { showHomeGreeting = it },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        text = stringResource(AYMR.strings.pref_show_home_streak),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = showHomeStreak,
                        onCheckedChange = { showHomeStreak = it },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        text = stringResource(AYMR.strings.home_header_layout_editor_greeting_align_right),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = greetingAlignRight,
                        onCheckedChange = { greetingAlignRight = it },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        text = stringResource(AYMR.strings.home_header_layout_editor_nickname_align_right),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = nicknameAlignRight,
                        onCheckedChange = { nicknameAlignRight = it },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        text = stringResource(AYMR.strings.home_header_layout_editor_only_selected_overlay),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = showOnlySelectedOverlay,
                        onCheckedChange = { showOnlySelectedOverlay = it },
                    )
                }
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
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                    ) {
                        Text(stringResource(AYMR.strings.home_header_layout_editor_reset))
                    }
                    TextButton(
                        onClick = navigator::pop,
                    ) {
                        Text(stringResource(AYMR.strings.home_header_layout_editor_cancel))
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(144.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        val canvasWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val designWidthPx = layout.canvas.width.coerceAtLeast(1f)
        val previewMeasureScaleX = canvasWidthPx / designWidthPx
        val greetingMeasureWidthPx =
            (elementSizes.getValue(HomeHeaderLayoutElement.Greeting).width * previewMeasureScaleX).toInt().coerceAtLeast(1)
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

        HomeHeaderLayoutLivePreview(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewCanvasHeightDp),
            layoutSpec = layout,
            showGreeting = showGreeting,
            showStreak = showStreak,
            greetingAlignRight = greetingAlignRight,
            nicknameAlignRight = nicknameAlignRight,
        )

        if (showGrid) {
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            Canvas(Modifier.fillMaxSize()) {
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
    val border = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }
    val chipBg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    }
    val chipText = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .background(Color.Transparent, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(chipBg, RoundedCornerShape(999.dp))
                .border(1.dp, border.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
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

@Composable
private fun homeHeaderLayoutElementLabel(element: HomeHeaderLayoutElement): String {
    return when (element) {
        HomeHeaderLayoutElement.Greeting -> stringResource(AYMR.strings.home_header_layout_element_greeting)
        HomeHeaderLayoutElement.Nickname -> stringResource(AYMR.strings.home_header_layout_element_nickname)
        HomeHeaderLayoutElement.Avatar -> stringResource(AYMR.strings.home_header_layout_element_avatar)
        HomeHeaderLayoutElement.Streak -> stringResource(AYMR.strings.home_header_layout_element_streak)
    }
}
