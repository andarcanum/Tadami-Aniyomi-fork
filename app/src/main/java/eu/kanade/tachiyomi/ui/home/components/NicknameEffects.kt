package eu.kanade.tachiyomi.ui.home.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tadami.aurora.R
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.home.NicknameEffectPreset
import eu.kanade.tachiyomi.ui.home.NicknameStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun NicknameEffectPreset.isTreasury(): Boolean =
    this == NicknameEffectPreset.AuroraCrown ||
        this == NicknameEffectPreset.GlitchRune ||
        this == NicknameEffectPreset.Cipher ||
        this == NicknameEffectPreset.TrinityPrism ||
        this == NicknameEffectPreset.ShadowCrown ||
        this == NicknameEffectPreset.RankSigils

private val GLITCH_NOISE_CHARS = charArrayOf('█', '▓', '░', '▰', '§', '⟡', '⌬', '⚡', '✖', '▚', '▞', '▩')

private val TRINITY_ORBIT_COLORS = listOf(Color(0xFF64E8FF), Color(0xFF9C7CFF), Color(0xFFFFD36E))
private val TRINITY_TILTS = listOf(-22f, 22f, 90f)
private val HOLO_COLORS = listOf(Color(0xFFFF33B4), Color(0xFFFF85D4), Color(0xFFD500F9), Color(0xFFFF33B4))
private val RAINBOW_COLORS = listOf(
    Color(0xFFFF4E9E),
    Color(0xFFFF9A3C),
    Color(0xFFFFF176),
    Color(0xFF72F6C0),
    Color(0xFF6CC6FF),
    Color(0xFFB388FF),
    Color(0xFFFF4E9E),
)
private val FLARE_POSITIONS = listOf(25f, 110f, 205f, 290f)

private fun createShurikenPath(): Path {
    val path = Path()
    val radius = 1.0f
    val innerRadius = radius * 0.35f
    path.moveTo(0f, -radius)
    path.quadraticTo(0f, 0f, innerRadius, -innerRadius)
    path.lineTo(radius, 0f)
    path.quadraticTo(0f, 0f, innerRadius, innerRadius)
    path.lineTo(0f, radius)
    path.quadraticTo(0f, 0f, -innerRadius, innerRadius)
    path.lineTo(-radius, 0f)
    path.quadraticTo(0f, 0f, -innerRadius, -innerRadius)
    path.close()
    return path
}

private fun DrawScope.drawNodeWithTrail(
    center: Offset,
    rx: Float,
    ry: Float,
    theta: Float,
    tilt: Float,
    orbitColor: Color,
    isVertical: Boolean,
    trailRadiusPx: Float,
) {
    val trailLength = 15
    val currentRx = if (isVertical) rx * 0.75f else rx
    val currentRy = if (isVertical) ry * 1.8f else ry

    for (step in 0 until trailLength) {
        val trailTheta = theta - step * 0.04f
        val tx = currentRx * cos(trailTheta.toDouble()).toFloat()
        val ty = currentRy * sin(trailTheta.toDouble()).toFloat() + tx * sin(tilt.toDouble()).toFloat()
        val tz = sin(trailTheta.toDouble()).toFloat()

        val cx = center.x + tx
        val cy = center.y + ty
        val zScale = 0.55f + 0.45f * (tz + 1f) / 2f
        val zAlpha = 0.15f + 0.85f * (tz + 1f) / 2f

        val trailDecay = 1f - step / trailLength.toFloat()
        val scale = zScale * (0.4f + 0.6f * trailDecay)
        val alpha = zAlpha * trailDecay * trailDecay * 0.65f
        val radius = trailRadiusPx * scale

        if (alpha > 0.02f) {
            drawCircle(
                color = orbitColor.copy(alpha = alpha),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
    }
}

private fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees) % 360f
    if (hsv[0] < 0) hsv[0] += 360f
    return Color(android.graphics.Color.HSVToColor((this.alpha * 255).toInt(), hsv))
}

@Composable
internal fun AnimatedNicknameOverlay(
    text: String,
    nicknameStyle: NicknameStyle,
    modifier: Modifier = Modifier,
) {
    val isInspection = LocalInspectionMode.current
    val colors = AuroraTheme.colors
    val isEInk = colors.isEInk

    val context = LocalContext.current
    val reduceMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f,
            ) == 0.0f
        } catch (_: Exception) {
            false
        }
    }

    // Skip animation in inspection / preview mode, for e-ink, and for reduce motion
    if (isInspection || isEInk || reduceMotion) {
        StaticNicknameText(text, nicknameStyle, modifier)
        return
    }

    when (nicknameStyle.effect) {
        NicknameEffectPreset.GlitchRune -> GlitchRuneEffect(text, nicknameStyle, modifier)
        NicknameEffectPreset.Cipher -> CipherSigilEffect(text, nicknameStyle, modifier)
        NicknameEffectPreset.AuroraCrown -> AuroraCrownEffect(text, nicknameStyle, modifier)
        NicknameEffectPreset.TrinityPrism -> TrinityPrismEffect(text, nicknameStyle, modifier)
        NicknameEffectPreset.ShadowCrown -> ShadowCrownEffect(text, nicknameStyle, modifier)
        NicknameEffectPreset.RankSigils -> RankSigilsEffect(text, nicknameStyle, modifier)
        else -> StaticNicknameText(text, nicknameStyle, modifier)
    }
}

@Composable
private fun StaticNicknameText(
    text: String,
    nicknameStyle: NicknameStyle,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val textColor = resolveNicknameColor(nicknameStyle.color, nicknameStyle.customColorHex, colors)
    val outlineColor = if (textColor.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.8f)
    }
    val outlineOffset = nicknameStyle.outlineWidth.coerceIn(1, 8).dp
    val fontFamily = nicknameStyle.font.fontRes?.let { FontFamily(Font(it)) }
    val baseStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Black,
        fontSize = nicknameStyle.fontSize.coerceIn(14, 36).sp,
        lineHeight = (nicknameStyle.fontSize.coerceIn(14, 36) + 2).sp,
    )
    val shadow = if (nicknameStyle.glow) {
        Shadow(
            color = if (colors.isDark) {
                textColor.copy(alpha = 0.85f)
            } else {
                if (textColor.luminance() > 0.6f) {
                    colors.accent.copy(alpha = 0.45f)
                } else {
                    textColor.copy(alpha = 0.55f)
                }
            },
            blurRadius = if (colors.isDark) 20f else 12f,
        )
    } else {
        null
    }

    val displayText = applyNicknameEffect(text, nicknameStyle.effect)

    if (nicknameStyle.effect == NicknameEffectPreset.AuroraCrown) {
        Row(
            modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_crown_small),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.achievementGold,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = displayText,
                style = baseStyle.copy(
                    color = textColor,
                    shadow = shadow,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Box(modifier = modifier) {
            if (nicknameStyle.outline) {
                listOf(
                    -outlineOffset to 0.dp,
                    outlineOffset to 0.dp,
                    0.dp to -outlineOffset,
                    0.dp to outlineOffset,
                    -outlineOffset to -outlineOffset,
                    -outlineOffset to outlineOffset,
                    outlineOffset to -outlineOffset,
                    outlineOffset to outlineOffset,
                ).forEach { (x, y) ->
                    Text(
                        text = displayText,
                        modifier = Modifier.offset(x = x, y = y),
                        style = baseStyle.copy(color = outlineColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = displayText,
                style = baseStyle.copy(
                    color = textColor,
                    shadow = shadow,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun resolveNicknameColor(
    preset: eu.kanade.tachiyomi.ui.home.NicknameColorPreset,
    customHex: String,
    colors: AuroraColors,
): Color {
    return when (preset) {
        eu.kanade.tachiyomi.ui.home.NicknameColorPreset.Theme -> colors.textPrimary
        eu.kanade.tachiyomi.ui.home.NicknameColorPreset.Accent -> colors.accent
        eu.kanade.tachiyomi.ui.home.NicknameColorPreset.Gold -> colors.achievementGold
        eu.kanade.tachiyomi.ui.home.NicknameColorPreset.Cyan -> Color(0xFF66D9EF)
        eu.kanade.tachiyomi.ui.home.NicknameColorPreset.Pink -> Color(0xFFFF7BC0)
        eu.kanade.tachiyomi.ui.home.NicknameColorPreset.Custom -> {
            val hex = customHex.trim()
            try {
                if (hex.length == 7 && hex.startsWith("#")) {
                    Color(hex.removePrefix("#").toLong(16) or 0xFF000000)
                } else {
                    colors.textPrimary
                }
            } catch (_: Exception) {
                colors.textPrimary
            }
        }
    }
}

private fun applyNicknameEffect(text: String, effect: NicknameEffectPreset): String {
    return when (effect) {
        NicknameEffectPreset.None -> text
        NicknameEffectPreset.Sparkle -> "✦ $text ✦"
        NicknameEffectPreset.Hearts -> "♡ $text ♡"
        NicknameEffectPreset.Stars -> "★ $text ★"
        NicknameEffectPreset.Flowers -> "✿ $text ✿"
        NicknameEffectPreset.Kawaii -> "(≧◡≦) $text"
        NicknameEffectPreset.Cat -> "ฅ^•ﻌ•^ฅ $text"
        NicknameEffectPreset.Moon -> "☾ $text ☽"
        NicknameEffectPreset.Cloud -> "☁ $text ☁"
        NicknameEffectPreset.Ribbon -> "୨୧ $text ୨୧"
        NicknameEffectPreset.Sakura -> "❀ $text ❀"
        NicknameEffectPreset.AuroraCrown -> text
        NicknameEffectPreset.GlitchRune -> text
        NicknameEffectPreset.Cipher -> text
        NicknameEffectPreset.TrinityPrism -> text
        NicknameEffectPreset.ShadowCrown -> text
        NicknameEffectPreset.RankSigils -> text
    }
}

@Composable
private fun GlitchRuneEffect(
    text: String,
    nicknameStyle: NicknameStyle,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val isAmoled = colors.isAmoled
    val infiniteTransition = rememberInfiniteTransition(label = "glitch")
    val timeState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing), // 25% faster cycle (6s)
            repeatMode = RepeatMode.Restart,
        ),
        label = "glitch_time",
    )

    val textColor = resolveNicknameColor(nicknameStyle.color, nicknameStyle.customColorHex, colors)
    val nicknameFontFamily = nicknameStyle.font.fontRes?.let { FontFamily(Font(it)) }
    val baseStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = nicknameFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = nicknameStyle.fontSize.coerceIn(14, 36).sp,
        lineHeight = (nicknameStyle.fontSize.coerceIn(14, 36) + 2).sp,
    )

    val scrambleTextAndBurstState = remember(text) {
        derivedStateOf {
            val time = timeState.value
            val (isBurst, burstIntensity) = when {
                time in 0.20f..0.23f -> {
                    val progress = (time - 0.20f) / 0.03f
                    true to (1f - kotlin.math.abs(progress - 0.5f) * 2f)
                }
                time in 0.65f..0.68f -> {
                    val progress = (time - 0.65f) / 0.03f
                    true to (1f - kotlin.math.abs(progress - 0.5f) * 2f)
                }
                time in 0.92f..0.97f -> {
                    val progress = (time - 0.92f) / 0.05f
                    true to (1f - kotlin.math.abs(progress - 0.5f) * 2f)
                }
                else -> false to 0f
            }

            val scrambleText = if (!isBurst) {
                text
            } else {
                val frame = (time * 50f).toInt()
                val sb = StringBuilder(text)
                if (text.isNotEmpty()) {
                    val rng = kotlin.random.Random(frame.toLong())
                    val numScrambles = (1..2).random(rng).coerceAtMost(text.length)
                    repeat(numScrambles) {
                        val idx = rng.nextInt(text.length)
                        sb[idx] = GLITCH_NOISE_CHARS[rng.nextInt(GLITCH_NOISE_CHARS.size)]
                    }
                }
                sb.toString()
            }

            val textAlpha = if (isBurst && (time * 50f).toInt() % 3 == 0) 0.5f else 1.0f
            val leftAlpha = if (isBurst) (0.5f + 0.3f * burstIntensity) else 0.35f
            val rightAlpha = if (isBurst) (0.5f + 0.3f * burstIntensity) else 0.35f

            Triple(scrambleText, isBurst, Triple(burstIntensity, textAlpha, leftAlpha to rightAlpha))
        }
    }

    val (scrambleText, isBurst, extraData) = scrambleTextAndBurstState.value
    val (burstIntensity, textAlpha, alphas) = extraData
    val (leftAlpha, rightAlpha) = alphas

    val leftColor = remember(textColor, isAmoled) {
        textColor.shiftHue(-45f).copy(alpha = if (isAmoled) 0.5f else 0.7f)
    }
    val rightColor = remember(textColor, isAmoled) {
        textColor.shiftHue(45f).copy(alpha = if (isAmoled) 0.5f else 0.7f)
    }

    val centerString = remember(scrambleText, textColor, textAlpha) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = textColor.copy(alpha = textAlpha))) {
                append(scrambleText)
            }
        }
    }
    val leftString = remember(scrambleText, leftColor, leftAlpha) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = leftColor.copy(alpha = leftColor.alpha * leftAlpha))) {
                append(scrambleText)
            }
        }
    }
    val rightString = remember(scrambleText, rightColor, rightAlpha) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = rightColor.copy(alpha = rightColor.alpha * rightAlpha))) {
                append(scrambleText)
            }
        }
    }

    Box(
        modifier = modifier
            .drawWithContent {
                drawContent()
                if (isBurst) {
                    val time = timeState.value
                    val frame = (time * 50f).toInt()
                    val rng = kotlin.random.Random(frame.toLong() + 999L)
                    val numStripes = rng.nextInt(2) + 1
                    repeat(numStripes) {
                        val stripeHeight = (rng.nextFloat() * 3f + 1f).dp.toPx()
                        val stripeWidth = size.width * (rng.nextFloat() * 0.4f + 0.2f)
                        val stripeX = rng.nextFloat() * (size.width - stripeWidth)
                        val stripeY = rng.nextFloat() * size.height
                        val color = when (rng.nextInt(3)) {
                            0 -> Color(0xFF00FFFF).copy(alpha = 0.7f) // Cyan
                            1 -> Color(0xFFFF00FF).copy(alpha = 0.7f) // Magenta
                            else -> colors.accent.copy(alpha = 0.7f)
                        }
                        drawRect(
                            color = color,
                            topLeft = Offset(stripeX, stripeY),
                            size = androidx.compose.ui.geometry.Size(stripeWidth, stripeHeight),
                        )
                    }
                }
            },
    ) {
        Text(
            text = leftString,
            style = baseStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset {
                val time = timeState.value
                val x = if (isBurst) {
                    (-3.5f - sin(time * 60f) * 5f * burstIntensity)
                } else {
                    -1.2f - sin(time * 2 * PI.toFloat()) * 0.4f
                }
                val y = if (isBurst) {
                    (cos(time * 50f) * 2f * burstIntensity)
                } else {
                    cos(time * 2 * PI.toFloat()) * 0.2f
                }
                IntOffset(x.dp.roundToPx(), y.dp.roundToPx())
            },
        )
        Text(
            text = rightString,
            style = baseStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset {
                val time = timeState.value
                val x = if (isBurst) {
                    (3.5f + sin(time * 65f) * 5f * burstIntensity)
                } else {
                    1.2f + sin(time * 2 * PI.toFloat()) * 0.4f
                }
                val y = if (isBurst) {
                    (-cos(time * 45f) * 2f * burstIntensity)
                } else {
                    -cos(time * 2 * PI.toFloat()) * 0.2f
                }
                IntOffset(x.dp.roundToPx(), y.dp.roundToPx())
            },
        )
        Text(
            text = centerString,
            style = baseStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset {
                val time = timeState.value
                val x = if (isBurst) (sin(time * 40f) * 4f * burstIntensity) else 0f
                val y = if (isBurst) (cos(time * 30f) * 2.5f * burstIntensity) else 0f
                IntOffset(x.dp.roundToPx(), y.dp.roundToPx())
            },
        )
    }
}

@Composable
private fun CipherSigilEffect(
    text: String,
    nicknameStyle: NicknameStyle,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val isAmoled = colors.isAmoled
    val infiniteTransition = rememberInfiniteTransition(label = "cipher")
    val timeState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cipher_angle",
    )

    val textColor = resolveNicknameColor(nicknameStyle.color, nicknameStyle.customColorHex, colors)
    val nicknameFontFamily = nicknameStyle.font.fontRes?.let { FontFamily(Font(it)) }
    val baseStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = nicknameFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = nicknameStyle.fontSize.coerceIn(14, 36).sp,
        lineHeight = (nicknameStyle.fontSize.coerceIn(14, 36) + 2).sp,
    )

    // Scandinavian Futhark runes for floating particles
    val glyphs = remember {
        listOf(
            'ᚠ', 'ᚢ', 'ᚦ', 'ᚨ', 'ᚱ', 'ᚲ', 'ᚷ', 'ᚹ', 'ᚺ', 'ᚾ', 'ᛁ', 'ᛃ',
            'ᛇ', 'ᛈ', 'ᛉ', 'ᛊ', 'ᛏ', 'ᛒ', 'ᛖ', 'ᛗ', 'ᛚ', 'ᛜ', 'ᛞ', 'ᛟ',
        )
    }
    val n = glyphs.size

    val density = LocalDensity.current
    val glyphGlowColor = colors.accent
    val textMeasurer = rememberTextMeasurer()

    // Pre-measure layouts once at 14.sp (they will be scaled in draw phase)
    val runeGlowLayouts = remember(glyphGlowColor, nicknameFontFamily) {
        glyphs.map { char ->
            textMeasurer.measure(
                text = char.toString(),
                style = TextStyle(
                    color = glyphGlowColor,
                    fontSize = 14.sp,
                    fontFamily = nicknameFontFamily,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = glyphGlowColor.copy(alpha = 0.8f),
                        blurRadius = with(density) { 8.dp.toPx() },
                    ),
                ),
            )
        }
    }
    val runeCoreLayouts = remember(glyphGlowColor, nicknameFontFamily) {
        glyphs.map { char ->
            textMeasurer.measure(
                text = char.toString(),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = nicknameFontFamily,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = glyphGlowColor.copy(alpha = 0.8f),
                        blurRadius = with(density) { 3.dp.toPx() },
                    ),
                ),
            )
        }
    }

    Box(
        modifier = modifier
            .drawWithContent {
                val actualTextWidth = size.width

                val rx = (actualTextWidth / 2f + 12.dp.toPx()).coerceAtLeast(40.dp.toPx())
                val ry = 12.dp.toPx()
                val alphaRad = 0.22f // 12.6° tilt
                val thetaBase = timeState.value

                val particles = glyphs.indices.take(8).map { i ->
                    val theta = thetaBase + (2f * PI.toFloat() * i / 8)
                    val x = rx * cos(theta.toDouble()).toFloat()
                    val y = ry * sin(theta.toDouble()).toFloat() + x * sin(alphaRad.toDouble()).toFloat()
                    val z = sin(theta.toDouble()).toFloat()
                    val scale = 0.5f + 0.5f * (z + 1f) / 2f
                    val alpha = (0.15f + 0.85f * (z + 1f) / 2f).coerceIn(0f, 1f) * (if (isAmoled) 0.5f else 1.0f)
                    ParticleData(glyphs[i % n], x, y, z, scale, alpha, i)
                }

                // Draw back particles (z < 0)
                particles.filter { it.z < 0 }.sortedBy { it.z }.forEach { p ->
                    drawGlyph(
                        glowLayoutResult = runeGlowLayouts[p.index % n],
                        coreLayoutResult = runeCoreLayouts[p.index % n],
                        cx = size.width / 2f + p.x,
                        cy = size.height / 2f + p.y,
                        scale = p.scale,
                        alpha = p.alpha,
                    )
                }
                // Draw text content
                drawContent()
                // Draw front particles (z >= 0)
                particles.filter { it.z >= 0 }.sortedByDescending { it.z }.forEach { p ->
                    drawGlyph(
                        glowLayoutResult = runeGlowLayouts[p.index % n],
                        coreLayoutResult = runeCoreLayouts[p.index % n],
                        cx = size.width / 2f + p.x,
                        cy = size.height / 2f + p.y,
                        scale = p.scale,
                        alpha = p.alpha,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val styledText = remember(text) { "ᛟ  $text  ᛟ" }
        Text(
            text = styledText,
            style = baseStyle.copy(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF80DEEA), // Ice Blue
                        Color(0xFFE0F7FA), // Frost Silver
                        Color(0xFF80DEEA), // Ice Blue
                    ),
                ),
                shadow = Shadow(
                    color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                    blurRadius = 8f,
                ),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class ParticleData(
    val glyph: Char,
    val x: Float,
    val y: Float,
    val z: Float,
    val scale: Float,
    val alpha: Float,
    val index: Int,
)

private data class PrismaticSparkle(
    val angle: Double,
    val phaseOffset: Double,
    val sizeDp: Float,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlyph(
    glowLayoutResult: androidx.compose.ui.text.TextLayoutResult,
    coreLayoutResult: androidx.compose.ui.text.TextLayoutResult,
    cx: Float,
    cy: Float,
    scale: Float,
    alpha: Float,
) {
    withTransform({
        translate(cx, cy)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawText(
            textLayoutResult = glowLayoutResult,
            topLeft = Offset(-glowLayoutResult.size.width / 2f, -glowLayoutResult.size.height / 2f),
            alpha = alpha,
        )
        drawText(
            textLayoutResult = coreLayoutResult,
            topLeft = Offset(-coreLayoutResult.size.width / 2f, -coreLayoutResult.size.height / 2f),
            alpha = alpha,
        )
    }
}

@Composable
private fun AuroraCrownEffect(
    text: String,
    nicknameStyle: NicknameStyle,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val isAmoled = colors.isAmoled
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val gradientOffsetState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing), // slower speed (12s)
            repeatMode = RepeatMode.Reverse, // seamless back and forth gradient loop
        ),
        label = "aurora_gradient",
    )
    val particleTimeState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing), // slower speed (12s)
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora_particles",
    )

    val textColor = resolveNicknameColor(nicknameStyle.color, nicknameStyle.customColorHex, colors)
    val nicknameFontFamily = nicknameStyle.font.fontRes?.let { FontFamily(Font(it)) }
    val headlineSmall = MaterialTheme.typography.headlineSmall
    val baseStyle = remember(headlineSmall, nicknameFontFamily, nicknameStyle.fontSize) {
        headlineSmall.copy(
            fontFamily = nicknameFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = nicknameStyle.fontSize.coerceIn(14, 36).sp,
            lineHeight = (nicknameStyle.fontSize.coerceIn(14, 36) + 2).sp,
        )
    }

    val particles = remember {
        List(10) {
            AuroraParticle(
                xFraction = Math.random().toFloat(),
                cycles = (1..3).random(),
                yFraction = Math.random().toFloat(),
                size = 1.5f + Math.random().toFloat() * 2f,
                phase = Math.random().toFloat() * 2f * PI.toFloat(),
            )
        }
    }

    val brushColors = remember(colors.gradientStart, colors.accent, colors.accentVariant, colors.gradientEnd) {
        listOf(
            colors.gradientStart,
            colors.accent,
            colors.accentVariant,
            colors.gradientEnd,
        )
    }

    val auroraBrush = remember(brushColors) {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                val width = size.width.coerceAtLeast(1f)
                val height = size.height.coerceAtLeast(1f)
                val off = gradientOffsetState.value
                val startX = width * (off - 0.5f)
                val endX = width * (off + 0.5f)
                return LinearGradientShader(
                    colors = brushColors,
                    colorStops = null,
                    from = Offset(startX, 0f),
                    to = Offset(endX, height),
                    tileMode = TileMode.Clamp,
                )
            }
        }
    }

    Box(
        modifier = modifier
            .drawWithContent {
                val textWidth = size.width
                val textHeight = size.height
                val textLeft = 0f
                val textBottom = textHeight

                // Draw text content
                drawContent()

                // Draw mist particles
                val yMin = -20.dp.toPx()
                val yMax = textBottom
                val yRange = yMax - yMin
                val particleTime = particleTimeState.value

                particles.forEach { particle ->
                    val progressY = (particleTime * particle.cycles * yRange + particle.yFraction * yRange) % yRange
                    val y = yMax - progressY
                    val x =
                        textLeft + particle.xFraction * textWidth +
                            sin(particleTime * 2 * PI.toFloat() + particle.phase) * 6.dp.toPx()
                    val alpha = ((y - yMin) / yRange).coerceIn(0f, 1f) * (if (isAmoled) 0.4f else 0.7f)

                    // Draw soft particle glow (larger circle with lower alpha)
                    drawCircle(
                        color = colors.accent.copy(alpha = alpha * 0.4f),
                        radius = (particle.size * 1.8f).dp.toPx(),
                        center = Offset(x, y),
                    )
                    // Draw particle core (smaller circle with higher alpha)
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = particle.size.dp.toPx(),
                        center = Offset(x, y),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = auroraBrush,
                            blendMode = BlendMode.SrcIn,
                        )
                    },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_crown_small),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = baseStyle.copy(
                    brush = auroraBrush,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class AuroraParticle(
    val xFraction: Float,
    val cycles: Int,
    val yFraction: Float,
    val size: Float,
    val phase: Float,
)

@Composable
private fun TrinityPrismEffect(
    text: String,
    nicknameStyle: NicknameStyle,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "trinity_prism")
    val shimmerState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "trinity_prism_shimmer",
    )
    val nicknameFontFamily = nicknameStyle.font.fontRes?.let { FontFamily(Font(it)) }
    val headlineSmall = MaterialTheme.typography.headlineSmall
    val baseStyle = remember(headlineSmall, nicknameFontFamily, nicknameStyle.fontSize) {
        headlineSmall.copy(
            fontFamily = nicknameFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = nicknameStyle.fontSize.coerceIn(14, 36).sp,
            lineHeight = (nicknameStyle.fontSize.coerceIn(14, 36) + 2).sp,
        )
    }
    val prismColors = remember {
        listOf(Color(0xFF64E8FF), Color(0xFF9C7CFF), Color(0xFFFFD36E))
    }
    val prismBrush = remember(prismColors) {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                val shimmer = shimmerState.value
                return LinearGradientShader(
                    colors = prismColors,
                    colorStops = null,
                    from = Offset(120f * shimmer, 0f),
                    to = Offset(420f + 120f * shimmer, 120f),
                    tileMode = TileMode.Clamp,
                )
            }
        }
    }
    Text(
        text = text,
        modifier = modifier,
        style = baseStyle.copy(
            brush = prismBrush,
            shadow = Shadow(Color(0xFF9C7CFF).copy(alpha = 0.65f), blurRadius = 18f),
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ShadowCrownEffect(
    text: String,
    nicknameStyle: NicknameStyle,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "shadow_crown")
    val pulseState = transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "shadow_crown_pulse",
    )
    val baseStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = nicknameStyle.font.fontRes?.let { FontFamily(Font(it)) },
        fontWeight = FontWeight.Black,
        fontSize = nicknameStyle.fontSize.coerceIn(14, 36).sp,
        lineHeight = (nicknameStyle.fontSize.coerceIn(14, 36) + 2).sp,
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            Text(
                text = "♛",
                style = baseStyle.copy(
                    color = Color.Transparent,
                    shadow = Shadow(Color(0xFF5D2A9D), blurRadius = 22f),
                ),
                modifier = Modifier.graphicsLayer { alpha = pulseState.value },
            )
            Text(
                text = "♛",
                style = baseStyle.copy(
                    color = Color(0xFFB36BFF),
                ),
            )
        }
        Spacer(Modifier.width(4.dp))
        Box {
            Text(
                text = text,
                style = baseStyle.copy(
                    color = Color.Transparent,
                    shadow = Shadow(Color(0xFFB36BFF), blurRadius = 18f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = pulseState.value },
            )
            Text(
                text = text,
                style = baseStyle.copy(
                    color = Color(0xFFE7D7FF),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RankSigilsEffect(
    text: String,
    nicknameStyle: NicknameStyle,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "rank_sigils")
    val glowState = transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "rank_sigil_glow",
    )
    val baseStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = nicknameStyle.font.fontRes?.let { FontFamily(Font(it)) },
        fontWeight = FontWeight.Black,
        fontSize = nicknameStyle.fontSize.coerceIn(14, 36).sp,
        lineHeight = (nicknameStyle.fontSize.coerceIn(14, 36) + 2).sp,
    )
    val displayText = remember(text) { "◆ $text ◆" }
    Box(modifier = modifier) {
        Text(
            text = displayText,
            style = baseStyle.copy(
                color = Color.Transparent,
                shadow = Shadow(Color(0xFFFFD36E), blurRadius = 16f),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = glowState.value },
        )
        Text(
            text = displayText,
            style = baseStyle.copy(
                color = Color(0xFFFFE08A),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun toRunic(text: String): String {
    val runeMap = mapOf(
        // English
        'a' to 'ᚨ', 'b' to 'ᛒ', 'c' to 'ᚲ', 'd' to 'ᛞ', 'e' to 'ᛖ', 'f' to 'ᚠ', 'g' to 'ᚷ',
        'h' to 'ᚺ', 'i' to 'ᛁ', 'j' to 'ᛃ', 'k' to 'ᚲ', 'l' to 'ᛚ', 'm' to 'ᛗ', 'n' to 'ᚾ',
        'o' to 'ᛟ', 'p' to 'ᛈ', 'q' to 'ᚲ', 'r' to 'ᚱ', 's' to 'ᛊ', 't' to 'ᛏ', 'u' to 'ᚢ',
        'v' to 'ᚢ', 'w' to 'ᚹ', 'x' to 'ᛘ', 'y' to 'ᛦ', 'z' to 'ᛉ',
        'A' to 'ᚨ', 'B' to 'ᛒ', 'C' to 'ᚲ', 'D' to 'ᛞ', 'E' to 'ᛖ', 'F' to 'ᚠ', 'G' to 'ᚷ',
        'H' to 'ᚺ', 'I' to 'ᛁ', 'J' to 'ᛃ', 'K' to 'ᚲ', 'L' to 'ᛚ', 'M' to 'ᛗ', 'N' to 'ᚾ',
        'O' to 'ᛟ', 'P' to 'ᛈ', 'Q' to 'ᚲ', 'R' to 'ᚱ', 'S' to 'ᛊ', 'T' to 'ᛏ', 'U' to 'ᚢ',
        'V' to 'ᚢ', 'W' to 'ᚹ', 'X' to 'ᛘ', 'Y' to 'ᛦ', 'Z' to 'ᛉ',
        // Cyrillic (Russian)
        'а' to 'ᚨ', 'б' to 'ᛒ', 'в' to 'ᚹ', 'г' to 'ᚷ', 'д' to 'ᛞ', 'е' to 'ᛖ', 'ё' to 'ᛖ',
        'ж' to 'ᛉ', 'з' to 'ᛉ', 'и' to 'ᛁ', 'й' to 'ᛁ', 'к' to 'ᚲ', 'л' to 'ᛚ', 'м' to 'ᛗ',
        'н' to 'ᚾ', 'о' to 'ᛟ', 'п' to 'ᛈ', 'р' to 'ᚱ', 'с' to 'ᛊ', 'т' to 'ᛏ', 'у' to 'ᚢ',
        'ф' to 'ᚠ', 'х' to 'ᚺ', 'ц' to 'ᛏ', 'ч' to 'ᚲ', 'ш' to 'ᛊ', 'щ' to 'ᛊ', 'ъ' to 'ᛁ',
        'ы' to 'ᛁ', 'ь' to 'ᛁ', 'э' to 'ᛖ', 'ю' to 'ᚢ', 'я' to 'ᛦ',
        'А' to 'ᚨ', 'Б' to 'ᛒ', 'В' to 'ᚹ', 'Г' to 'ᚷ', 'Д' to 'ᛞ', 'Е' to 'ᛖ', 'Ё' to 'ᛖ',
        'Ж' to 'ᛉ', 'З' to 'ᛉ', 'И' to 'ᛁ', 'Й' to 'ᛁ', 'К' to 'ᚲ', 'Л' to 'ᛚ', 'М' to 'ᛗ',
        'Н' to 'ᚾ', 'О' to 'ᛟ', 'П' to 'ᛈ', 'Р' to 'ᚱ', 'С' to 'ᛊ', 'Т' to 'ᛏ', 'У' to 'ᚢ',
        'Ф' to 'ᚠ', 'Х' to 'ᚺ', 'Ц' to 'ᛏ', 'Ч' to 'ᚲ', 'Ш' to 'ᛊ', 'Щ' to 'ᛊ', 'Ъ' to 'ᛁ',
        'Ы' to 'ᛁ', 'Ь' to 'ᛁ', 'Э' to 'ᛖ', 'Ю' to 'ᚢ', 'Я' to 'ᛦ',
    )
    return text.map { runeMap[it] ?: it }.joinToString("")
}

@Composable
internal fun NicknameBadgeDecorator(
    badgeStyleKey: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (badgeStyleKey == "none") {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            content()
        }
        return
    }

    val imageResId = when (badgeStyleKey) {
        "trinity" -> R.drawable.ic_reward_badge_trinity
        "finisher" -> R.drawable.ic_reward_badge_finisher
        "immersion" -> R.drawable.ic_reward_badge_immersion
        "ascendant" -> R.drawable.ic_reward_nickname_rank_sigils
        else -> null
    }

    if (imageResId != null) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(id = imageResId),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.Unspecified,
            )
            Spacer(modifier = Modifier.width(6.dp))
            content()
        }
        return
    }

    val shurikenPath = remember { createShurikenPath() }

    val transition = rememberInfiniteTransition(label = "nickname_badge")
    val timeState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "badge_time",
    )

    val effectiveBadgeStyleKey = when (badgeStyleKey) {
        "trinity" -> "orbit"
        "finisher" -> "crown"
        "immersion" -> "orbit"
        "ascendant" -> "crown"
        else -> badgeStyleKey
    }

    when (effectiveBadgeStyleKey) {
        "orbit" -> {
            // Intersecting 3D atomic orbits with comets (lead nodes + trails) running along them
            Box(
                modifier = modifier.drawWithContent {
                    val rx = (size.width / 2f - 11.dp.toPx()).coerceAtLeast(12.dp.toPx())
                    val ry = 3.5.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // 3 elliptical planes with tilts representing an atom model (diagonal L, diagonal R, vertical)
                    val tilt1 = 0.55f // ~30 deg Left
                    val tilt2 = -0.55f // ~30 deg Right
                    val tilt3 = 1.35f // ~77 deg Vertical-ish

                    // Atom model colors (Cyan, Green-Teal, Electric Green)
                    val orbitColor1 = Color(0xFF00E5FF)
                    val orbitColor2 = Color(0xFF00FFCC)
                    val orbitColor3 = Color(0xFF00FF66)

                    // Lead angles (1x/2x integer multipliers for seamless 2*PI wrap, all clockwise)
                    val time = timeState.value
                    val theta1 = time
                    val theta2 = time + (PI.toFloat() / 3f)
                    val theta3 = time * 2.0f + (PI.toFloat() * 2f / 3f)

                    // Z coordinates for 3D sorting
                    val z1 = sin(theta1)
                    val z2 = sin(theta2)
                    val z3 = sin(theta3)

                    val trailRadiusPx = 3.dp.toPx()

                    // 1. Draw all back orbit paths and nodes (z < 0)
                    if (z1 < 0) drawNodeWithTrail(center, rx, ry, theta1, tilt1, orbitColor1, false, trailRadiusPx)
                    if (z2 < 0) drawNodeWithTrail(center, rx, ry, theta2, tilt2, orbitColor2, false, trailRadiusPx)
                    if (z3 < 0) drawNodeWithTrail(center, rx, ry, theta3, tilt3, orbitColor3, true, trailRadiusPx)

                    // 2. Draw nickname text content
                    drawContent()

                    // 3. Draw all front nodes (z >= 0)
                    if (z1 >= 0) drawNodeWithTrail(center, rx, ry, theta1, tilt1, orbitColor1, false, trailRadiusPx)
                    if (z2 >= 0) drawNodeWithTrail(center, rx, ry, theta2, tilt2, orbitColor2, false, trailRadiusPx)
                    if (z3 >= 0) drawNodeWithTrail(center, rx, ry, theta3, tilt3, orbitColor3, true, trailRadiusPx)
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                    content()
                }
            }
        }
        "crown" -> {
            // Royal gold shimmer sweep (uses SrcAtop so nickname is fully visible from start)
            val goldShimmerState = transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "gold_shimmer",
            )

            Box(
                modifier = modifier
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()

                        val width = size.width
                        val height = size.height
                        val sweepDist = width * 2f
                        val startX = -width + goldShimmerState.value * sweepDist

                        val goldColors = listOf(
                            Color.Transparent,
                            Color(0xFFFFD700).copy(alpha = 0.5f), // Royal Gold
                            Color.White.copy(alpha = 0.9f), // Bright Glint Shine
                            Color(0xFFFFD700).copy(alpha = 0.5f),
                            Color.Transparent,
                        )

                        drawRect(
                            brush = Brush.linearGradient(
                                colors = goldColors,
                                start = Offset(startX, 0f),
                                end = Offset(startX + width * 0.4f, height),
                            ),
                            blendMode = BlendMode.SrcAtop, // Preserves text visibility throughout the animation
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
        "shuriken" -> {
            // Metallic shurikens slowly spinning and floating around the nickname
            val bobValState = transition.animateFloat(
                initialValue = 0f,
                targetValue = 2f * PI.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shuriken_bob",
            )
            val rotationAngleState = transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(12000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shuriken_rotation",
            )

            Box(
                modifier = modifier.drawWithContent {
                    drawContent()

                    val width = size.width
                    val height = size.height

                    val bobVal = bobValState.value
                    val bobOffset1 = sin(bobVal) * 2.5f.dp.toPx()
                    val bobOffset2 = cos(bobVal) * 2.5f.dp.toPx()
                    val rotationAngle = rotationAngleState.value

                    drawShurikenOnCanvas(
                        shurikenPath = shurikenPath,
                        cx = -6.dp.toPx(),
                        cy = height / 2f + bobOffset1,
                        radius = 6.dp.toPx(),
                        angle = rotationAngle,
                    )

                    drawShurikenOnCanvas(
                        shurikenPath = shurikenPath,
                        cx = width + 6.dp.toPx(),
                        cy = height / 2f + bobOffset2,
                        radius = 6.dp.toPx(),
                        angle = -rotationAngle - 45f,
                    )
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                    content()
                }
            }
        }
        else -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

private fun DrawScope.drawGoldenSparkle(cx: Float, cy: Float, size: Float, alpha: Float) {
    val starColor = Color(0xFFFFF6D6)
    val glowColor = Color(0xFFFFD700)

    drawCircle(
        color = glowColor.copy(alpha = alpha * 0.3f),
        radius = size * 2.2f,
        center = Offset(cx, cy),
    )

    val path = Path().apply {
        moveTo(cx, cy - size)
        quadraticTo(cx, cy, cx + size, cy)
        quadraticTo(cx, cy, cx, cy + size)
        quadraticTo(cx, cy, cx - size, cy)
        quadraticTo(cx, cy, cx, cy - size)
        close()
    }
    drawPath(path, color = starColor.copy(alpha = alpha))
}

private fun DrawScope.drawPrismaticGlint(cx: Float, cy: Float, size: Float, alpha: Float) {
    if (alpha <= 0f) return
    // Bright white core (glass reflection point)
    drawCircle(
        color = Color.White.copy(alpha = alpha * 0.95f),
        radius = 1.8.dp.toPx(),
        center = Offset(cx, cy),
    )
    // Soft outer white glow (glass reflection halo)
    drawCircle(
        color = Color.White.copy(alpha = alpha * 0.3f),
        radius = 4.dp.toPx(),
        center = Offset(cx, cy),
    )
}

private fun DrawScope.drawShurikenOnCanvas(
    shurikenPath: Path,
    cx: Float,
    cy: Float,
    radius: Float,
    angle: Float,
) {
    val darkSteel = Color(0xFF2C3E50)
    val silverEdge = Color(0xFFBDC3C7)

    withTransform({
        translate(cx, cy)
        rotate(angle, pivot = Offset.Zero)
        scale(radius, radius, pivot = Offset.Zero)
    }) {
        drawPath(path = shurikenPath, color = darkSteel)
        drawPath(
            path = shurikenPath,
            color = silverEdge,
            style = Stroke(width = 1.dp.toPx() / radius),
        )
    }
    drawCircle(
        color = Color.Black.copy(alpha = 0.4f),
        radius = radius * 0.15f,
        center = Offset(cx, cy),
    )
}

@Composable
internal fun AvatarFrameDecorations(
    styleKey: String,
    accentColor: Color,
) {
    if (styleKey == "none") return

    val transition = rememberInfiniteTransition(label = "avatar_frame")
    val spinState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "avatar_frame_spin",
    )
    val pulseState = transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avatar_frame_pulse",
    )
    val scanlineState = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avatar_frame_scanline",
    )

    // 1. Neon Hue Shift Animation
    val neonHueShiftState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "neon_hue_shift",
    )

    // 2. Hologram Sonar Expansion Animation
    val hologramSonarState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "hologram_sonar",
    )

    // 3. Prismatic Rainbow Rotation Animation
    val rainbowShiftState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "prismatic_rainbow",
    )

    val density = LocalDensity.current
    val relicRadialBrush = remember(density) {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - with(density) { 0.75.dp.toPx() }
                return RadialGradientShader(
                    colors = listOf(
                        Color(0xFF9C7CFF).copy(alpha = 0.12f * pulseState.value),
                        Color.Transparent,
                    ),
                    colorStops = null,
                    center = center,
                    radius = radius * 1.5f,
                    tileMode = TileMode.Clamp,
                )
            }
        }
    }

    val ascendantRadialBrush = remember(density) {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - with(density) { 2.dp.toPx() }
                return RadialGradientShader(
                    colors = listOf(Color(0xFFFFD36E).copy(alpha = 0.18f * pulseState.value), Color.Transparent),
                    colorStops = null,
                    center = center,
                    radius = radius * 1.7f,
                    tileMode = TileMode.Clamp,
                )
            }
        }
    }

    val holoBrush = remember {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                val center = Offset(size.width / 2f, size.height / 2f)
                return SweepGradientShader(
                    colors = HOLO_COLORS,
                    colorStops = null,
                    center = center,
                )
            }
        }
    }

    val rainbowBrush = remember {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                val center = Offset(size.width / 2f, size.height / 2f)
                return SweepGradientShader(
                    colors = RAINBOW_COLORS,
                    colorStops = null,
                    center = center,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        when (styleKey) {
            "trinity_orbit" -> {
                // Three-media orbital relic frame: cyan / violet / gold arcs with travelling nodes.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()

                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.minDimension / 2f - 0.75.dp.toPx()
                            val spin = spinState.value
                            val pulse = pulseState.value

                            repeat(3) { index ->
                                val orbitColor = TRINITY_ORBIT_COLORS[index]
                                val tilt = TRINITY_TILTS[index]
                                val orbitRadius = radius * (1f - index * 0.035f)
                                val angle = spin + index * 120f
                                rotate(tilt, pivot = center) {
                                    drawCircle(
                                        color = orbitColor.copy(alpha = 0.24f * pulse),
                                        radius = orbitRadius,
                                        center = center,
                                        style = Stroke(width = (1.55f + index * 0.25f).dp.toPx()),
                                    )
                                }

                                val rad = Math.toRadians(angle.toDouble())
                                val node = Offset(
                                    x = center.x + kotlin.math.cos(rad).toFloat() * orbitRadius,
                                    y = center.y + kotlin.math.sin(rad).toFloat() * orbitRadius,
                                )
                                drawCircle(
                                    color = orbitColor.copy(alpha = 0.34f * pulse),
                                    radius = 5.5.dp.toPx(),
                                    center = node,
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.86f),
                                    radius = 2.25.dp.toPx(),
                                    center = node,
                                )
                            }

                            drawCircle(
                                brush = relicRadialBrush,
                                radius = radius * 1.5f,
                                center = center,
                            )
                        },
                )
            }
            "deep_archive" -> {
                // Archive-grade frame: quiet blue-gold illuminated manuscript ring.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()

                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.minDimension / 2f - 2.dp.toPx()
                            val teal = Color(0xFF5DE7D8)
                            val archiveGold = Color(0xFFFFD36E)
                            val ink = Color(0xFF0B1028)
                            val pulse = pulseState.value

                            drawCircle(
                                color = ink.copy(alpha = 0.18f),
                                radius = radius + 1.5.dp.toPx(),
                                center = center,
                                style = Stroke(width = 5.dp.toPx()),
                            )
                            drawCircle(
                                color = teal.copy(alpha = 0.30f * pulse),
                                radius = radius,
                                center = center,
                                style = Stroke(width = 2.4.dp.toPx()),
                            )
                            drawCircle(
                                color = archiveGold.copy(alpha = 0.40f),
                                radius = radius - 5.dp.toPx(),
                                center = center,
                                style = Stroke(width = 1.dp.toPx()),
                            )

                            repeat(12) { i ->
                                val angle = Math.toRadians((i * 30f).toDouble())
                                val inner = radius - 8.dp.toPx()
                                val outer = radius - 2.dp.toPx()
                                val start = Offset(
                                    center.x + kotlin.math.cos(angle).toFloat() * inner,
                                    center.y + kotlin.math.sin(angle).toFloat() * inner,
                                )
                                val end = Offset(
                                    center.x + kotlin.math.cos(angle).toFloat() * outer,
                                    center.y + kotlin.math.sin(angle).toFloat() * outer,
                                )
                                drawLine(
                                    color = if (i % 3 == 0) {
                                        archiveGold.copy(alpha = 0.42f)
                                    } else {
                                        teal.copy(alpha = 0.28f)
                                    },
                                    start = start,
                                    end = end,
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        },
                )
            }
            "hybrid_scroll" -> {
                // Synchronized hybrid frame: left digital arc + right manuscript arc, deliberately separated.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()

                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.minDimension / 2f - 1.5.dp.toPx()
                            val animeBlue = Color(0xFF40C4FF)
                            val scrollGold = Color(0xFFFFB86B)
                            val violet = Color(0xFFB388FF)
                            val rotation = spinState.value // full 0..360 cycle; seamless at loop boundary
                            val pulse = pulseState.value

                            rotate(rotation, pivot = center) {
                                // Left / digital half. Kept away from the right half with a clear top/bottom gap.
                                drawArc(
                                    color = animeBlue.copy(alpha = 0.58f * pulse),
                                    startAngle = 128f,
                                    sweepAngle = 104f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                                )
                                // Right / manuscript half, synchronized to the same transform so it never drifts into the other half.
                                drawArc(
                                    color = scrollGold.copy(alpha = 0.68f),
                                    startAngle = -52f,
                                    sweepAngle = 104f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                                )
                            }

                            drawCircle(
                                color = violet.copy(alpha = 0.18f),
                                radius = radius - 6.dp.toPx(),
                                center = center,
                                style = Stroke(width = 1.dp.toPx()),
                            )

                            // Manuscript strokes on the right side only.
                            repeat(4) { i ->
                                val y = center.y - radius * 0.48f + i * radius * 0.32f
                                drawLine(
                                    color = scrollGold.copy(alpha = 0.20f),
                                    start = Offset(center.x + radius * 0.24f, y),
                                    end = Offset(center.x + radius * 0.66f, y),
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                            // Digital scan marks on the left side only.
                            repeat(4) { i ->
                                val y = center.y - radius * 0.48f + i * radius * 0.32f
                                drawLine(
                                    color = animeBlue.copy(alpha = 0.22f),
                                    start = Offset(center.x - radius * 0.66f, y),
                                    end = Offset(center.x - radius * 0.24f, y),
                                    strokeWidth = 1.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        },
                )
            }
            "ascendant" -> {
                // Mythic prestige halo: white-gold compass rays and slow crown ticks.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()

                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.minDimension / 2f - 2.dp.toPx()
                            val whiteGold = Color(0xFFFFF8D6)
                            val gold = Color(0xFFFFD36E)
                            val amber = Color(0xFFFFA726)
                            val pulse = pulseState.value
                            val spin = spinState.value

                            drawCircle(
                                brush = ascendantRadialBrush,
                                radius = radius * 1.7f,
                                center = center,
                            )
                            drawCircle(
                                color = whiteGold.copy(alpha = 0.72f),
                                radius = radius,
                                center = center,
                                style = Stroke(width = 2.dp.toPx()),
                            )
                            drawCircle(
                                color = amber.copy(alpha = 0.48f * pulse),
                                radius = radius - 5.dp.toPx(),
                                center = center,
                                style = Stroke(width = 1.dp.toPx()),
                            )

                            repeat(16) { i ->
                                val angle = Math.toRadians((i * 22.5f + spin).toDouble())
                                val longTick = i % 4 == 0
                                val inner = radius - if (longTick) 10.dp.toPx() else 6.dp.toPx()
                                val outer = radius + if (longTick) 4.dp.toPx() else 1.dp.toPx()
                                drawLine(
                                    color = if (longTick) whiteGold.copy(alpha = 0.82f) else gold.copy(alpha = 0.50f),
                                    start = Offset(
                                        center.x + kotlin.math.cos(angle).toFloat() * inner,
                                        center.y + kotlin.math.sin(angle).toFloat() * inner,
                                    ),
                                    end = Offset(
                                        center.x + kotlin.math.cos(angle).toFloat() * outer,
                                        center.y + kotlin.math.sin(angle).toFloat() * outer,
                                    ),
                                    strokeWidth = if (longTick) 1.5.dp.toPx() else 1.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        },
                )
            }
            "neon" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()

                            val spin = spinState.value
                            val pulse = pulseState.value
                            val neonHueShift = neonHueShiftState.value

                            // Intense glowing neon light tube that shifts colors with a chasing light effect
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(accentColor.toArgb(), hsv)
                            hsv[0] = (hsv[0] + neonHueShift) % 360f
                            hsv[1] = 0.95f // Max saturation for vibrant neon look
                            hsv[2] = 1.0f // Max brightness/value
                            val baseNeonColor = Color(android.graphics.Color.HSVToColor(hsv))

                            // Add a micro-flicker to simulate a real neon tube
                            val flicker = 0.92f + 0.08f * sin(spin * 15f)
                            val neonColor = baseNeonColor.copy(alpha = baseNeonColor.alpha * flicker)

                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f - 1.5.dp.toPx()

                            // Outer Neon Glow 1 (thick, faint)
                            drawCircle(
                                color = neonColor.copy(alpha = 0.12f * pulse),
                                radius = radius,
                                center = center,
                                style = Stroke(width = 6.dp.toPx()),
                            )

                            // Outer Neon Glow 2 (medium, stronger)
                            drawCircle(
                                color = neonColor.copy(alpha = 0.35f * pulse),
                                radius = radius,
                                center = center,
                                style = Stroke(width = 4.dp.toPx()),
                            )

                            // Neon Core Tube
                            drawCircle(
                                color = Color.White.copy(alpha = 0.9f),
                                radius = radius,
                                center = center,
                                style = Stroke(width = 1.5.dp.toPx()),
                            )

                            // Chasing energy light nodes (contained to avoid bleed artifacts)
                            repeat(3) { index ->
                                val angle = spin + index * 120f
                                val rad = Math.toRadians(angle.toDouble())
                                val cx = center.x + radius * cos(rad).toFloat()
                                val cy = center.y + radius * sin(rad).toFloat()

                                // Node core only — no large glow circle that bleeds outside the ring
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.9f),
                                    radius = 2.5.dp.toPx(),
                                    center = Offset(cx, cy),
                                )
                            }
                        },
                )
            }
            "hologram" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()

                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f - 1.5.dp.toPx()
                            val holoColor = Color(0xFFFF33B4) // Pink color
                            val spin = spinState.value
                            val pulse = pulseState.value

                            // Continuous holographic sweep gradient pink ring
                            rotate(-spin, pivot = center) {
                                drawCircle(
                                    brush = holoBrush,
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }

                            // Solid inner protective glow ring
                            drawCircle(
                                color = holoColor.copy(alpha = 0.15f * pulse),
                                radius = radius - 2.dp.toPx(),
                                center = center,
                                style = Stroke(width = 1.dp.toPx()),
                            )

                            // Draw small digital tick boxes rotating around (1x spin multiplier for seamless loop)
                            val tickCount = 4
                            for (i in 0 until tickCount) {
                                val angle = -spin * 1.0f + i * (360f / tickCount)
                                val rad = Math.toRadians(angle.toDouble())
                                val tx = center.x + radius * cos(rad).toFloat()
                                val ty = center.y + radius * sin(rad).toFloat()

                                drawRect(
                                    color = holoColor.copy(alpha = 0.65f),
                                    topLeft = Offset(tx - 1.5.dp.toPx(), ty - 1.5.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(3.dp.toPx(), 3.dp.toPx()),
                                )
                            }
                        },
                )
            }
            "prismatic" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()

                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f - 2.5.dp.toPx()
                            val rainbowShift = rainbowShiftState.value
                            val spin = spinState.value

                            // Draw rotating rainbow ring (seamless 1x spin)
                            rotate(rainbowShift, pivot = center) {
                                drawCircle(
                                    brush = rainbowBrush,
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = 2.5.dp.toPx()),
                                )
                            }

                            // Draw random glints/flares around the frame (sequentially, not too frequently)
                            val flareCount = 4
                            for (i in 0 until flareCount) {
                                val phase = (spin + i * 90f) % 360f
                                val activeRange = 120f
                                if (phase < activeRange) {
                                    val progress = phase / activeRange
                                    val alpha = sin(progress * PI.toFloat())
                                    val angleRad = Math.toRadians(FLARE_POSITIONS[i].toDouble())
                                    val fx = center.x + radius * cos(angleRad).toFloat()
                                    val fy = center.y + radius * sin(angleRad).toFloat()

                                    drawPrismaticGlint(fx, fy, 6.dp.toPx(), alpha)
                                }
                            }
                        },
                )
            }
        }
    }
}
