package eu.kanade.presentation.components

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.domain.ui.UserProfilePreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

/* =============================================================================
 *  TERMINAL GLITCH DIALOG (v2) — на базе GlitchStack.
 *
 *  Отличия от v1:
 *   • CRT power-on при появлении (окно "включается" из линии);
 *   • скрембл-печать (rememberScrambleReveal) вместо ровного typewriter;
 *   • хроматический дрожащий заголовок (RGB-split);
 *   • скан-линии + статик поверх scrim;
 *   • растущие трещины внутри окна;
 *   • hazard-кнопки (диагональные полосы) + опция press-and-hold.
 *
 *  Сигнатура совместима с v1 — шаги 2 и 3 продолжают работать без изменений.
 * ========================================================================== */

@Composable
fun TerminalGlitchDialog(
    title: String,
    message: String,
    buttonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = {},
    dismissButtonText: String? = null,
    holdToConfirm: Boolean = false,
    accent: Color = GlitchPalette.HazardRed,
) {
    val time by rememberGlitchTime()
    val userProfilePreferences = remember { Injekt.get<UserProfilePreferences>() }
    val isRussian = remember(title) { title.contains(Regex("[а-яА-Я]")) }
    val observerName = remember(userProfilePreferences) {
        val raw = userProfilePreferences.name().get()
        val cleaned = raw.filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' || it == '_' }.trim()
        if (cleaned.isNotEmpty()) {
            cleaned
        } else {
            if (isRussian) "Странник" else "Wanderer"
        }
    }

    // Скрембл-печать тела сообщения
    val displayedText = rememberScrambleReveal(message, charDelayMs = 48, scramblePerChar = 2)

    // Мигающий курсор
    val infinite = rememberInfiniteTransition(label = "cursor")
    val cursorOn by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursor_blink",
    )

    // CRT power-on при первом кадре
    var powered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { powered = true }

    // Растущие трещины
    val crack = remember { Animatable(0f) }
    LaunchedEffect(Unit) { crack.animateTo(1f, tween(2600, easing = LinearEasing)) }

    BackHandler {
        onDismiss()
    }

    val bgAlpha = if (dismissButtonText != null) 0f else 0.88f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        // Скан-линии + статик поверх scrim
        ScanlineOverlay(intensity = 0.7f, time = time, modifier = Modifier.fillMaxSize())
        StaticNoiseOverlay(intensity = 0.5f, time = time, modifier = Modifier.fillMaxSize())

        if (dismissButtonText != null) {
            BrinkTerminalDialogContent(
                title = title,
                message = message,
                buttonText = buttonText,
                dismissButtonText = dismissButtonText,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                accent = accent,
                time = time,
                observerName = observerName,
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 360.dp)
                    .crtPowerOn(powered)
                    .clip(CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp))
                    .background(GlitchPalette.Void)
                    // пульсирующий алый бордер + свечение
                    .border(
                        width = 2.dp,
                        color = accent.copy(alpha = 0.65f + 0.35f * cursorOn),
                        shape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp),
                    )
                    .padding(20.dp),
            ) {
                // Растущие трещины на фоне окна
                Canvas(modifier = Modifier.matchParentSize()) {
                    val p = crack.value
                    val path = Path().apply {
                        moveTo(0f, size.height * 0.2f)
                        lineTo(size.width * 0.15f * p, size.height * 0.28f)
                        lineTo(size.width * 0.10f * p, size.height * 0.45f)
                        moveTo(size.width, size.height * 0.75f)
                        lineTo(size.width * (1f - 0.15f * p), size.height * 0.65f)
                        lineTo(size.width * (1f - 0.10f * p), size.height * 0.50f)
                        lineTo(size.width * (1f - 0.22f * p), size.height * 0.45f)
                        moveTo(size.width * 0.5f, 0f)
                        lineTo(size.width * 0.55f, size.height * 0.18f * p)
                        lineTo(size.width * 0.47f, size.height * 0.30f * p)
                    }
                    drawPath(
                        path = path,
                        color = accent.copy(alpha = 0.30f * p),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Хроматический заголовок
                    GlitchTitle(text = title, accent = accent, time = time)

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 104.dp)
                            .background(Color(0xFF050001))
                            .border(1.dp, accent.copy(alpha = 0.35f))
                            .padding(12.dp),
                    ) {
                        val cursor = if (cursorOn > 0.5f) "\u2588" else " "
                        Text(
                            text = "$displayedText$cursor",
                            color = GlitchPalette.Phosphor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp,
                        )
                    }

                    if (displayedText == message) {
                        Spacer(modifier = Modifier.height(20.dp))

                        HazardButton(
                            text = buttonText,
                            accent = accent,
                            filled = true,
                            holdToConfirm = holdToConfirm,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onConfirm,
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
//  Хроматический заголовок (RGB-split через 3 смещённых слоя)
// -----------------------------------------------------------------------------
@Composable
private fun GlitchTitle(text: String, accent: Color, time: Float) {
    // лёгкое дрожание смещения (может быть отрицательным)
    val jitter = (kotlin.math.sin(time * 30f) * 1.5f).dp
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = accent.copy(alpha = 0.8f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(x = jitter),
        )
        Text(
            text = text,
            color = Color(0xFF00E5FF).copy(alpha = 0.6f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(x = -jitter),
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// -----------------------------------------------------------------------------
//  Hazard-кнопка (диагональные полосы) + опция press-and-hold
// -----------------------------------------------------------------------------
@Composable
private fun HazardButton(
    text: String,
    accent: Color,
    filled: Boolean,
    modifier: Modifier = Modifier,
    holdToConfirm: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val base = modifier
        .height(50.dp)
        .clip(shape)
        .background(if (filled) accent.copy(alpha = 0.14f) else Color(0xFF160306))
        .border(1.dp, accent.copy(alpha = if (filled) 0.9f else 0.5f), shape)
        // диагональные hazard-полосы для залитой кнопки
        .then(
            if (filled) {
                Modifier.drawBehind {
                    val stripeW = 14f
                    val gap = 14f
                    var x = -size.height
                    while (x < size.width + size.height) {
                        val p = Path().apply {
                            moveTo(x, size.height)
                            lineTo(x + size.height, 0f)
                            lineTo(x + size.height + stripeW, 0f)
                            lineTo(x + stripeW, size.height)
                            close()
                        }
                        drawPath(p, color = accent.copy(alpha = 0.16f))
                        x += stripeW + gap
                    }
                    // заполнение при hold
                    if (progress.value > 0f) {
                        drawRect(
                            color = accent.copy(alpha = 0.45f),
                            size = androidx.compose.ui.geometry.Size(size.width * progress.value, size.height),
                        )
                    }
                }
            } else {
                Modifier
            },
        )

    val clickMod = if (holdToConfirm) {
        base.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    val job = scope.launch {
                        progress.animateTo(1f, tween(1400, easing = LinearEasing))
                        onClick()
                    }
                    val released = tryAwaitRelease()
                    if (!released || progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(220)) }
                    }
                },
            )
        }
    } else {
        base.clickable { onClick() }
    }

    val spacedText = remember(text) {
        val uppercase = text.uppercase()
        val builder = StringBuilder("† ")
        for (i in uppercase.indices) {
            builder.append(uppercase[i])
            if (i < uppercase.length - 1) {
                builder.append(" ")
            }
        }
        builder.append(" †")
        builder.toString()
    }

    Box(modifier = clickMod, contentAlignment = Alignment.Center) {
        val pVal = progress.value
        val jitterX = (kotlin.math.sin(System.currentTimeMillis() * 0.05f) * (1f + pVal * 4.5f)).dp
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = spacedText,
                color = GlitchPalette.SignalRed.copy(alpha = 0.75f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = -jitterX, y = 0.dp)
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = spacedText,
                color = GlitchPalette.Phosphor.copy(alpha = 0.75f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = jitterX, y = 0.dp)
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = spacedText,
                color = if (filled) Color.White else GlitchPalette.Phosphor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

// -----------------------------------------------------------------------------
//  Критический сбой : разрыв связи (Brink Terminal Layout)
// -----------------------------------------------------------------------------
@Composable
private fun BrinkTerminalDialogContent(
    title: String,
    message: String,
    buttonText: String,
    dismissButtonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    accent: Color,
    time: Float,
    observerName: String,
) {
    val isRussian = remember(title) { title.contains(Regex("[а-яА-Я]")) }
    val cleanTitle = remember(title) { title.replace("[", "").replace("]", "").trim() }
    val titleParts = remember(cleanTitle) { cleanTitle.split(Regex("\\s*:\\s*")) }

    val lines = remember(isRussian, observerName) {
        if (isRussian) {
            listOf(
                "> сканирую ядро реальности...",
                "> целостность: 00%",
                "> обнаружен наблюдатель: $observerName",
                "> резервный выход... не найден",
                "> протокол КРАСНЫЙ СЕКТОР готов",
                "> ожидаю подтверждения оператора_"
            )
        } else {
            listOf(
                "> scanning reality core...",
                "> integrity: 00%",
                "> observer detected: JAN",
                "> backup gate... not found",
                "> protocol RED SECTOR ready",
                        "> awaiting operator authorization_"
            )
        }
    }

    val descText = remember(isRussian) {
        if (isRussian) {
            "процесс необратим. пустота не возвращает то, что забрала."
        } else {
            "the process is irreversible. the void does not return what it took."
        }
    }

    var headerText by remember { mutableStateOf("") }
    var showTag by remember { mutableStateOf(false) }
    var integrityLabel by remember { mutableStateOf("") }
    var integrityBar by remember { mutableStateOf("") }
    val typedLogs = remember { mutableStateListOf<String>() }
    var typedTitle1 by remember { mutableStateOf("") }
    var typedTitle2 by remember { mutableStateOf("") }
    var typedDesc by remember { mutableStateOf("") }
    var showButtons by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val scrambleGlyphs = listOf(
            '░', '▒', '▓', '█', '▄', '▀', '■', '▲', '▼', '◀', '▶', '♦', '◊', '○', '●', '◙', '◘',
            '0', '1', 'X', 'Y', 'Z', 'F', 'E', 'A', 'D', 'C', 'O', 'N', 'N', 'E', 'C', 'T'
        )
        suspend fun typeText(target: String, delayMs: Long = 45L, scramblePerChar: Int = 2, onUpdate: (String) -> Unit) {
            val sb = StringBuilder()
            for (i in target.indices) {
                val targetChar = target[i]
                if (!targetChar.isWhitespace()) {
                    repeat(scramblePerChar) {
                        sb.append(scrambleGlyphs.random())
                        onUpdate(sb.toString())
                        kotlinx.coroutines.delay(delayMs / (scramblePerChar + 1))
                        sb.deleteCharAt(sb.length - 1)
                    }
                }
                sb.append(targetChar)
                onUpdate(sb.toString())
                kotlinx.coroutines.delay(delayMs)
            }
        }

        // 1. Type Header
        typeText("// KERNEL EXPOSED   REC ", delayMs = 45L, scramblePerChar = 2) { headerText = it }
        kotlinx.coroutines.delay(350)

        // 2. Show Tag
        showTag = true
        kotlinx.coroutines.delay(350)

        // 3. Type Integrity Label
        typeText("REALITY INTEGRITY", delayMs = 45L, scramblePerChar = 2) { integrityLabel = it }
        kotlinx.coroutines.delay(200)

        // 4. Type Integrity Bar
        typeText("░░░░░░░░░░░░░░░░ 00%", delayMs = 35L, scramblePerChar = 1) { integrityBar = it }
        kotlinx.coroutines.delay(400)

        // 5. Type Logs sequentially
        for (i in lines.indices) {
            typedLogs.add("")
            val idx = typedLogs.lastIndex
            typeText(lines[i], delayMs = 40L, scramblePerChar = 2) { typedLogs[idx] = it }
            kotlinx.coroutines.delay(400)
        }
        kotlinx.coroutines.delay(500)

        // 6. Type Title Line 1
        if (titleParts.isNotEmpty()) {
            typeText(titleParts[0], delayMs = 60L, scramblePerChar = 2) { typedTitle1 = it }
        }
        kotlinx.coroutines.delay(250)

        // 7. Type Title Line 2
        if (titleParts.size >= 2) {
            typeText(titleParts[1], delayMs = 60L, scramblePerChar = 2) { typedTitle2 = it }
        }
        kotlinx.coroutines.delay(400)

        // 8. Type Description
        typeText(descText, delayMs = 45L, scramblePerChar = 1) { typedDesc = it }
        kotlinx.coroutines.delay(500)

        // 9. Show Buttons
        showButtons = true
    }

    val infinite = rememberInfiniteTransition(label = "blink")
    val blinkOn by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink_anim",
    )

    val pulseInfinite = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseInfinite.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .drawBehind {
                // 1. Draw linear-gradient background from HTML:
                // background: linear-gradient(180deg, rgba(6,0,9,.30) 0%, rgba(6,0,9,.55) 42%, rgba(6,0,9,.86) 100%);
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF060009).copy(alpha = 0.30f),
                            0.42f to Color(0xFF060009).copy(alpha = 0.55f),
                            1.0f to Color(0xFF060009).copy(alpha = 0.86f),
                        )
                    ),
                    size = size
                )

                // 2. Pulse red heartbeat vignette (сверху мерцает красная зона, как подсветка)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFF0038).copy(alpha = pulseAlpha * 0.42f)
                        ),
                        center = center,
                        radius = size.maxDimension * 0.7f
                    ),
                    size = size
                )
            }
            .padding(horizontal = 26.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            if (headerText.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = headerText,
                        color = accent.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    if (headerText == "// KERNEL EXPOSED   REC ") {
                        Text(
                            text = "●",
                            color = if (blinkOn > 0.5f) accent else Color.Transparent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (showTag) {
                Box(
                    modifier = Modifier
                        .background(accent)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "POINT OF NO RETURN",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (integrityLabel.isNotEmpty()) {
                Column {
                    Text(
                        text = integrityLabel,
                        color = GlitchPalette.Phosphor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    if (integrityBar.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = integrityBar,
                            color = accent,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (typedLogs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    typedLogs.forEach { logLine ->
                        Text(
                            text = logLine,
                            color = when {
                                logLine.contains("00%") || logLine.contains("не найден") || logLine.contains("not found") || logLine.contains("наблюдатель") || logLine.contains("observer") -> GlitchPalette.HazardRed
                                logLine.contains("готов") || logLine.contains("ready") -> GlitchPalette.Phosphor
                                else -> Color(0xFFCDBDBE)
                            },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (typedTitle1.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GlitchLine(text = typedTitle1.uppercase(), accent = accent, time = time)
                    if (typedTitle2.isNotEmpty()) {
                        GlitchLine(text = typedTitle2.uppercase(), accent = accent, time = time)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (typedDesc.isNotEmpty()) {
                Text(
                    text = typedDesc,
                    color = accent.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (showButtons) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val rawAcceptText = buttonText.lowercase()
                    val acceptText = if (rawAcceptText.startsWith("▶")) rawAcceptText else "▶ $rawAcceptText"
                    HazardButton(
                        text = acceptText,
                        accent = accent,
                        filled = true,
                        holdToConfirm = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onConfirm,
                    )

                    ShatterButton(
                        text = dismissButtonText,
                        accent = accent.copy(alpha = 0.6f),
                        onClick = {
                            // Shatters and stays on screen
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Spacer(modifier = Modifier.fillMaxWidth().height(116.dp))
            }
        }
    }
}

@Composable
private fun GlitchLine(text: String, accent: Color, time: Float) {
    val jitter = (kotlin.math.sin(time * 35f) * 2f).dp
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = accent.copy(alpha = 0.8f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(x = jitter),
        )
        Text(
            text = text,
            color = Color(0xFF00E5FF).copy(alpha = 0.6f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(x = -jitter),
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class RefuseParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val alpha: Float,
)

@Composable
private fun ShatterButton(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isShattering by remember { mutableStateOf(false) }
    var hasShattered by remember { mutableStateOf(false) }
    val particles = remember { mutableStateListOf<RefuseParticle>() }

    LaunchedEffect(isShattering) {
        if (isShattering) {
            val count = 80
            for (i in 0 until count) {
                val px = (Math.random() * 800).toFloat()
                val py = (Math.random() * 120).toFloat()
                val vx = ((Math.random() - 0.5) * 14f).toFloat()
                val vy = ((Math.random() - 0.7) * 10f - 4f).toFloat()
                particles.add(
                    RefuseParticle(
                        x = px,
                        y = py,
                        vx = vx,
                        vy = vy,
                        size = (2f + Math.random() * 4f).toFloat(),
                        alpha = 1f,
                    )
                )
            }

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 1600) {
                kotlinx.coroutines.delay(16)
                for (j in particles.indices) {
                    val p = particles[j]
                    particles[j] = p.copy(
                        x = p.x + p.vx,
                        y = p.y + p.vy + 0.15f,
                        vx = p.vx * 0.96f,
                        vy = p.vy * 0.96f,
                        alpha = (p.alpha - 0.012f).coerceAtLeast(0f),
                    )
                }
            }
            isShattering = false
            hasShattered = true
            onClick()
        }
    }

    if (hasShattered) {
        Spacer(modifier = modifier.fillMaxWidth().height(52.dp))
        return
    }

    if (isShattering) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            particles.forEach { p ->
                val cx = (p.x / 800f) * size.width
                val cy = (p.y / 120f) * size.height
                drawRect(
                    color = accent.copy(alpha = p.alpha),
                    topLeft = Offset(cx, cy),
                    size = androidx.compose.ui.geometry.Size(p.size, p.size),
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(
                    width = 2.dp,
                    color = accent,
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                )
                .clickable { isShattering = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text.lowercase(),
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
