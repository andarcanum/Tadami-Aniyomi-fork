package eu.kanade.presentation.easteregg.crystal

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.isActive
import tachiyomi.presentation.core.util.LocalAppHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun rememberCrystalTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/** One deliberate hold, with a semantic activation alternative for assistive technology. */
@Composable
internal fun CrystalMemorialTouch(
    labels: CrystalSceneLabels,
    enabled: Boolean,
    started: Boolean,
    elapsedMs: () -> Float,
    ambientMs: () -> Float,
    reduced: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val haptics = LocalAppHaptics.current
    val policy = rememberCrystalMotionPolicy()
    var input by remember { mutableStateOf(CrystalHoldInput()) }
    val progress = remember { mutableFloatStateOf(0f) }
    val canStart by rememberUpdatedState(enabled && !started && policy.resumed)
    val startLatest by rememberUpdatedState(onStart)
    val pressed = input.pressed
    LaunchedEffect(enabled, started, policy.resumed) {
        if (!enabled || started || !policy.resumed) {
            input = input.cancel()
        }
    }
    LaunchedEffect(pressed, input.generation, enabled, started, policy.resumed) {
        val attempt = input.generation
        when {
            !policy.resumed -> progress.floatValue = 0f
            started -> progress.floatValue = 1f
            pressed && enabled -> {
                // Foreground frame time, not an Animator: duration-scale zero must not bypass the hold.
                progress.floatValue = 0f
                var firstFrame: Long? = null
                while (isActive && progress.floatValue < 1f) {
                    withFrameNanos { now ->
                        val first = firstFrame ?: now.also { firstFrame = it }
                        progress.floatValue = CrystalTimeline.holdProgress((now - first) / 1_000_000f)
                    }
                }
                if (canStart && input.canCommit(attempt)) startLatest()
            }
            else -> {
                val from = progress.floatValue
                if (reduced || from == 0f) {
                    progress.floatValue = 0f
                } else {
                    var firstFrame: Long? = null
                    while (isActive && progress.floatValue > 0f) {
                        withFrameNanos { now ->
                            val first = firstFrame ?: now.also { firstFrame = it }
                            progress.floatValue = from *
                                (1f - CrystalTimeline.smooth((now - first) / 1_000_000f / CrystalTimeline.RELEASE_MS))
                        }
                    }
                }
            }
        }
    }
    val center = remember {
        Path().apply {
            moveTo(32f, 29.5f)
            cubicTo(24f, 23.5f, 18.5f, 26.5f, 14.8f, 34.2f)
            cubicTo(11.2f, 41.8f, 13.5f, 50.5f, 22.2f, 51.5f)
            cubicTo(26.2f, 52f, 28.5f, 47.8f, 32f, 47.8f)
            cubicTo(35.5f, 47.8f, 37.8f, 52f, 41.8f, 51.5f)
            cubicTo(50.5f, 50.5f, 52.8f, 41.8f, 49.2f, 34.2f)
            cubicTo(45.5f, 26.5f, 40f, 23.5f, 32f, 29.5f)
            close()
        }
    }
    val centerHighlight = remember {
        Path().apply {
            moveTo(23f, 33.5f)
            cubicTo(26f, 30.2f, 31f, 30.2f, 35f, 32.5f)
        }
    }
    val pads = remember {
        arrayOf(
            floatArrayOf(14.5f, 23.5f, 5.2f, 7.4f, -26f),
            floatArrayOf(25.5f, 15.5f, 5.5f, 7.8f, -10f),
            floatArrayOf(38.5f, 15.5f, 5.5f, 7.8f, 10f),
            floatArrayOf(49.5f, 23.5f, 5.2f, 7.4f, 26f),
        )
    }
    val palmBrush = remember(colors.accent, colors.accentVariant) {
        Brush.radialGradient(
            0f to Color(0xFFFFF9EC),
            0.44f to colors.accent,
            0.82f to colors.accentVariant,
            1f to Color(0xFFA27B91),
            center = Offset(28f, 33f),
            radius = 24f,
        )
    }
    Column(
        modifier.fillMaxWidth().graphicsLayer {
            alpha = if (started) 1f - CrystalTimeline.smooth(elapsedMs() / 700f) else 1f
        }.then(if (started) Modifier.clearAndSetSemantics { } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier.size(88.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = labels.start
                    progressBarRangeInfo = ProgressBarRangeInfo(progress.floatValue, 0f..1f)
                    if (!enabled) disabled()
                    onClick(labels.startAction) {
                        if (canStart) {
                            startLatest()
                            true
                        } else {
                            false
                        }
                    }
                }
                .onFocusChanged { if (!it.isFocused) input = input.keyUp() }
                .onKeyEvent { event ->
                    if (event.key !in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar) || !canStart) {
                        false
                    } else {
                        input = if (event.type == KeyEventType.KeyDown) input.keyDown() else input.keyUp()
                        true
                    }
                }
                .focusable(enabled && !started)
                .pointerInput(enabled, started, policy.resumed) {
                    if (enabled && !started && policy.resumed) {
                        detectTapGestures(onPress = {
                            input = input.touchDown()
                            val token = requireNotNull(input.touchToken)
                            haptics.tap()
                            try {
                                tryAwaitRelease()
                            } finally {
                                input = input.touchUp(token)
                            }
                        })
                    }
                },
        ) {
            val p = progress.floatValue
            val fade = if (enabled || started) 1f else 0.35f
            val breath = if (reduced) 0f else sin(ambientMs() / 1600f) * 0.04f
            val squash = if (reduced) 1f else 1f - p * 0.05f
            withTransform({
                scale(size.width / 64f, size.height / 64f, pivot = Offset.Zero)
                scale(squash, squash, pivot = Offset(32f, 33f))
            }) {
                val centerLight = CrystalTimeline.smooth(p / 0.35f)
                if (colors.isEInk) {
                    drawPath(
                        center,
                        colors.textPrimary,
                        style = if (p > 0.2f) Fill else Stroke(1.2f),
                    )
                } else {
                    val palmAlpha = (0.42f + centerLight * 0.58f + breath).coerceIn(0f, 1f) * fade
                    drawPath(center, palmBrush, alpha = palmAlpha)
                    drawPath(
                        center,
                        Color(0xFFFFF6E4),
                        alpha = (0.25f + centerLight * 0.45f) * fade,
                        style = Stroke(width = 0.65f, cap = StrokeCap.Round),
                    )
                    drawPath(
                        centerHighlight,
                        Color.White,
                        alpha = (0.20f + centerLight * 0.45f) * fade,
                        style = Stroke(width = 1.9f, cap = StrokeCap.Round),
                    )
                }
                pads.forEachIndexed { index, pad ->
                    val light = CrystalTimeline.smooth((p - 0.18f - index * 0.16f) / 0.24f)
                    rotate(pad[4], Offset(pad[0], pad[1])) {
                        if (colors.isEInk) {
                            drawOval(
                                colors.textPrimary,
                                topLeft = Offset(pad[0] - pad[2], pad[1] - pad[3]),
                                size = Size(pad[2] * 2, pad[3] * 2),
                                alpha = 1f,
                                style = if (light == 0f) Stroke(1.2f) else Fill,
                            )
                        } else {
                            val beanAlpha = (0.38f + light * 0.62f + breath).coerceIn(0f, 1f) * fade
                            val beanBrush = Brush.radialGradient(
                                0f to Color(0xFFFFF9EC),
                                0.45f to colors.accent,
                                0.85f to colors.accentVariant,
                                1f to Color(0xFF9E788F),
                                center = Offset(pad[0] - 1.5f, pad[1] - 2.4f),
                                radius = pad[3] * 1.65f,
                            )
                            if (light > 0.01f && !reduced) {
                                drawOval(
                                    colors.accent.copy(alpha = 0.28f * light * fade),
                                    topLeft = Offset(pad[0] - pad[2] * 1.35f, pad[1] - pad[3] * 1.35f),
                                    size = Size(pad[2] * 2.7f, pad[3] * 2.7f),
                                )
                            }
                            drawOval(
                                beanBrush,
                                topLeft = Offset(pad[0] - pad[2], pad[1] - pad[3]),
                                size = Size(pad[2] * 2, pad[3] * 2),
                                alpha = beanAlpha,
                            )
                            drawOval(
                                Color(0xFFFFF6E4),
                                topLeft = Offset(pad[0] - pad[2], pad[1] - pad[3]),
                                size = Size(pad[2] * 2, pad[3] * 2),
                                alpha = (0.22f + light * 0.45f) * fade,
                                style = Stroke(width = 0.55f),
                            )
                            drawOval(
                                Color.White,
                                topLeft = Offset(pad[0] - 2.8f, pad[1] - 4.9f),
                                size = Size(3.4f, 5.0f),
                                alpha = (0.20f + light * 0.50f) * fade,
                            )
                        }
                    }
                }
                // The contact releases a few quiet grains before fading into the main scene.
                if (started && !reduced) {
                    val release = CrystalTimeline.smooth(elapsedMs() / 700f)
                    for (index in 0..4) {
                        drawCircle(
                            colors.accent,
                            0.8f + index % 2 * 0.35f,
                            Offset(
                                18f + index * 7f + sin(index * 1.7f) * release * 4f,
                                30f - release * (12f + index * 3f),
                            ),
                            alpha = sin(release * PI.toFloat()).coerceAtLeast(0f) * 0.75f,
                        )
                    }
                }
            }
        }
        Text(
            labels.start,
            Modifier.padding(top = 8.dp),
            color = colors.textPrimary.copy(alpha = 0.86f),
            fontFamily = CrystalFonts.Ui,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Shape the whole cursive word once; reveal complete glyph regions, not a moving shutter. */
@Composable
internal fun CrystalFarewellWord(
    elapsedMs: () -> Float,
    reduced: Boolean,
    fontSize: Float,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = remember(fontSize, colors.accent) {
        TextStyle(
            color = colors.accent,
            fontFamily = CrystalFonts.Script,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.5f).sp,
        )
    }
    val layout = measurer.measure(CrystalTimeline.FAREWELL, style, softWrap = false, maxLines = 1)
    val bounds = remember(layout) { CrystalTimeline.FAREWELL.indices.map(layout::getBoundingBox) }
    val height = with(density) { layout.size.height.toDp() + 24.dp }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val time = elapsedMs()
        val padding = 16.dp.toPx()
        val fit = min(1f, ((size.width - padding * 2) / layout.size.width).coerceAtLeast(0.1f))
        withTransform({
            translate((size.width - layout.size.width * fit) / 2, (size.height - layout.size.height * fit) / 2)
            scale(fit, fit, pivot = Offset.Zero)
        }) {
            if (CrystalTimeline.farewellLetterOpacity(time, reduced, bounds.lastIndex) == 1f) {
                // No clipping at rest: final l and the font's flourishes retain their full ink bounds.
                drawText(layout)
            } else {
                for (index in bounds.indices) {
                    val opacity = CrystalTimeline.farewellLetterOpacity(time, reduced, index)
                    if (opacity == 0f) continue
                    clipRect(
                        left = if (index == 0) -padding / fit else bounds[index].left,
                        top = -padding / fit,
                        right = if (index ==
                            bounds.lastIndex
                        ) {
                            layout.size.width + padding / fit
                        } else {
                            bounds[index].right
                        },
                        bottom = layout.size.height + padding / fit,
                    ) {
                        drawText(layout, alpha = opacity)
                    }
                }
            }
        }
    }
}
