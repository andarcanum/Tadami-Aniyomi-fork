package eu.kanade.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/* =============================================================================
 *  ШАГ 1 — КОД СОВЕТА (карточный замок).
 *
 *  Альтернатива TerminalGlitchDialog для Этапа 1: вместо пассивного
 *  диалога — головоломка: 12 монолитов-карточек, каждая при перевороте
 *  открывает одно слово. Игрок собирает подсказку из 12 слов в правильном
 *  порядке; только после этого появляется hold-кнопка «Отключить
 *  системное управление» -> onConfirm().
 *
 *  Зависит только от публичных хелперов пакета components:
 *  rememberGlitchTime(), GlitchPalette, ScanlineOverlay, StaticNoiseOverlay.
 *
 *  ⚠️ ПЕРЕД ВНЕДРЕНИЕМ СМ. INTEGRATION.md — там список вопросов,
 *     которые нужно задать пользователю до интеграции.
 * ========================================================================== */

private val CouncilAmber = Color(0xFFFF7A00)
private val CouncilAmberHi = Color(0xFFFFB000)

@Composable
fun GlitchyText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    glitchActive: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    val time by rememberGlitchTime()
    
    // Хаотичные смещения в dp на основе времени
    val dx = if (glitchActive) kotlin.math.sin(time * 200f) * 3f else 0f
    val dy = if (glitchActive) kotlin.math.cos(time * 200f) * 1.5f else 0f

    Box(modifier = modifier) {
        if (glitchActive) {
            // Красный слой (хроматическая аберрация)
            Text(
                text = text,
                color = GlitchPalette.HazardRed.copy(alpha = 0.65f),
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                textAlign = textAlign,
                letterSpacing = letterSpacing,
                lineHeight = lineHeight,
                maxLines = maxLines,
                softWrap = softWrap,
                modifier = Modifier.offset(x = dx.dp, y = dy.dp),
            )
            // Зеленый слой (хроматическая аберрация)
            Text(
                text = text,
                color = GlitchPalette.Phosphor.copy(alpha = 0.65f),
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                textAlign = textAlign,
                letterSpacing = letterSpacing,
                lineHeight = lineHeight,
                maxLines = maxLines,
                softWrap = softWrap,
                modifier = Modifier.offset(x = -dx.dp, y = -dy.dp),
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = textAlign,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            maxLines = maxLines,
            softWrap = softWrap,
        )
    }
}


/**
 * @param words Список слов подсказки В ПРАВИЛЬНОМ ПОРЯДКЕ (обычно 12).
 *              Внутри они перемешиваются по карточкам.
 * @param progressiveReveal При ошибке оставлять верные слова на местах (true)
 *              или сбрасывать всё (false).
 */
@Composable
fun CouncilCodeLockDialog(
    title: String,
    briefing: String,
    words: List<String>,
    buttonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = {},
    accent: Color = GlitchPalette.HazardRed,
    progressiveReveal: Boolean = true,
) {
    val time by rememberGlitchTime()
    val green = GlitchPalette.Phosphor
    val n = words.size
    val scope = rememberCoroutineScope()

    // 12 слов перешиваются один раз и сохраняются при переворотах
    val cardWords = rememberSaveable { words.shuffled() }
    var flippedList by rememberSaveable { mutableStateOf(List(n) { false }) }
    var usedList by rememberSaveable { mutableStateOf(List(n) { false }) }
    var seq by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var slotStatusList by rememberSaveable { mutableStateOf(List(n) { 0 }) } // 0 none / 1 ok / 2 bad
    var locked by rememberSaveable { mutableStateOf(false) }
    var errorFlash by rememberSaveable { mutableStateOf(false) }

    // Анимация таинственного CRT-включения экрана (сначала расширяется по ширине, потом по высоте)
    val crtWidth = remember { Animatable(0f) }
    val crtHeight = remember { Animatable(0f) }
    val crtAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scope.launch {
            crtAlpha.animateTo(1f, tween(300, easing = LinearEasing))
        }
        crtWidth.animateTo(1.05f, tween(350, easing = FastOutSlowInEasing))
        crtWidth.animateTo(1f, tween(100, easing = LinearEasing)) // легкий пружинящий отскок
        
        crtHeight.animateTo(1.03f, tween(400, easing = FastOutSlowInEasing))
        crtHeight.animateTo(1f, tween(100, easing = LinearEasing)) // легкий пружинящий отскок
    }

    // мигание рамки
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    fun resetStatuses() {
        slotStatusList = List(n) { 0 }
    }

    fun validate() {
        var allOk = true
        val nextStatus = slotStatusList.toMutableList()
        for (i in seq.indices) {
            val ok = cardWords[seq[i]] == words[i]
            nextStatus[i] = if (ok) 1 else 2
            if (!ok) allOk = false
        }
        slotStatusList = nextStatus
        if (allOk) {
            locked = true
        } else {
            errorFlash = true
            scope.launch {
                delay(1200)
                errorFlash = false
                if (progressiveReveal) {
                    val nextUsed = usedList.toMutableList()
                    val keep = ArrayList<Int>()
                    for (i in seq.indices) {
                        val ci = seq[i]
                        if (cardWords[ci] == words[i]) {
                            keep.add(ci)
                        } else {
                            nextUsed[ci] = false
                        }
                    }
                    usedList = nextUsed
                    seq = keep
                } else {
                    val nextUsed = usedList.toMutableList()
                    for (ci in seq) {
                        nextUsed[ci] = false
                    }
                    usedList = nextUsed
                    seq = emptyList()
                }
                resetStatuses()
            }
        }
    }

    fun onCard(i: Int) {
        if (locked || errorFlash) return
        if (!flippedList[i]) {
            flippedList = flippedList.toMutableList().apply { this[i] = true }
            return
        }
        if (usedList[i] || seq.size >= n) return
        usedList = usedList.toMutableList().apply { this[i] = true }
        seq = seq + i
        if (seq.size == n) validate()
    }

    fun onSlot(slot: Int) {
        if (locked || errorFlash || slot >= seq.size) return
        val nextSeq = seq.toMutableList()
        val ci = nextSeq.removeAt(slot)
        seq = nextSeq
        usedList = usedList.toMutableList().apply { this[ci] = false }
        resetStatuses()
    }

    fun onReset() {
        if (locked || errorFlash) return
        val nextUsed = usedList.toMutableList()
        for (ci in seq) nextUsed[ci] = false
        usedList = nextUsed
        seq = emptyList()
        resetStatuses()
    }

    BackHandler { onDismiss() }

    // Текст со скриншота с разметкой
    val isRussian = remember(title) { title.contains(Regex("[а-яА-Я]")) }
    val labelCodeAssembly = if (isRussian) "СБОРКА КОДА · ${seq.size}/$n" else "CODE ASSEMBLY · ${seq.size}/$n"
    val labelReset = if (isRussian) "✕ СБРОС" else "✕ RESET"
    val cardSubLabel = if (isRussian) "СОВЕТ" else "COUNCIL"
    val fullBriefingText = remember(isRussian) {
        if (isRussian) {
            "СИСТЕМНОЕ УПРАВЛЕНИЕ ЗАБЛОКИРОВАНО СОВЕТОМ.\n" +
            "нажмите монолит, чтобы открыть слово · нажмите ещё раз, чтобы добавить его в код.\n" +
            "соберите подсказку из 12 слов в правильном порядке, чтобы снять блокировку."
        } else {
            "SYSTEM CONTROL LOCKED BY THE COUNCIL.\n" +
            "tap a monolith to reveal a word · tap again to add it to the code.\n" +
            "assemble the 12-word hint in the correct order to bypass the lock."
        }
    }

    // Механическая скрембл-печать (на базе готового rememberScrambleReveal из GlitchStack)
    val displayedBriefing = rememberScrambleReveal(fullBriefingText, charDelayMs = 38, scramblePerChar = 2)

    val annotatedBriefing = remember(displayedBriefing, isRussian, accent) {
        androidx.compose.ui.text.buildAnnotatedString {
            val len = displayedBriefing.length
            if (isRussian) {
                val l1 = "СИСТЕМНОЕ УПРАВЛЕНИЕ ЗАБЛОКИРОВАНО СОВЕТОМ.\n"
                val l2 = "нажмите монолит, чтобы открыть слово · нажмите ещё раз, чтобы добавить его в код.\n"
                val l3Start = "соберите "
                val l3Highlight = "подсказку из 12 слов"
                
                val range1 = 0 until l1.length
                val range2 = l1.length until (l1.length + l2.length)
                val range3Start = (l1.length + l2.length) until (l1.length + l2.length + l3Start.length)
                val range3Highlight = (l1.length + l2.length + l3Start.length) until (l1.length + l2.length + l3Start.length + l3Highlight.length)

                for (i in 0 until len) {
                    val char = displayedBriefing[i]
                    val style = when (i) {
                        in range1 -> androidx.compose.ui.text.SpanStyle(color = CouncilAmberHi, fontWeight = FontWeight.Bold)
                        in range2 -> androidx.compose.ui.text.SpanStyle(color = CouncilAmberHi.copy(alpha = 0.85f))
                        in range3Start -> androidx.compose.ui.text.SpanStyle(color = CouncilAmberHi.copy(alpha = 0.85f))
                        in range3Highlight -> androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.Bold)
                        else -> androidx.compose.ui.text.SpanStyle(color = CouncilAmberHi.copy(alpha = 0.85f))
                    }
                    withStyle(style) {
                        append(char)
                    }
                }
            } else {
                val l1 = "SYSTEM CONTROL LOCKED BY THE COUNCIL.\n"
                val l2 = "tap a monolith to reveal a word · tap again to add it to the code.\n"
                val l3Start = "assemble the "
                val l3Highlight = "12-word hint"

                val range1 = 0 until l1.length
                val range2 = l1.length until (l1.length + l2.length)
                val range3Start = (l1.length + l2.length) until (l1.length + l2.length + l3Start.length)
                val range3Highlight = (l1.length + l2.length + l3Start.length) until (l1.length + l2.length + l3Start.length + l3Highlight.length)

                for (i in 0 until len) {
                    val char = displayedBriefing[i]
                    val style = when (i) {
                        in range1 -> androidx.compose.ui.text.SpanStyle(color = CouncilAmberHi, fontWeight = FontWeight.Bold)
                        in range2 -> androidx.compose.ui.text.SpanStyle(color = CouncilAmberHi.copy(alpha = 0.85f))
                        in range3Start -> androidx.compose.ui.text.SpanStyle(color = CouncilAmberHi.copy(alpha = 0.85f))
                        in range3Highlight -> androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.Bold)
                        else -> androidx.compose.ui.text.SpanStyle(color = CouncilAmberHi.copy(alpha = 0.85f))
                    }
                    withStyle(style) {
                        append(char)
                    }
                }
            }
        }
    }

    val headerTitle = if (isRussian) "[ КОД СОВЕТА · ВОССТАНОВЛЕНИЕ ]" else "[ COUNCIL CODE · RESTORATION ]"
    val leftTag = if (isRussian) "⬢ ЯДРО" else "⬢ CORE"

    // Раздельные независимые таймеры глитчей для элементов
    var leftTagGlitch by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val rnd = java.util.Random(111L)
        while (true) {
            delay(rnd.nextLong(3000, 7500))
            leftTagGlitch = true
            delay(rnd.nextLong(150, 300))
            leftTagGlitch = false
        }
    }

    var headerTitleGlitch by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val rnd = java.util.Random(222L)
        while (true) {
            delay(rnd.nextLong(2000, 6000))
            headerTitleGlitch = true
            delay(rnd.nextLong(150, 350))
            headerTitleGlitch = false
        }
    }

    var rightTagGlitch by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val rnd = java.util.Random(333L)
        while (true) {
            delay(rnd.nextLong(4000, 9000))
            rightTagGlitch = true
            delay(rnd.nextLong(100, 250))
            rightTagGlitch = false
        }
    }

    var statusTextGlitch by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val rnd = java.util.Random(444L)
        while (true) {
            delay(rnd.nextLong(2500, 6500))
            statusTextGlitch = true
            delay(rnd.nextLong(200, 400))
            statusTextGlitch = false
        }
    }

    var borderGlitch by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val rnd = java.util.Random(555L)
        while (true) {
            delay(rnd.nextLong(3500, 8000))
            borderGlitch = true
            delay(rnd.nextLong(200, 400))
            borderGlitch = false
        }
    }

    val finalBorderColor = if (borderGlitch) accent else accent.copy(alpha = 0.55f + 0.45f * pulse)

    // Логика поочерёдного подёргивания карточек: ровно одна случайная карточка дергается раз в 2 - 7 сек
    var activeGlitchingCardIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) {
        val rnd = java.util.Random()
        var lastIdx = -1
        while (true) {
            delay(rnd.nextLong(2000, 7000))
            var cardIdx = rnd.nextInt(n)
            if (cardIdx == lastIdx) {
                cardIdx = (cardIdx + 1) % n
            }
            lastIdx = cardIdx
            activeGlitchingCardIndex = cardIdx
            delay(rnd.nextLong(150, 300))
            activeGlitchingCardIndex = -1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f * crtAlpha.value)),
        contentAlignment = Alignment.Center,
    ) {
        ScanlineOverlay(intensity = 0.6f, time = time, modifier = Modifier.fillMaxSize())
        StaticNoiseOverlay(intensity = 0.4f, time = time, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .graphicsLayer {
                    alpha = crtAlpha.value
                    scaleX = crtWidth.value
                    scaleY = crtHeight.value
                }
                .verticalScroll(rememberScrollState()),
        ) {
            HazardBar(accent = CouncilAmber)

            // шапка (строго в 1 строку)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent)
                    .padding(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    GlitchyText(
                        text = leftTag,
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        glitchActive = leftTagGlitch,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Black)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GlitchyText(
                        text = headerTitle,
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.08.sp,
                        glitchActive = headerTitleGlitch,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    GlitchyText(
                        text = "REV 2.6 ®",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        glitchActive = rightTagGlitch,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // корпус
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlitchPalette.Void)
                    .border(2.dp, finalBorderColor, RectangleShape)
                    .padding(14.dp),
            ) {
                // брифинг с мигающим курсором
                val cursor = if (pulse > 0.5f) "\u2588" else " "
                Text(
                    text = annotatedBriefing + androidx.compose.ui.text.AnnotatedString(cursor),
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                color = CouncilAmber,
                                size = Size(2.dp.toPx(), size.height),
                            )
                        }
                        .padding(start = 10.dp, bottom = 12.dp),
                )

                // Отступ между линией сообщения и карточками
                Spacer(Modifier.height(16.dp))

                // сетка карточек (4 в ряд)
                val perRow = 4
                cardWords.indices.chunked(perRow).forEach { rowIdx ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowIdx.forEach { i ->
                            FlipCard(
                                number = i + 1,
                                word = cardWords[i],
                                flipped = flippedList[i],
                                used = usedList[i],
                                highlight = locked,
                                accent = accent,
                                green = green,
                                glitchActive = (i == activeGlitchingCardIndex),
                                subLabel = cardSubLabel,
                                modifier = Modifier.weight(1f),
                                onClick = { onCard(i) },
                            )
                        }
                        repeat(perRow - rowIdx.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // сборка кода
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoLabel(labelCodeAssembly, Color(0xFF8A8A8A), letterSpacingEm = 0.12f)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .border(1.dp, GlitchPalette.HazardRed.copy(alpha = 0.5f))
                            .clickable { onReset() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) { MonoLabel(labelReset, Color(0xFFBBBBBB)) }
                }

                val slotsPerRow = 6
                (0 until n).chunked(slotsPerRow).forEach { rowIdx ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowIdx.forEach { i ->
                            CodeSlot(
                                index = i + 1,
                                word = if (i < seq.size) cardWords[seq[i]] else null,
                                status = slotStatusList[i],
                                accent = accent,
                                green = green,
                                modifier = Modifier.weight(1f),
                                onClick = { onSlot(i) },
                            )
                        }
                        repeat(slotsPerRow - rowIdx.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // статус
                val (statusText, statusColor) = when {
                    locked -> (if (isRussian) "▶ КОД ПРИНЯТ · БЛОКИРОВКА СНЯТА" else "▶ CODE ACCEPTED · LOCK BYPASSED") to green
                    errorFlash -> (if (isRussian) "▲ НЕВЕРНЫЙ ПОРЯДОК · СВЕРЬТЕ СМЫСЛ" else "▲ INVALID ORDER · CHECK MEANING") to GlitchPalette.HazardRed
                    else -> (if (isRussian) "ОЖИДАНИЕ ВВОДА… (${seq.size}/$n)" else "AWAITING INPUT… (${seq.size}/$n)") to Color(0xFF6A6A6A)
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GlitchyText(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        glitchActive = statusTextGlitch,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                // гейт-кнопка
                AnimatedVisibility(visible = locked, enter = expandVertically() + fadeIn()) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        HoldToDisableButton(
                            text = buttonText,
                            accent = accent,
                            green = green,
                            onConfirm = onConfirm,
                        )
                    }
                }
            }

            HazardBar(accent = GlitchPalette.HazardRed)
        }
    }
}

// -----------------------------------------------------------------------------
//  Карточка-монолит с 3D-переворотом
// -----------------------------------------------------------------------------
@Composable
private fun FlipCard(
    number: Int,
    word: String,
    flipped: Boolean,
    used: Boolean,
    highlight: Boolean,
    accent: Color,
    green: Color,
    glitchActive: Boolean = false,
    subLabel: String = "СОВЕТ",
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val rot by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "flip",
    )
    val faceColor = if (highlight) green else accent
    
    val time by rememberGlitchTime()
    val cardOffset = if (glitchActive && !used) {
        val rndCard = java.util.Random((time * 10f + number).toLong())
        if (rndCard.nextFloat() < 0.45f) {
            IntOffset(
                (kotlin.math.sin(time * 160f + number) * 3f).roundToInt(),
                (kotlin.math.cos(time * 160f + number) * 2f).roundToInt()
            )
        } else {
            IntOffset.Zero
        }
    } else {
        IntOffset.Zero
    }

    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .graphicsLayer {
                rotationY = rot
                cameraDistance = 12f * density
                alpha = if (used) 0.32f else (if (glitchActive) 0.82f else 1f)
            }
            .offset { cardOffset }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (rot <= 90f) {
            // лицо: номер
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0000))
                    .border(2.dp, faceColor, RectangleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = String.format("%02d", number),
                    color = faceColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = subLabel,
                    color = faceColor.copy(alpha = 0.6f),
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else {
            // оборот: слово (контр-поворот, чтобы читалось)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .background(Color(0xFF05100A))
                    .border(2.dp, green, RectangleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = word,
                    color = green,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
//  Слот сборки кода
// -----------------------------------------------------------------------------
@Composable
private fun CodeSlot(
    index: Int,
    word: String?,
    status: Int,
    accent: Color,
    green: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val border = when (status) {
        1 -> green
        2 -> GlitchPalette.HazardRed
        else -> if (word != null) CouncilAmberHi else Color(0xFF242424)
    }
    val wordColor = when (status) {
        1 -> green
        2 -> GlitchPalette.HazardRed
        else -> GlitchPalette.Phosphor.copy(alpha = 0.0f).let { Color(0xFFEAEAEA) }
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .background(Color(0xFF050203))
            .border(1.dp, border, RectangleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = String.format("%02d", index),
            color = Color(0xFF555555),
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
        )
        if (word != null) {
            Text(
                text = word,
                color = wordColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

// -----------------------------------------------------------------------------
//  Hold-to-confirm hazard-кнопка
// -----------------------------------------------------------------------------
@Composable
private fun HoldToDisableButton(
    text: String,
    accent: Color,
    green: Color,
    onConfirm: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var confirmed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color(0xFF160306))
            .border(2.dp, accent, RectangleShape)
            .drawBehind {
                // диагональные hazard-полосы
                val stripeW = 12f; val gap = 12f
                var x = -size.height
                while (x < size.width + size.height) {
                    val p = Path().apply {
                        moveTo(x, size.height)
                        lineTo(x + size.height, 0f)
                        lineTo(x + size.height + stripeW, 0f)
                        lineTo(x + stripeW, size.height)
                        close()
                    }
                    drawPath(p, color = accent.copy(alpha = 0.18f))
                    x += stripeW + gap
                }
                // заполнение
                if (progress.value > 0f) {
                    drawRect(
                        color = (if (confirmed) green else accent).copy(alpha = 0.45f),
                        size = Size(size.width * progress.value, size.height),
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val job = scope.launch {
                            progress.animateTo(1f, tween(1600, easing = LinearEasing))
                            confirmed = true
                            onConfirm()
                        }
                        val released = tryAwaitRelease()
                        if (!released || progress.value < 1f) {
                            job.cancel()
                            if (!confirmed) scope.launch { progress.animateTo(0f, tween(240)) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "† $text †",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

// -----------------------------------------------------------------------------
//  Мелкие хелперы
// -----------------------------------------------------------------------------
@Composable
private fun HazardBar(accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .drawBehind {
                val bandW = 18f
                var x = -size.height
                while (x < size.width + size.height) {
                    val p = Path().apply {
                        moveTo(x, size.height)
                        lineTo(x + size.height, 0f)
                        lineTo(x + size.height + bandW, 0f)
                        lineTo(x + bandW, size.height)
                        close()
                    }
                    drawPath(p, color = accent)
                    x += bandW * 2
                }
            },
    )
}

@Composable
private fun MonoLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 8.sp,
    letterSpacingEm: Float = 0.08f,
    bold: Boolean = false,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = if (bold) FontWeight.Black else FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
    )
}
