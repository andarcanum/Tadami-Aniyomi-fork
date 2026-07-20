package eu.kanade.presentation.easteregg.aurora

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import eu.kanade.domain.easteregg.aurora.AuroraLocalization

/**
 * «Созвездие» — переосмысленный ввод ответа (вместо 3×3 сигильной панели).
 *
 * Пользователь соединяет девять «звёзд» непрерывной нитью света.
 * Нить хрупка: слишком быстрый жест рвёт её («тонкая грань» — буквально).
 * Reduced motion / accessibility: режим «касаться по одной» — пошаговый
 * выбор звёзд обычными touch-целями 44dp с contentDescription и явной
 * кнопкой «Запечатать».
 *
 * Совместимость: наружу отдаются те же логические узлы 1..9, что и у
 * старой панели — AuroraChannels.sigil() и проверка ответа не меняются.
 * Никакой индикации «верно/неверно» здесь нет — обратная связь только
 * через общий обработчик AuroraEcho.
 */
@Composable
fun AuroraConstellationPad(
    onSigil: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val reducedMotion = rememberAuroraReducedMotion()
    val tier = rememberAuroraTier()
    val en = AuroraLocalization.isEnglish

    val path = remember { mutableStateListOf<Int>() }
    var broken by remember { mutableStateOf(false) }
    var tapMode by remember { mutableStateOf(reducedMotion) }

    // Поле проявляется медленно, как участок неба из темноты
    val emerge = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            emerge.animateTo(1f, tween(AuroraMotion.EMERGE_SLOW_MS, easing = FastOutSlowInEasing))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF0050B14))
                .padding(22.dp)
                .alpha(emerge.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (en) "CONSTELLATION" else "СОЗВЕЗДИЕ",
                color = Color(0xFF8FD6FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 4.sp,
            )
            Text(
                text = when {
                    broken -> if (en) "the thread is thin — slower" else "нить тонка — медленнее"
                    tapMode -> if (en) "touch the stars, one by one" else "коснись звёзд, одной за другой"
                    else -> if (en) "join the stars with one thread" else "соедини звёзды одной нитью"
                },
                color = if (broken) Color(0xFFFFB56B) else Color(0x99B8D8FF),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 8.dp),
            )

            Box(
                modifier = Modifier.padding(top = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Живая проекция-фон — только на устройствах с AGSL (тир FULL)
                if (tier == AuroraTier.FULL) {
                    AuroraSigilProjection(intensity = 0.22f, modifier = Modifier.size(300.dp))
                }
                if (tapMode) {
                    ConstellationTapField(path = path)
                } else {
                    ConstellationTraceField(
                        path = path,
                        onBroken = { broken = true },
                        onWoven = { broken = false },
                        onSeal = onSigil,
                    )
                }
            }

            if (tapMode) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = { path.clear() }) {
                        Text(
                            text = if (en) "Anew" else "Заново",
                            color = Color(0x99B8D8FF),
                        )
                    }
                    TextButton(
                        onClick = {
                            val result = path.toList()
                            if (result.size >= 3) {
                                AuroraSensory.seal(view)
                                path.clear()
                                onSigil(result)
                            }
                        },
                        enabled = path.size >= 3,
                    ) {
                        Text(
                            text = if (en) "Seal" else "Запечатать",
                            color = if (path.size >= 3) ShinkaiPalette.SkyCyan else Color(0x5539536F),
                        )
                    }
                }
            }

            TextButton(
                onClick = {
                    path.clear()
                    broken = false
                    tapMode = !tapMode
                },
                modifier = Modifier.padding(top = if (tapMode) 0.dp else 10.dp),
            ) {
                Text(
                    text = if (tapMode) {
                        if (en) "weave with a thread" else "вести нитью"
                    } else {
                        if (en) "touch one by one" else "касаться по одной"
                    },
                    color = Color(0x66B8D8FF),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** Непрерывное ведение нити (основной режим). */
@Composable
private fun ConstellationTraceField(
    path: SnapshotStateList<Int>,
    onBroken: () -> Unit,
    onWoven: () -> Unit,
    onSeal: (List<Int>) -> Unit,
) {
    val view = LocalView.current
    var fingertip by remember { mutableStateOf<Offset?>(null) }
    Canvas(
        modifier = Modifier
            .size(300.dp)
            .pointerInput(Unit) {
                var lastTime = 0L
                detectDragGestures(
                    onDragStart = { pos ->
                        path.clear()
                        lastTime = 0L
                        fingertip = pos
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val cell = AuroraConstellationGeometry.hitStar(pos.x / w, pos.y / h)
                        if (AuroraConstellationGeometry.registerStar(cell, path)) {
                            AuroraSensory.node(view)
                            onWoven()
                        }
                    },
                    onDrag = { change, _ ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val from = fingertip ?: change.position
                        fingertip = change.position
                        val dt = if (lastTime == 0L) 16L else change.uptimeMillis - lastTime
                        lastTime = change.uptimeMillis
                        val frac = (change.position - from).getDistance() / w
                        if (path.isNotEmpty() && AuroraConstellationGeometry.threadBreaks(frac, dt)) {
                            // «Тонкая грань»: нить не выдерживает спешки
                            path.clear()
                            AuroraSensory.bridge(view)
                            onBroken()
                        } else {
                            // Сэмплируем отрезок, чтобы допустимо быстрый жест
                            // не перепрыгивал звёзды (как в старой панели)
                            val steps = 12
                            var added = false
                            for (i in 0..steps) {
                                val t = i.toFloat() / steps
                                val px = from.x + (change.position.x - from.x) * t
                                val py = from.y + (change.position.y - from.y) * t
                                val cell = AuroraConstellationGeometry.hitStar(px / w, py / h)
                                if (AuroraConstellationGeometry.registerStar(cell, path)) added = true
                            }
                            if (added) {
                                AuroraSensory.node(view)
                                onWoven()
                            }
                        }
                    },
                    onDragEnd = {
                        val result = path.toList()
                        path.clear()
                        fingertip = null
                        if (result.size >= 3) {
                            AuroraSensory.seal(view)
                            onSeal(result)
                        }
                    },
                    onDragCancel = {
                        path.clear()
                        fingertip = null
                    },
                )
            },
    ) {
        drawConstellation(path, fingertip)
    }
}

/**
 * Пошаговый режим (reduced motion / accessibility): большие touch-цели
 * 44dp, у каждой звезды — contentDescription с порядком выбора.
 */
@Composable
private fun ConstellationTapField(
    path: SnapshotStateList<Int>,
) {
    val view = LocalView.current
    val en = AuroraLocalization.isEnglish
    BoxWithConstraints(modifier = Modifier.size(300.dp)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(modifier = Modifier.fillMaxSize()) {
            val thread = ShinkaiPalette.SkyCyan
            for (i in 0 until path.size - 1) {
                drawLine(
                    color = thread.copy(alpha = 0.8f),
                    start = starCenter(path[i], size),
                    end = starCenter(path[i + 1], size),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        for (cell in 1..9) {
            val orderIndex = path.indexOf(cell)
            val selected = orderIndex >= 0
            Box(
                modifier = Modifier
                    .offset(
                        x = w * AuroraConstellationGeometry.starX(cell) - 22.dp,
                        y = h * AuroraConstellationGeometry.starY(cell) - 22.dp,
                    )
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (!selected && AuroraConstellationGeometry.registerStar(cell, path)) {
                            AuroraSensory.node(view)
                        }
                    }
                    .semantics {
                        contentDescription = if (en) {
                            if (selected) "Star $cell, chosen ${orderIndex + 1}" else "Star $cell"
                        } else {
                            if (selected) "Звезда $cell, выбрана ${orderIndex + 1}-й" else "Звезда $cell"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (selected) 16.dp else 10.dp)
                        .clip(CircleShape)
                        .background(if (selected) ShinkaiPalette.StarWhite else Color(0xFF39536F)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Text(
                            text = "${orderIndex + 1}",
                            color = Color(0xFF0A1626),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun starCenter(cell: Int, size: Size): Offset = Offset(
    AuroraConstellationGeometry.starX(cell) * size.width,
    AuroraConstellationGeometry.starY(cell) * size.height,
)

private fun DrawScope.drawConstellation(
    path: List<Int>,
    fingertip: Offset?,
) {
    val thread = ShinkaiPalette.SkyCyan
    // Нить между звёздами
    for (i in 0 until path.size - 1) {
        drawLine(
            color = thread.copy(alpha = 0.8f),
            start = starCenter(path[i], size),
            end = starCenter(path[i + 1], size),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    // Тонкая нить от последней звезды к пальцу
    if (fingertip != null && path.isNotEmpty()) {
        drawLine(
            color = thread.copy(alpha = 0.3f),
            start = starCenter(path.last(), size),
            end = fingertip,
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    // Звёзды: без мерцания — никаких бесконечных пульсаций
    for (cell in 1..9) {
        val center = starCenter(cell, size)
        val selected = cell in path
        if (selected) {
            drawCircle(
                color = thread.copy(alpha = 0.20f),
                radius = 13.dp.toPx(),
                center = center,
            )
        }
        drawCircle(
            color = if (selected) ShinkaiPalette.StarWhite else Color(0xFF39536F),
            radius = if (selected) 5.dp.toPx() else 3.5.dp.toPx(),
            center = center,
        )
    }
}
