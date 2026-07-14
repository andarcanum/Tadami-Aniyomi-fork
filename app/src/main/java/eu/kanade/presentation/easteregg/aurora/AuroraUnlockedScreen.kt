package eu.kanade.presentation.easteregg.aurora

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.easteregg.aurora.AuroraLocalization
import eu.kanade.domain.easteregg.aurora.AuroraPayload

private const val REVEAL_MS = 4500

private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

private fun easeOut(x: Float): Float {
    val i = 1f - x
    return 1f - i * i * i
}

/**
 * Финальный экран награды, v2 — постановочное проявление (~4.5 с):
 * темнота -> сияние расцветает секретной палитрой -> титул
 * съезжается трекингом букв -> элементы проявляются каскадом.
 *
 * ВСЕ тексты и цвета берутся ТОЛЬКО из payload — ничего не хардкодить!
 */
@Composable
fun AuroraUnlockedScreen(
    payload: AuroraPayload,
    onClose: () -> Unit,
) {
    fun themeColor(key: String, fallback: Color): Color =
        payload.themeColors?.get(key)
            ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
            ?: fallback

    val primary = themeColor("primary", AuroraPublicPalette.Green)
    val secondary = themeColor("secondary", AuroraPublicPalette.Violet)
    val accent = themeColor("accent", AuroraPublicPalette.Blue)
    val surface = themeColor("surface", Color(0xFF0A1626))

    // «Живой материал»: если ваулт прислал themeMaterial — письмо лежит
    // на металлической поверхности с бликом от наклона устройства
    val metal = AuroraMaterialSpec.from(payload)
    val letterSurface = if (metal != null) {
        Modifier.auroraMetal(metal)
    } else {
        Modifier.background(surface.copy(alpha = 0.8f))
    }

    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(REVEAL_MS, easing = LinearEasing))
    }
    val t = anim.value

    val darkness = 1f - easeOut(seg(t, 0.02f, 0.32f))
    val bloom = easeOut(seg(t, 0.05f, 0.55f))
    val titleIn = easeOut(seg(t, 0.22f, 0.50f))
    val holderIn = easeOut(seg(t, 0.42f, 0.58f))
    val descIn = easeOut(seg(t, 0.52f, 0.68f))
    val letterIn = easeOut(seg(t, 0.62f, 0.78f))
    val pointsIn = easeOut(seg(t, 0.72f, 0.86f))
    val themeIn = easeOut(seg(t, 0.80f, 0.92f))
    val buttonIn = easeOut(seg(t, 0.88f, 1.00f))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColor("background", AuroraPublicPalette.Night)),
    ) {
        AuroraBackdrop(
            intensity = 0.15f + 1.35f * bloom,
            colorA = primary,
            colorB = secondary,
            colorC = accent,
        )

        // Первые мгновения — полная темнота, из которой всё рождается
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(darkness)
                .background(Color.Black),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = AuroraLocalization.translate(payload.achievementTitle).orEmpty(),
                color = primary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                // трекинг букв, как в кинотитрах
                letterSpacing = (9f - 8f * titleIn).sp,
                modifier = Modifier.graphicsLayer {
                    alpha = titleIn
                    scaleX = 0.92f + 0.08f * titleIn
                    scaleY = 0.92f + 0.08f * titleIn
                },
            )

            payload.holderTitle?.let { title ->
                Text(
                    text = "\u2726 ${AuroraLocalization.translate(title)} \u2726",
                    color = accent,
                    fontSize = 15.sp,
                    letterSpacing = (6f - 4f * holderIn).sp,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .alpha(holderIn),
                )
            }

            payload.achievementDescription?.let { description ->
                Text(
                    text = AuroraLocalization.translate(description).orEmpty(),
                    color = Color(0xCCDCEBFF),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .alpha(descIn),
                )
            }

            payload.letter?.let { letter ->
                Box(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .then(letterSurface)
                        .padding(20.dp)
                        .alpha(letterIn),
                ) {
                    Text(
                        text = AuroraLocalization.translate(letter).orEmpty(),
                        color = Color(0xFFDCEBFF),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }

            payload.bonusPoints?.let { points ->
                Text(
                    text = "+$points",
                    color = primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .graphicsLayer {
                            alpha = pointsIn
                            scaleX = 0.7f + 0.3f * pointsIn
                            scaleY = 0.7f + 0.3f * pointsIn
                        },
                )
            }

            payload.themeName?.let { themeName ->
                Text(
                    text = AuroraLocalization.translate("Открыта тема: $themeName").orEmpty(),
                    color = Color(0x99DCEBFF),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .alpha(themeIn),
                )
            }

            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .alpha(buttonIn),
            ) {
                Text(text = AuroraLocalization.translate("Сохранить в сердце").orEmpty(), color = accent)
            }
        }
    }
}
