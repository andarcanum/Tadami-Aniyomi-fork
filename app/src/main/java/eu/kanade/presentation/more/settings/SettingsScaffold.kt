package eu.kanade.presentation.more.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.auroraHeaderIconSurface
import eu.kanade.presentation.theme.resolveAuroraTopBarScrimColor
import eu.kanade.tachiyomi.ui.home.LocalHomeHazeState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import kotlin.math.roundToInt

private val AuroraSettingsBottomContentInset = 16.dp
private val AuroraTopBarQuickReturnHideThreshold = 24.dp
private val AuroraTopBarQuickReturnShowThreshold = 16.dp
private val AuroraTopBarBackdropFadeDistance = 16.dp
private val AuroraTopBarBackdropExpandedFeather = 18.dp
private val AuroraTopBarBackdropCollapsedFeather = 10.dp
private const val AURORA_TOP_BAR_FLING_RESET_VELOCITY_PX = 150f

internal val LocalAuroraTopBarScrollProgress = compositionLocalOf { 0f }
internal val LocalAuroraTopBarInteractive = compositionLocalOf { true }

internal data class AuroraTopBarQuickReturnState(
    val scrolledDownPx: Float = 0f,
    val hideAccumulatorPx: Float = 0f,
    val showAccumulatorPx: Float = 0f,
    val isRevealed: Boolean = true,
)

@Stable
internal class AuroraQuickReturnTopBarController(
    private val delegate: TopAppBarScrollBehavior,
    private val canScrollProvider: () -> Boolean,
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
) : TopAppBarScrollBehavior by delegate {

    internal var measuredRowHeightPx: Int = 0

    internal var quickReturnState by mutableStateOf(AuroraTopBarQuickReturnState())
        private set

    internal fun canScroll(): Boolean = canScrollProvider()

    internal fun resetToTop() {
        val next = AuroraTopBarQuickReturnState()
        if (next != quickReturnState) {
            quickReturnState = next
            delegate.state.contentOffset = 0f
        }
    }

    override val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val next = reduceAuroraTopBarQuickReturn(
                current = quickReturnState,
                consumedY = consumed.y,
                availableY = available.y,
                rowHeightPx = measuredRowHeightPx,
                canScroll = canScrollProvider(),
                hideThresholdPx = hideThresholdPx,
                showThresholdPx = showThresholdPx,
            )
            if (next != quickReturnState) {
                quickReturnState = next
                delegate.state.contentOffset = -next.scrolledDownPx
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!canScrollProvider() || available.y > AURORA_TOP_BAR_FLING_RESET_VELOCITY_PX) {
                resetToTop()
            }
            return Velocity.Zero
        }
    }
}

@Composable
internal fun rememberAuroraQuickReturnTopBarScrollBehavior(
    state: TopAppBarState = rememberTopAppBarState(),
    canScroll: () -> Boolean = { true },
): TopAppBarScrollBehavior {
    val currentCanScroll by rememberUpdatedState(canScroll)
    val stableCanScroll = remember { { currentCanScroll() } }
    val density = LocalDensity.current
    val hideThresholdPx = with(density) { AuroraTopBarQuickReturnHideThreshold.toPx() }
    val showThresholdPx = with(density) { AuroraTopBarQuickReturnShowThreshold.toPx() }
    val delegate = TopAppBarDefaults.enterAlwaysScrollBehavior(
        state = state,
        canScroll = stableCanScroll,
    )
    return remember(delegate, stableCanScroll, hideThresholdPx, showThresholdPx) {
        AuroraQuickReturnTopBarController(
            delegate = delegate,
            canScrollProvider = stableCanScroll,
            hideThresholdPx = hideThresholdPx,
            showThresholdPx = showThresholdPx,
        )
    }
}

@Composable
fun SettingsScaffold(
    title: String,
    uiStyle: SettingsUiStyle,
    onBackPressed: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    showTopBar: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    topBarCanScroll: () -> Boolean = { true },
    content: @Composable (PaddingValues) -> Unit,
) {
    CompositionLocalProvider(LocalSettingsUiStyle provides uiStyle) {
        when (uiStyle) {
            SettingsUiStyle.Classic -> {
                Scaffold(
                    topBar = if (showTopBar) {
                        { scrollBehavior ->
                            if (titleContent != null) {
                                AppBar(
                                    titleContent = titleContent,
                                    navigateUp = onBackPressed,
                                    actions = actions,
                                    scrollBehavior = scrollBehavior,
                                )
                            } else {
                                AppBar(
                                    title = title,
                                    navigateUp = onBackPressed,
                                    actions = actions,
                                    scrollBehavior = scrollBehavior,
                                )
                            }
                        }
                    } else {
                        { _ -> }
                    },
                    floatingActionButton = floatingActionButton,
                    content = content,
                )
            }
            SettingsUiStyle.Aurora -> {
                val layoutDirection = LocalLayoutDirection.current
                val topBarState = rememberTopAppBarState()
                val hazeState = remember { HazeState() }
                val topBarScrollBehavior = if (showTopBar) {
                    rememberAuroraQuickReturnTopBarScrollBehavior(
                        state = topBarState,
                        canScroll = topBarCanScroll,
                    )
                } else {
                    TopAppBarDefaults.pinnedScrollBehavior(topBarState)
                }
                Scaffold(
                    topBarScrollBehavior = topBarScrollBehavior,
                    containerColor = Color.Transparent,
                    floatingActionButton = floatingActionButton,
                    topBar = { scrollBehavior ->
                        if (showTopBar) {
                            AuroraSettingsTopBarChrome(
                                scrollBehavior = scrollBehavior,
                                hazeState = hazeState,
                            ) {
                                AuroraTopBarLayout(
                                    title = title,
                                    titleContent = titleContent,
                                    onNavigateUp = onBackPressed,
                                    actions = actions,
                                )
                            }
                        }
                    },
                ) { contentPadding ->
                    SettingsAuroraBackground(
                        modifier = Modifier.fillMaxSize(),
                        hazeState = hazeState,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            content(
                                PaddingValues(
                                    start = contentPadding.calculateLeftPadding(layoutDirection),
                                    top = contentPadding.calculateTopPadding(),
                                    end = contentPadding.calculateRightPadding(layoutDirection),
                                    bottom = contentPadding.calculateBottomPadding() + AuroraSettingsBottomContentInset,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private class ChromeMeasuredHeights {
    var fullHeightPx: Int = 0
    var rowHeightPx: Int = 0
}

@Composable
internal fun AuroraSettingsTopBarChrome(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density).coerceAtLeast(0)
    val fadeDistancePx = if (colors.isEInk) 0f else with(density) { AuroraTopBarBackdropFadeDistance.toPx() }
    val expandedFeatherPx = with(density) { AuroraTopBarBackdropExpandedFeather.toPx().roundToInt() }
    val collapsedFeatherPx = with(density) { AuroraTopBarBackdropCollapsedFeather.toPx().roundToInt() }
    val dividerHeightPx = with(density) { 1.dp.toPx().roundToInt().coerceAtLeast(1) }

    val quickReturnController = scrollBehavior as? AuroraQuickReturnTopBarController
    if (quickReturnController != null) {
        LaunchedEffect(quickReturnController) {
            snapshotFlow { quickReturnController.canScroll() }
                .collect { scrollable ->
                    if (!scrollable) {
                        quickReturnController.resetToTop()
                    }
                }
        }
    }

    val targetRevealFraction = when {
        quickReturnController != null -> if (quickReturnController.quickReturnState.isRevealed) 1f else 0f
        scrollBehavior.isPinned -> 1f
        else -> {
            val limit = scrollBehavior.state.heightOffsetLimit
            if (limit < -0.5f) {
                (1f - (scrollBehavior.state.heightOffset / limit)).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }

    val revealFractionState = animateFloatAsState(
        targetValue = targetRevealFraction,
        animationSpec = if (colors.isEInk) {
            snap()
        } else {
            tween(durationMillis = 220, easing = FastOutSlowInEasing)
        },
        label = "AuroraTopBarRevealFraction",
    )

    val rawScrolledDownPx = quickReturnController?.quickReturnState?.scrolledDownPx
        ?: (-scrollBehavior.state.contentOffset).coerceAtLeast(0f)
    val targetBackdropProgress = resolveAuroraTopBarBackdropProgress(
        scrolledDownPx = rawScrolledDownPx,
        fadeDistancePx = fadeDistancePx,
    )

    val backdropProgressState = animateFloatAsState(
        targetValue = targetBackdropProgress,
        animationSpec = if (colors.isEInk) {
            snap()
        } else {
            tween(durationMillis = 160, easing = FastOutSlowInEasing)
        },
        label = "AuroraTopBarBackdropProgress",
    )

    val chromeHeights = remember { ChromeMeasuredHeights() }
    val isInteractive by remember {
        derivedStateOf { revealFractionState.value > 0.08f }
    }
    val backdropVisible by remember {
        derivedStateOf { backdropProgressState.value > 0.001f }
    }
    val scrollProgress by remember {
        derivedStateOf { backdropProgressState.value.coerceIn(0f, 1f) }
    }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (quickReturnController == null) {
                    scrollBehavior.state.heightOffsetLimit = resolveAuroraSettingsTopBarHeightOffsetLimit(
                        heightPx = size.height,
                        statusBarInsetPx = statusBarTopPx,
                    )
                }
            },
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                if (backdropVisible) {
                    AuroraSettingsTopBarBackdrop(
                        colors = colors,
                        hazeState = hazeState,
                        backdropProgressState = backdropProgressState,
                        revealFractionState = revealFractionState,
                        expandedFeatherPx = expandedFeatherPx.toFloat(),
                        collapsedFeatherPx = collapsedFeatherPx.toFloat(),
                        dividerHeightPx = dividerHeightPx.toFloat(),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .focusProperties { canFocus = isInteractive }
                    .then(if (!isInteractive) Modifier.clearAndSetSemantics {} else Modifier)
                    .layout { measurable, constraints ->
                        val childConstraints = constraints.copy(minHeight = 0)
                        val placeable = measurable.measure(childConstraints)
                        val fullHeightPx = placeable.height
                        val rowHeightPx = (fullHeightPx - statusBarTopPx).coerceAtLeast(0)
                        chromeHeights.fullHeightPx = fullHeightPx
                        chromeHeights.rowHeightPx = rowHeightPx
                        if (quickReturnController != null) {
                            quickReturnController.measuredRowHeightPx = rowHeightPx
                        }

                        val fraction = revealFractionState.value.coerceIn(0f, 1f)
                        val visibleRowPx = if (rowHeightPx <= 0) {
                            0
                        } else {
                            (rowHeightPx * fraction).roundToInt().coerceIn(0, rowHeightPx)
                        }
                        val visualOffsetPx = visibleRowPx - rowHeightPx

                        layout(placeable.width, visibleRowPx) {
                            placeable.placeRelativeWithLayer(0, -statusBarTopPx + visualOffsetPx) {
                                alpha = if (colors.isEInk) {
                                    1f
                                } else {
                                    (fraction / 0.55f).coerceIn(0f, 1f)
                                }
                            }
                        }
                    },
            ) {
                CompositionLocalProvider(
                    LocalAuroraTopBarScrollProgress provides scrollProgress,
                    LocalAuroraTopBarInteractive provides isInteractive,
                ) {
                    content()
                }
            }
        },
    ) { measurables, constraints ->
        val viewportPlaceable = measurables[1].measure(constraints)
        val fullHeightPx = chromeHeights.fullHeightPx
        val visibleRowPx = viewportPlaceable.height
        val width = viewportPlaceable.width.coerceAtLeast(0)

        val fraction = revealFractionState.value.coerceIn(0f, 1f)
        val backdropAlpha = backdropProgressState.value.coerceIn(0f, 1f)
        val backdropHeightPx = if (backdropAlpha <= 0.001f) {
            0
        } else if (colors.isEInk) {
            statusBarTopPx + visibleRowPx + dividerHeightPx
        } else {
            val featherPx = (collapsedFeatherPx + (expandedFeatherPx - collapsedFeatherPx) * fraction).roundToInt()
            statusBarTopPx + visibleRowPx + featherPx
        }.coerceAtLeast(0)

        val backdropPlaceable = measurables[0].measure(
            Constraints.fixed(width = width, height = backdropHeightPx),
        )

        layout(width, fullHeightPx) {
            if (backdropHeightPx > 0) {
                backdropPlaceable.placeRelative(0, 0)
            }
            viewportPlaceable.placeRelative(0, statusBarTopPx)
        }
    }
}

@Composable
private fun AuroraSettingsTopBarBackdrop(
    colors: AuroraColors,
    hazeState: HazeState?,
    backdropProgressState: State<Float>,
    revealFractionState: State<Float>,
    expandedFeatherPx: Float,
    collapsedFeatherPx: Float,
    dividerHeightPx: Float,
) {
    if (colors.isEInk) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .drawBehind {
                    drawRect(
                        color = colors.divider,
                        topLeft = Offset(0f, (size.height - dividerHeightPx).coerceAtLeast(0f)),
                        size = Size(size.width, dividerHeightPx),
                    )
                },
        )
        return
    }

    val surfaceFillModifier = if (hazeState != null) {
        Modifier.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle {
                backgroundColor(colors.background)
                colorEffects(
                    listOf(
                        HazeColorEffect.tint(
                            colors.surface.copy(alpha = if (colors.isDark) 0.72f else 0.82f),
                        ),
                    ),
                )
                blurRadius(22.dp)
                noiseFactor(0.10f)
            },
        )
    } else {
        Modifier.background(
            colors.background.copy(alpha = if (colors.isDark) 0.88f else 0.90f),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = backdropProgressState.value.coerceIn(0f, 1f)
            }
            .drawWithContent {
                drawContent()
                val fraction = revealFractionState.value.coerceIn(0f, 1f)
                val featherPx = collapsedFeatherPx + (expandedFeatherPx - collapsedFeatherPx) * fraction
                val solidStop = if (size.height > 1f) {
                    ((size.height - featherPx).coerceAtLeast(0f) / size.height).coerceIn(0f, 0.98f)
                } else {
                    0.75f
                }
                val midStop = solidStop + (1f - solidStop) * 0.45f
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        solidStop to Color.Black,
                        midStop to Color.Black.copy(alpha = 0.52f),
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .then(surfaceFillModifier),
    )
}

internal fun resolveAuroraSettingsTopBarHeightOffsetLimit(
    heightPx: Int,
    statusBarInsetPx: Int = 0,
): Float {
    val rowHeightPx = (heightPx - statusBarInsetPx.coerceAtLeast(0)).coerceAtLeast(0)
    return if (rowHeightPx <= 0) {
        0f
    } else {
        -rowHeightPx.toFloat()
    }
}

internal fun resolveAuroraTopBarBackdropProgress(
    scrolledDownPx: Float,
    fadeDistancePx: Float,
): Float {
    if (scrolledDownPx <= 0.5f) return 0f
    if (fadeDistancePx <= 0f) return 1f
    return (scrolledDownPx / fadeDistancePx).coerceIn(0f, 1f)
}

internal fun reduceAuroraTopBarQuickReturn(
    current: AuroraTopBarQuickReturnState,
    consumedY: Float,
    availableY: Float,
    rowHeightPx: Int,
    canScroll: Boolean,
    hideThresholdPx: Float,
    showThresholdPx: Float,
): AuroraTopBarQuickReturnState {
    if (!canScroll) {
        return AuroraTopBarQuickReturnState()
    }

    val clampedRowHeight = rowHeightPx.coerceAtLeast(0)

    if (availableY > 0.5f && consumedY >= 0f) {
        return AuroraTopBarQuickReturnState()
    }

    if (consumedY < 0f) {
        val deltaDown = -consumedY
        val nextScrolledDown = current.scrolledDownPx + deltaDown
        val nextHideAcc = current.hideAccumulatorPx + deltaDown
        val hideMinScroll = clampedRowHeight.toFloat() + hideThresholdPx
        val shouldHide = clampedRowHeight > 0 &&
            nextScrolledDown >= hideMinScroll &&
            nextHideAcc >= hideThresholdPx
        return current.copy(
            scrolledDownPx = nextScrolledDown,
            hideAccumulatorPx = if (shouldHide) 0f else nextHideAcc,
            showAccumulatorPx = 0f,
            isRevealed = if (shouldHide) false else current.isRevealed,
        )
    }

    if (consumedY > 0f) {
        val deltaUp = consumedY
        val nextScrolledDown = (current.scrolledDownPx - deltaUp).coerceAtLeast(0f)
        val nextShowAcc = current.showAccumulatorPx + deltaUp
        val inTopZone = clampedRowHeight > 0 && nextScrolledDown <= clampedRowHeight.toFloat()
        val shouldReveal = inTopZone || nextShowAcc >= showThresholdPx
        return current.copy(
            scrolledDownPx = nextScrolledDown,
            hideAccumulatorPx = 0f,
            showAccumulatorPx = if (shouldReveal) 0f else nextShowAcc,
            isRevealed = if (shouldReveal) true else current.isRevealed,
        )
    }

    return current
}

internal fun LazyListState.canScroll() = canScrollBackward || canScrollForward

internal fun ScrollState.canScroll() = canScrollBackward || canScrollForward

@Composable
internal fun SettingsAuroraBackground(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val sourceModifier = if (hazeState != null && !colors.isEInk) {
        Modifier.hazeSource(hazeState)
    } else {
        Modifier
    }
    Box(modifier = modifier.then(sourceModifier)) {
        AuroraBackground(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            resolveAuroraTopBarScrimColor(colors),
                        ),
                )
                content()
            }
        }
    }
}

@Composable
internal fun AuroraTopBarLayout(
    title: String,
    titleContent: (@Composable () -> Unit)?,
    onNavigateUp: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNavigateUp != null) {
            AuroraTopBarIconButton(
                onClick = onNavigateUp,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(MR.strings.action_bar_up_description),
            )
        }

        if (titleContent != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (onNavigateUp != null) 12.dp else 4.dp,
                        end = 12.dp,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                titleContent()
            }
        } else {
            AuroraTopBarTitleText(
                title = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (onNavigateUp != null) 12.dp else 4.dp,
                        end = 12.dp,
                    ),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

@Composable
internal fun AuroraTopBarIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = AuroraTheme.colors.textPrimary,
    iconRotation: Float = 0f,
    hazeState: HazeState? = LocalHomeHazeState.current,
    scrollProgress: Float = LocalAuroraTopBarScrollProgress.current,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val isInteractive = LocalAuroraTopBarInteractive.current
    Box(
        modifier = modifier
            .auroraHeaderIconSurface(
                colors = colors,
                hazeState = hazeState,
                scrollProgress = scrollProgress,
            )
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                enabled = isInteractive,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 22.dp),
                onClick = {
                    appHaptics.tap()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .rotate(iconRotation),
        )
    }
}

@Composable
internal fun AuroraTopBarTitleText(
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Text(
        text = title,
        modifier = modifier,
        style = if (title.length > 15) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.headlineSmall
        },
        color = colors.textPrimary,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
