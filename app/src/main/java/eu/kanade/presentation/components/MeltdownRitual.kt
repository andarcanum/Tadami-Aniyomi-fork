package eu.kanade.presentation.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Path as AndroidPath

/* =============================================================================
 *  ШАГ 3 — АППАРАТНЫЙ РИТУАЛ "В темноте" (читалка).
 *
 *  Визуальные слои финального meltdown на базе GlitchStack:
 *
 *   • MeltdownSwipeGlitch — эскалация по свайпам (1..5): каждый свайп в
 *     пустоту поднимает интенсивность глитча (swipeIntensity) и затем
 *     медленно оседает до фонового уровня. НЕ перехватывает касания
 *     (нет pointerInput/clickable) — чтобы можно было продолжать свайпать.
 *   • VoidRitualGlitch — полный meltdown (intensity -> 1f) под терминалом "Принять
 *     пустоту?".
 *   • VoidCollapseOverlay — экран схлопывается в красную CRT-точку (crtCollapse).
 *
 *  Символы движка (GlitchStack, MeltdownPresets, GlitchPalette, crtCollapse)
 *  лежат в этом же пакете — импорт не нужен.
 * ========================================================================== */

/**
 * Эскалирующий глитч поверх читалки на каждый свайп в пустоту.
 * [swipe] 0..5. Оверлей прозрачен и не ест касания.
 */
@Composable
fun MeltdownSwipeGlitch(
    swipe: Int,
    modifier: Modifier = Modifier,
) {
    if (swipe <= 0) return
    val intensity = remember { Animatable(0f) }
    LaunchedEffect(swipe) {
        val target = MeltdownPresets.swipeIntensity(swipe)
        // резкий скачок вверх на свайпе
        intensity.snapTo(kotlin.math.max(intensity.value, target))
        // медленный спад до фонового уровня, пропорционального прогрессу
        intensity.animateTo(target * 0.4f, tween(2200, easing = LinearEasing))
    }
    GlitchStack(
        intensity = intensity.value,
        modifier = modifier.fillMaxSize(),
        config = MeltdownPresets.Void,
        content = {},
    )
}

/**
 * Полный meltdown-фон для финального терминала. Заменяет старый
 * MeltdownGlitchOverlay (случайные красные полосы) на многослойный глитч.
 */
@Composable
fun VoidRitualGlitch(
    modifier: Modifier = Modifier,
) {
    val intensity = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        intensity.animateTo(1f, tween(700, easing = LinearEasing))
    }
    GlitchStack(
        intensity = intensity.value,
        modifier = modifier.fillMaxSize(),
        config = MeltdownPresets.Void,
        content = {},
    )
}

/**
 * Финальное схлопывание экрана в красную CRT-точку и гашение.
 * По завершении вызывает [onFinished] (там выставляется stage=0, достижение,
 * переход в Treasury).
 */
@Composable
fun VoidCollapseOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { active = true }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .crtCollapse(active = active, durationMs = 680, onFinished = onFinished)
                .background(GlitchPalette.HazardRed),
        )
    }
}

/* =============================================================================
 *  ФИНАЛ РИТУАЛА (v2): РАЗРУШЕНИЕ + BRUTALIST-ЭКРАН РАСКРЫТИЯ НАГРАД
 *
 *   • VoidDisintegrationOverlay — медленное «разрушение реальности» после
 *     схлопывания: ударная волна из точки, датамош/трещины/кровь, гашение.
 *   • VoidRevealScreen — brutalist-раскрытие: лор достижения + карточки наград
 *     «Красный Сектор» с моментальным применением.
 * ========================================================================== */

/** Награда Красного Сектора для экрана раскрытия. */
data class VoidReward(
    val tag: String,
    val name: String,
    val lore: String,
    val onApply: () -> Unit,
)

/**
 * Медленное «разрушение реальности» после схлопывания в точку.
 * По завершению вызывает [onFinished].
 */
@Composable
fun VoidDisintegrationOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(5200, easing = LinearEasing))
        onFinished()
    }
    val p = progress.value
    val intensity = (0.35f + p * 2.2f).coerceIn(0f, 1f)
    val blackout = ((p - 0.72f) / 0.28f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        GlitchStack(
            intensity = intensity,
            modifier = Modifier.fillMaxSize(),
            config = MeltdownPresets.Void,
            content = {},
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxR = kotlin.math.hypot(size.width, size.height) / 2f
            val coreA = ((0.30f - p) / 0.30f).coerceIn(0f, 1f)
            if (coreA > 0f) {
                drawCircle(
                    color = Color.White.copy(alpha = coreA),
                    radius = 26f + p * 140f,
                    center = center,
                )
            }
            val ringP = p.coerceIn(0f, 1f)
            drawCircle(
                color = GlitchPalette.HazardRed.copy(alpha = (1f - ringP) * 0.9f),
                radius = maxR * ringP,
                center = center,
                style = Stroke(width = 2f + (1f - ringP) * 46f),
            )
            val echo = (p - 0.15f).coerceIn(0f, 1f)
            drawCircle(
                color = GlitchPalette.SignalRed.copy(alpha = (1f - echo) * 0.5f),
                radius = maxR * echo,
                center = center,
                style = Stroke(width = 1f + (1f - echo) * 24f),
            )
        }
        if (blackout > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = blackout)),
            )
        }
    }
}

/**
 * BRUTALIST-экран раскрытия наград «Красный Сектор». Появляется на месте
 * читалки после разрушения. Монохром + алый, жёсткие рамки, моношрифт.
 */
@Composable
fun VoidRevealScreen(
    rewards: List<VoidReward>,
    justUnlocked: Boolean = false,
    onEnterTreasury: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundReveal = remember { Animatable(0f) }
    var powered by remember { mutableStateOf(false) }
    val applied = remember {
        mutableStateListOf<Boolean>().apply { repeat(rewards.size) { add(false) } }
    }

    val revealGlitchIntensity = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) {
        // 1. Плавно проявляем фон
        backgroundReveal.animateTo(1f, tween(2500, easing = LinearEasing))
        // 2. Включаем питание CRT-эффекта и запускаем последовательное заполнение экрана
        powered = true
        // 3. Плавно затухает интенсивность общего глитча
        revealGlitchIntensity.animateTo(
            targetValue = 0.25f,
            animationSpec = tween(2500, easing = LinearEasing),
        )
    }

    val scope = rememberCoroutineScope()
    var activeStep by remember { mutableStateOf(0) }

    AuroraAmbientBackground(
        enabled = true,
        specialBackgroundStyle = "void_weeping_red",
        modifier = modifier.fillMaxSize(),
    ) {
        // Черный оверлей для плавного проявления фона и плачущего глаза
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - backgroundReveal.value))
        )

        GlitchStack(
            intensity = revealGlitchIntensity.value,
            modifier = Modifier.fillMaxSize(),
            config = MeltdownPresets.Rift,
            content = {},
        )

        if (powered) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .crtPowerOn(powered)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 30.dp),
            ) {
                SequentialRevealItem(enabled = activeStep >= 0) {
                    GlitchTypewriterText(
                        text = "// TRANSMISSION_LOST   REC \u25CF",
                        enabled = activeStep >= 0,
                        onFinished = { if (activeStep == 0) activeStep = 1 },
                        color = GlitchPalette.HazardRed.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(14.dp))
                SequentialRevealItem(enabled = activeStep >= 1) {
                    BrutalTag(
                        text = "MYTHIC // 999 PTS",
                        enabled = activeStep >= 1,
                        onFinished = { if (activeStep == 1) activeStep = 2 },
                    )
                }
                Spacer(Modifier.height(14.dp))
                SequentialRevealItem(enabled = activeStep >= 2) {
                    var titleText by remember { mutableStateOf("") }
                    val rawTitle = stringResource(AYMR.strings.meltdown_ritual_reveal_title)
                    var hasStarted by remember { mutableStateOf(false) }
                    LaunchedEffect(activeStep) {
                        if (activeStep >= 2) {
                            hasStarted = true
                        }
                    }
                    LaunchedEffect(hasStarted) {
                        if (!hasStarted) return@LaunchedEffect
                        val sb = StringBuilder()
                        val charDelayMs = 26L
                        val scramblePerChar = 2
                        for (i in rawTitle.indices) {
                            val target = rawTitle[i]
                            if (!target.isWhitespace()) {
                                repeat(scramblePerChar) {
                                    sb.append(SCRAMBLE_GLYPHS.random())
                                    titleText = sb.toString()
                                    kotlinx.coroutines.delay(charDelayMs / (scramblePerChar + 1))
                                    sb.deleteCharAt(sb.length - 1)
                                }
                            }
                            sb.append(target)
                            titleText = sb.toString()
                            kotlinx.coroutines.delay(charDelayMs)
                        }
                        if (activeStep == 2) activeStep = 3

                        // Бесконечный цикл фонового глитча после успешного вывода заголовка
                        while (true) {
                            kotlinx.coroutines.delay(kotlin.random.Random.nextLong(2000, 4000))
                            val glitchDuration = kotlin.random.Random.nextLong(150, 300)
                            val startTime = System.currentTimeMillis()
                            while (System.currentTimeMillis() - startTime < glitchDuration) {
                                val chars = rawTitle.toCharArray()
                                val glitchCount = kotlin.random.Random.nextInt(1, 3)
                                repeat(glitchCount) {
                                    val idx = kotlin.random.Random.nextInt(chars.size)
                                    if (!chars[idx].isWhitespace()) {
                                        chars[idx] = SCRAMBLE_GLYPHS.random()
                                    }
                                }
                                titleText = String(chars)
                                kotlinx.coroutines.delay(50L)
                            }
                            titleText = rawTitle
                        }
                    }
                    Text(
                        text = titleText,
                        color = Color.White,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(6.dp))
                SequentialRevealItem(enabled = activeStep >= 3) {
                    val achievementText = if (justUnlocked) {
                        stringResource(AYMR.strings.meltdown_ritual_achievement_unlocked)
                    } else {
                        val isRussian = stringResource(AYMR.strings.meltdown_ritual_reveal_title).contains(Regex("[а-яА-Я]"))
                        if (isRussian) "ДОСТИЖЕНИЕ УЖЕ ПОЛУЧЕНО" else "ACHIEVEMENT ALREADY UNLOCKED"
                    }
                    GlitchTypewriterText(
                        text = achievementText,
                        enabled = activeStep >= 3,
                        onFinished = { if (activeStep == 3) activeStep = 4 },
                        color = GlitchPalette.Phosphor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(20.dp))
                SequentialRevealItem(enabled = activeStep >= 4) {
                    HazardDivider()
                    LaunchedEffect(activeStep) {
                        if (activeStep == 4) {
                            kotlinx.coroutines.delay(200L)
                            activeStep = 5
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                SequentialRevealItem(enabled = activeStep >= 5) {
                    GlitchTypewriterText(
                        text = stringResource(AYMR.strings.meltdown_ritual_reveal_desc),
                        enabled = activeStep >= 5,
                        onFinished = { if (activeStep == 5) activeStep = 6 },
                        color = Color(0xFFCDBDBE),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
                Spacer(Modifier.height(24.dp))
                SequentialRevealItem(enabled = activeStep >= 6) {
                    GlitchTypewriterText(
                        text = stringResource(AYMR.strings.meltdown_ritual_rewards_count, rewards.size),
                        enabled = activeStep >= 6,
                        onFinished = { if (activeStep == 6) activeStep = 7 },
                        color = GlitchPalette.HazardRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(14.dp))

                rewards.forEachIndexed { index, reward ->
                    val cardEnabled = activeStep >= 7 + index
                    SequentialRevealItem(enabled = cardEnabled) {
                        VoidRewardRow(
                            index = index + 1,
                            reward = reward,
                            applied = applied[index],
                            enabled = cardEnabled,
                            onCardFinished = {
                                scope.launch {
                                    kotlinx.coroutines.delay(200L)
                                    if (activeStep == 7 + index) {
                                        activeStep = 8 + index
                                    }
                                }
                            },
                            onApply = {
                                reward.onApply()
                                applied[index] = true
                            },
                        )
                    }
                    if (cardEnabled) {
                        Spacer(Modifier.height(12.dp))
                    }
                }

                val applyAllEnabled = activeStep >= 7 + rewards.size
                SequentialRevealItem(enabled = applyAllEnabled) {
                    BrutalActionButton(
                        text = stringResource(AYMR.strings.meltdown_ritual_apply_all),
                        filled = false,
                        visibleEnabled = applyAllEnabled,
                        onFinished = {
                            scope.launch {
                                kotlinx.coroutines.delay(150L)
                                if (activeStep == 7 + rewards.size) {
                                    activeStep = 8 + rewards.size
                                }
                            }
                        },
                        onClick = {
                            rewards.forEachIndexed { i, r ->
                                if (!applied[i]) {
                                    r.onApply()
                                    applied[i] = true
                                }
                            }
                        },
                    )
                }
                if (applyAllEnabled) {
                    Spacer(Modifier.height(12.dp))
                }

                val enterTreasuryEnabled = activeStep >= 8 + rewards.size
                SequentialRevealItem(enabled = enterTreasuryEnabled) {
                    BrutalActionButton(
                        text = stringResource(AYMR.strings.meltdown_ritual_enter_treasury),
                        filled = true,
                        visibleEnabled = enterTreasuryEnabled,
                        onFinished = {
                            if (activeStep == 8 + rewards.size) {
                                activeStep = 9 + rewards.size
                            }
                        },
                        onClick = onEnterTreasury,
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/**
 * Директива после бреши (Шаг 2 → Шаг 3): полноэкранный brutalist
 * CRT power-on с инст��укцией, как добраться до финала в читалке.
 * onEnter вызывается кнопкой ��ВОЙТИ В ПУСТОТУ» — там и переводим meltdownStage в 2.
 */
@Composable
fun RiftBreachDirective(
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isCollapsing by remember { mutableStateOf(false) }
    val collapseAnim = remember { Animatable(0f) }

    val view = androidx.compose.ui.platform.LocalView.current
    var screenTex by remember { mutableStateOf<Bitmap?>(null) }

    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = with(density) { config.screenWidthDp.dp.toPx() }
    val h = with(density) { config.screenHeightDp.dp.toPx() }
    val shards = remember(w, h) {
        if (w > 0f && h > 0f) buildCrumbleShards(w, h) else emptyList()
    }
    // Снимок UI директивы — фолбэк текстура (если скриншот не удался)
    val texTitle = stringResource(AYMR.strings.meltdown_directive_title)
    val texEnter = stringResource(AYMR.strings.meltdown_directive_enter_void)
    val texStep1 = stringResource(AYMR.strings.meltdown_directive_step_1)
    val texStep2 = stringResource(AYMR.strings.meltdown_directive_step_2)
    val texStep3 = stringResource(AYMR.strings.meltdown_directive_step_3)
    val texStep4 = stringResource(AYMR.strings.meltdown_directive_step_4)
    val fallbackTex = remember(w, h, texTitle) {
        if (w > 0f && h > 0f) {
            buildCrumbleTexture(
                w.toInt(),
                h.toInt(),
                texTitle,
                listOf(texStep1, texStep2, texStep3, texStep4),
                texEnter,
            )
        } else {
            null
        }
    }

    var powered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        powered = true
    }

    var activeStep by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isCollapsing) Color.Black else GlitchPalette.Void),
    ) {
        // Скрываем реальный UI полностью при начале схлопывания, т.к. Canvas рисует его точный снимок
        val contentAlpha = if (isCollapsing) 0f else 1f

        if (!isCollapsing) {
            GlitchStack(
                intensity = 0.3f,
                modifier = Modifier.fillMaxSize(),
                config = MeltdownPresets.Rift,
                content = {},
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(if (isCollapsing) Modifier else Modifier.crtPowerOn(powered))
                .graphicsLayer { alpha = contentAlpha }
                .padding(horizontal = 26.dp, vertical = 72.dp),
        ) {
            Text(
                text = "// TRANSMISSION ● REC",
                color = GlitchPalette.Phosphor.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            BrutalTag(text = "DIRECTIVE // STAGE 03")
            Spacer(modifier = Modifier.height(18.dp))

            SequentialRevealItem(enabled = activeStep >= 0) {
                GlitchTypewriterText(
                    text = stringResource(AYMR.strings.meltdown_directive_title),
                    enabled = activeStep >= 0,
                    onFinished = { if (activeStep == 0) activeStep = 1 },
                    color = GlitchPalette.SignalRed,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            SequentialRevealItem(enabled = activeStep >= 1) {
                GlitchTypewriterText(
                    text = "SYSTEM BREACH CONFIRMED",
                    enabled = activeStep >= 1,
                    onFinished = { if (activeStep == 1) activeStep = 2 },
                    color = GlitchPalette.Phosphor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(22.dp))

            SequentialRevealItem(enabled = activeStep >= 2) {
                Column {
                    HazardDivider()
                    Spacer(modifier = Modifier.height(22.dp))
                }
            }

            SequentialRevealItem(enabled = activeStep >= 2) {
                DirectiveStep(
                    num = "01",
                    text = stringResource(AYMR.strings.meltdown_directive_step_1),
                    enabled = activeStep >= 2,
                    onFinished = { if (activeStep == 2) activeStep = 3 },
                )
            }
            SequentialRevealItem(enabled = activeStep >= 3) {
                DirectiveStep(
                    num = "02",
                    text = stringResource(AYMR.strings.meltdown_directive_step_2),
                    enabled = activeStep >= 3,
                    onFinished = { if (activeStep == 3) activeStep = 4 },
                )
            }
            SequentialRevealItem(enabled = activeStep >= 4) {
                DirectiveStep(
                    num = "03",
                    text = stringResource(AYMR.strings.meltdown_directive_step_3),
                    enabled = activeStep >= 4,
                    onFinished = { if (activeStep == 4) activeStep = 5 },
                )
            }
            SequentialRevealItem(enabled = activeStep >= 5) {
                DirectiveStep(
                    num = "04",
                    text = stringResource(AYMR.strings.meltdown_directive_step_4),
                    enabled = activeStep >= 5,
                    onFinished = { if (activeStep == 5) activeStep = 6 },
                )
            }
            Spacer(modifier = Modifier.height(28.dp))

            SequentialRevealItem(enabled = activeStep >= 6) {
                BrutalActionButton(
                    text = stringResource(AYMR.strings.meltdown_directive_enter_void),
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!isCollapsing) {
                            try {
                                val bmp = Bitmap.createBitmap(
                                    view.width.coerceAtLeast(1),
                                    view.height.coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888,
                                )
                                val canvas = android.graphics.Canvas(bmp)
                                view.draw(canvas)
                                screenTex = bmp
                            } catch (e: Exception) {
                                // ignore/fallback
                            }
                            isCollapsing = true
                            scope.launch {
                                collapseAnim.animateTo(1f, tween(CRUMBLE_MS, easing = LinearEasing))
                                onEnter()
                            }
                        }
                    },
                )
            }
        }

        // Оверлей схлопывания / разрушения экрана
        if (isCollapsing) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawScreenCrumble(shards, collapseAnim.value, screenTex ?: fallbackTex)
            }
        }
    }
}

@Composable
private fun DirectiveStep(
    num: String,
    text: String,
    enabled: Boolean,
    onFinished: () -> Unit = {},
) {
    if (enabled) {
        Row(modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                text = "[$num] ",
                color = GlitchPalette.HazardRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            GlitchTypewriterText(
                text = text,
                enabled = enabled,
                onFinished = onFinished,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** Стаггер-появление карты награды: сдвиг по index на BREACH_CARD_STAGGER_MS. */
@Composable
private fun StaggerReveal(
    index: Int,
    baseDelayMs: Long = 0L,
    content: @Composable () -> Unit,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(baseDelayMs + index.toLong() * breachDur(BREACH_CARD_STAGGER_MS))
        anim.animateTo(1f, tween(breachDur(840), easing = LinearEasing))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = anim.value
            translationY = (1f - anim.value) * 44f
        },
    ) {
        content()
    }
}

@Composable
private fun BrutalTag(
    text: String,
    enabled: Boolean = true,
    onFinished: () -> Unit = {},
) {
    var out by remember(text) { mutableStateOf("") }
    LaunchedEffect(text, enabled) {
        if (!enabled) {
            out = ""
            return@LaunchedEffect
        }
        val sb = StringBuilder()
        val scrambleGlyphs = SCRAMBLE_GLYPHS
        val charDelayMs = 26L
        val scramblePerChar = 2
        for (i in text.indices) {
            val target = text[i]
            if (!target.isWhitespace()) {
                repeat(scramblePerChar) {
                    sb.append(scrambleGlyphs.random())
                    out = sb.toString()
                    kotlinx.coroutines.delay(charDelayMs / (scramblePerChar + 1))
                    sb.deleteCharAt(sb.length - 1)
                }
            }
            sb.append(target)
            out = sb.toString()
            kotlinx.coroutines.delay(charDelayMs)
        }
        onFinished()
    }
    if (enabled && out.isNotEmpty()) {
        Box(
            modifier = Modifier
                .background(GlitchPalette.HazardRed)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                text = out,
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun HazardDivider() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val stripeW = 16f
        val gap = 16f
        var x = -size.height
        while (x < size.width + size.height) {
            val path = Path().apply {
                moveTo(x, size.height)
                lineTo(x + size.height, 0f)
                lineTo(x + size.height + stripeW, 0f)
                lineTo(x + stripeW, size.height)
                close()
            }
            drawPath(path, color = GlitchPalette.HazardRed.copy(alpha = 0.8f))
            x += stripeW + gap
        }
    }
}

@Composable
private fun VoidRewardRow(
    index: Int,
    reward: VoidReward,
    applied: Boolean,
    onApply: () -> Unit,
    enabled: Boolean,
    onCardFinished: () -> Unit,
) {
    var cardStep by remember(enabled) { mutableStateOf(0) }
    if (enabled) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 5.dp, y = 5.dp)
                    .background(GlitchPalette.HazardRed.copy(alpha = 0.20f)),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0203))
                    .border(2.dp, GlitchPalette.HazardRed.copy(alpha = if (applied) 0.95f else 0.5f))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlitchTypewriterText(
                        text = "[" + index.toString().padStart(2, '0') + "]",
                        enabled = enabled && cardStep >= 0,
                        onFinished = { if (cardStep == 0) cardStep = 1 },
                        color = GlitchPalette.HazardRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.width(10.dp))
                    GlitchTypewriterText(
                        text = reward.tag,
                        enabled = enabled && cardStep >= 1,
                        onFinished = { if (cardStep == 1) cardStep = 2 },
                        color = GlitchPalette.Phosphor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                GlitchTypewriterText(
                    text = reward.name,
                    enabled = enabled && cardStep >= 2,
                    onFinished = { if (cardStep == 2) cardStep = 3 },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(4.dp))
                GlitchTypewriterText(
                    text = reward.lore,
                    enabled = enabled && cardStep >= 3,
                    onFinished = { if (cardStep == 3) cardStep = 4 },
                    color = Color(0xFF9A8A8B),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(12.dp))
                val btnText = if (applied) {
                    stringResource(AYMR.strings.meltdown_ritual_reward_active)
                } else {
                    stringResource(AYMR.strings.meltdown_ritual_reward_apply)
                }
                BrutalActionButton(
                    text = btnText,
                    filled = applied,
                    enabled = !applied,
                    visibleEnabled = enabled && cardStep >= 4,
                    onFinished = {
                        if (cardStep == 4) {
                            cardStep = 5
                            onCardFinished()
                        }
                    },
                    onClick = onApply,
                )
            }
        }
    }
}

@Composable
private fun BrutalActionButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visibleEnabled: Boolean = true,
    onFinished: () -> Unit = {},
    onClick: () -> Unit,
) {
    val accent = GlitchPalette.HazardRed
    var out by remember(text) { mutableStateOf("") }
    LaunchedEffect(text, visibleEnabled) {
        if (!visibleEnabled) {
            out = ""
            return@LaunchedEffect
        }
        val sb = StringBuilder()
        val scrambleGlyphs = SCRAMBLE_GLYPHS
        val charDelayMs = 26L
        val scramblePerChar = 2
        for (i in text.indices) {
            val target = text[i]
            if (!target.isWhitespace()) {
                repeat(scramblePerChar) {
                    sb.append(scrambleGlyphs.random())
                    out = sb.toString()
                    kotlinx.coroutines.delay(charDelayMs / (scramblePerChar + 1))
                    sb.deleteCharAt(sb.length - 1)
                }
            }
            sb.append(target)
            out = sb.toString()
            kotlinx.coroutines.delay(charDelayMs)
        }
        onFinished()
    }
    if (visibleEnabled && out.isNotEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(if (filled) accent else Color(0xFF0A0203))
                .border(2.dp, accent)
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = out,
                color = if (filled) Color.Black else accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun GlitchTypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFinished: () -> Unit = {},
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = FontFamily.Monospace,
) {
    var out by remember(text) { mutableStateOf("") }
    LaunchedEffect(text, enabled) {
        if (!enabled) {
            out = ""
            return@LaunchedEffect
        }
        val sb = StringBuilder()
        val scrambleGlyphs = SCRAMBLE_GLYPHS
        val charDelayMs = 26L
        val scramblePerChar = 2
        for (i in text.indices) {
            val target = text[i]
            if (!target.isWhitespace()) {
                repeat(scramblePerChar) {
                    sb.append(scrambleGlyphs.random())
                    out = sb.toString()
                    kotlinx.coroutines.delay(charDelayMs / (scramblePerChar + 1))
                    sb.deleteCharAt(sb.length - 1)
                }
            }
            sb.append(target)
            out = sb.toString()
            kotlinx.coroutines.delay(charDelayMs)
        }
        onFinished()
    }
    if (enabled && out.isNotEmpty()) {
        Text(
            text = out,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
        )
    }
}

@Composable
private fun SequentialRevealItem(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            anim.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        } else {
            anim.snapTo(0f)
        }
    }
    if (enabled) {
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = anim.value
                translationY = (1f - anim.value) * 16f
            },
        ) {
            content()
        }
    }
}

private fun hash01(i: Int): Float {
    val x = kotlin.math.sin(i * 127.1f) * 43758.547f
    return x - kotlin.math.floor(x)
}

// =============================================================================
//  3D-РАЗРУШЕНИЕ ЭКРАНА (пресет подобран в HTML-прототипе)
//  Осколки несут текстуру экрана, кувыркаются в 3D, летят на камеру и падают.
// =============================================================================
private const val SCRAMBLE_GLYPHS = "\u2588\u2593\u2592\u2591#@%&/\\\\<>*+=-01"
private const val CRUMBLE_MS = 9500 // speed
private const val CRUMBLE_SECTORS = 14 // sectors
private const val CRUMBLE_RINGS = 4 // rings
private const val CRUMBLE_BLAST = 0.45f // сила разлёта
private const val CRUMBLE_CAM = 1.0f // «полёт на камеру»
private const val CRUMBLE_GRAV = 0.75f // гравитация
private const val CRUMBLE_SHAKE = 0.1f // тряска камеры
private const val CRUMBLE_THICK = 27f // толщина плит
private const val CRUMBLE_CAM_DIST = 920f // дистанция/фокус камеры
private const val CRUMBLE_SHATTER = 0.16f // момент раскола (0..1)

private class CrumbleShard(
    val path: Path, // контур в покое (ignite / fallback)
    val src: FloatArray, // 8 чисел: x0,y0..x3,y3 (текстурные координаты)
    val cx: Float,
    val cy: Float,
    val dFactor: Float,
    val angle: Float,
    val axX: Float,
    val axY: Float,
    val axZ: Float,
    val spin: Float,
    val vz: Float,
    val drift: Float,
    val seed: Int,
)

private fun buildCrumbleShards(w: Float, h: Float): List<CrumbleShard> {
    val cx = w * 0.5f
    val cy = h * 0.82f // эпицентр разрушения на месте кнопки
    val maxR = maxOf(
        kotlin.math.hypot(cx.toDouble(), cy.toDouble()),
        kotlin.math.hypot((w - cx).toDouble(), cy.toDouble()),
        kotlin.math.hypot(cx.toDouble(), (h - cy).toDouble()),
        kotlin.math.hypot((w - cx).toDouble(), (h - cy).toDouble()),
    ).toFloat()

    val sectors = CRUMBLE_SECTORS
    val rings = CRUMBLE_RINGS
    val list = mutableListOf<CrumbleShard>()

    fun getPoint(sectorIdx: Int, ringIdx: Int): Offset {
        if (ringIdx == 0) return Offset(cx, cy)
        val s = ((sectorIdx % sectors) + sectors) % sectors
        val angle = (s.toFloat() / sectors) * 2f * Math.PI.toFloat() +
            (hash01(s * 13 + ringIdx * 7) - 0.5f) * (2f * Math.PI.toFloat() / sectors) * 0.7f
        val rBase = Math.pow((ringIdx.toFloat() / rings).toDouble(), 1.35).toFloat() * maxR
        val rNoise = (hash01(s * 17 + ringIdx * 11) - 0.5f) * (maxR / rings) * 0.55f
        val finalR = (rBase + rNoise).coerceIn(0f, maxR * 1.15f)
        return Offset(cx + kotlin.math.cos(angle) * finalR, cy + kotlin.math.sin(angle) * finalR)
    }

    for (r in 0 until rings) {
        for (s in 0 until sectors) {
            val idx = r * sectors + s
            val p0 = getPoint(s, r)
            val p1 = getPoint(s + 1, r)
            val p2 = getPoint(s + 1, r + 1)
            val p3 = getPoint(s, r + 1)

            val path = Path().apply {
                moveTo(p0.x, p0.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                close()
            }
            val src = floatArrayOf(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            val tx = (p0.x + p1.x + p2.x + p3.x) / 4f
            val ty = (p0.y + p1.y + p2.y + p3.y) / 4f
            val dist = kotlin.math.hypot((tx - cx).toDouble(), (ty - cy).toDouble()).toFloat()
            val dFactor = (dist / maxR).coerceIn(0f, 1f)
            val angle = kotlin.math.atan2((ty - cy).toDouble(), (tx - cx).toDouble()).toFloat()

            var axx = hash01(idx * 5 + 1) - 0.5f
            var axy = hash01(idx * 5 + 2) - 0.5f
            var axz = hash01(idx * 5 + 3) - 0.5f
            val al = kotlin.math.sqrt(axx * axx + axy * axy + axz * axz).coerceAtLeast(1e-4f)
            axx /= al
            axy /= al
            axz /= al
            val spin = (hash01(idx * 9 + 4) * 2f + 1.2f) * (if (hash01(idx * 9 + 7) > 0.5f) 1f else -1f)
            val vz = hash01(idx * 11 + 2) * 1.25f - 0.15f
            val drift = hash01(idx * 13 + 5) * 0.6f + 0.7f

            list.add(CrumbleShard(path, src, tx, ty, dFactor, angle, axx, axy, axz, spin, vz, drift, idx))
        }
    }
    return list
}

private fun smooth01(a: Float, b: Float, x: Float): Float {
    val k = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

private fun crumbleLe(dF: Float, progress: Float): Float {
    val start = CRUMBLE_SHATTER + dF * 0.14f
    return ((progress - start) / (1f - start - 0.02f)).coerceIn(0f, 1f)
}

private fun crumbleDz(sh: CrumbleShard, progress: Float): Float {
    val le = crumbleLe(sh.dFactor, progress)
    return sh.vz * le * CRUMBLE_CAM_DIST * 0.62f * CRUMBLE_CAM
}

// вращение вектора вокруг оси (формула Родрига)
private fun rot3(vx: Float, vy: Float, vz: Float, ax: Float, ay: Float, az: Float, ang: Float, out: FloatArray) {
    val c = kotlin.math.cos(ang)
    val s = kotlin.math.sin(ang)
    val d = ax * vx + ay * vy + az * vz
    out[0] = vx * c + (ay * vz - az * vy) * s + ax * d * (1f - c)
    out[1] = vy * c + (az * vx - ax * vz) * s + ay * d * (1f - c)
    out[2] = vz * c + (ax * vy - ay * vx) * s + az * d * (1f - c)
}

// текстура экрана (снимок UI директивы) — рисуется один раз в Bitmap
private fun buildCrumbleTexture(
    w: Int,
    h: Int,
    title: String,
    steps: List<String>,
    enter: String,
): Bitmap {
    val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    val fw = w.toFloat()
    val fh = h.toFloat()
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.argb(255, 7, 0, 1)
    c.drawRect(0f, 0f, fw, fh, p)
    p.color = android.graphics.Color.argb(14, 255, 42, 42)
    var yl = 0f
    while (yl < fh) {
        c.drawRect(0f, yl, fw, yl + 1f, p)
        yl += 3f
    }
    val m = fw * 0.06f
    p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    p.textAlign = Paint.Align.LEFT
    p.color = android.graphics.Color.argb(180, 255, 77, 109)
    p.textSize = fw * 0.030f
    c.drawText("// TRANSMISSION \u25CF REC", m, fh * 0.085f, p)
    p.color = android.graphics.Color.argb(255, 255, 0, 60)
    c.drawRect(m, fh * 0.104f, m + fw * 0.36f, fh * 0.128f, p)
    p.color = android.graphics.Color.argb(255, 0, 0, 0)
    p.textSize = fw * 0.026f
    c.drawText("DIRECTIVE // STAGE 03", m + fw * 0.02f, fh * 0.122f, p)
    p.color = android.graphics.Color.argb(255, 255, 42, 42)
    p.textSize = fw * 0.072f
    c.drawText(title, m, fh * 0.176f, p)
    p.color = android.graphics.Color.argb(255, 255, 77, 109)
    p.textSize = fw * 0.030f
    c.drawText("SYSTEM BREACH CONFIRMED", m, fh * 0.200f, p)
    p.color = android.graphics.Color.argb(255, 255, 0, 60)
    var xd = m
    while (xd < fw - m) {
        c.drawRect(xd, fh * 0.223f, xd + fw * 0.017f, fh * 0.226f, p)
        xd += fw * 0.03f
    }
    var yy = fh * 0.258f
    for (i in steps.indices) {
        p.color = android.graphics.Color.argb(255, 255, 0, 60)
        p.textSize = fw * 0.040f
        c.drawText("0" + (i + 1), m, yy, p)
        p.color = android.graphics.Color.argb(235, 230, 180, 190)
        p.textSize = fw * 0.030f
        c.drawText(steps[i], m + fw * 0.09f, yy, p)
        yy += fh * 0.040f
    }
    val by = fh * 0.82f - fh * 0.03f
    p.color = android.graphics.Color.argb(255, 255, 0, 60)
    c.drawRect(m, by, fw - m, by + fh * 0.055f, p)
    p.color = android.graphics.Color.argb(255, 0, 0, 0)
    p.textSize = fw * 0.042f
    p.textAlign = Paint.Align.CENTER
    c.drawText(enter, fw * 0.5f, by + fh * 0.038f, p)
    p.textAlign = Paint.Align.LEFT
    return bmp
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScreenCrumble(
    shards: List<CrumbleShard>,
    progress: Float,
    bmp: Bitmap?,
) {
    val w = size.width
    val h = size.height
    val canvas = drawContext.canvas.nativeCanvas

    drawRect(color = Color.Black, size = size)
    drawAbyss(progress)
    drawEmbers(progress, front = false)

    if (progress <= CRUMBLE_SHATTER) {
        if (bmp != null) {
            canvas.drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        drawIgnite(shards, progress)
    } else {
        val decay = (1f - (progress - CRUMBLE_SHATTER) * 3.5f).coerceAtLeast(0f)
        val shk = CRUMBLE_SHAKE * (smooth01(0f, CRUMBLE_SHATTER, progress) * 8f + decay * 22f)
        val qx = (kotlin.math.sin(progress * 160f) * 0.6f + kotlin.math.sin(progress * 91f + 1f) * 0.4f) * shk
        val qy = kotlin.math.cos(progress * 143f) * shk
        withTransform({ translate(qx, qy) }) {
            val order = shards.indices.sortedBy { crumbleDz(shards[it], progress) }
            for (i in order) paintCrumbleShard(shards[i], progress, w, h, bmp)
        }
    }

    drawEmbers(progress, front = true)

    val flash = (1f - kotlin.math.abs(progress - CRUMBLE_SHATTER) / 0.04f).coerceAtLeast(0f)
    if (flash > 0f) drawRect(color = Color(0xFFFFEBF0), alpha = flash * 0.9f, size = size)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.paintCrumbleShard(
    sh: CrumbleShard,
    progress: Float,
    w: Float,
    h: Float,
    bmp: Bitmap?,
) {
    val canvas = drawContext.canvas.nativeCanvas
    val le = crumbleLe(sh.dFactor, progress)

    // осколок ещё на месте — кусок целого экрана
    if (le <= 0f) {
        if (bmp != null) {
            val face = AndroidPath().apply {
                moveTo(sh.src[0], sh.src[1])
                lineTo(sh.src[2], sh.src[3])
                lineTo(sh.src[4], sh.src[5])
                lineTo(sh.src[6], sh.src[7])
                close()
            }
            val mtx = Matrix()
            mtx.setPolyToPoly(sh.src, 0, sh.src, 0, 4)
            canvas.save()
            canvas.clipPath(face)
            canvas.drawBitmap(bmp, mtx, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        }
        return
    }

    val dirx = kotlin.math.cos(sh.angle)
    val diry = kotlin.math.sin(sh.angle)
    val ke = (le / 0.18f).coerceIn(0f, 1f)
    val blast = (1f - (1f - ke) * (1f - ke)) * 90f * CRUMBLE_BLAST
    val drift = le * sh.drift * 230f * CRUMBLE_BLAST
    val gy = le * le * h * 1.05f * CRUMBLE_GRAV
    val dz = sh.vz * le * CRUMBLE_CAM_DIST * 0.62f * CRUMBLE_CAM
    val posX = sh.cx + dirx * (blast + drift)
    val posY = sh.cy + diry * (blast + drift) + gy
    val ang = sh.spin * le * 3.1f

    val wxA = FloatArray(4)
    val wyA = FloatArray(4)
    val wzA = FloatArray(4)
    val dst = FloatArray(8)
    val rot = FloatArray(3)
    var avgS = 0f
    for (k in 0 until 4) {
        val ox = sh.src[k * 2] - sh.cx
        val oy = sh.src[k * 2 + 1] - sh.cy
        rot3(ox, oy, 0f, sh.axX, sh.axY, sh.axZ, ang, rot)
        val wx = posX + rot[0]
        val wy = posY + rot[1]
        val wzk = dz + rot[2]
        wxA[k] = wx
        wyA[k] = wy
        wzA[k] = wzk
        val dcam = (CRUMBLE_CAM_DIST - wzk).coerceAtLeast(1f)
        val sp = CRUMBLE_CAM_DIST / dcam
        avgS += sp
        dst[k * 2] = w * 0.5f + (wx - w * 0.5f) * sp
        dst[k * 2 + 1] = h * 0.5f + (wy - h * 0.5f) * sp
    }
    avgS /= 4f

    // нормаль грани для света/толщины
    var nx = (wyA[1] - wyA[0]) * (wzA[3] - wzA[0]) - (wzA[1] - wzA[0]) * (wyA[3] - wyA[0])
    var ny = (wzA[1] - wzA[0]) * (wxA[3] - wxA[0]) - (wxA[1] - wxA[0]) * (wzA[3] - wzA[0])
    var nz = (wxA[1] - wxA[0]) * (wyA[3] - wyA[0]) - (wyA[1] - wyA[0]) * (wxA[3] - wxA[0])
    val nl = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-4f)
    nx /= nl
    ny /= nl
    nz /= nl
    if (nz < 0f) {
        nx = -nx
        ny = -ny
        nz = -nz
    }
    val dot = (nx * 0.25f + ny * -0.45f + nz * 0.85f).coerceIn(-1f, 1f)
    val bright = (0.28f + 0.72f * dot.coerceIn(0f, 1f)) * (0.4f + 0.6f * kotlin.math.abs(nz))

    val alpha = ((1f - smooth01(0.72f, 1f, le)) * (1f - smooth01(3.5f, 6.5f, avgS))).coerceIn(0f, 1f)
    if (alpha <= 0.02f) return
    val a255 = (alpha * 255f).toInt().coerceIn(0, 255)

    val face = AndroidPath().apply {
        moveTo(dst[0], dst[1])
        lineTo(dst[2], dst[3])
        lineTo(dst[4], dst[5])
        lineTo(dst[6], dst[7])
        close()
    }

    // 1) боковые грани (толщина плиты)
    val th = CRUMBLE_THICK * (0.4f + 0.6f * le)
    if (th > 0.5f) {
        val back = FloatArray(8)
        for (k in 0 until 4) {
            val bx = wxA[k] - nx * th
            val bYy = wyA[k] - ny * th
            val bz = wzA[k] - nz * th
            val dcam = (CRUMBLE_CAM_DIST - bz).coerceAtLeast(1f)
            val sp = CRUMBLE_CAM_DIST / dcam
            back[k * 2] = w * 0.5f + (bx - w * 0.5f) * sp
            back[k * 2 + 1] = h * 0.5f + (bYy - h * 0.5f) * sp
        }
        val side = (0.10f + 0.28f * bright)
        val sp = Paint(Paint.ANTI_ALIAS_FLAG)
        sp.color = android.graphics.Color.argb(a255, (70f * side * 3f + 8f).toInt().coerceIn(0, 255), 4, 14)
        for (k in 0 until 4) {
            val j = (k + 1) % 4
            val q = AndroidPath().apply {
                moveTo(dst[k * 2], dst[k * 2 + 1])
                lineTo(dst[j * 2], dst[j * 2 + 1])
                lineTo(back[j * 2], back[j * 2 + 1])
                lineTo(back[k * 2], back[k * 2 + 1])
                close()
            }
            canvas.drawPath(q, sp)
        }
    }

    // 2) лицевая грань — текстура экрана
    if (bmp != null) {
        val mtx = Matrix()
        mtx.setPolyToPoly(sh.src, 0, dst, 0, 4)
        val tp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        tp.alpha = a255
        canvas.save()
        canvas.clipPath(face)
        canvas.drawBitmap(bmp, mtx, tp)
        canvas.restore()
    } else {
        val fp = Paint(Paint.ANTI_ALIAS_FLAG)
        fp.color = android.graphics.Color.argb(a255, 7, 0, 1)
        canvas.drawPath(face, fp)
    }

    // 3) затемнение по свету
    val shp = Paint(Paint.ANTI_ALIAS_FLAG)
    shp.color = android.graphics.Color.argb(((1f - bright) * 0.72f * alpha * 255f).toInt().coerceIn(0, 255), 0, 0, 0)
    canvas.drawPath(face, shp)

    // 4) раскалённые кромки сразу после раскола
    val hot = (1f - le / 0.35f).coerceIn(0f, 1f)
    val ep = Paint(Paint.ANTI_ALIAS_FLAG)
    ep.style = Paint.Style.STROKE
    if (hot > 0.01f) {
        ep.strokeWidth = 1.4f + 2.2f * hot
        ep.color = android.graphics.Color.argb(
            (0.85f * hot * 255f).toInt().coerceIn(0, 255),
            255,
            (60f + 150f * hot).toInt().coerceIn(0, 255),
            (60f + 120f * hot).toInt().coerceIn(0, 255),
        )
    } else {
        ep.strokeWidth = 1f
        ep.color = android.graphics.Color.argb((0.25f * alpha * 255f).toInt().coerceIn(0, 255), 255, 0, 60)
    }
    canvas.drawPath(face, ep)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAbyss(progress: Float) {
    val open = smooth01(CRUMBLE_SHATTER, 0.6f, progress)
    if (open <= 0f) return
    val cx = size.width * 0.5f
    val cy = size.height * 0.82f
    val rings = 6
    for (i in rings downTo 1) {
        val rr = size.height * 0.9f * (i / rings.toFloat())
        val a = (0.16f * open) * (1f - i / (rings + 1f))
        drawCircle(color = Color(0xFF5A0012), radius = rr, center = Offset(cx, cy), alpha = a)
    }
    drawCircle(color = Color(0xFF9A0020), radius = size.height * 0.05f, center = Offset(cx, cy), alpha = 0.5f * open)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmbers(progress: Float, front: Boolean) {
    if (progress <= CRUMBLE_SHATTER) return
    val n = if (front) 26 else 44
    val w = size.width
    val h = size.height
    val rise = progress - CRUMBLE_SHATTER
    val span = h * 0.9f
    for (i in 0 until n) {
        val seed = i + if (front) 1000 else 0
        val sp = 0.4f + hash01(seed * 5) * 1.6f
        val x = hash01(seed * 7) * w + kotlin.math.sin(progress * 6f + i) * 6f
        val y0 = h * (0.6f + hash01(seed * 3) * 0.42f) - rise * h * sp * 1.6f
        val y = h * 0.98f - (((h * 0.98f - y0) % span + span) % span)
        val life = (1f - rise * 0.8f).coerceIn(0f, 1f)
        if (life <= 0f) continue
        val r = (if (front) 1.4f else 1.0f) + hash01(seed * 11) * 1.8f
        drawCircle(color = Color(0xFFFF3344), radius = r, center = Offset(x, y), alpha = life * 0.7f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIgnite(shards: List<CrumbleShard>, progress: Float) {
    val glow = smooth01(0f, CRUMBLE_SHATTER, progress) *
        (1f - smooth01(CRUMBLE_SHATTER, CRUMBLE_SHATTER + 0.05f, progress))
    if (glow <= 0.01f) return
    for (sh in shards) {
        val near = 1f - sh.dFactor
        val on = (glow * 1.6f - sh.dFactor).coerceIn(0f, 1f)
        if (on <= 0f) continue
        drawPath(
            path = sh.path,
            color = Color(1f, (0.12f + 0.47f * near).coerceIn(0f, 1f), 0.24f, on * 0.9f),
            style = Stroke(width = 1f + 2f * on * near),
        )
    }
}
