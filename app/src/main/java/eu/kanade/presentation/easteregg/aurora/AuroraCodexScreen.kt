package eu.kanade.presentation.easteregg.aurora

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.easteregg.aurora.AuroraCodexEntry
import eu.kanade.domain.easteregg.aurora.AuroraLocalization
import eu.kanade.domain.easteregg.aurora.AuroraPayload
import kotlinx.coroutines.delay

/**
 * «Кодекс Авроры» (Pillar 5): красивый архив пройденного пути —
 * зов, каждое пройденное эхо, финальное письмо и повтор манифестации.
 * Данные брать из AuroraHeartManager: firstRiddle(), codex(), unlockedPayload().
 * Показывать из карточки достижения после первого прогресса.
 * Секретов будущих ступеней здесь нет — только уже открытое.
 *
 * @param onReplay повтор финальной манифестации: покажи AuroraUnlockedScreen
 *   ещё раз (например, через AuroraEchoBus.emitUnlocked(payload)).
 */
@Composable
fun AuroraCodexScreen(
    firstRiddle: String,
    entries: List<AuroraCodexEntry>,
    payload: AuroraPayload?,
    onReplay: () -> Unit,
    onClose: () -> Unit,
) {
    fun themeColor(key: String, fallback: Color): Color =
        payload?.themeColors?.get(key)
            ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
            ?: fallback

    val primary = themeColor("primary", AuroraPublicPalette.Green)
    val accent = themeColor("accent", AuroraPublicPalette.Blue)

    val reducedMotion = rememberAuroraReducedMotion()

    // «Время как материал»: в час сумерек Кодекс светится чуть ярче —
    // повторные возвращения в разное время выглядят по-разному
    val twilight = rememberAuroraTwilight()

    // Дыхание сияния; при reduced motion — статичная середина вдоха
    val breath by if (reducedMotion) {
        remember { mutableStateOf(0.4f) }
    } else {
        rememberInfiniteTransition(label = "codexBreath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(8000), RepeatMode.Reverse),
            label = "codexBreathValue",
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuroraPublicPalette.Night),
    ) {
        AuroraBackdrop(intensity = (0.45f + 0.15f * breath) * (0.85f + 0.35f * twilight))

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = Color(0x99B8D8FF),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = AuroraLocalization.translate("КОДЕКС АВРОРЫ").orEmpty(),
                color = Color(0xCCB8D8FF),
                fontSize = 14.sp,
                letterSpacing = 6.sp,
            )

            CodexReveal(index = 0, reducedMotion = reducedMotion) {
                CodexCard(
                    title = AuroraLocalization.translate("Зов").orEmpty(),
                    body = AuroraLocalization.translate(firstRiddle).orEmpty(),
                    accent = primary,
                )
            }

            entries.forEachIndexed { index, entry ->
                CodexReveal(index = index + 1, reducedMotion = reducedMotion) {
                    CodexCard(
                        title = AuroraLocalization.translate(entry.title ?: "Эхо").orEmpty(),
                        body = AuroraLocalization.translate(entry.riddle.orEmpty()).orEmpty(),
                        accent = primary,
                        ordinal = romanOrdinal(index + 1),
                    )
                }
            }

            payload?.letter?.let { letter ->
                CodexReveal(index = entries.size + 1, reducedMotion = reducedMotion) {
                    CodexCard(
                        title = AuroraLocalization.translate("Письмо").orEmpty(),
                        body = AuroraLocalization.translate(letter).orEmpty(),
                        accent = accent,
                        metal = AuroraMaterialSpec.from(payload),
                    )
                }
            }

            if (payload != null) {
                TextButton(
                    onClick = onReplay,
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Text(text = AuroraLocalization.translate("Пережить манифестацию снова").orEmpty(), color = accent)
                }
            } else {
                // Если квест ещё не закончен — даём возможность продолжить из Кодекса
                // (когда пользователь попал сюда после первой ступени)
                TextButton(
                    onClick = onClose, // родитель (AchievementCard) решит открыть riddle dialog
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Text(text = AuroraLocalization.translate("Продолжить путь").orEmpty(), color = primary)
                }
            }
        }
    }
}

@Composable
private fun CodexCard(
    title: String,
    body: String,
    accent: Color,
    metal: AuroraMaterialSpec? = null,
    ordinal: String? = null,
) {
    val cardSurface = if (metal != null) {
        Modifier.auroraMetal(metal)
    } else {
        Modifier.background(Color(0x8C0A1626))
    }
    Column(
        modifier = Modifier
            .padding(top = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(cardSurface)
            .padding(20.dp),
    ) {
        ordinal?.let {
            Text(
                text = it,
                color = accent.copy(alpha = 0.55f),
                fontSize = 10.sp,
                letterSpacing = 4.sp,
            )
        }
        Text(
            text = title,
            color = accent,
            fontSize = 13.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            color = Color(0xFFDCEBFF),
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Каскадное проявление карточек Кодекса; при reduced motion — мгновенно. */
@Composable
private fun CodexReveal(
    index: Int,
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    val appear = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            delay(150L + index * 160L)
            appear.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier = Modifier
            .alpha(appear.value)
            .graphicsLayer { translationY = (1f - appear.value) * 24f },
    ) {
        content()
    }
}

/** Римский порядковый номер эха — «галерея света» нумерует залы. */
private fun romanOrdinal(n: Int): String = when (n) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    4 -> "IV"
    5 -> "V"
    6 -> "VI"
    7 -> "VII"
    8 -> "VIII"
    9 -> "IX"
    else -> "$n"
}
