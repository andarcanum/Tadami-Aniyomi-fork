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
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
        this == NicknameEffectPreset.Cipher

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
    val time by infiniteTransition.animateFloat(
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

    val (isBurst, burstIntensity) = remember(time) {
        when {
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
    }

    val leftColor = remember(textColor, isAmoled) {
        textColor.shiftHue(-45f).copy(alpha = if (isAmoled) 0.5f else 0.7f)
    }
    val rightColor = remember(textColor, isAmoled) {
        textColor.shiftHue(45f).copy(alpha = if (isAmoled) 0.5f else 0.7f)
    }

    val scrambleText = if (!isBurst) {
        text
    } else {
        val frame = (time * 50f).toInt()
        val noiseChars = charArrayOf('█', '▓', '░', '▰', '§', '⟡', '⌬', '⚡', '✖', '▚', '▞', '▩')
        val sb = StringBuilder(text)
        if (text.isNotEmpty()) {
            val rng = kotlin.random.Random(frame.toLong())
            val numScrambles = (1..2).random(rng).coerceAtMost(text.length)
            repeat(numScrambles) {
                val idx = rng.nextInt(text.length)
                sb[idx] = noiseChars[rng.nextInt(noiseChars.size)]
            }
        }
        sb.toString()
    }

    val textAlpha = if (isBurst && (time * 50f).toInt() % 3 == 0) 0.5f else 1.0f

    fun buildGlitchString(textColorVal: Color): AnnotatedString {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = textColorVal)) {
                append(scrambleText)
            }
        }
    }

    val leftAlpha = if (isBurst) (0.5f + 0.3f * burstIntensity) else 0.35f
    val rightAlpha = if (isBurst) (0.5f + 0.3f * burstIntensity) else 0.35f

    val leftOffsetX = if (isBurst) {
        (-3.5f - sin(time * 60f) * 5f * burstIntensity)
    } else {
        -1.2f - sin(time * 2 * PI.toFloat()) * 0.4f
    }
    val leftOffsetY = if (isBurst) {
        (cos(time * 50f) * 2f * burstIntensity)
    } else {
        cos(time * 2 * PI.toFloat()) * 0.2f
    }

    val rightOffsetX = if (isBurst) {
        (3.5f + sin(time * 65f) * 5f * burstIntensity)
    } else {
        1.2f + sin(time * 2 * PI.toFloat()) * 0.4f
    }
    val rightOffsetY = if (isBurst) {
        (-cos(time * 45f) * 2f * burstIntensity)
    } else {
        -cos(time * 2 * PI.toFloat()) * 0.2f
    }

    val shakeX = if (isBurst) (sin(time * 40f) * 4f * burstIntensity) else 0f
    val shakeY = if (isBurst) (cos(time * 30f) * 2.5f * burstIntensity) else 0f

    val centerString = buildGlitchString(textColorVal = textColor.copy(alpha = textAlpha))
    val leftString = buildGlitchString(textColorVal = leftColor.copy(alpha = leftColor.alpha * leftAlpha))
    val rightString = buildGlitchString(textColorVal = rightColor.copy(alpha = rightColor.alpha * rightAlpha))

    Box(
        modifier = modifier
            .drawWithContent {
                drawContent()
                if (isBurst) {
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
            modifier = Modifier.offset(x = leftOffsetX.dp, y = leftOffsetY.dp),
        )
        Text(
            text = rightString,
            style = baseStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(x = rightOffsetX.dp, y = rightOffsetY.dp),
        )
        Text(
            text = centerString,
            style = baseStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(x = shakeX.dp, y = shakeY.dp),
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
    val time by infiniteTransition.animateFloat(
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

    Box(
        modifier = modifier
            .drawWithContent {
                val actualTextWidth = size.width

                val rx = (actualTextWidth / 2f + 12.dp.toPx()).coerceAtLeast(40.dp.toPx())
                val ry = 12.dp.toPx()
                val alphaRad = 0.22f // 12.6° tilt
                val thetaBase = time

                val particles = glyphs.indices.take(8).map { i ->
                    val theta = thetaBase + (2f * PI.toFloat() * i / 8)
                    val x = rx * cos(theta)
                    val y = ry * sin(theta) + x * sin(alphaRad)
                    val z = sin(theta)
                    val scale = 0.5f + 0.5f * (z + 1f) / 2f
                    val alpha = (0.15f + 0.85f * (z + 1f) / 2f).coerceIn(0f, 1f) * (if (isAmoled) 0.5f else 1.0f)
                    ParticleData(glyphs[i % n], x, y, z, scale, alpha)
                }

                // Draw back particles (z < 0)
                particles.filter { it.z < 0 }.sortedBy { it.z }.forEach { p ->
                    drawGlyph(p, textMeasurer, glyphGlowColor, density)
                }
                // Draw text content
                drawContent()
                // Draw front particles (z >= 0)
                particles.filter { it.z >= 0 }.sortedByDescending { it.z }.forEach { p ->
                    drawGlyph(p, textMeasurer, glyphGlowColor, density)
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
)

private data class PrismaticSparkle(
    val angle: Double,
    val phaseOffset: Double,
    val sizeDp: Float,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlyph(
    particle: ParticleData,
    textMeasurer: TextMeasurer,
    glyphGlowColor: Color,
    density: androidx.compose.ui.unit.Density,
) {
    val cx = size.width / 2f + particle.x
    val cy = size.height / 2f + particle.y
    val textStr = particle.glyph.toString()

    val glowStyle = TextStyle(
        color = glyphGlowColor.copy(alpha = particle.alpha),
        fontSize = 14.sp * particle.scale,
        shadow = Shadow(
            color = glyphGlowColor.copy(alpha = particle.alpha * 0.8f),
            blurRadius = with(density) { 8.dp.toPx() } * particle.scale,
        ),
    )
    val glowLayoutResult = textMeasurer.measure(text = textStr, style = glowStyle)
    drawText(
        textLayoutResult = glowLayoutResult,
        topLeft = Offset(
            x = cx - glowLayoutResult.size.width / 2f,
            y = cy - glowLayoutResult.size.height / 2f,
        ),
    )

    val coreStyle = TextStyle(
        color = Color.White.copy(alpha = particle.alpha),
        fontSize = 14.sp * particle.scale,
        shadow = Shadow(
            color = glyphGlowColor.copy(alpha = particle.alpha * 0.8f),
            blurRadius = with(density) { 3.dp.toPx() } * particle.scale,
        ),
    )
    val coreLayoutResult = textMeasurer.measure(text = textStr, style = coreStyle)
    drawText(
        textLayoutResult = coreLayoutResult,
        topLeft = Offset(
            x = cx - coreLayoutResult.size.width / 2f,
            y = cy - coreLayoutResult.size.height / 2f,
        ),
    )
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
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing), // slower speed (12s)
            repeatMode = RepeatMode.Reverse, // seamless back and forth gradient loop
        ),
        label = "aurora_gradient",
    )
    val particleTime by infiniteTransition.animateFloat(
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
    val baseStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = nicknameFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = nicknameStyle.fontSize.coerceIn(14, 36).sp,
        lineHeight = (nicknameStyle.fontSize.coerceIn(14, 36) + 2).sp,
    )

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

    val brushColors = listOf(
        colors.gradientStart,
        colors.accent,
        colors.accentVariant,
        colors.gradientEnd,
    )

    val auroraBrush = remember(brushColors, gradientOffset) {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                val width = size.width.coerceAtLeast(1f)
                val height = size.height.coerceAtLeast(1f)
                val off = gradientOffset
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

    val transition = rememberInfiniteTransition(label = "nickname_badge")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "badge_time",
    )

    when (badgeStyleKey) {
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
                    val theta1 = time
                    val theta2 = time + (PI.toFloat() / 3f)
                    val theta3 = time * 2.0f + (PI.toFloat() * 2f / 3f)

                    // Z coordinates for 3D sorting
                    val z1 = sin(theta1)
                    val z2 = sin(theta2)
                    val z3 = sin(theta3)

                    val orbitStrokeWidth = 1.dp.toPx()

                    fun drawOrbitPath(tilt: Float, orbitColor: Color, isVertical: Boolean = false) {
                        val path = Path()
                        val steps = 80
                        val currentRx = if (isVertical) rx * 0.75f else rx
                        val currentRy = if (isVertical) ry * 1.8f else ry
                        for (i in 0..steps) {
                            val t = (2f * PI.toFloat() * i / steps)
                            val px = currentRx * cos(t)
                            val py = currentRy * sin(t) + px * sin(tilt)
                            val screenX = center.x + px
                            val screenY = center.y + py
                            if (i == 0) {
                                path.moveTo(screenX, screenY)
                            } else {
                                path.lineTo(screenX, screenY)
                            }
                        }
                        // Visually hidden per request, but path calculation is kept in code
                        /*
                        drawPath(
                            path = path,
                            color = orbitColor.copy(alpha = 0.15f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = orbitStrokeWidth),
                        )
                         */
                    }

                    fun drawNodeWithTrail(theta: Float, tilt: Float, orbitColor: Color, isVertical: Boolean = false) {
                        val trailLength = 15
                        val currentRx = if (isVertical) rx * 0.75f else rx
                        val currentRy = if (isVertical) ry * 1.8f else ry

                        for (step in 0 until trailLength) {
                            // Steps are closer (0.04 rad) for a continuous-looking trail
                            val trailTheta = theta - step * 0.04f
                            val tx = currentRx * cos(trailTheta)
                            val ty = currentRy * sin(trailTheta) + tx * sin(tilt)
                            val tz = sin(trailTheta)

                            val cx = center.x + tx
                            val cy = center.y + ty
                            val zScale = 0.55f + 0.45f * (tz + 1f) / 2f
                            val zAlpha = 0.15f + 0.85f * (tz + 1f) / 2f

                            val trailDecay = 1f - step / trailLength.toFloat()
                            val scale = zScale * (0.4f + 0.6f * trailDecay)
                            val alpha = zAlpha * trailDecay * trailDecay * 0.65f
                            val radius = 3.dp.toPx() * scale

                            if (alpha > 0.02f) {
                                // Draw only colored glowing particles, no central white glow cores
                                drawCircle(
                                    color = orbitColor.copy(alpha = alpha),
                                    radius = radius,
                                    center = Offset(cx, cy),
                                )
                            }
                        }
                    }

                    // 1. Draw all back orbit paths and nodes (z < 0)
                    drawOrbitPath(tilt1, orbitColor1)
                    drawOrbitPath(tilt2, orbitColor2)
                    drawOrbitPath(tilt3, orbitColor3, isVertical = true)

                    if (z1 < 0) drawNodeWithTrail(theta1, tilt1, orbitColor1)
                    if (z2 < 0) drawNodeWithTrail(theta2, tilt2, orbitColor2)
                    if (z3 < 0) drawNodeWithTrail(theta3, tilt3, orbitColor3, isVertical = true)

                    // 2. Draw nickname text content
                    drawContent()

                    // 3. Draw all front nodes (z >= 0)
                    if (z1 >= 0) drawNodeWithTrail(theta1, tilt1, orbitColor1)
                    if (z2 >= 0) drawNodeWithTrail(theta2, tilt2, orbitColor2)
                    if (z3 >= 0) drawNodeWithTrail(theta3, tilt3, orbitColor3, isVertical = true)
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
            val goldShimmer by transition.animateFloat(
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
                        val startX = -width + goldShimmer * sweepDist

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
            val bobVal by transition.animateFloat(
                initialValue = 0f,
                targetValue = 2f * PI.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shuriken_bob",
            )
            val rotationAngle by transition.animateFloat(
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

                    val bobOffset1 = sin(bobVal) * 2.5f.dp.toPx()
                    val bobOffset2 = cos(bobVal) * 2.5f.dp.toPx()

                    drawShurikenOnCanvas(
                        cx = -6.dp.toPx(),
                        cy = height / 2f + bobOffset1,
                        radius = 6.dp.toPx(),
                        angle = rotationAngle,
                    )

                    drawShurikenOnCanvas(
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

private fun DrawScope.drawShurikenOnCanvas(cx: Float, cy: Float, radius: Float, angle: Float) {
    val darkSteel = Color(0xFF2C3E50)
    val silverEdge = Color(0xFFBDC3C7)

    rotate(angle, pivot = Offset(cx, cy)) {
        val path = Path().apply {
            val innerRadius = radius * 0.35f
            moveTo(cx, cy - radius)
            quadraticTo(cx, cy, cx + innerRadius, cy - innerRadius)
            lineTo(cx + radius, cy)
            quadraticTo(cx, cy, cx + innerRadius, cy + innerRadius)
            lineTo(cx, cy + radius)
            quadraticTo(cx, cy, cx - innerRadius, cy + innerRadius)
            lineTo(cx - radius, cy)
            quadraticTo(cx, cy, cx - innerRadius, cy - innerRadius)
            close()
        }

        drawPath(path = path, color = darkSteel)
        drawPath(
            path = path,
            color = silverEdge,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            radius = radius * 0.15f,
            center = Offset(cx, cy),
        )
    }
}

@Composable
internal fun AvatarFrameDecorations(
    styleKey: String,
    accentColor: Color,
) {
    if (styleKey == "none") return

    val transition = rememberInfiniteTransition(label = "avatar_frame")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "avatar_frame_spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avatar_frame_pulse",
    )
    val scanline by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avatar_frame_scanline",
    )

    // 1. Neon Hue Shift Animation
    val neonHueShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "neon_hue_shift",
    )

    // 2. Hologram Sonar Expansion Animation
    val hologramSonar by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "hologram_sonar",
    )

    // 3. Prismatic Rainbow Rotation Animation
    val rainbowShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "prismatic_rainbow",
    )

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        when (styleKey) {
            "neon" -> {
                // Intense glowing neon light tube that shifts colors with a chasing light effect
                val baseNeonColor = remember(accentColor, neonHueShift) {
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(accentColor.toArgb(), hsv)
                    hsv[0] = (hsv[0] + neonHueShift) % 360f
                    hsv[1] = 0.95f // Max saturation for vibrant neon look
                    hsv[2] = 1.0f // Max brightness/value
                    Color(android.graphics.Color.HSVToColor(hsv))
                }

                // Add a micro-flicker to simulate a real neon tube
                val flicker = 0.92f + 0.08f * sin(spin * 15f)
                val neonColor = baseNeonColor.copy(alpha = baseNeonColor.alpha * flicker)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()

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
                            val nodeAngles = listOf(spin, spin + 120f, spin + 240f)
                            nodeAngles.forEach { angle ->
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
                // Continuous glowing holographic pink ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()

                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f - 1.5.dp.toPx()
                            val holoColor = Color(0xFFFF33B4) // Pink color

                            // Continuous holographic sweep gradient pink ring
                            val holoColors = listOf(
                                Color(0xFFFF33B4),
                                Color(0xFFFF85D4),
                                Color(0xFFD500F9),
                                Color(0xFFFF33B4),
                            )
                            rotate(-spin, pivot = center) {
                                drawCircle(
                                    brush = Brush.sweepGradient(holoColors, center = center),
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
                // Rotating rainbow sweep gradient border with small, soft glint dots on the ring
                val rainbowColors = listOf(
                    Color(0xFFFF4E9E),
                    Color(0xFFFF9A3C),
                    Color(0xFFFFF176),
                    Color(0xFF72F6C0),
                    Color(0xFF6CC6FF),
                    Color(0xFFB388FF),
                    Color(0xFFFF4E9E),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()

                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f - 2.5.dp.toPx()

                            // Draw rotating rainbow ring (seamless 1x spin)
                            rotate(rainbowShift, pivot = center) {
                                drawCircle(
                                    brush = Brush.sweepGradient(rainbowColors, center = center),
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = 2.5.dp.toPx()),
                                )
                            }

                            // Draw random glints/flares around the frame (sequentially, not too frequently)
                            val flareCount = 4
                            val flarePositions = listOf(25f, 110f, 205f, 290f)
                            for (i in 0 until flareCount) {
                                val phase = (spin + i * 90f) % 360f
                                val activeRange = 120f
                                if (phase < activeRange) {
                                    val progress = phase / activeRange
                                    val alpha = sin(progress * PI.toFloat())
                                    val angleRad = Math.toRadians(flarePositions[i].toDouble())
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
