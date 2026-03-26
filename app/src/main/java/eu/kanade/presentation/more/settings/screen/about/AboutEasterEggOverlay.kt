package eu.kanade.presentation.more.settings.screen.about

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.gradientBorderGlow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private const val GLYPH_RAIN_DURATION_MS = 2_200
private const val PAGE_MATERIALIZE_DURATION_MS = 700
private const val DISMISS_DURATION_MS = 280
private const val TYPEWRITER_TICK_MS = 16L
private const val TYPEWRITER_SECTION_PAUSE_MS = 420
private const val EXIT_BUTTON_PAUSE_MS = 180

private val OverlayShape = RoundedCornerShape(28.dp)
private val ExitButtonShape = RoundedCornerShape(18.dp)

@Composable
internal fun AboutEasterEggOverlay(
    phase: AboutEasterEggPhase,
    content: AboutHiddenFeatureLocalizedContent,
    onGlyphRainFinished: () -> Unit,
    onPageMaterialized: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onRevealComplete: () -> Unit,
) {
    if (phase == AboutEasterEggPhase.Idle || phase == AboutEasterEggPhase.Primed) return

    val timeline = remember(content) { buildTypewriterTimeline(content) }
    var revealElapsedMs by remember { mutableIntStateOf(0) }

    LaunchedEffect(phase, timeline) {
        when (phase) {
            AboutEasterEggPhase.GlyphRain -> {
                revealElapsedMs = 0
                delay(GLYPH_RAIN_DURATION_MS.toLong())
                onGlyphRainFinished()
            }
            AboutEasterEggPhase.PageMaterializing -> {
                revealElapsedMs = 0
                delay(PAGE_MATERIALIZE_DURATION_MS.toLong())
                onPageMaterialized()
            }
            AboutEasterEggPhase.PrologueVisible -> {
                revealElapsedMs = 0
                val startAt = SystemClock.uptimeMillis()
                while (revealElapsedMs < timeline.exitButtonRevealDelayMs) {
                    revealElapsedMs = (SystemClock.uptimeMillis() - startAt).toInt()
                    delay(TYPEWRITER_TICK_MS)
                }
                revealElapsedMs = timeline.exitButtonRevealDelayMs
                onRevealComplete()
            }
            AboutEasterEggPhase.Dismissing -> {
                delay(DISMISS_DURATION_MS.toLong())
                onDismissFinished()
            }
        }
    }

    val dismissing = phase == AboutEasterEggPhase.Dismissing
    val canDismiss = revealElapsedMs >= timeline.exitButtonRevealDelayMs
    val scrimAlpha by animateFloatAsState(
        targetValue = if (dismissing) 0f else 0.86f,
        animationSpec = tween(durationMillis = if (dismissing) DISMISS_DURATION_MS else 220),
        label = "aboutEasterEggScrimAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
            .clickable(
                enabled = phase == AboutEasterEggPhase.PrologueVisible && canDismiss,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
            ),
    ) {
        GlyphRainLayer(
            phase = phase,
            modifier = Modifier.fillMaxSize(),
        )

        MaterializingPage(
            phase = phase,
            content = content,
            timeline = timeline,
            revealElapsedMs = revealElapsedMs,
            canDismiss = canDismiss,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            onDismissRequest = onDismissRequest,
        )
    }
}

@Composable
internal fun GlyphRainLayer(
    phase: AboutEasterEggPhase,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glyphRain")
    val layerVisibility by animateFloatAsState(
        targetValue = when (phase) {
            AboutEasterEggPhase.GlyphRain -> 1f
            AboutEasterEggPhase.PageMaterializing -> 0.88f
            AboutEasterEggPhase.PrologueVisible -> 0.72f
            AboutEasterEggPhase.Dismissing -> 0f
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = if (phase ==
                AboutEasterEggPhase.Dismissing
            ) {
                DISMISS_DURATION_MS
            } else {
                600
            },
        ),
        label = "glyphLayerVisibility",
    )
    val glyphPalette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    )
    val glyphLayers = remember {
        listOf(
            GlyphLayerSpec(
                name = "background",
                streamCount = 9,
                minFontSizeSp = 11,
                maxFontSizeSp = 13,
                minLineHeightSp = 12,
                maxLineHeightSp = 14,
                minDurationMs = 6_000,
                maxDurationMs = 8_000,
                minAlpha = 0.24f,
                maxAlpha = 0.38f,
                blurRadiusDp = 1.8f,
                minGlyphCount = 14,
                maxGlyphCount = 18,
            ),
            GlyphLayerSpec(
                name = "midground",
                streamCount = 8,
                minFontSizeSp = 13,
                maxFontSizeSp = 15,
                minLineHeightSp = 15,
                maxLineHeightSp = 17,
                minDurationMs = 4_500,
                maxDurationMs = 6_000,
                minAlpha = 0.38f,
                maxAlpha = 0.56f,
                blurRadiusDp = 0f,
                minGlyphCount = 15,
                maxGlyphCount = 20,
            ),
            GlyphLayerSpec(
                name = "foreground",
                streamCount = 7,
                minFontSizeSp = 15,
                maxFontSizeSp = 17,
                minLineHeightSp = 18,
                maxLineHeightSp = 20,
                minDurationMs = 3_800,
                maxDurationMs = 5_100,
                minAlpha = 0.52f,
                maxAlpha = 0.7f,
                blurRadiusDp = 0f,
                minGlyphCount = 16,
                maxGlyphCount = 22,
            ),
        )
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        glyphLayers.forEachIndexed { layerIndex, layer ->
            val streams = remember(layerIndex) {
                List(layer.streamCount) { streamIndex ->
                    val random = Random((layerIndex + 1) * 997 + streamIndex * 67)
                    GlyphStreamSpec(
                        normalizedX = (
                            (streamIndex + random.nextFloat() * 0.45f) /
                                (layer.streamCount - 1).coerceAtLeast(1)
                            )
                            .coerceIn(0f, 1f) * 0.88f + 0.06f,
                        durationMs = random.nextInt(layer.minDurationMs, layer.maxDurationMs + 1),
                        delayMs = random.nextInt(0, 1_800),
                        glyphs = buildGlyphColumn(
                            random = random,
                            count = random.nextInt(layer.minGlyphCount, layer.maxGlyphCount + 1),
                        ),
                        colorIndex = (streamIndex + layerIndex) % glyphPalette.size,
                        fontSizeSp = random.nextInt(layer.minFontSizeSp, layer.maxFontSizeSp + 1),
                        lineHeightSp = random.nextInt(layer.minLineHeightSp, layer.maxLineHeightSp + 1),
                        alpha = random.nextFloat().let { lerp(layer.minAlpha, layer.maxAlpha, it) },
                        blurRadiusDp = layer.blurRadiusDp,
                    )
                }
            }

            streams.forEachIndexed { streamIndex, stream ->
                val loopProgress by infiniteTransition.animateFloat(
                    initialValue = -0.58f,
                    targetValue = 1.18f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = stream.durationMs,
                            delayMillis = stream.delayMs,
                            easing = LinearEasing,
                        ),
                    ),
                    label = "${layer.name}GlyphLoop$streamIndex",
                )

                val x = widthPx * stream.normalizedX
                val y = heightPx * loopProgress
                val color = glyphPalette[stream.colorIndex].copy(alpha = stream.alpha * layerVisibility)
                val streamModifier = Modifier
                    .offset {
                        IntOffset(
                            x = x.roundToInt(),
                            y = y.roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        translationX = -18.dp.toPx()
                        translationY = -96.dp.toPx()
                    }
                    .then(backgroundBlurModifier(stream.blurRadiusDp))

                Text(
                    text = stream.glyphs,
                    color = color,
                    lineHeight = stream.lineHeightSp.sp,
                    fontSize = stream.fontSizeSp.sp,
                    fontWeight = if (layer.name == "foreground") FontWeight.Bold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = streamModifier,
                )
            }
        }
    }
}

@Composable
private fun MaterializingPage(
    phase: AboutEasterEggPhase,
    content: AboutHiddenFeatureLocalizedContent,
    timeline: TypewriterTimeline,
    revealElapsedMs: Int,
    canDismiss: Boolean,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
) {
    val transition = updateTransition(targetState = phase, label = "materializingPage")
    var glitchTargetX by remember { mutableFloatStateOf(0f) }
    val pageScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = PAGE_MATERIALIZE_DURATION_MS) },
        label = "pageScale",
    ) { state ->
        when (state) {
            AboutEasterEggPhase.GlyphRain -> 0.76f
            AboutEasterEggPhase.PageMaterializing -> 1f
            AboutEasterEggPhase.PrologueVisible -> 1f
            AboutEasterEggPhase.Dismissing -> 0.97f
            else -> 0.76f
        }
    }
    val pageAlpha by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (phase ==
                    AboutEasterEggPhase.Dismissing
                ) {
                    DISMISS_DURATION_MS
                } else {
                    PAGE_MATERIALIZE_DURATION_MS
                },
            )
        },
        label = "pageAlpha",
    ) { state ->
        when (state) {
            AboutEasterEggPhase.GlyphRain -> 0f
            AboutEasterEggPhase.PageMaterializing -> 1f
            AboutEasterEggPhase.PrologueVisible -> 1f
            AboutEasterEggPhase.Dismissing -> 0f
            else -> 0f
        }
    }
    val glitchOffsetX by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 70) },
        label = "glitchOffsetX",
    ) { state ->
        when (state) {
            AboutEasterEggPhase.PageMaterializing -> glitchTargetX
            AboutEasterEggPhase.PrologueVisible,
            AboutEasterEggPhase.Dismissing,
            AboutEasterEggPhase.GlyphRain,
            AboutEasterEggPhase.Primed,
            AboutEasterEggPhase.Idle,
            -> 0f
        }
    }
    val glowPulse = rememberInfiniteTransition(label = "pageGlow")
    val glowRadius by glowPulse.animateFloat(
        initialValue = 14f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
        ),
        label = "pageGlowRadius",
    )
    val glowAlpha by glowPulse.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
        ),
        label = "pageGlowAlpha",
    )

    val glowColors = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.46f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.36f),
    )
    val pageBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.985f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.965f),
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        ),
    )

    LaunchedEffect(phase) {
        if (phase == AboutEasterEggPhase.PageMaterializing) {
            repeat(10) {
                glitchTargetX = Random.nextInt(-10, 11).toFloat()
                delay(45)
            }
        }
        glitchTargetX = 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.84f)
            .widthIn(max = 520.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(glitchOffsetX.roundToInt(), 0) }
                .graphicsLayer {
                    alpha = pageAlpha
                    scaleX = pageScale
                    scaleY = pageScale
                }
                .shadow(24.dp, OverlayShape)
                .clip(OverlayShape)
                .background(pageBrush)
                .gradientBorderGlow(
                    colors = glowColors,
                    borderWidth = 1.6.dp,
                    glowRadius = glowRadius.dp,
                    alpha = glowAlpha,
                    cornerRadius = 28.dp,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    shape = OverlayShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            ProloguePage(
                phase = phase,
                content = content,
                timeline = timeline,
                revealElapsedMs = revealElapsedMs,
                canDismiss = canDismiss,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

@Composable
private fun ProloguePage(
    phase: AboutEasterEggPhase,
    content: AboutHiddenFeatureLocalizedContent,
    timeline: TypewriterTimeline,
    revealElapsedMs: Int,
    canDismiss: Boolean,
    onDismissRequest: () -> Unit,
) {
    val textAlpha by animateFloatAsState(
        targetValue = when (phase) {
            AboutEasterEggPhase.PageMaterializing -> 0.36f
            AboutEasterEggPhase.PrologueVisible -> 1f
            AboutEasterEggPhase.Dismissing -> 0f
            else -> 0f
        },
        animationSpec = tween(
            durationMillis = if (phase ==
                AboutEasterEggPhase.Dismissing
            ) {
                DISMISS_DURATION_MS
            } else {
                420
            },
        ),
        label = "prologueTextAlpha",
    )
    val exitButtonAlpha by animateFloatAsState(
        targetValue = if (canDismiss) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "exitButtonAlpha",
    )
    val buttonPulse = rememberInfiniteTransition(label = "exitButtonPulse")
    val buttonScale by buttonPulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "exitButtonScale",
    )
    val buttonBrushShift by buttonPulse.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "exitButtonGlow",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TypewriterText(
            text = content.systemLabel,
            elapsedMs = revealElapsedMs,
            spec = timeline.systemLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer(alpha = textAlpha),
            letterSpacing = 1.4.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        TypewriterText(
            text = content.title,
            elapsedMs = revealElapsedMs,
            spec = timeline.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer(alpha = textAlpha),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))
        TypewriterText(
            text = content.subtitle,
            elapsedMs = revealElapsedMs,
            spec = timeline.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer(alpha = textAlpha),
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        TypewriterText(
            text = content.body,
            elapsedMs = revealElapsedMs,
            spec = timeline.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer(alpha = textAlpha),
            textAlign = TextAlign.Start,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = textAlpha * exitButtonAlpha
                    scaleX = if (canDismiss) buttonScale else 0.96f
                    scaleY = if (canDismiss) buttonScale else 0.96f
                }
                .clip(ExitButtonShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = buttonBrushShift),
                            MaterialTheme.colorScheme.secondary.copy(alpha = buttonBrushShift - 0.05f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (canDismiss) 0.34f else 0.16f),
                    shape = ExitButtonShape,
                )
                .clickable(
                    enabled = phase == AboutEasterEggPhase.PrologueVisible && canDismiss,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                text = content.exitLabel,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TypewriterText(
    text: String,
    elapsedMs: Int,
    spec: TypewriterSpec,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    val revealed = revealTypewriterText(
        text = text,
        elapsedMs = elapsedMs,
        startDelayMs = spec.startDelayMs,
        millisPerChar = spec.millisPerChar,
    )
    Text(
        text = revealed,
        style = style,
        color = color,
        modifier = modifier.fillMaxWidth(),
        letterSpacing = letterSpacing,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textAlign = textAlign,
    )
}

internal fun revealTypewriterText(
    text: String,
    elapsedMs: Int,
    startDelayMs: Int,
    millisPerChar: Int,
): String {
    if (elapsedMs <= startDelayMs) return ""
    if (millisPerChar <= 0) return text

    val visibleCharacters = ((elapsedMs - startDelayMs) / millisPerChar).coerceIn(0, text.length)
    return text.take(visibleCharacters)
}

private fun buildTypewriterTimeline(content: AboutHiddenFeatureLocalizedContent): TypewriterTimeline {
    val systemLabel = TypewriterSpec(startDelayMs = 0, millisPerChar = 52)
    val title = TypewriterSpec(
        startDelayMs = revealDurationMs(content.systemLabel, systemLabel) + TYPEWRITER_SECTION_PAUSE_MS,
        millisPerChar = 44,
    )
    val subtitle = TypewriterSpec(
        startDelayMs = revealDurationMs(content.title, title) + TYPEWRITER_SECTION_PAUSE_MS,
        millisPerChar = 26,
    )
    val body = TypewriterSpec(
        startDelayMs = revealDurationMs(content.subtitle, subtitle) + TYPEWRITER_SECTION_PAUSE_MS,
        millisPerChar = 18,
    )

    return TypewriterTimeline(
        systemLabel = systemLabel,
        title = title,
        subtitle = subtitle,
        body = body,
        exitButtonRevealDelayMs = revealDurationMs(content.body, body) + EXIT_BUTTON_PAUSE_MS,
    )
}

private fun revealDurationMs(text: String, spec: TypewriterSpec): Int {
    return spec.startDelayMs + text.length * spec.millisPerChar
}

private fun backgroundBlurModifier(blurRadiusDp: Float): Modifier {
    return if (blurRadiusDp > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(blurRadiusDp.dp)
    } else {
        Modifier
    }
}

private data class GlyphLayerSpec(
    val name: String,
    val streamCount: Int,
    val minFontSizeSp: Int,
    val maxFontSizeSp: Int,
    val minLineHeightSp: Int,
    val maxLineHeightSp: Int,
    val minDurationMs: Int,
    val maxDurationMs: Int,
    val minAlpha: Float,
    val maxAlpha: Float,
    val blurRadiusDp: Float,
    val minGlyphCount: Int,
    val maxGlyphCount: Int,
)

private data class GlyphStreamSpec(
    val normalizedX: Float,
    val durationMs: Int,
    val delayMs: Int,
    val glyphs: String,
    val colorIndex: Int,
    val fontSizeSp: Int,
    val lineHeightSp: Int,
    val alpha: Float,
    val blurRadiusDp: Float,
)

private data class TypewriterSpec(
    val startDelayMs: Int,
    val millisPerChar: Int,
)

private data class TypewriterTimeline(
    val systemLabel: TypewriterSpec,
    val title: TypewriterSpec,
    val subtitle: TypewriterSpec,
    val body: TypewriterSpec,
    val exitButtonRevealDelayMs: Int,
)

private fun buildGlyphColumn(random: Random, count: Int): String {
    val glyphPool = "界章異世界夢天語電雷章詠零刻幻光扉魂読書夜空塔綴記録箱東京魔導頁雨終焉カタカナシステムノベルログ"
    return buildString {
        repeat(count) { index ->
            append(glyphPool[random.nextInt(glyphPool.length)])
            if (index != count - 1) append('\n')
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}
