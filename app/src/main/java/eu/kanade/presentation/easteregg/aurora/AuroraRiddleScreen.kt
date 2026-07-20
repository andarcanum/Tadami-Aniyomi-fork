package eu.kanade.presentation.easteregg.aurora

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.easteregg.aurora.AuroraChannels
import eu.kanade.domain.easteregg.aurora.AuroraLocalization
import kotlinx.coroutines.delay

/**
 * Полноэкранный экран загадки, v2 (кинематографичный):
 *  - экран «просыпается» из темноты (1.4 с) с кашетированием;
 *  - сияние «дышит» (медленная пульсация интенсивности, 7 с);
 *  - печатная машинка с мигающим курсором и паузами на знаках
 *    препинания — текст читается как закадровый голос;
 *  - подсказка проявляется только после окончания печати.
 *
 * Долгое нажатие на карточку — «Созвездие» (ввод ответа).
 * Хостить через Dialog(usePlatformDefaultWidth = false).
 * @param onPhrase прокинь в AuroraQuest.offer() в launchIO.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroraRiddleScreen(
    riddle: String,
    stageIndex: Int,
    totalStages: Int,
    onPhrase: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showConstellation by remember { mutableStateOf(false) }
    var replayKey by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val reducedMotion = rememberAuroraReducedMotion()

    // Экран просыпается из темноты
    val awake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reducedMotion) {
            awake.snapTo(1f)
        } else {
            awake.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        }
    }

    // Сияние «дышит»
    // «Дыхание» сияния — один медленный цикл (7 с).
    // При reduced motion — статичная середина вдоха, никакой анимации.
    val breath by if (reducedMotion) {
        remember { mutableStateOf(0.35f) }
    } else {
        rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(7000), RepeatMode.Reverse),
            label = "breathValue",
        )
    }

    // Построчный фейд-ин текста:
    val translatedRiddle = remember(riddle) {
        AuroraLocalization.translate(riddle).orEmpty()
    }
    val lines = remember(translatedRiddle) {
        val regex = Regex("(?<=[.!?…])\\s+")
        translatedRiddle.split(regex).filter { it.isNotBlank() }
    }

    val lineAlphas = remember(lines, replayKey) {
        List(lines.size) { Animatable(0f) }
    }

    LaunchedEffect(lines, replayKey) {
        if (reducedMotion) {
            // Reduced motion: текст виден сразу, без построчного проявления
            lineAlphas.forEach { animatable -> animatable.snapTo(1f) }
            return@LaunchedEffect
        }
        lineAlphas.forEach { animatable -> animatable.snapTo(0f) }
        delay(if (replayKey == 0) 900L else 300L) // дать экрану проснуться
        lineAlphas.forEach { animatable ->
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            )
            delay(500L) // задержка перед следующей строкой
        }
    }

    val typing = (lineAlphas.lastOrNull()?.value ?: 0f) < 1f

    val hintAlpha by animateFloatAsState(
        targetValue = if (!typing) 1f else 0f,
        animationSpec = tween(1500),
        label = "hint",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuroraPublicPalette.Night),
    ) {
        AuroraBackdrop(
            intensity = (0.7f + 0.25f * stageIndex) * (0.35f + 0.65f * awake.value) + 0.10f * breath,
            veilThinness = 0.8f + 0.2f * awake.value, // time dilation
            ritualIntensity = 0.2f * stageIndex + 0.1f * breath, // build with depth
        )

        // Кашетирование — чёрные полосы сверху/снизу
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(26.dp)
                .alpha(awake.value)
                .background(Color.Black),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(26.dp)
                .alpha(awake.value)
                .background(Color.Black),
        )

        // Синкай-слои: сумеречный «час кого-то» у горизонта дышит вмест��
        // с сиянием, парящие пылинки света и редкая одинокая комета.
        if (!reducedMotion) {
            KatawareDokiVeil(alpha = (0.16f + 0.10f * breath) * awake.value)
            AuroraLightMotes(count = 18, alpha = 0.35f * awake.value)
            AuroraCometShower(alpha = 0.8f * awake.value, periodSeconds = 12f)
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 34.dp, start = 8.dp)
                .alpha(awake.value),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = Color(0x99B8D8FF),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .alpha(awake.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Premium Eyebrow Pill Badge (Apple / Linear-tier)
            Box(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0x1F1A304D))
                    .border(
                        width = 0.5.dp,
                        color = Color(0x33B8D8FF),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (AuroraLocalization.isEnglish) "AURORA ECHO" else "ЭХО АВРОРЫ",
                    color = Color(0xFF8FD6FF),
                    fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                )
            }

            Text(
                text = "「オーロラの残響」",
                color = Color(0x669BD8FF),
                fontSize = 11.sp,
                letterSpacing = 6.sp,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                modifier = Modifier.padding(top = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(totalStages) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == stageIndex) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < stageIndex) {
                                    AuroraPublicPalette.Green
                                } else if (i == stageIndex) {
                                    AuroraPublicPalette.Green
                                } else {
                                    Color(0xFF24405C)
                                },
                            ),
                    )
                }
            }

            // Карточка загадки с двойной рамкой (Double-Bezel nested architecture).
            // Долгое нажатие = сигильная панель.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0x14B8D8FF))
                    .border(
                        width = 0.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                ShinkaiPalette.SkyCyan.copy(alpha = 0.20f),
                                Color.White.copy(alpha = 0.06f),
                                ShinkaiPalette.Rose.copy(alpha = 0.18f),
                            ),
                        ),
                        shape = RoundedCornerShape(26.dp),
                    )
                    .padding(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xD9050B14))
                        .combinedClickable(
                            onClick = { /* ничего: карточка молчит */ },
                            onLongClick = {
                                AuroraSensory.seal(view)
                                showConstellation = true
                            },
                        )
                        .semantics { contentDescription = translatedRiddle }
                        .padding(24.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        lines.forEachIndexed { index, line ->
                            val alpha = lineAlphas.getOrNull(index)?.value ?: 0f
                            Text(
                                text = line,
                                color = Color(0xFFDCEBFF),
                                fontSize = 17.sp,
                                lineHeight = 25.sp,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.alpha(alpha),
                            )
                        }
                    }
                }
            }

            Text(
                text = AuroraLocalization.translate("если слова бессильны — удержи загадку").orEmpty(),
                color = Color(0x66B8D8FF),
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .alpha(hintAlpha),
            )

            // Перечитать эхо заново (появляется после окончания печати)
            IconButton(
                onClick = { replayKey++ },
                modifier = Modifier
                    .padding(top = 2.dp)
                    .alpha(hintAlpha),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = AuroraLocalization.translate("Прочитать эхо заново"),
                    tint = Color(0x66B8D8FF),
                )
            }
        }
    }

    if (showConstellation) {
        AuroraConstellationPad(
            onSigil = { cells ->
                showConstellation = false
                AuroraChannels.sigil(cells)?.let(onPhrase)
            },
            onDismiss = { showConstellation = false },
        )
    }
}
