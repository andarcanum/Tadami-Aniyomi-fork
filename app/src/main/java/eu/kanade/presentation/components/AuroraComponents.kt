package eu.kanade.presentation.components

import android.animation.ValueAnimator
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.pow

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val colors = AuroraTheme.colors
    val animatedAuroraBackground by uiPreferences.animatedAuroraBackground().collectAsState()
    val specialBackgroundStyle by uiPreferences.specialBackgroundStyle().collectAsState()

    AuroraAmbientBackground(
        enabled = animatedAuroraBackground && !colors.isEInk,
        specialBackgroundStyle = specialBackgroundStyle,
        modifier = modifier,
    ) {
        // Independent additive cosmetic: the chosen base renders first, UI content last.
        eu.kanade.presentation.easteregg.crystal.CrystalBackgroundOverlay(Modifier.matchParentSize())
        content()
    }
}

@Composable
fun AuroraAmbientBackground(
    enabled: Boolean,
    specialBackgroundStyle: String = "none",
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val powerManager =
        remember(context) {
            context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        }
    val isPowerSaveMode = powerManager?.isPowerSaveMode == true

    val shouldAnimate = shouldAnimateAuroraBackground(
        userEnabled = enabled && !colors.isEInk && !isPowerSaveMode,
        isLifecycleResumed = isLifecycleResumed,
        systemAnimationsEnabled = ValueAnimator.areAnimatorsEnabled(),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        if (enabled && !colors.isEInk) {
            if (specialBackgroundStyle == "none") {
                if (shouldAnimate) {
                    AuroraAnimatedAmbientCanvas(colors = colors)
                } else {
                    AuroraAmbientCanvas(
                        colors = colors,
                        blobOneDriftX = { -0.08f },
                        blobOneDriftY = { -0.05f },
                        blobTwoDriftX = { 0.08f },
                        blobTwoDriftY = { 0.02f },
                        blobThreeDriftX = { -0.03f },
                        blobThreeDriftY = { 0.06f },
                        blobOneAlpha = { 0.2f },
                        blobTwoAlpha = { 0.12f },
                        blobThreeAlpha = { 0.1f },
                    )
                }
            } else {
                AuroraSpecialBackgroundCanvas(
                    colors = colors,
                    styleKey = specialBackgroundStyle,
                    animate = shouldAnimate,
                )
            }
        }

        content()
    }
}

internal fun auroraPingPong(
    timeMillis: Long,
    durationMillis: Long,
    from: Float,
    to: Float,
): Float {
    if (durationMillis <= 0L) return from
    val cycle = durationMillis * 2L
    val pos = ((timeMillis % cycle) + cycle) % cycle
    val raw = if (pos <= durationMillis) {
        pos.toFloat() / durationMillis.toFloat()
    } else {
        (cycle - pos).toFloat() / durationMillis.toFloat()
    }
    // Smoothstep easing matching slow ambient drift without allocating a Compose Easing/Animation.
    val eased = raw * raw * (3f - 2f * raw)
    return from + (to - from) * eased
}

@Composable
private fun AuroraAnimatedAmbientCanvas(
    colors: eu.kanade.presentation.theme.AuroraColors,
) {
    // Throttle ambient blob drift to ~30fps (same cadence as special backgrounds) instead of
    // running 9 unthrottled 120Hz infinite transitions that invalidate Haze blur every 8ms.
    val timeMillisState = rememberAuroraSpecialClockState(animate = true)

    AuroraAmbientCanvas(
        colors = colors,
        blobOneDriftX = { auroraPingPong(timeMillisState.longValue, 26_000L, -0.08f, 0.1f) },
        blobOneDriftY = { auroraPingPong(timeMillisState.longValue, 30_000L, -0.05f, 0.04f) },
        blobTwoDriftX = { auroraPingPong(timeMillisState.longValue, 22_000L, 0.08f, -0.06f) },
        blobTwoDriftY = { auroraPingPong(timeMillisState.longValue, 28_000L, 0.02f, -0.08f) },
        blobThreeDriftX = { auroraPingPong(timeMillisState.longValue, 20_000L, -0.03f, 0.05f) },
        blobThreeDriftY = { auroraPingPong(timeMillisState.longValue, 24_000L, 0.06f, -0.04f) },
        blobOneAlpha = { auroraPingPong(timeMillisState.longValue, 18_000L, 0.2f, 0.3f) },
        blobTwoAlpha = { auroraPingPong(timeMillisState.longValue, 20_000L, 0.12f, 0.22f) },
        blobThreeAlpha = { auroraPingPong(timeMillisState.longValue, 24_000L, 0.1f, 0.18f) },
    )
}

@Composable
private fun AuroraAmbientCanvas(
    colors: eu.kanade.presentation.theme.AuroraColors,
    blobOneDriftX: () -> Float,
    blobOneDriftY: () -> Float,
    blobTwoDriftX: () -> Float,
    blobTwoDriftY: () -> Float,
    blobThreeDriftX: () -> Float,
    blobThreeDriftY: () -> Float,
    blobOneAlpha: () -> Float,
    blobTwoAlpha: () -> Float,
    blobThreeAlpha: () -> Float,
) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val radiusOne = size.minDimension * 0.5f
                val radiusTwo = size.minDimension * 0.44f
                val radiusThree = size.minDimension * 0.52f
                val brushOne = Brush.radialGradient(
                    colors = listOf(colors.glowEffect, Color.Transparent),
                    center = Offset(radiusOne, radiusOne),
                    radius = radiusOne.coerceAtLeast(1f),
                )
                val brushTwo = Brush.radialGradient(
                    colors = listOf(colors.gradientPurple, Color.Transparent),
                    center = Offset(radiusTwo, radiusTwo),
                    radius = radiusTwo.coerceAtLeast(1f),
                )
                val brushThree = Brush.radialGradient(
                    colors = listOf(colors.accent, Color.Transparent),
                    center = Offset(radiusThree, radiusThree),
                    radius = radiusThree.coerceAtLeast(1f),
                )
                val vignetteBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.background.copy(alpha = if (colors.isDark) 0.2f else 0.12f),
                        colors.background.copy(alpha = if (colors.isDark) 0.38f else 0.2f),
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.4f),
                    radius = (size.maxDimension * 0.92f).coerceAtLeast(1f),
                )
                val blobBaseAlpha = if (colors.isDark) 1f else 0.45f

                onDrawBehind {
                    fun drawAmbientBlob(
                        centerFractionX: Float,
                        centerFractionY: Float,
                        driftX: Float,
                        driftY: Float,
                        radius: Float,
                        aspectX: Float,
                        aspectY: Float,
                        brush: Brush,
                        alpha: Float,
                    ) {
                        val centerX = size.width * (centerFractionX + driftX)
                        val centerY = size.height * (centerFractionY + driftY)
                        withTransform({
                            translate(
                                left = centerX - radius * aspectX,
                                top = centerY - radius * aspectY,
                            )
                            scale(scaleX = aspectX, scaleY = aspectY, pivot = Offset.Zero)
                        }) {
                            drawCircle(
                                brush = brush,
                                radius = radius,
                                center = resolveAuroraAmbientBlobCircleCenter(radius),
                                alpha = alpha.coerceIn(0f, 1f),
                            )
                        }
                    }

                    drawAmbientBlob(
                        centerFractionX = 0.22f,
                        centerFractionY = 0.14f,
                        driftX = blobOneDriftX(),
                        driftY = blobOneDriftY(),
                        radius = radiusOne,
                        aspectX = 1.35f,
                        aspectY = 0.8f,
                        brush = brushOne,
                        alpha = blobOneAlpha() * blobBaseAlpha,
                    )
                    drawAmbientBlob(
                        centerFractionX = 0.84f,
                        centerFractionY = 0.22f,
                        driftX = blobTwoDriftX(),
                        driftY = blobTwoDriftY(),
                        radius = radiusTwo,
                        aspectX = 1.15f,
                        aspectY = 0.72f,
                        brush = brushTwo,
                        alpha = blobTwoAlpha() * blobBaseAlpha,
                    )
                    drawAmbientBlob(
                        centerFractionX = 0.48f,
                        centerFractionY = 0.72f,
                        driftX = blobThreeDriftX(),
                        driftY = blobThreeDriftY(),
                        radius = radiusThree,
                        aspectX = 1.45f,
                        aspectY = 0.9f,
                        brush = brushThree,
                        alpha = blobThreeAlpha() * 0.72f,
                    )
                    drawRect(brush = vignetteBrush)
                }
            },
    )
}

internal fun resolveAuroraAmbientBlobCircleCenter(radius: Float): Offset = Offset(radius, radius)

private data class PetalConfig(
    val initialX: Float,
    val initialY: Float,
    val speedY: Float,
    val swayAmp: Float,
    val swayFreq: Float,
    val rotationSpeed: Float,
    val scale: Float,
    val phaseOffset: Float,
)

private val PETALS = listOf(
    PetalConfig(0.10f, 0.05f, 0.22f, 0.035f, 1.4f, 40f, 0.85f, 0.0f),
    PetalConfig(0.25f, 0.40f, 0.18f, 0.045f, 1.1f, -30f, 0.70f, 1.2f),
    PetalConfig(0.40f, 0.15f, 0.26f, 0.025f, 1.8f, 60f, 1.10f, 0.5f),
    PetalConfig(0.55f, 0.75f, 0.20f, 0.040f, 1.3f, -45f, 0.95f, 2.3f),
    PetalConfig(0.70f, 0.30f, 0.24f, 0.030f, 1.6f, 50f, 0.80f, 3.1f),
    PetalConfig(0.85f, 0.60f, 0.16f, 0.050f, 0.9f, -25f, 0.65f, 0.8f),
    PetalConfig(0.95f, 0.10f, 0.28f, 0.020f, 2.0f, 70f, 1.00f, 4.2f),
    PetalConfig(0.05f, 0.80f, 0.20f, 0.040f, 1.2f, -35f, 0.75f, 1.7f),
    PetalConfig(0.18f, 0.65f, 0.25f, 0.030f, 1.5f, 55f, 0.90f, 2.8f),
    PetalConfig(0.32f, 0.90f, 0.19f, 0.045f, 1.0f, -40f, 0.75f, 0.3f),
    PetalConfig(0.48f, 0.35f, 0.23f, 0.035f, 1.3f, 45f, 1.05f, 1.9f),
    PetalConfig(0.62f, 0.50f, 0.21f, 0.040f, 1.2f, -50f, 0.80f, 3.5f),
    PetalConfig(0.78f, 0.85f, 0.17f, 0.050f, 0.8f, 30f, 0.70f, 0.9f),
    PetalConfig(0.90f, 0.45f, 0.27f, 0.025f, 1.9f, -65f, 1.15f, 2.1f),
    PetalConfig(0.12f, 0.32f, 0.21f, 0.038f, 1.4f, 35f, 0.85f, 0.6f),
    PetalConfig(0.82f, 0.02f, 0.23f, 0.032f, 1.6f, -42f, 0.95f, 1.4f),
    PetalConfig(0.22f, 0.12f, 0.24f, 0.033f, 1.5f, 48f, 0.88f, 3.7f),
    PetalConfig(0.38f, 0.58f, 0.17f, 0.048f, 0.9f, -28f, 0.72f, 2.5f),
    PetalConfig(0.52f, 0.08f, 0.27f, 0.028f, 1.7f, 58f, 1.12f, 0.1f),
    PetalConfig(0.68f, 0.95f, 0.20f, 0.042f, 1.1f, -46f, 0.92f, 1.3f),
    PetalConfig(0.74f, 0.22f, 0.25f, 0.031f, 1.6f, 52f, 0.82f, 2.9f),
    PetalConfig(0.88f, 0.70f, 0.15f, 0.052f, 0.7f, -22f, 0.62f, 4.0f),
    PetalConfig(0.98f, 0.28f, 0.29f, 0.022f, 2.1f, 72f, 1.02f, 0.7f),
    PetalConfig(0.28f, 0.82f, 0.18f, 0.042f, 1.1f, -38f, 0.78f, 3.3f),
)

private data class StarConfig(
    val xFraction: Float,
    val yFraction: Float,
    val pulseSpeed: Float,
    val phase: Float,
    val size: Float,
)

private val FLOATING_STARS = listOf(
    StarConfig(0.12f, 0.15f, 1.2f, 0.0f, 2f),
    StarConfig(0.85f, 0.25f, 0.8f, 1.5f, 3f),
    StarConfig(0.35f, 0.78f, 1.5f, 0.7f, 1.5f),
    StarConfig(0.72f, 0.65f, 1.0f, 2.3f, 2.5f),
    StarConfig(0.22f, 0.45f, 0.7f, 3.1f, 2f),
    StarConfig(0.90f, 0.82f, 1.4f, 1.1f, 3.5f),
    StarConfig(0.08f, 0.70f, 1.1f, 0.4f, 2f),
    StarConfig(0.55f, 0.12f, 1.3f, 2.8f, 1.8f),
    StarConfig(0.42f, 0.30f, 0.9f, 1.9f, 2.5f),
    StarConfig(0.68f, 0.28f, 1.6f, 0.2f, 1.5f),
    StarConfig(0.18f, 0.88f, 0.6f, 3.5f, 3f),
    StarConfig(0.80f, 0.55f, 1.2f, 2.0f, 2.2f),
    StarConfig(0.48f, 0.92f, 1.0f, 1.7f, 2.5f),
    StarConfig(0.28f, 0.22f, 1.5f, 0.9f, 1.8f),
    StarConfig(0.62f, 0.85f, 0.8f, 2.5f, 3f),
)

private fun floatMod(value: Float, max: Float): Float {
    val r = value % max
    return if (r < 0f) r + max else r
}

private data class Vec3(val x: Float, val y: Float, val z: Float)
private data class Projected(val x: Float, val y: Float, val z: Float, val p: Float)
private data class BookSpec(
    val index: Int,
    val theme: Int,
    val width: Float,
    val depth: Float,
    val height: Float,
    val x: Float,
    val y: Float,
    val z: Float,
    val bobPhase: Float,
    val bobAmp: Float,
)
private data class FaceDraw(
    val book: BookSpec,
    val face: String,
    val corners: List<Projected>,
    val z: Float,
)

private data class NeonRingSpec(
    val pitchBase: Float,
    val yawBase: Float,
    val rollBase: Float,
    val spinPitch: Float,
    val spinYaw: Float,
    val radiusFraction: Float,
    val color: Color,
    val seed: Float,
)

private class NeonSegment(
    var x1: Float = 0f,
    var y1: Float = 0f,
    var z1: Float = 0f,
    var x2: Float = 0f,
    var y2: Float = 0f,
    var z2: Float = 0f,
    var z: Float = 0f,
    var color: Color = Color.Transparent,
    var alphaMod: Float = 1f,
    var widthMod: Float = 1f,
)

private class NeonPacket(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var color: Color = Color.Transparent,
    var sizeMod: Float = 1f,
)

private fun srHash(seed: Float): Float {
    return kotlin.math.abs(kotlin.math.sin(seed * 127.1f + 311.7f) * 43758.5453f) % 1f
}

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun blendColor(a: Color, b: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    val inv = 1f - clamped
    return Color(
        red = a.red * inv + b.red * clamped,
        green = a.green * inv + b.green * clamped,
        blue = a.blue * inv + b.blue * clamped,
        alpha = a.alpha * inv + b.alpha * clamped,
    )
}

private const val AURORA_SPECIAL_BG_FRAME_INTERVAL_MS = 33L

private class MutableProjected(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var p: Float = 1f,
)

private class MutableFaceDraw(
    var book: BookSpec? = null,
    var face: String = "",
    val corners: Array<MutableProjected> = Array(4) { MutableProjected() },
    var z: Float = 0f,
)

private data class DeepSpaceStarSpec(
    val xFraction: Float,
    val yFraction: Float,
    val seed: Float,
    val colorIndex: Int,
    val baseAlpha: Float,
    val radiusDp: Float,
)

private data class DeepSpaceParticleSpec(
    val phaseBase: Float,
    val speedFactor: Float,
    val angleOffset: Float,
    val radialPhase: Float,
    val tone: Float,
    val radiusDp: Float,
)

private val DEEP_SPACE_THEME_LABELS = listOf("アニメ", "漫画", "ラノベ", "アーカイブ")
private val DEEP_SPACE_TITLE_SETS = listOf(
    listOf("進撃の巨人", "鬼滅の刃", "僕のヒーローアカデミア", "呪術廻戦", "ワンピース", "鋼の錬金術師"),
    listOf("ナルト", "BLEACH", "東京喰種", "デスノート", "ベルセルク", "寄生獣"),
    listOf("君の名は。", "天気の子", "聲の形", "涼宮ハルヒの憂鬱", "ソードアート", "四月は君の嘘"),
    listOf("星海アーカイブ", "虚空写本", "古書目録", "銀河読本", "TADAMI ARCHIVE", "宙の書架"),
)
private val DEEP_SPACE_COVER_MARKS = listOf(
    listOf("動", "幕", "光", "声"),
    listOf("漫", "頁", "墨", "線"),
    listOf("小", "説", "章", "語"),
    listOf("蔵", "書", "録", "星"),
)
private val DEEP_SPACE_SPINE_RAILS = floatArrayOf(0.10f, 0.90f)
private val DEEP_SPACE_FACE_COMPARATOR = Comparator<MutableFaceDraw> { a, b -> a.z.compareTo(b.z) }

private data class ShadowStarSpec(
    val baseRadiusFactor: Float,
    val theta0: Float,
    val seed: Float,
    val baseAlpha: Float,
    val mod11: Boolean,
    val mod5: Boolean,
)

private data class ShadowParticleSpec(
    val band: Float,
    val theta0: Float,
    val dotRadiusSeed: Float,
    val mod9: Boolean,
    val mod4: Boolean,
)

private val DEEP_SPACE_BOOK_FACES = listOf("back", "left", "right", "bottom", "top", "front")

internal fun resolveAuroraSpecialBackgroundDispatchKey(
    styleKey: String,
    isEInk: Boolean,
): String? {
    if (isEInk || styleKey == "none") return null
    return when (styleKey) {
        "void_weeping_red",
        "ink_water",
        "petal_storm",
        "trinity_constellation",
        "event_horizon_library",
        "deep_space_archive",
        "shadow_realm",
        "neon_orbit",
        -> styleKey
        else -> null
    }
}

internal fun resolveAuroraSpecialElapsedSeconds(
    timeMillis: Long,
    animate: Boolean,
    staticFallbackSeconds: Float = 0f,
): Float = if (animate) timeMillis / 1000f else staticFallbackSeconds

@Composable
private fun rememberAuroraSpecialClockState(animate: Boolean): LongState {
    val timeMillisState = remember { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            timeMillisState.longValue = android.os.SystemClock.uptimeMillis()
            kotlinx.coroutines.delay(AURORA_SPECIAL_BG_FRAME_INTERVAL_MS)
        }
    }
    return timeMillisState
}

/**
 * Dispatches each special Aurora background to a dedicated leaf composable so:
 * 1) `timeMillisState.longValue` is read strictly in the Draw phase (`Canvas`) — 0 recompositions/sec;
 * 2) only the active style allocates its shaders, palettes, and pools;
 * 3) ART JIT compiles small focused Canvas lambdas instead of a single 1350-line monolith.
 */
@Composable
private fun AuroraSpecialBackgroundCanvas(
    colors: eu.kanade.presentation.theme.AuroraColors,
    styleKey: String,
    animate: Boolean,
) {
    when (resolveAuroraSpecialBackgroundDispatchKey(styleKey, colors.isEInk)) {
        "void_weeping_red" -> AuroraVoidWeepingRedBackground(colors = colors, animate = animate)
        "ink_water" -> AuroraInkWaterBackground(colors = colors, animate = animate)
        "petal_storm" -> AuroraPetalStormBackground(colors = colors, animate = animate)
        "trinity_constellation" -> AuroraTrinityConstellationBackground(colors = colors, animate = animate)
        "event_horizon_library" -> AuroraEventHorizonLibraryBackground(colors = colors, animate = animate)
        "deep_space_archive" -> AuroraDeepSpaceArchiveBackground(colors = colors, animate = animate)
        "shadow_realm" -> AuroraShadowRealmBackground(colors = colors, animate = animate)
        "neon_orbit" -> AuroraNeonOrbitBackground(colors = colors, animate = animate)
        null -> Unit
    }
}

@Composable
private fun AuroraVoidWeepingRedBackground(
    colors: eu.kanade.presentation.theme.AuroraColors,
    animate: Boolean,
) {
    val timeMillisState = rememberAuroraSpecialClockState(animate)
    // RuntimeShader (AGSL) only exists on API 33+. Keep all references isolated in
    // WeepingVoidShader so the class is never loaded/verified on older devices.
    val weepingVoid = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            WeepingVoidShader.getInstance()
        } else {
            null
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && weepingVoid != null) {
            val time = resolveAuroraSpecialElapsedSeconds(
                timeMillis = timeMillisState.longValue,
                animate = animate,
                staticFallbackSeconds = 0f,
            )
            with(weepingVoid) { drawWeepingVoid(light = !colors.isDark, time = time) }
        } else {
            // Fallback < Android 13
            drawRect(
                brush = Brush.radialGradient(
                    colors = if (colors.isDark) {
                        listOf(Color(0xFF3A000A), Color(0xFF140004), Color(0xFF070001))
                    } else {
                        listOf(Color(0x55B00020), Color(0x1AB00020), Color(0xFFE9E1DC))
                    },
                    center = center,
                    radius = size.minDimension * 0.75f,
                ),
            )
        }
    }
}

@Composable
private fun AuroraInkWaterBackground(
    colors: eu.kanade.presentation.theme.AuroraColors,
    animate: Boolean,
) {
    val timeMillisState = rememberAuroraSpecialClockState(animate)
    val inkWater = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InkWaterShader.getInstance()
        } else {
            null
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val palette = inkWaterPalette(
            accent = colors.accent,
            accentVariant = colors.accentVariant,
            background = colors.background,
            dark = colors.isDark,
        )
        val time = resolveAuroraSpecialElapsedSeconds(
            timeMillis = timeMillisState.longValue,
            animate = animate,
            staticFallbackSeconds = 20f,
        )
        val activeInkShader = inkWater
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activeInkShader != null) {
            with(activeInkShader) {
                drawInkWater(
                    timeSec = time,
                    deepColor = palette.deep,
                    midColor = palette.mid,
                    accentColor = palette.accent,
                    density = if (colors.isDark) 0.67f else 0.40f,
                )
            }
        } else {
            // Fallback < Android 13 / отключённые анимации / power save
            drawInkWaterFallback(colors.accent, colors.accentVariant, dark = colors.isDark)
        }
        drawInkWaterVignette(palette.deep, palette.background, dark = colors.isDark)
    }
}

@Composable
private fun AuroraPetalStormBackground(
    colors: eu.kanade.presentation.theme.AuroraColors,
    animate: Boolean,
) {
    val timeMillisState = rememberAuroraSpecialClockState(animate)
    val petalTemplatePath = remember {
        Path().apply {
            moveTo(0f, -0.5f)
            quadraticTo(0.45f, -0.28f, 0.275f, 0.1f)
            quadraticTo(0.1f, 0.5f, 0f, 0.5f)
            quadraticTo(-0.1f, 0.5f, -0.275f, 0.1f)
            quadraticTo(-0.45f, -0.28f, 0f, -0.5f)
            close()
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val elapsedSeconds = timeMillisState.longValue / 1000f
        val petalColor = if (colors.isDark) {
            Color(0xFFFFA7C8)
        } else {
            Color(0xFFFF84B7)
        }

        val petalTime = elapsedSeconds * 0.20f
        PETALS.forEachIndexed { index, petal ->
            val yVal = ((petal.initialY + petal.speedY * petalTime) % 1.2f) - 0.1f

            val sway = petal.swayAmp * kotlin.math.sin(petal.swayFreq * petalTime + petal.phaseOffset)
            val xVal = floatMod(petal.initialX + sway, 1.0f)

            val rotationDeg = petal.rotationSpeed * petalTime + (petal.phaseOffset * 50f)
            val scale3DX = kotlin.math.abs(
                kotlin.math.cos(petalTime * 2.5f + petal.phaseOffset),
            ).coerceAtLeast(0.12f)

            val petalLength = 28f + (index % 4) * 4f
            val petalWidth = 10f + (index % 3) * 1.8f

            withTransform({
                translate(
                    left = xVal * size.width,
                    top = yVal * size.height,
                )
                rotate(rotationDeg, pivot = Offset.Zero)
                scale(
                    scaleX = petal.scale * scale3DX * petalWidth,
                    scaleY = petal.scale * petalLength,
                    pivot = Offset.Zero,
                )
            }) {
                drawPath(
                    path = petalTemplatePath,
                    color = petalColor.copy(alpha = 0.22f + (0.06f * (index % 5))),
                )
            }
        }
    }
}

@Composable
private fun AuroraTrinityConstellationBackground(
    colors: eu.kanade.presentation.theme.AuroraColors,
    animate: Boolean,
) {
    val timeMillisState = rememberAuroraSpecialClockState(animate)
    val trianglePath = remember { Path() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val elapsedSeconds = timeMillisState.longValue / 1000f
        val center = Offset(size.width * 0.5f, size.height * 0.42f)
        val minDim = size.minDimension
        val time = if (animate) elapsedSeconds else 0f

        val orbitSize = 1.50f
        val orbitSpeed = 0.05f
        val coreSize = 0.78f
        val particles = 0.12f
        val energySpeed = 0.02f
        val spread = 0.35f
        val swirl = 0.11f
        val glow = 0.44f

        val orbitR = orbitSize * minDim * 0.28f
        val coreR = coreSize * minDim * 0.12f
        val baseAng = time * orbitSpeed * 0.6f
        val swirlFactor = swirl * 80f
        val spreadBase = spread * minDim * 0.15f

        val coreCyan = Color(0xFF64E8FF)
        val corePurple = Color(0xFF9C7CFF)
        val coreGold = Color(0xFFFFD36E)
        val accentColor = colors.accent

        fun srHash(seed: Float): Float {
            return kotlin.math.abs(kotlin.math.sin(seed * 127.1f + 311.7f) * 43758.5453f) % 1f
        }

        fun blendColor(a: Color, b: Color, t: Float): Color {
            val clamped = t.coerceIn(0f, 1f)
            val inv = 1f - clamped
            return Color(
                red = a.red * inv + b.red * clamped,
                green = a.green * inv + b.green * clamped,
                blue = a.blue * inv + b.blue * clamped,
                alpha = a.alpha * inv + b.alpha * clamped,
            )
        }

        val cores = listOf(
            Pair(coreCyan, baseAng),
            Pair(corePurple, baseAng + (2f * Math.PI.toFloat() / 3f)),
            Pair(coreGold, baseAng + (4f * Math.PI.toFloat() / 3f)),
        )

        val corePts = cores.map { (col, ang) ->
            Triple(
                center.x + kotlin.math.cos(ang) * orbitR,
                center.y + kotlin.math.sin(ang) * orbitR,
                col,
            )
        }

        // Swirling background stars
        FLOATING_STARS.forEachIndexed { i, star ->
            val seed = i + 1f
            val sxRaw = star.xFraction * size.width
            val syRaw = star.yFraction * size.height
            val dx = sxRaw - center.x
            val dy = syRaw - center.y
            val baseDist = kotlin.math.hypot(dx, dy)
            val baseAngle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()

            val swirlOff = (swirlFactor * minDim) / (baseDist + 100f)
            val ang = baseAngle + time * 0.08f * orbitSpeed * swirlOff

            val sx = center.x + kotlin.math.cos(ang) * baseDist
            val sy = center.y + kotlin.math.sin(ang) * baseDist

            val blink = 0.6f + 0.4f * kotlin.math.sin(time * 1.5f + star.phase)
            val starCol = when (i % 3) {
                0 -> coreCyan
                1 -> corePurple
                else -> coreGold
            }
            drawCircle(
                color = starCol.copy(alpha = (star.size * 0.15f * blink * glow).coerceIn(0f, 1f)),
                radius = (star.size * 1.2f).dp.toPx(),
                center = Offset(sx, sy),
            )
        }

        // Connective Triangle (Accent Color)
        trianglePath.reset()
        trianglePath.moveTo(corePts[0].first, corePts[0].second)
        trianglePath.lineTo(corePts[1].first, corePts[1].second)
        trianglePath.lineTo(corePts[2].first, corePts[2].second)
        trianglePath.close()
        drawPath(
            path = trianglePath,
            color = accentColor.copy(alpha = 0.16f * glow),
            style = Stroke(width = 1.5f.dp.toPx()),
        )

        // Energy Flow Particles
        val numParts = (particles * 450).toInt()
        repeat(numParts) { i ->
            val seed = i + 1f
            val phase = srHash(seed * 1.1f)
            val nx = (srHash(seed * 2.2f) + srHash(seed * 3.3f) + srHash(seed * 4.4f) - 1.5f) * 1.5f
            val ny = (srHash(seed * 5.5f) + srHash(seed * 6.6f) + srHash(seed * 7.7f) - 1.5f) * 1.5f
            val pSize = (0.8f + srHash(seed * 8.8f) * 1.8f).dp.toPx()
            val speedMod = 0.7f + srHash(seed * 9.9f) * 0.6f

            val t = (phase + time * energySpeed * 0.4f * speedMod) % 1f
            val segment = (t * 3f).toInt()
            val u = (t * 3f) % 1f

            val c1 = corePts[segment]
            val c2 = corePts[(segment + 1) % 3]

            val cpX = center.x + nx * spreadBase
            val cpY = center.y + ny * spreadBase

            val inv = 1f - u
            val px = inv * inv * c1.first + 2f * inv * u * cpX + u * u * c2.first
            val py = inv * inv * c1.second + 2f * inv * u * cpY + u * u * c2.second

            val baseCol = if (u > 0.5f) c2.third else c1.third
            val finalCol = blendColor(baseCol, accentColor, 0.4f + 0.2f * srHash(seed * 10.1f))
            val particleA = (0.2f + 0.8f * kotlin.math.sin(u * Math.PI.toFloat())) * glow

            drawCircle(
                color = finalCol.copy(alpha = particleA.coerceIn(0f, 1f)),
                radius = pSize,
                center = Offset(px, py),
            )
            drawCircle(
                color = finalCol.copy(alpha = (particleA * 0.3f).coerceIn(0f, 1f)),
                radius = pSize * 1.5f,
                center = Offset(px - (px - cpX) * 0.015f, py - (py - cpY) * 0.015f),
            )
        }

        // Draw The 3 Cores
        corePts.forEach { (cx, cy, col) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        col.copy(alpha = 0.4f * glow),
                        col.copy(alpha = 0.15f * glow),
                        col.copy(alpha = 0f),
                    ),
                    center = Offset(cx, cy),
                    radius = coreR * 3f,
                ),
                radius = coreR * 3f,
                center = Offset(cx, cy),
            )

            val innerCol = blendColor(col, accentColor, 0.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 1f * glow),
                        innerCol.copy(alpha = 0.9f * glow),
                        innerCol.copy(alpha = 0f),
                    ),
                    center = Offset(cx, cy),
                    radius = coreR,
                ),
                radius = coreR,
                center = Offset(cx, cy),
            )

            drawCircle(
                color = Color.Black.copy(alpha = 0.4f * glow),
                radius = coreR * 0.4f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f.dp.toPx()),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f * glow),
                radius = coreR * 0.2f,
                center = Offset(cx, cy),
                style = Stroke(width = 0.5f.dp.toPx()),
            )
        }

        // Center ambient glow with accent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.18f * glow),
                    coreCyan.copy(alpha = 0.04f * glow),
                    Color.Transparent,
                ),
                center = center,
                radius = orbitR * 1.5f,
            ),
            radius = orbitR * 1.5f,
            center = center,
        )
    }
}

@Composable
private fun AuroraEventHorizonLibraryBackground(
    colors: eu.kanade.presentation.theme.AuroraColors,
    animate: Boolean,
) {
    val timeMillisState = rememberAuroraSpecialClockState(animate)
    // RuntimeShader (AGSL) only exists on API 33+. Keep all references isolated in
    // AuroraEventHorizonShader so the class is never loaded/verified on older devices.
    val eventHorizon = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AuroraEventHorizonShader()
        } else {
            null
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val elapsedSeconds = timeMillisState.longValue / 1000f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            eventHorizon != null
        ) {
            // GPU path (Android 13+): one full-screen AGSL pass.
            val time = if (animate) elapsedSeconds else 0f
            with(eventHorizon) { drawEventHorizon(colors.accent, time) }
        } else {
            // Fallback (< Android 13): static OLED-black -> violet ember gradient.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black,
                        Color(0xFF1A0320),
                        Color(0xFF3A0640),
                    ),
                    center = center,
                    radius = size.minDimension * 0.7f,
                ),
            )
        }
    }
}

@Composable
private fun AuroraDeepSpaceArchiveBackground(
    colors: eu.kanade.presentation.theme.AuroraColors,
    animate: Boolean,
) {
    val timeMillisState = rememberAuroraSpecialClockState(animate)
    val facePath = remember { Path() }
    val shadowPath = remember { Path() }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val categoryTypeface = remember { Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }

    val booksCount = 11
    val variety = 0.84f
    val scatter = 0.53f
    val vertical = 1.15f
    val scalePreset = 1.07f
    val pitch = -8f * Math.PI.toFloat() / 180f
    val baseYaw = 13f * Math.PI.toFloat() / 180f
    val floatStrength = 1.44f
    val dustDensity = 0.78f
    val flowSpeed = 0.07f
    val glowStrength = 0.44f
    val accentStrength = 0.67f
    val cosYaw = kotlin.math.cos(baseYaw)
    val sinYaw = kotlin.math.sin(baseYaw)
    val cosPitch = kotlin.math.cos(pitch)
    val sinPitch = kotlin.math.sin(pitch)

    val books = remember {
        List(booksCount) { i ->
            val seed = i + 1f
            BookSpec(
                index = i,
                theme = i % 4,
                width = mix(0.62f, 0.98f, srHash(seed * 2.1f)) * mix(0.92f, 1.16f, variety),
                depth = mix(0.38f, 0.70f, srHash(seed * 3.7f)) * mix(0.88f, 1.12f, variety),
                height = mix(0.05f, 0.11f, srHash(seed * 4.2f)) * mix(0.88f, 1.10f, variety),
                x = (srHash(seed * 5.3f) - 0.5f) * 0.22f * scatter,
                z = (srHash(seed * 6.1f) - 0.5f) * 0.12f * scatter,
                y = (i - (booksCount - 1) * 0.5f) * 0.155f * vertical +
                    (srHash(seed * 7.4f) - 0.5f) * 0.022f * scatter,
                bobPhase = srHash(seed * 11.3f) * (2f * Math.PI.toFloat()),
                bobAmp = mix(0.010f, 0.026f, srHash(seed * 12.7f)),
            )
        }
    }

    val pageBright = 0.28f
    val pageTint = 0.46f + pageBright * 0.42f
    val archiveCyan = colors.glowEffect
    val archivePurple = colors.gradientPurple
    val archiveAccent = colors.accent
    val archivePink = Color(0xFFFF5BD0)
    val archiveGold = Color(0xFFFFD36E)
    val archiveWhite = Color(0xFFEAFDFF)
    val paperShadow = Color(0xFFBCAA82)

    val starSpecs = remember {
        Array(34) { i ->
            val seed = i + 1f
            DeepSpaceStarSpec(
                xFraction = srHash(seed * 1.9f),
                yFraction = srHash(seed * 4.1f),
                seed = seed,
                colorIndex = i % 4,
                baseAlpha = 0.03f + 0.07f * srHash(seed * 3.4f),
                radiusDp = 0.6f + srHash(seed * 5.2f) * 1.2f,
            )
        }
    }
    val particleCount = (42 + 96 * dustDensity).toInt()
    val particleSpecs = remember(particleCount) {
        Array(particleCount) { i ->
            val seed = i + 1f
            DeepSpaceParticleSpec(
                phaseBase = srHash(seed * 2.1f),
                speedFactor = (0.45f + srHash(seed * 3.3f) * 0.95f) * flowSpeed * 0.11f,
                angleOffset = srHash(seed * 4.7f) * 2f * Math.PI.toFloat(),
                radialPhase = srHash(seed * 6.4f) * 8f,
                tone = srHash(seed * 8.8f),
                radiusDp = 0.7f + srHash(seed * 9.9f) * 1.25f,
            )
        }
    }
    val facePool = remember {
        Array(booksCount * DEEP_SPACE_BOOK_FACES.size) { MutableFaceDraw() }
    }
    val shadowCorners = remember { Array(4) { MutableProjected() } }
    val shadowAlphas = remember {
        FloatArray(booksCount) { i -> 0.08f + 0.04f * srHash((i + 1f) * 1.7f) }
    }

    val bookColors = remember(colors) {
        val paperLight = Color(0xFFF2E8CB)
        val paperMid = Color(0xFFD8C9A2)
        val leatherBase = Color(0xFF2A1F36)
        val coverShadow = Color(0xFF171222)
        val coverShadow2 = Color(0xFF0E0A18)
        val bookBright = 0.26f

        fun themeBase(theme: Int): Color = when (theme % 4) {
            0 -> archiveCyan
            1 -> archivePurple
            2 -> archivePink
            else -> archiveGold
        }

        fun scaleColor(color: Color, factor: Float): Color {
            val f = factor.coerceAtLeast(0f)
            return Color(
                red = (color.red * f).coerceIn(0f, 1f),
                green = (color.green * f).coerceIn(0f, 1f),
                blue = (color.blue * f).coerceIn(0f, 1f),
                alpha = color.alpha,
            )
        }

        fun coverColor(book: BookSpec): Color {
            val base = themeBase(book.theme)
            val tintMix = when (book.theme % 4) {
                0 -> 0.10f
                1 -> 0.12f
                2 -> 0.18f
                else -> 0.08f
            } + 0.06f * srHash((book.index + 1f) * 4.7f)
            val brightness = 0.28f + bookBright * 0.42f + srHash((book.index + 1f) * 8.1f) * 0.06f
            return blendColor(scaleColor(base, brightness), archiveAccent, tintMix * accentStrength * 0.35f)
        }

        fun spineColor(book: BookSpec): Color {
            return blendColor(leatherBase, coverColor(book), 0.30f + 0.08f * srHash((book.index + 1f) * 2.9f))
        }

        books.map { book ->
            val cover = coverColor(book)
            val spine = spineColor(book)
            Triple(
                listOf(
                    scaleColor(paperLight, pageTint * 1.02f),
                    scaleColor(paperMid, pageTint),
                    scaleColor(
                        paperShadow,
                        pageTint * 0.96f,
                    ),
                ),
                listOf(scaleColor(spine, 0.82f), blendColor(spine, cover, 0.42f), scaleColor(spine, 0.62f)),
                listOf(blendColor(coverShadow, cover, 0.28f), cover, blendColor(coverShadow2, cover, 0.18f)),
            )
        }
    }
    val bookCoverColors = remember(bookColors) {
        Array(booksCount) { i -> bookColors[i].third[1] }
    }
    val bookMarkColors = remember(colors) {
        Array(4) { theme ->
            val base = when (theme % 4) {
                0 -> archiveCyan
                1 -> archivePurple
                2 -> archivePink
                else -> archiveGold
            }
            blendColor(base, archiveAccent, 0.14f * accentStrength)
        }
    }
    val scaledPaperShadow = remember(colors) {
        Color(
            red = (paperShadow.red * pageTint * 0.96f).coerceIn(0f, 1f),
            green = (paperShadow.green * pageTint * 0.96f).coerceIn(0f, 1f),
            blue = (paperShadow.blue * pageTint * 0.96f).coerceIn(0f, 1f),
            alpha = paperShadow.alpha,
        )
    }

    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val center = Offset(size.width * 0.5f, size.height * 0.52f)
                val minDim = size.minDimension
                val scale = minDim * 0.40f * scalePreset
                val faceStroke = Stroke(width = 1.0f.dp.toPx())
                val pageLineStrokeWidth = 0.45f.dp.toPx()
                val railStrokeWidth = 1.0f.dp.toPx()
                val coverLineStrokeWidth = 0.6f.dp.toPx()
                val pageStepPx = 6f.dp.toPx()
                val minSpineW = 30.dp.toPx()
                val minSpineH = 12.dp.toPx()
                val minTopW = 26.dp.toPx()
                val minTopH = 16.dp.toPx()
                val titleMinPx = 8.dp.toPx()
                val titleMaxPx = 14.dp.toPx()
                val catMinPx = 5.5f.dp.toPx()
                val catMaxPx = 10.dp.toPx()
                val topTitleMinPx = 6.dp.toPx()
                val topTitleMaxPx = 10.dp.toPx()
                val markMinPx = 10.dp.toPx()
                val markMaxPx = 18.dp.toPx()

                val backdropGlowBrush = Brush.radialGradient(
                    colors = listOf(
                        archivePurple.copy(alpha = 0.07f * glowStrength),
                        archiveCyan.copy(alpha = 0.03f * glowStrength),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = (minDim * 0.82f).coerceAtLeast(1f),
                )
                val foregroundGlowBrush = Brush.radialGradient(
                    colors = listOf(
                        archiveCyan.copy(alpha = 0.06f * glowStrength),
                        archivePurple.copy(alpha = 0.03f * glowStrength),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = (scale * 1.45f).coerceAtLeast(1f),
                )
                val starOffsets = Array(starSpecs.size) { i ->
                    Offset(size.width * starSpecs[i].xFraction, size.height * starSpecs[i].yFraction)
                }
                val starRadiiPx = FloatArray(starSpecs.size) { i -> starSpecs[i].radiusDp.dp.toPx() }
                val particleRadiiPx = FloatArray(particleSpecs.size) { i -> particleSpecs[i].radiusDp.dp.toPx() }
                val particleHeight = (size.height / (scale * 1.02f).coerceAtLeast(0.001f)).coerceAtLeast(3.0f) * 1.08f
                val particleScale = scale * 1.05f
                val shadowColorList = Array(booksCount) { i -> Color.Black.copy(alpha = shadowAlphas[i]) }

                fun projectCornerInto(
                    lx: Float,
                    ly: Float,
                    lz: Float,
                    book: BookSpec,
                    bobOffset: Float,
                    scaleFactor: Float,
                    out: MutableProjected,
                ) {
                    val rx = lx * cosYaw - lz * sinYaw + book.x
                    val ry = ly + book.y + bobOffset
                    val rz = lx * sinYaw + lz * cosYaw + book.z
                    val px = rx
                    val py = ry * cosPitch - rz * sinPitch
                    val pz = ry * sinPitch + rz * cosPitch
                    val camera = 30f
                    val persp = camera / (camera + pz)
                    out.x = center.x + px * scaleFactor * persp
                    out.y = center.y - py * scaleFactor * persp
                    out.z = pz
                    out.p = persp
                }

                fun projectFaceInto(
                    book: BookSpec,
                    face: String,
                    bobOffset: Float,
                    scaleFactor: Float,
                    corners: Array<MutableProjected>,
                ) {
                    val hw = book.width * 0.5f
                    val hh = book.height * 0.5f
                    val hd = book.depth * 0.5f
                    when (face) {
                        "top" -> {
                            projectCornerInto(-hw, hh, -hd, book, bobOffset, scaleFactor, corners[0])
                            projectCornerInto(hw, hh, -hd, book, bobOffset, scaleFactor, corners[1])
                            projectCornerInto(hw, hh, hd, book, bobOffset, scaleFactor, corners[2])
                            projectCornerInto(-hw, hh, hd, book, bobOffset, scaleFactor, corners[3])
                        }
                        "bottom" -> {
                            projectCornerInto(-hw, -hh, hd, book, bobOffset, scaleFactor, corners[0])
                            projectCornerInto(hw, -hh, hd, book, bobOffset, scaleFactor, corners[1])
                            projectCornerInto(hw, -hh, -hd, book, bobOffset, scaleFactor, corners[2])
                            projectCornerInto(-hw, -hh, -hd, book, bobOffset, scaleFactor, corners[3])
                        }
                        "front" -> {
                            projectCornerInto(-hw, -hh, hd, book, bobOffset, scaleFactor, corners[0])
                            projectCornerInto(hw, -hh, hd, book, bobOffset, scaleFactor, corners[1])
                            projectCornerInto(hw, hh, hd, book, bobOffset, scaleFactor, corners[2])
                            projectCornerInto(-hw, hh, hd, book, bobOffset, scaleFactor, corners[3])
                        }
                        "back" -> {
                            projectCornerInto(hw, -hh, -hd, book, bobOffset, scaleFactor, corners[0])
                            projectCornerInto(-hw, -hh, -hd, book, bobOffset, scaleFactor, corners[1])
                            projectCornerInto(-hw, hh, -hd, book, bobOffset, scaleFactor, corners[2])
                            projectCornerInto(hw, hh, -hd, book, bobOffset, scaleFactor, corners[3])
                        }
                        "right" -> {
                            projectCornerInto(hw, -hh, hd, book, bobOffset, scaleFactor, corners[0])
                            projectCornerInto(hw, -hh, -hd, book, bobOffset, scaleFactor, corners[1])
                            projectCornerInto(hw, hh, -hd, book, bobOffset, scaleFactor, corners[2])
                            projectCornerInto(hw, hh, hd, book, bobOffset, scaleFactor, corners[3])
                        }
                        else -> {
                            projectCornerInto(-hw, -hh, -hd, book, bobOffset, scaleFactor, corners[0])
                            projectCornerInto(-hw, -hh, hd, book, bobOffset, scaleFactor, corners[1])
                            projectCornerInto(-hw, hh, hd, book, bobOffset, scaleFactor, corners[2])
                            projectCornerInto(-hw, hh, -hd, book, bobOffset, scaleFactor, corners[3])
                        }
                    }
                }

                fun bilinear(corners: Array<MutableProjected>, u: Float, v: Float): Offset {
                    val x = corners[0].x + (corners[1].x - corners[0].x) * u + (corners[3].x - corners[0].x) * v
                    val y = corners[0].y + (corners[1].y - corners[0].y) * u + (corners[3].y - corners[0].y) * v
                    return Offset(x, y)
                }

                onDrawBehind {
                    val elapsedSeconds = timeMillisState.longValue / 1000f
                    val time = if (animate) elapsedSeconds else 0f

                    fun drawFaceText(
                        text: String,
                        position: Offset,
                        sizePx: Float,
                        rotationDeg: Float,
                        color: Color,
                        alpha: Float,
                        typeface: Typeface = Typeface.DEFAULT_BOLD,
                    ) {
                        if (sizePx < 4f || alpha <= 0f) return
                        textPaint.reset()
                        textPaint.flags = Paint.ANTI_ALIAS_FLAG
                        textPaint.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
                        textPaint.textAlign = Paint.Align.CENTER
                        textPaint.textSize = sizePx
                        textPaint.typeface = typeface
                        textPaint.setShadowLayer(
                            sizePx * 0.18f,
                            0f,
                            0f,
                            color.copy(alpha = alpha * 0.35f).toArgb(),
                        )

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.rotate(rotationDeg, position.x, position.y)
                        drawContext.canvas.nativeCanvas.drawText(
                            text,
                            position.x,
                            position.y + sizePx * 0.34f,
                            textPaint,
                        )
                        drawContext.canvas.nativeCanvas.restore()
                    }

                    fun drawBookFace(faceDraw: MutableFaceDraw) {
                        val book = faceDraw.book ?: return
                        val corners = faceDraw.corners
                        val cover = bookCoverColors[book.index]
                        facePath.reset()
                        facePath.moveTo(corners[0].x, corners[0].y)
                        facePath.lineTo(corners[1].x, corners[1].y)
                        facePath.lineTo(corners[2].x, corners[2].y)
                        facePath.lineTo(corners[3].x, corners[3].y)
                        facePath.close()

                        val vxx = corners[1].x - corners[0].x
                        val vxy = corners[1].y - corners[0].y
                        val vyx = corners[3].x - corners[0].x
                        val vyy = corners[3].y - corners[0].y
                        val faceWidthPx = kotlin.math.hypot(vxx, vxy)
                        val faceHeightPx = kotlin.math.hypot(vyx, vyy)
                        val axisDeg = (Math.atan2(vxy.toDouble(), vxx.toDouble()) * 180.0 / Math.PI).toFloat()
                        val near = ((0.30f - faceDraw.z) / 1.15f).coerceIn(0f, 1f)
                        val isSpine = faceDraw.face == "front"
                        val isCover = faceDraw.face == "top" || faceDraw.face == "bottom"
                        val isPageEdge = faceDraw.face == "back" ||
                            faceDraw.face == "left" ||
                            faceDraw.face == "right"

                        val colorsList = when {
                            isPageEdge -> bookColors[book.index].first
                            isSpine -> bookColors[book.index].second
                            else -> bookColors[book.index].third
                        }

                        val brush = Brush.linearGradient(
                            colors = colorsList,
                            start = Offset(corners[0].x, corners[0].y),
                            end = Offset(corners[2].x, corners[2].y),
                        )

                        drawPath(path = facePath, brush = brush)
                        drawPath(
                            path = facePath,
                            color = if (isPageEdge) {
                                scaledPaperShadow.copy(alpha = 0.24f + 0.06f * near)
                            } else {
                                cover.copy(alpha = 0.06f + 0.03f * near)
                            },
                        )
                        drawPath(
                            path = facePath,
                            color = if (isPageEdge) {
                                archiveWhite.copy(alpha = 0.07f + 0.03f * near)
                            } else {
                                cover.copy(alpha = 0.10f + 0.04f * near)
                            },
                            style = faceStroke,
                        )

                        if (isPageEdge) {
                            val longest = if (faceWidthPx > faceHeightPx) faceWidthPx else faceHeightPx
                            val count = kotlin.math.max(6, (longest / pageStepPx).toInt())
                            val lineColor = archiveWhite.copy(alpha = 0.08f + 0.04f * near)
                            repeat(count + 1) { i ->
                                val t = i / count.toFloat()
                                val a = if (faceWidthPx >= faceHeightPx) {
                                    bilinear(corners, 0.06f, 0.08f + t * 0.84f)
                                } else {
                                    bilinear(corners, 0.08f + t * 0.84f, 0.06f)
                                }
                                val b = if (faceWidthPx >= faceHeightPx) {
                                    bilinear(corners, 0.94f, 0.08f + t * 0.84f)
                                } else {
                                    bilinear(corners, 0.08f + t * 0.84f, 0.94f)
                                }
                                drawLine(
                                    color = lineColor,
                                    start = a,
                                    end = b,
                                    strokeWidth = pageLineStrokeWidth,
                                )
                            }
                        }

                        if (isSpine && faceWidthPx > minSpineW && faceHeightPx > minSpineH) {
                            val railColor = archiveGold.copy(alpha = 0.18f + accentStrength * 0.20f + near * 0.08f)
                            for (u in DEEP_SPACE_SPINE_RAILS) {
                                drawLine(
                                    color = railColor,
                                    start = bilinear(corners, u, 0.10f),
                                    end = bilinear(corners, u, 0.90f),
                                    strokeWidth = railStrokeWidth,
                                )
                            }
                            val titleSet = DEEP_SPACE_TITLE_SETS[book.theme]
                            val title = titleSet[book.index % titleSet.size]
                            val category = DEEP_SPACE_THEME_LABELS[book.theme]
                            drawFaceText(
                                text = title,
                                position = bilinear(corners, 0.50f, 0.52f),
                                sizePx = (faceHeightPx * 0.34f).coerceIn(titleMinPx, titleMaxPx),
                                rotationDeg = axisDeg,
                                color = archiveWhite,
                                alpha = 0.70f + 0.12f * near,
                            )
                            drawFaceText(
                                text = category,
                                position = bilinear(corners, 0.50f, 0.80f),
                                sizePx = (faceHeightPx * 0.18f).coerceIn(catMinPx, catMaxPx),
                                rotationDeg = axisDeg,
                                color = archiveGold,
                                alpha = 0.34f + 0.16f * accentStrength,
                                typeface = categoryTypeface,
                            )
                        }

                        if (faceDraw.face == "top" && faceWidthPx > minTopW && faceHeightPx > minTopH) {
                            val titleSet = DEEP_SPACE_TITLE_SETS[book.theme]
                            val title = titleSet[book.index % titleSet.size]
                            val marks = DEEP_SPACE_COVER_MARKS[book.theme]
                            drawFaceText(
                                text = title,
                                position = bilinear(corners, 0.52f, 0.26f),
                                sizePx = (faceHeightPx * 0.17f).coerceIn(topTitleMinPx, topTitleMaxPx),
                                rotationDeg = axisDeg,
                                color = archiveWhite,
                                alpha = 0.30f + 0.10f * near,
                            )
                            drawFaceText(
                                text = marks[book.index % marks.size],
                                position = bilinear(corners, 0.52f, 0.57f),
                                sizePx = (faceHeightPx * 0.36f).coerceIn(markMinPx, markMaxPx),
                                rotationDeg = axisDeg,
                                color = bookMarkColors[book.theme],
                                alpha = 0.20f + 0.10f * near,
                            )
                        }

                        if (isCover) {
                            drawLine(
                                color = archiveWhite.copy(alpha = 0.04f + 0.03f * near),
                                start = bilinear(corners, 0.12f, 0.18f),
                                end = bilinear(corners, 0.84f, 0.18f),
                                strokeWidth = coverLineStrokeWidth,
                            )
                        }
                    }

                    drawRect(brush = backdropGlowBrush)

                    for (i in starSpecs.indices) {
                        val spec = starSpecs[i]
                        val blink = 0.65f + 0.35f * kotlin.math.sin(time * 1.2f + spec.seed)
                        val starColor = when (spec.colorIndex) {
                            0 -> archiveCyan
                            1 -> archivePurple
                            2 -> archiveGold
                            else -> archiveWhite
                        }
                        drawCircle(
                            color = starColor.copy(alpha = spec.baseAlpha * blink),
                            radius = starRadiiPx[i],
                            center = starOffsets[i],
                        )
                    }

                    for (i in particleSpecs.indices) {
                        val spec = particleSpecs[i]
                        val loopT = (spec.phaseBase + time * spec.speedFactor) % 1f
                        val angle = loopT * 2f * Math.PI.toFloat() * 3.2f + spec.angleOffset
                        val radial = 0.76f + 0.22f * kotlin.math.sin(angle * 2.0f + spec.radialPhase)
                        val py = (loopT - 0.5f) * particleHeight
                        val px = radial * kotlin.math.cos(angle)
                        val pz = radial * kotlin.math.sin(angle)
                        val rotY = py * cosPitch - pz * sinPitch
                        val rotZ = py * sinPitch + pz * cosPitch
                        val persp = 30f / (30f + rotZ)
                        val prx = center.x + px * particleScale * persp
                        val pry = center.y - rotY * particleScale * persp
                        val tone = spec.tone
                        val color = when {
                            tone > 0.82f -> archiveGold
                            tone > 0.45f -> archiveCyan
                            else -> archivePurple
                        }
                        val alpha = (0.05f + 0.18f * persp) * glowStrength *
                            if (tone > 0.82f) (0.72f + 0.42f * accentStrength) else 1f
                        drawCircle(
                            color = color.copy(alpha = alpha.coerceIn(0f, 0.55f)),
                            radius = particleRadiiPx[i] * persp,
                            center = Offset(prx, pry),
                        )
                    }

                    var faceIdx = 0
                    for (bookIdx in books.indices) {
                        val book = books[bookIdx]
                        val bobOffset = kotlin.math.sin(time * 0.9f + book.bobPhase) * book.bobAmp * floatStrength
                        projectFaceInto(book, "bottom", bobOffset, scale, shadowCorners)
                        shadowPath.reset()
                        shadowPath.moveTo(shadowCorners[0].x, shadowCorners[0].y + minDim * 0.010f)
                        shadowPath.lineTo(shadowCorners[1].x, shadowCorners[1].y + minDim * 0.010f)
                        shadowPath.lineTo(shadowCorners[2].x, shadowCorners[2].y + minDim * 0.018f)
                        shadowPath.lineTo(shadowCorners[3].x, shadowCorners[3].y + minDim * 0.018f)
                        shadowPath.close()
                        drawPath(path = shadowPath, color = shadowColorList[bookIdx])

                        for (faceName in DEEP_SPACE_BOOK_FACES) {
                            val drawSlot = facePool[faceIdx++]
                            drawSlot.book = book
                            drawSlot.face = faceName
                            projectFaceInto(book, faceName, bobOffset, scale, drawSlot.corners)
                            drawSlot.z = (
                                drawSlot.corners[0].z +
                                    drawSlot.corners[1].z +
                                    drawSlot.corners[2].z +
                                    drawSlot.corners[3].z
                                ) * 0.25f
                        }
                    }
                    java.util.Arrays.sort(facePool, 0, faceIdx, DEEP_SPACE_FACE_COMPARATOR)
                    for (i in 0 until faceIdx) {
                        drawBookFace(facePool[i])
                    }

                    drawCircle(
                        brush = foregroundGlowBrush,
                        radius = scale * 1.45f,
                        center = center,
                    )
                }
            },
    )
}

@Composable
private fun AuroraShadowRealmBackground(
    colors: eu.kanade.presentation.theme.AuroraColors,
    animate: Boolean,
) {
    val timeMillisState = rememberAuroraSpecialClockState(animate)
    val lensPath = remember { Path() }
    val diskColors = remember(colors) {
        val diskBrightness = 0.96f
        val ghostLight = colors.glowEffect
        val realmAccent = colors.accent
        val photonWhite = Color(0xFFEAFDFF)
        List(18) { i ->
            val yNorm = i / 17f - 0.5f
            val alphaShape = (1f - kotlin.math.abs(yNorm) / 0.9f).coerceIn(0f, 1f)
            listOf(
                realmAccent.copy(alpha = 0f),
                realmAccent.copy(alpha = 0.05f * diskBrightness * alphaShape),
                ghostLight.copy(alpha = 0.15f * diskBrightness * alphaShape),
                photonWhite.copy(alpha = 0.42f * diskBrightness * alphaShape),
                ghostLight.copy(alpha = 0.16f * diskBrightness * alphaShape),
                realmAccent.copy(alpha = 0.05f * diskBrightness * alphaShape),
                realmAccent.copy(alpha = 0f),
            )
        }
    }

    val diskBrightness = 1.15f
    val ringStrength = 1.1f
    val particleCount = 64
    val particleSpeed = 0.05f
    val starDensity = 1.85f
    val gravityPull = 0.65f
    val realmSpeed = 0.7f

    val voidBlack = if (colors.isDark) Color(0xFF000000) else Color(0xFF151020)
    val horizonCoreAlpha = if (colors.isDark) 1f else 0.4f
    val horizonEdgeAlpha = if (colors.isDark) 0.95f else 0.2f
    val lightThemeRealmBoost = if (colors.isDark) 1f else 1.4f
    val photonWhite = Color(0xFFEAFDFF)
    val ghostLight = colors.glowEffect
    val shadowViolet = colors.gradientPurple
    val realmAccent = colors.accent

    val backgroundStarCount = (42 * starDensity).toInt().coerceAtLeast(24)
    val starSpecs = remember(backgroundStarCount) {
        Array(backgroundStarCount) { i ->
            val seed = i + 1f
            ShadowStarSpec(
                baseRadiusFactor = mix(0.18f, 0.78f, kotlin.math.sqrt(srHash(seed * 1.91f))),
                theta0 = srHash(seed * 3.17f) * 2f * Math.PI.toFloat(),
                seed = seed,
                baseAlpha = 0.08f + 0.16f * srHash(seed * 5.4f),
                mod11 = i % 11 == 0,
                mod5 = i % 5 == 0,
            )
        }
    }
    val particleSpecs = remember(particleCount) {
        Array(particleCount) { i ->
            val seed = i + 1f
            ShadowParticleSpec(
                band = srHash(seed * 9.1f),
                theta0 = srHash(seed * 3.7f) * 2f * Math.PI.toFloat(),
                dotRadiusSeed = srHash(seed * 5.3f),
                mod9 = i % 9 == 0,
                mod4 = i % 4 == 0,
            )
        }
    }

    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val center = Offset(size.width * 0.5f, size.height * 0.50f)
                val minDim = size.minDimension
                val holeRadius = minDim * 0.115f
                val diskRadius = minDim * 0.295f
                val lensHeight = holeRadius * 1.55f
                val diskThickness = holeRadius * 0.38f

                fun populateLensPath(path: Path, radius: Float, height: Float, sign: Float) {
                    path.reset()
                    path.moveTo(center.x - radius, center.y)
                    path.cubicTo(
                        center.x - radius * 0.62f,
                        center.y + sign * height * 0.95f,
                        center.x - radius * 0.34f,
                        center.y + sign * height * 1.12f,
                        center.x,
                        center.y + sign * height * 1.08f,
                    )
                    path.cubicTo(
                        center.x + radius * 0.34f,
                        center.y + sign * height * 1.12f,
                        center.x + radius * 0.62f,
                        center.y + sign * height * 0.95f,
                        center.x + radius,
                        center.y,
                    )
                }

                val upperArcBrush = Brush.linearGradient(
                    colors = listOf(
                        realmAccent.copy(alpha = 0.03f),
                        shadowViolet.copy(alpha = 0.15f * diskBrightness),
                        ghostLight.copy(alpha = 0.35f * diskBrightness),
                        photonWhite.copy(alpha = 0.45f * diskBrightness),
                        ghostLight.copy(alpha = 0.35f * diskBrightness),
                        shadowViolet.copy(alpha = 0.15f * diskBrightness),
                        realmAccent.copy(alpha = 0.05f),
                    ),
                    start = Offset(center.x - diskRadius, center.y - lensHeight),
                    end = Offset(center.x + diskRadius, center.y - lensHeight),
                )
                val lowerArcBrush = Brush.linearGradient(
                    colors = listOf(
                        realmAccent.copy(alpha = 0.02f),
                        shadowViolet.copy(alpha = 0.08f * diskBrightness),
                        ghostLight.copy(alpha = 0.18f * diskBrightness),
                        shadowViolet.copy(alpha = 0.08f * diskBrightness),
                        realmAccent.copy(alpha = 0.025f),
                    ),
                    start = Offset(center.x - diskRadius * 0.78f, center.y + lensHeight * 0.45f),
                    end = Offset(center.x + diskRadius * 0.78f, center.y + lensHeight * 0.45f),
                )
                val upperArcRadii = FloatArray(10) { layer -> diskRadius * mix(0.55f, 1.05f, layer / 9f) }
                val upperArcHeights = FloatArray(10) { layer -> lensHeight * mix(0.42f, 1.05f, layer / 9f) }
                val upperArcAlphas = FloatArray(10) { layer -> diskBrightness * mix(0.15f, 0.04f, layer / 9f) }
                val upperArcStrokes = Array(10) { layer ->
                    Stroke(width = diskThickness * mix(6.0f, 0.8f, layer / 9f), cap = StrokeCap.Round)
                }
                val lowerArcRadii = FloatArray(7) { layer -> diskRadius * mix(0.45f, 0.82f, layer / 6f) }
                val lowerArcHeights = FloatArray(7) { layer -> lensHeight * mix(0.20f, 0.52f, layer / 6f) }
                val lowerArcAlphas = FloatArray(7) { layer -> diskBrightness * mix(0.09f, 0.02f, layer / 6f) }
                val lowerArcStrokes = Array(7) { layer ->
                    Stroke(width = diskThickness * mix(4.2f, 0.7f, layer / 6f), cap = StrokeCap.Round)
                }

                val diskBrushes = arrayOfNulls<Brush>(22)
                val diskY = FloatArray(22)
                val diskHalfWidth = FloatArray(22)
                val diskHalfHeight = FloatArray(22)
                val diskAlpha = FloatArray(22)
                val diskWobbleAmp = 0.65f.dp.toPx()
                for (i in 0 until 22) {
                    val yNorm = i / 21f - 0.5f
                    val alphaShape = (1f - kotlin.math.abs(yNorm) / 0.85f).coerceIn(0f, 1f)
                    if (alphaShape > 0f) {
                        val y = center.y + yNorm * diskThickness * 1.8f
                        val halfWidth = diskRadius * mix(1.1f, 1.95f, alphaShape.pow(0.3f))
                        diskY[i] = y
                        diskHalfWidth[i] = halfWidth
                        diskHalfHeight[i] = (1.2f + 2f * alphaShape).dp.toPx()
                        diskAlpha[i] = alphaShape * 0.85f
                        diskBrushes[i] = Brush.linearGradient(
                            colors = diskColors[i % diskColors.size],
                            start = Offset(center.x - halfWidth, y),
                            end = Offset(center.x + halfWidth, y),
                        )
                    }
                }

                val eventHorizonBrush = Brush.radialGradient(
                    colors = listOf(
                        voidBlack.copy(alpha = horizonCoreAlpha),
                        voidBlack.copy(alpha = horizonCoreAlpha),
                        voidBlack.copy(alpha = horizonEdgeAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(center.x - holeRadius * 0.22f, center.y - holeRadius * 0.12f),
                    radius = (holeRadius * 1.4f).coerceAtLeast(1f),
                )
                val ringStroke1 = Stroke(width = 1.4f.dp.toPx())
                val ringStroke2 = Stroke(width = 1.0f.dp.toPx())
                val frontHalfWidth = diskRadius * 1.85f
                val frontWobbleAmp = 1.5f.dp.toPx()
                val frontStrokeWidth = 1.2f.dp.toPx()
                val frontGlintBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        ghostLight.copy(alpha = 0.08f * diskBrightness),
                        photonWhite.copy(alpha = 0.18f * diskBrightness),
                        ghostLight.copy(alpha = 0.08f * diskBrightness),
                        Color.Transparent,
                    ),
                    start = Offset(center.x - frontHalfWidth, center.y),
                    end = Offset(center.x + frontHalfWidth, center.y),
                )

                val starBaseRadii = FloatArray(starSpecs.size) { i -> minDim * starSpecs[i].baseRadiusFactor }
                val starNear = FloatArray(starSpecs.size) { i ->
                    (holeRadius * 3.6f / (starBaseRadii[i] + 1f)).coerceIn(0f, 1f)
                }
                val starOmega = FloatArray(starSpecs.size) { i ->
                    (0.025f + starNear[i] * 0.18f) * realmSpeed * gravityPull
                }
                val starColors = Array(starSpecs.size) { i ->
                    val spec = starSpecs[i]
                    val starAlpha = spec.baseAlpha * (0.75f + starNear[i] * 0.65f)
                    when {
                        spec.mod11 -> realmAccent.copy(alpha = starAlpha * 0.65f)
                        spec.mod5 -> shadowViolet.copy(alpha = starAlpha * 0.8f)
                        else -> ghostLight.copy(alpha = starAlpha)
                    }
                }
                val starHalfW = FloatArray(starSpecs.size) { i -> (0.9f + starNear[i] * 2.2f).dp.toPx() }
                val starHalfH = 0.45f.dp.toPx()
                val starSizes = Array(starSpecs.size) { i ->
                    androidx.compose.ui.geometry.Size(
                        width = (2.3f + starNear[i] * 5.2f).dp.toPx(),
                        height = 1.05f.dp.toPx(),
                    )
                }

                val particleOrbitRadius = FloatArray(particleSpecs.size) { i ->
                    holeRadius * mix(1.15f, 2.15f, particleSpecs[i].band.pow(0.85f))
                }
                val particleNear = FloatArray(particleSpecs.size) { i ->
                    (1f - (particleOrbitRadius[i] - holeRadius * 1.15f) / (holeRadius * 1.0f).coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                }
                val particleOmega = FloatArray(particleSpecs.size) { i ->
                    (0.85f + particleNear[i] * 2.8f) * particleSpeed * gravityPull
                }
                val particleDotRadii = FloatArray(particleSpecs.size) { i ->
                    (0.7f + particleNear[i] * 0.85f + particleSpecs[i].dotRadiusSeed * 0.65f).dp.toPx()
                }
                val particleBaseAlpha = FloatArray(particleSpecs.size) { i ->
                    (0.25f + 0.45f * particleNear[i]) * gravityPull
                }

                // Cache static cinematic grain once per layout size so 33ms redraws do 1 blit instead of 350 drawRects.
                val grainW = size.width.toInt().coerceIn(1, 512)
                val grainH = size.height.toInt().coerceIn(1, 512)
                val grainBitmap = if (size.width >= 1f && size.height >= 1f) {
                    val bmp = ImageBitmap(grainW, grainH)
                    val grainCanvas = androidx.compose.ui.graphics.Canvas(bmp)
                    val grainPaint = androidx.compose.ui.graphics.Paint()
                    val grainScaleX = grainW.toFloat() / size.width.coerceAtLeast(1f)
                    val grainScaleY = grainH.toFloat() / size.height.coerceAtLeast(1f)
                    val grainBoxW = (1.5f.dp.toPx() * grainScaleX).coerceAtLeast(1f)
                    val grainBoxH = (1.5f.dp.toPx() * grainScaleY).coerceAtLeast(1f)
                    val grainCount = 350
                    for (i in 0 until grainCount) {
                        val seed = i + 1f
                        val gx = grainW * srHash(seed * 7.1f)
                        val gy = grainH * srHash(seed * 8.3f)
                        val gTone = srHash(seed * 9.5f)
                        val gColor = when {
                            gTone > 0.6f -> shadowViolet
                            gTone > 0.3f -> realmAccent
                            else -> photonWhite
                        }
                        grainPaint.color = gColor.copy(alpha = 0.025f + 0.02f * srHash(seed * 2.4f))
                        grainCanvas.drawRect(gx, gy, gx + grainBoxW, gy + grainBoxH, grainPaint)
                    }
                    bmp
                } else {
                    null
                }

                onDrawBehind {
                    val elapsedSeconds = timeMillisState.longValue / 1000f
                    val pulseProgress = elapsedSeconds * (2f * Math.PI.toFloat() / 2.2f)
                    val pulse = if (animate) 0.85f + 0.15f * kotlin.math.sin(pulseProgress) else 0.85f
                    val time = if (animate) elapsedSeconds else 0f

                    // 1) Deep Void Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ghostLight.copy(alpha = 0.25f * pulse * lightThemeRealmBoost),
                                shadowViolet.copy(alpha = 0.12f * lightThemeRealmBoost),
                                realmAccent.copy(alpha = 0.05f * lightThemeRealmBoost),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = (holeRadius * 5.8f).coerceAtLeast(1f),
                        ),
                        radius = holeRadius * 5.8f,
                        center = center,
                    )

                    // 2) Background stars: orbital swirl
                    for (i in starSpecs.indices) {
                        val spec = starSpecs[i]
                        val near = starNear[i]
                        val theta = spec.theta0 + time * starOmega[i]
                        val breathe = 1f - gravityPull * near * 0.08f *
                            (0.6f + 0.4f * kotlin.math.sin(time * 0.6f + spec.seed))
                        val radialScale = starBaseRadii[i] * breathe * (1f + near * 0.18f * gravityPull)
                        val x = center.x + kotlin.math.cos(theta) * radialScale
                        val y = center.y + kotlin.math.sin(theta) * radialScale

                        withTransform({ rotate(theta * 57.29578f + 90f, Offset(x, y)) }) {
                            drawOval(
                                color = starColors[i],
                                topLeft = Offset(x - starHalfW[i], y - starHalfH),
                                size = starSizes[i],
                            )
                        }
                    }

                    // 3) Lensed upper and lower arcs (Volumetric Plasma Glow)
                    for (layer in 0 until 10) {
                        populateLensPath(lensPath, upperArcRadii[layer], upperArcHeights[layer], -1f)
                        drawPath(
                            path = lensPath,
                            brush = upperArcBrush,
                            alpha = upperArcAlphas[layer],
                            style = upperArcStrokes[layer],
                        )
                    }

                    for (layer in 0 until 7) {
                        populateLensPath(lensPath, lowerArcRadii[layer], lowerArcHeights[layer], 1f)
                        drawPath(
                            path = lensPath,
                            brush = lowerArcBrush,
                            alpha = lowerArcAlphas[layer],
                            style = lowerArcStrokes[layer],
                        )
                    }

                    // 4) Continuous accretion plane - Organically breathing and volumetric
                    for (i in 0 until 22) {
                        val diskBrush = diskBrushes[i] ?: continue
                        val wobble = (
                            kotlin.math.sin(i * 0.5f + time * realmSpeed * 0.7f) +
                                kotlin.math.cos(i * 0.9f - time * realmSpeed * 1.3f) * 0.5f
                            ) * diskWobbleAmp
                        val halfW = diskHalfWidth[i]
                        val halfH = diskHalfHeight[i]
                        drawOval(
                            brush = diskBrush,
                            topLeft = Offset(center.x - halfW, diskY[i] + wobble - halfH),
                            size = androidx.compose.ui.geometry.Size(halfW * 2f, halfH * 2f),
                            alpha = diskAlpha[i],
                        )
                    }

                    // 5) Local particles (dust)
                    for (i in particleSpecs.indices) {
                        val spec = particleSpecs[i]
                        val orbitRadius = particleOrbitRadius[i]
                        val theta = spec.theta0 - time * particleOmega[i]
                        val x = center.x + orbitRadius * kotlin.math.cos(theta)
                        val y = center.y + orbitRadius * kotlin.math.sin(theta)
                        if (kotlin.math.hypot(x - center.x, y - center.y) > holeRadius * 1.08f) {
                            val depth = 0.72f + 0.28f * kotlin.math.sin(theta)
                            val alpha = particleBaseAlpha[i] * depth
                            val particleColor = when {
                                spec.mod9 -> realmAccent.copy(alpha = alpha * 0.8f)
                                spec.mod4 -> shadowViolet.copy(alpha = alpha * 0.9f)
                                else -> ghostLight.copy(alpha = alpha)
                            }
                            val dotRadius = particleDotRadii[i]
                            val pos = Offset(x, y)

                            drawCircle(
                                color = particleColor.copy(alpha = particleColor.alpha * 0.35f),
                                radius = dotRadius * 3.2f,
                                center = pos,
                            )
                            drawCircle(
                                color = particleColor,
                                radius = dotRadius,
                                center = pos,
                            )
                            drawCircle(
                                color = photonWhite.copy(alpha = alpha * 0.45f),
                                radius = dotRadius * 0.42f,
                                center = pos,
                            )
                        }
                    }

                    // 6) Event horizon: true black singularity with deep soft shadow edge
                    drawCircle(
                        brush = eventHorizonBrush,
                        radius = holeRadius * 1.4f,
                        center = center,
                    )
                    drawCircle(
                        color = voidBlack.copy(alpha = horizonCoreAlpha),
                        radius = holeRadius * 0.93f,
                        center = center,
                    )

                    // 7) Continuous photon ring + secondary rim with intense cinematic glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                photonWhite.copy(alpha = 0.55f * ringStrength * pulse * lightThemeRealmBoost),
                                ghostLight.copy(alpha = 0.35f * ringStrength * lightThemeRealmBoost),
                                shadowViolet.copy(alpha = 0.15f * ringStrength * lightThemeRealmBoost),
                                realmAccent.copy(alpha = 0.05f * ringStrength * lightThemeRealmBoost),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = (holeRadius * 1.45f).coerceAtLeast(1f),
                        ),
                        radius = holeRadius * 1.45f,
                        center = center,
                    )
                    drawCircle(
                        color = photonWhite.copy(alpha = 0.75f * ringStrength * pulse * lightThemeRealmBoost),
                        radius = holeRadius * 1.05f,
                        center = center,
                        style = ringStroke1,
                    )
                    drawCircle(
                        color = ghostLight.copy(alpha = 0.35f * ringStrength * lightThemeRealmBoost),
                        radius = holeRadius * 1.18f,
                        center = center,
                        style = ringStroke2,
                    )

                    // 8) A very thin front disk glint, organic wobble
                    val frontWobble = kotlin.math.sin(time * realmSpeed * 1.2f) * frontWobbleAmp
                    drawLine(
                        brush = frontGlintBrush,
                        start = Offset(center.x - frontHalfWidth, center.y + frontWobble),
                        end = Offset(center.x + frontHalfWidth, center.y - frontWobble),
                        strokeWidth = frontStrokeWidth,
                        cap = StrokeCap.Round,
                    )

                    // 9) Cinematic Grain Overlay (cached bitmap blit)
                    if (grainBitmap != null && size.width >= 1f && size.height >= 1f) {
                        drawImage(
                            image = grainBitmap,
                            dstSize = IntSize(
                                size.width.toInt().coerceAtLeast(1),
                                size.height.toInt().coerceAtLeast(1),
                            ),
                        )
                    }
                }
            },
    )
}

@Composable
private fun AuroraNeonOrbitBackground(
    colors: eu.kanade.presentation.theme.AuroraColors,
    animate: Boolean,
) {
    val timeMillisState = rememberAuroraSpecialClockState(animate)
    val steps = 48
    val ringsCount = 8
    val rings = remember(colors) {
        val cyan = Color(0xFF49E6FF)
        val purple = Color(0xFF9B8CFF)
        val gold = Color(0xFFFFD36E)
        val pink = Color(0xFFFF5BD0)
        val accentColor = colors.accent
        val accentStrength = 0.57f

        fun neonRingColor(seed: Float): Color {
            val colorSeed = srHash(seed * 7f)
            val base = when {
                colorSeed > 0.90f -> pink
                colorSeed > 0.75f -> gold
                colorSeed > 0.40f -> purple
                else -> cyan
            }
            return when (base) {
                gold -> blendColor(gold, accentColor, accentStrength * 0.42f)
                pink -> blendColor(pink, accentColor, accentStrength * 0.24f)
                else -> base
            }
        }

        List(ringsCount) { i ->
            val seed = i * 17.3f
            NeonRingSpec(
                pitchBase = srHash(seed) * Math.PI.toFloat() * 2f,
                yawBase = srHash(seed * 2f) * Math.PI.toFloat() * 2f,
                rollBase = srHash(seed * 3f) * Math.PI.toFloat() * 2f,
                spinPitch = (srHash(seed * 5f) - 0.5f) * 1.5f,
                spinYaw = (srHash(seed * 6f) - 0.5f) * 1.5f,
                radiusFraction = 0.60f + srHash(seed * 4f) * 0.60f,
                color = neonRingColor(seed),
                seed = seed,
            )
        }
    }
    val segmentPool = remember(steps, ringsCount) {
        MutableList(steps * ringsCount) { NeonSegment() }
    }
    val segmentComparator = remember { compareBy<NeonSegment> { it.z } }
    val packetPool = remember {
        MutableList(40) { NeonPacket() }
    }
    val packetComparator = remember { compareBy<NeonPacket> { it.z } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val elapsedSeconds = timeMillisState.longValue / 1000f
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val minDim = size.minDimension
        val time = if (animate) elapsedSeconds else 0f

        // NEON_DYSON_V1 preset from the approved HTML prototype.
        val sphereScale = 0.90f
        val spinSpeed = 0.11f
        val packetDensity = 0.19f
        val dataSpeed = 0.53f * 0.35f // 65% slower packet motion.
        val cameraPreset = 0.45f
        val coreSize = 0.93f
        val coreGlow = if (colors.isDark) 0.62f else 0.50f
        val glowStrength = if (colors.isDark) 0.55f else 0.74f
        val accentStrength = 0.57f
        val themeLineBoost = if (colors.isDark) 1f else 2.05f
        val themePacketBoost = if (colors.isDark) 1f else 1.45f
        val themeCoreInkAlpha = if (colors.isDark) 0.78f else 0.22f

        val cyan = Color(0xFF49E6FF)
        val purple = Color(0xFF9B8CFF)
        val gold = Color(0xFFFFD36E)
        val pink = Color(0xFFFF5BD0)
        val white = Color(0xFFEAFDFF)
        val accentColor = colors.accent

        fun neonPacketColor(seed: Float): Color {
            val orbitIndex = (seed / 17.3f).toInt() % 4
            return when (orbitIndex) {
                0 -> blendColor(accentColor, white, 0.14f)
                1 -> blendColor(cyan, accentColor, 0.10f)
                2 -> blendColor(purple, white, 0.08f)
                else -> blendColor(gold, accentColor, 0.16f)
            }
        }

        val scale = minDim * 0.40f * sphereScale
        val camDist = cameraPreset * 3f + 1f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    purple.copy(alpha = 0.05f * glowStrength * themeLineBoost),
                    cyan.copy(alpha = 0.02f * glowStrength * themeLineBoost),
                    Color.Transparent,
                ),
                center = center,
                radius = scale * 1.20f,
            ),
            radius = scale * 1.20f,
            center = center,
        )

        // Populate segment pool
        var segmentIndex = 0
        rings.forEach { ring ->
            val ringTime = time * spinSpeed
            val pitchNow = ring.pitchBase + ringTime * ring.spinPitch
            val yawNow = ring.yawBase + ringTime * ring.spinYaw
            val rollNow = ring.rollBase
            val ringRadius = ring.radiusFraction * scale

            val cr = kotlin.math.cos(rollNow)
            val sr = kotlin.math.sin(rollNow)
            val cp = kotlin.math.cos(pitchNow)
            val sp = kotlin.math.sin(pitchNow)
            val cy = kotlin.math.cos(yawNow)
            val sy = kotlin.math.sin(yawNow)

            // Compute for theta = 0
            val rx0 = ringRadius * cr
            val ry0 = ringRadius * sr
            val py0 = ry0 * cp
            val pz0 = ry0 * sp
            val firstX = rx0 * cy - pz0 * sy
            val firstY = py0
            val firstZ = rx0 * sy + pz0 * cy

            var prevX = firstX
            var prevY = firstY
            var prevZ = firstZ

            for (j in 1..steps) {
                val theta = (j % steps / steps.toFloat()) * Math.PI.toFloat() * 2f
                val currX: Float
                val currY: Float
                val currZ: Float
                if (j == steps) {
                    currX = firstX
                    currY = firstY
                    currZ = firstZ
                } else {
                    val lx = kotlin.math.cos(theta) * ringRadius
                    val ly = kotlin.math.sin(theta) * ringRadius
                    val rx = lx * cr - ly * sr
                    val ry = lx * sr + ly * cr
                    val py = ry * cp
                    val pz = ry * sp
                    currX = rx * cy - pz * sy
                    currY = py
                    currZ = rx * sy + pz * cy
                }

                val seg = segmentPool[segmentIndex++]
                seg.x1 = prevX
                seg.y1 = prevY
                seg.z1 = prevZ
                seg.x2 = currX
                seg.y2 = currY
                seg.z2 = currZ
                seg.z = (prevZ + currZ) * 0.5f
                seg.color = ring.color
                seg.alphaMod = 1f
                seg.widthMod = 1f

                prevX = currX
                prevY = currY
                prevZ = currZ
            }
        }

        // Populate packet pool
        var packetIndex = 0
        rings.forEach { ring ->
            val ringTime = time * spinSpeed
            val pitchNow = ring.pitchBase + ringTime * ring.spinPitch
            val yawNow = ring.yawBase + ringTime * ring.spinYaw
            val rollNow = ring.rollBase
            val ringRadius = ring.radiusFraction * scale

            val cr = kotlin.math.cos(rollNow)
            val sr = kotlin.math.sin(rollNow)
            val cp = kotlin.math.cos(pitchNow)
            val sp = kotlin.math.sin(pitchNow)
            val cy = kotlin.math.cos(yawNow)
            val sy = kotlin.math.sin(yawNow)

            val packetCount = (packetDensity * 15f * ring.radiusFraction).toInt().coerceAtLeast(1)
            repeat(packetCount) { k ->
                val pSeed = ring.seed + k * 11.1f
                var packetSpeed = (0.5f + srHash(pSeed) * 1.5f) * dataSpeed
                if (srHash(pSeed * 2f) > 0.5f) packetSpeed *= -1f
                var currentTheta =
                    (srHash(pSeed * 3f) * Math.PI.toFloat() * 2f + time * packetSpeed) %
                        (Math.PI.toFloat() * 2f)
                if (currentTheta < 0f) currentTheta += Math.PI.toFloat() * 2f

                val lx = kotlin.math.cos(currentTheta) * ringRadius
                val ly = kotlin.math.sin(currentTheta) * ringRadius

                val rx = lx * cr - ly * sr
                val ry = lx * sr + ly * cr

                val py = ry * cp
                val pz = ry * sp

                val ox = rx * cy - pz * sy
                val oy = py
                val oz = rx * sy + pz * cy

                if (packetIndex < packetPool.size) {
                    val packet = packetPool[packetIndex++]
                    packet.x = ox
                    packet.y = oy
                    packet.z = oz
                    packet.color = neonPacketColor(ring.seed)
                    packet.sizeMod = 0.82f + srHash(pSeed * 4f) * 0.36f
                }
            }
        }

        // Sort Pools In-Place (zero allocations)
        val activeSegments = segmentPool.subList(0, segmentIndex)
        activeSegments.sortWith(segmentComparator)

        val activePackets = packetPool.subList(0, packetIndex)
        activePackets.sortWith(packetComparator)

        // Inline drawing functions to avoid local method allocations
        fun drawNeonSegment(segment: NeonSegment) {
            val cameraScale = camDist * scale
            val cz1 = (segment.z1 + cameraScale).coerceAtLeast(0.01f)
            val p1p = cameraScale / cz1
            val p1x = center.x + segment.x1 * p1p
            val p1y = center.y - segment.y1 * p1p

            val cz2 = (segment.z2 + cameraScale).coerceAtLeast(0.01f)
            val p2p = cameraScale / cz2
            val p2x = center.x + segment.x2 * p2p
            val p2y = center.y - segment.y2 * p2p

            val pMid = (p1p + p2p) * 0.5f
            val width = pMid * segment.widthMod
            val depthAlpha = ((pMid - 0.3f) * 1.2f).coerceIn(0.05f, 1f)
            val alpha = depthAlpha * glowStrength * segment.alphaMod * 0.12f * themeLineBoost
            if (alpha < 0.01f) return
            drawLine(
                color = segment.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                start = Offset(p1x, p1y),
                end = Offset(p2x, p2y),
                strokeWidth = width.dp.toPx(),
                cap = StrokeCap.Butt,
            )
        }

        fun drawNeonPacket(packet: NeonPacket) {
            val cameraScale = camDist * scale
            val cz = (packet.z + cameraScale).coerceAtLeast(0.01f)
            val pP = cameraScale / cz
            val pX = center.x + packet.x * pP
            val pY = center.y - packet.y * pP

            val depthAlpha = ((pP - 0.3f) * 1.2f).coerceIn(0.08f, 1f)
            val alpha = (depthAlpha * glowStrength * 0.78f * themePacketBoost).coerceIn(0f, 1f)
            if (alpha < 0.01f) return

            val radius = (pP * 2.35f * packet.sizeMod).dp.toPx()
            val centerPoint = Offset(pX, pY)

            drawCircle(
                color = packet.color.copy(alpha = alpha * 0.16f),
                radius = radius * 2.8f,
                center = centerPoint,
            )
            drawCircle(
                color = packet.color.copy(alpha = alpha * 0.58f),
                radius = radius * 1.15f,
                center = centerPoint,
            )
            drawCircle(
                color = white.copy(alpha = alpha * 0.52f),
                radius = radius * 0.42f,
                center = centerPoint,
            )
        }

        activeSegments.forEach { if (it.z >= 0f) drawNeonSegment(it) }
        activePackets.forEach { if (it.z >= 0f) drawNeonPacket(it) }

        val coreRadius = coreSize * minDim * 0.07f
        val reactorGlow = coreGlow * glowStrength
        if (coreRadius > 0.5f) {
            // Soft reactor bloom underlay so the center reads as glowing.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        white.copy(alpha = 0.42f * reactorGlow),
                        gold.copy(alpha = 0.26f * reactorGlow),
                        accentColor.copy(alpha = 0.12f * reactorGlow),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = coreRadius * 2.7f,
                ),
                radius = coreRadius * 2.7f,
                center = center,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        gold.copy(alpha = 0.10f * reactorGlow),
                        pink.copy(alpha = 0.07f * reactorGlow * accentStrength),
                        cyan.copy(alpha = 0.035f * reactorGlow),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = coreRadius * 5.2f,
                ),
                radius = coreRadius * 5.2f,
                center = center,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        white.copy(alpha = 0.55f * reactorGlow),
                        gold.copy(alpha = 0.36f * reactorGlow),
                        blendColor(pink, accentColor, accentStrength * 0.35f).copy(alpha = 0.16f * reactorGlow),
                        Color(0xFF2A2140).copy(alpha = themeCoreInkAlpha),
                        cyan.copy(alpha = 0.22f * reactorGlow),
                    ),
                    center = center,
                    radius = coreRadius * 1.25f,
                ),
                radius = coreRadius * 1.25f,
                center = center,
            )
            withTransform({ rotate(time * 10.3f, pivot = center) }) {
                drawCircle(
                    color = white.copy(alpha = 0.16f * reactorGlow),
                    radius = coreRadius * 1.75f,
                    center = center,
                    style = Stroke(width = 0.8f.dp.toPx()),
                )
            }
            withTransform({ rotate(-time * 17.8f, pivot = center) }) {
                drawCircle(
                    color = cyan.copy(alpha = 0.12f * reactorGlow),
                    radius = coreRadius * 1.15f,
                    center = center,
                    style = Stroke(width = 0.7f.dp.toPx()),
                )
            }
        }

        activeSegments.forEach { if (it.z < 0f) drawNeonSegment(it) }
        activePackets.forEach { if (it.z < 0f) drawNeonPacket(it) }
    }
}

internal fun shouldAnimateAuroraBackground(
    userEnabled: Boolean,
    isLifecycleResumed: Boolean,
    systemAnimationsEnabled: Boolean,
): Boolean {
    return userEnabled && isLifecycleResumed && systemAnimationsEnabled
}

@Composable
fun AuroraHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    // Reusable header component if needed, currently TopBars are custom per screen
}
