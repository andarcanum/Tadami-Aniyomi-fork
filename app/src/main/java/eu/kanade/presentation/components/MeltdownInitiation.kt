package eu.kanade.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/* =============================================================================
 *  ШАГ 1 — ИНИЦИАЦИЯ (Поиск "third impact" / "третий удар").
 *
 *  Единый хост для всех трёх экранов глобального поиска (manga/anime/novel).
 *  Оборачивает контент экрана и проигрывает сценарий:
 *
 *   Фаза CORRUPTING (~1с): резкий джолт -> эскалирующее "землетрясение" +
 *                       нарастающий глитч (без терминала).
 *   Фаза TERMINAL:        глитч утихает до фонового, всплывает терминал
 *                       (CRT power-on + скрембл-печать).
 *   Подтверждение  -> onAcknowledged() (экран выставляет meltdownStage = 1).
 * ========================================================================== */

private enum class InitiationPhase { Idle, Corrupting, Terminal }

@Composable
fun MeltdownInitiationHost(
    triggered: Boolean,
    onAcknowledged: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var phase by remember { mutableStateOf(InitiationPhase.Idle) }
    val time by rememberGlitchTime()
    val intensity = remember { Animatable(0f) }

    val scope = rememberCoroutineScope()
    var isShuttingDown by remember { mutableStateOf(false) }
    val shutdownAnim = remember { Animatable(0f) }

    LaunchedEffect(triggered) {
        if (triggered && phase == InitiationPhase.Idle) {
            phase = InitiationPhase.Corrupting
            // резкий первичный джолт, затем удержание высокой интенсивности
            intensity.snapTo(0.9f)
            intensity.animateTo(0.6f, tween(1000))
            delay(2000)
            // глитч утихает до фонового уровня под терминал
            intensity.animateTo(0.4f, tween(800))
            phase = InitiationPhase.Terminal
        }
    }

    // Интенсивность глитча: во время выключения резко нарастает до максимума
    val currentIntensity = if (isShuttingDown) {
        intensity.value + shutdownAnim.value * (1f - intensity.value)
    } else {
        if (phase == InitiationPhase.Idle) 0f else intensity.value
    }

    // Амплитуда тряски зависит от фазы (во время выключения тряска усиливается)
    val quakeAmp = when {
        isShuttingDown -> (intensity.value + shutdownAnim.value * (1.5f - intensity.value)) * 1.8f
        phase == InitiationPhase.Corrupting -> intensity.value * 1.4f
        phase == InitiationPhase.Terminal -> intensity.value * 0.35f
        else -> 0f
    }
    val quake = quakeOffset(time, quakeAmp)
    val collapseActive = isShuttingDown && shutdownAnim.value > 0.60f

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(quake.x.roundToInt(), quake.y.roundToInt())
                }
                .crtCollapse(active = collapseActive, durationMs = 2000),
        ) {
            GlitchStack(
                intensity = currentIntensity,
                config = if (isShuttingDown) {
                    GlitchConfig(
                        bloodVignette = false,
                        bloodDrips = false,
                        chromaticAberration = true,
                        blockDisplacement = true,
                        scanlines = true,
                        staticNoise = true,
                        flicker = true,
                    )
                } else {
                    MeltdownPresets.Initiation
                },
                modifier = Modifier.graphicsLayer {
                    if (isShuttingDown) {
                        alpha = (1f - shutdownAnim.value * 5f).coerceAtLeast(0f)
                    }
                },
            ) {
                content()
            }
        }

        if (isShuttingDown) {
            TvShutdownOverlay(
                progress = shutdownAnim.value,
                time = time,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (phase == InitiationPhase.Terminal && !isShuttingDown) {
            val titleStr = stringResource(AYMR.strings.meltdown_initiation_title)
            val isRussian = remember(titleStr) { titleStr.contains(Regex("[а-яА-Я]")) }
            val phraseWords = remember(isRussian) {
                if (isRussian) {
                    listOf(
                        "ДЛЯ", "СБРОСА", "ОХЛАЖДЕНИЯ", "СПУСТИСЬ", "В",
                        "ДОПОЛНИТЕЛЬНЫЙ", "ИНЖЕНЕРНЫЙ", "ОТСЕК", "СИСТЕМНОГО",
                        "УПРАВЛЕНИЯ", "И", "НАСТРОЙКИ"
                    )
                } else {
                    listOf(
                        "TO", "RESET", "COOLING", "DESCEND", "INTO",
                        "THE", "ADVANCED", "ENGINEERING", "SECTION",
                        "OF", "SYSTEM", "SETTINGS"
                    )
                }
            }

            CouncilCodeLockDialog(
                title = titleStr,
                briefing = stringResource(AYMR.strings.meltdown_initiation_message),
                words = phraseWords,
                buttonText = stringResource(AYMR.strings.meltdown_initiation_button),
                onConfirm = {
                    isShuttingDown = true
                    scope.launch {
                        shutdownAnim.animateTo(1f, tween(5000, easing = LinearEasing))
                        phase = InitiationPhase.Idle
                        isShuttingDown = false
                        shutdownAnim.snapTo(0f)
                        onAcknowledged()
                    }
                },
                onDismiss = {
                    isShuttingDown = true
                    scope.launch {
                        shutdownAnim.animateTo(1f, tween(1000, easing = LinearEasing))
                        phase = InitiationPhase.Idle
                        isShuttingDown = false
                        shutdownAnim.snapTo(0f)
                        onDismiss()
                    }
                },
                progressiveReveal = true,
            )
        }
    }
}

@Composable
private fun TvShutdownOverlay(progress: Float, time: Float, modifier: Modifier = Modifier) {
    val noise = remember { createNoiseBitmap(160, 160) }
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        // Черный фон под ТВ-помехами
        drawRect(color = androidx.compose.ui.graphics.Color.Black)

        val rnd = java.util.Random((time * 60f).toLong())
        val dx = rnd.nextFloat() * 40f
        val dy = rnd.nextFloat() * 40f
        var x = -dx
        val staticAlpha = (progress * 0.95f).coerceIn(0f, 0.95f)
        if (staticAlpha > 0f) {
            // Тонируем белый ТВ-шум в красно-бордовый цвет
            val filter = androidx.compose.ui.graphics.ColorFilter.tint(
                androidx.compose.ui.graphics.Color(0xFF5A000A), // темно-бордовый
                androidx.compose.ui.graphics.BlendMode.Screen,
            )
            while (x < size.width) {
                var y = -dy
                while (y < size.height) {
                    drawImage(
                        image = noise,
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        alpha = staticAlpha,
                        colorFilter = filter,
                    )
                    y += noise.height
                }
                x += noise.width
            }
        }

        // Горизонтальные красно-бордовые аналоговые полосы помех
        val lines = (progress * 14f).toInt()
        repeat(lines) {
            val h = rnd.nextFloat() * 20f + 2f
            val y = rnd.nextFloat() * size.height
            val a = rnd.nextFloat() * 0.40f * progress
            drawRect(
                color = androidx.compose.ui.graphics.Color(0xFF8B0016).copy(alpha = a),
                topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                size = androidx.compose.ui.geometry.Size(size.width, h),
            )
        }
    }
}
