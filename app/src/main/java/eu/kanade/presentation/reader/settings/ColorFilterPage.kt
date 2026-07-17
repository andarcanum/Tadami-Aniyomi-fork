package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.Companion.ColorFilterMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun ColumnScope.ColorFilterPage(screenModel: ReaderSettingsScreenModel) {
    val customBrightness by screenModel.preferences.customBrightness().collectAsState()
    val customBrightnessValue by screenModel.preferences.customBrightnessValue().collectAsState()
    val colorFilter by screenModel.preferences.colorFilter().collectAsState()
    val colorFilterValue by screenModel.preferences.colorFilterValue().collectAsState()
    val colorFilterMode by screenModel.preferences.colorFilterMode().collectAsState()
    val grayscale by screenModel.preferences.grayscale().collectAsState()
    val invertedColors by screenModel.preferences.invertedColors().collectAsState()
    val sharpening by screenModel.preferences.sharpening().collectAsState()
    val denoise by screenModel.preferences.denoise().collectAsState()
    val binarization by screenModel.preferences.binarization().collectAsState()

    // Live sample manga panel — reacts to every filter on this page.
    AuroraGlassSection(title = stringResource(AYMR.strings.aurora_reader_preview_title)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .height(148.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            val matrix = buildPreviewColorMatrix(
                grayscale = grayscale,
                inverted = invertedColors,
                sharpening = sharpening,
                denoise = denoise,
                binarization = binarization,
            )
            val colorFilterCompose = ColorFilter.colorMatrix(matrix)

            drawMangaSamplePanel(colorFilter = colorFilterCompose)

            if (colorFilter) {
                drawRect(
                    color = Color(colorFilterValue),
                    blendMode = when (colorFilterMode) {
                        1 -> BlendMode.Multiply
                        2 -> BlendMode.Screen
                        3 -> BlendMode.Overlay
                        4 -> BlendMode.Lighten
                        else -> BlendMode.SrcOver
                    },
                )
            }

            if (customBrightness) {
                val v = customBrightnessValue
                when {
                    v < 0 -> drawRect(Color.Black.copy(alpha = (-v / 100f).coerceIn(0f, 0.85f)))
                    v > 0 -> drawRect(Color.White.copy(alpha = (v / 140f).coerceIn(0f, 0.55f)))
                }
            }
        }
        Text(
            text = stringResource(AYMR.strings.aurora_reader_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = AuroraTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }

    AuroraGlassSection(title = stringResource(MR.strings.custom_filter)) {
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_custom_brightness),
            pref = screenModel.preferences.customBrightness(),
        )
        if (customBrightness) {
            AuroraSliderRow(
                label = stringResource(MR.strings.pref_custom_brightness),
                value = customBrightnessValue,
                valueRange = -75..100,
                onChange = { screenModel.preferences.customBrightnessValue().set(it) },
            )
        }

        AuroraToggleRow(
            label = stringResource(MR.strings.pref_custom_color_filter),
            pref = screenModel.preferences.colorFilter(),
        )
        if (colorFilter) {
            AuroraSliderRow(
                label = stringResource(MR.strings.color_filter_r_value),
                value = colorFilterValue.red,
                valueRange = 0..255,
                onChange = { newRValue ->
                    screenModel.preferences.colorFilterValue().getAndSet {
                        getColorValue(it, newRValue, RED_MASK, 16)
                    }
                },
            )
            AuroraSliderRow(
                label = stringResource(MR.strings.color_filter_g_value),
                value = colorFilterValue.green,
                valueRange = 0..255,
                onChange = { newGValue ->
                    screenModel.preferences.colorFilterValue().getAndSet {
                        getColorValue(it, newGValue, GREEN_MASK, 8)
                    }
                },
            )
            AuroraSliderRow(
                label = stringResource(MR.strings.color_filter_b_value),
                value = colorFilterValue.blue,
                valueRange = 0..255,
                onChange = { newBValue ->
                    screenModel.preferences.colorFilterValue().getAndSet {
                        getColorValue(it, newBValue, BLUE_MASK, 0)
                    }
                },
            )
            AuroraSliderRow(
                label = stringResource(MR.strings.color_filter_a_value),
                value = colorFilterValue.alpha,
                valueRange = 0..255,
                onChange = { newAValue ->
                    screenModel.preferences.colorFilterValue().getAndSet {
                        getColorValue(it, newAValue, ALPHA_MASK, 24)
                    }
                },
            )

            AuroraFieldLabel(stringResource(MR.strings.pref_color_filter_mode))
            AuroraChipFlow {
                ColorFilterMode.forEachIndexed { index, it ->
                    AuroraChip(
                        selected = colorFilterMode == index,
                        onClick = { screenModel.preferences.colorFilterMode().set(index) },
                        label = stringResource(it.first),
                    )
                }
            }
        }

        AuroraToggleRow(
            label = stringResource(MR.strings.pref_grayscale),
            pref = screenModel.preferences.grayscale(),
        )
        AuroraToggleRow(
            label = stringResource(MR.strings.pref_inverted_colors),
            pref = screenModel.preferences.invertedColors(),
        )
    }

    AuroraGlassSection(title = stringResource(AYMR.strings.aurora_reader_processing)) {
        AuroraSliderRow(
            label = stringResource(MR.strings.pref_sharpening),
            value = sharpening,
            valueRange = 0..100,
            onChange = { screenModel.preferences.sharpening().set(it) },
        )
        AuroraSliderRow(
            label = stringResource(MR.strings.pref_denoise),
            value = denoise,
            valueRange = 0..100,
            onChange = { screenModel.preferences.denoise().set(it) },
        )
        AuroraSliderRow(
            label = stringResource(MR.strings.pref_binarization),
            value = binarization,
            valueRange = 0..100,
            onChange = { screenModel.preferences.binarization().set(it) },
        )
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * Builds a ColorMatrix approximating the reader processing pipeline for the
 * sample preview. Not pixel-identical to AGSL filters, but directionally correct
 * so users can see grayscale / invert / contrast / threshold effects live.
 */
private fun buildPreviewColorMatrix(
    grayscale: Boolean,
    inverted: Boolean,
    sharpening: Int,
    denoise: Int,
    binarization: Int,
): ColorMatrix {
    val matrix = ColorMatrix()

    if (grayscale) {
        matrix.timesAssign(
            ColorMatrix(
                floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    // Sharpen → contrast bump; denoise → slight contrast drop / soft wash.
    val contrast = 1f + (sharpening / 100f) * 0.55f - (denoise / 100f) * 0.28f
    val translate = (1f - contrast) * 128f
    matrix.timesAssign(
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    // Soft denoise: reduce saturation slightly so the page looks flatter.
    if (denoise > 0) {
        val s = 1f - (denoise / 100f) * 0.35f
        val invSat = 1f - s
        val r = 0.213f * invSat
        val g = 0.715f * invSat
        val b = 0.072f * invSat
        matrix.timesAssign(
            ColorMatrix(
                floatArrayOf(
                    r + s, g, b, 0f, 0f,
                    r, g + s, b, 0f, 0f,
                    r, g, b + s, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    // Binarization: hard contrast toward black/white.
    if (binarization > 0) {
        val t = binarization / 100f
        val c = 1f + t * 8f
        val tr = (0.5f - t * 0.08f) * 255f * (1f - c)
        matrix.timesAssign(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, tr,
                    0f, c, 0f, 0f, tr,
                    0f, 0f, c, 0f, tr,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    if (inverted) {
        matrix.timesAssign(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    return matrix
}

/** Stylized mini manga page used only as a live filter preview. */
private fun DrawScope.drawMangaSamplePanel(colorFilter: ColorFilter) {
    val w = size.width
    val h = size.height
    val paper = Color(0xFFF4EFE4)
    val ink = Color(0xFF1A1A1A)
    val inkSoft = Color(0xFF3A3A3A)
    val tone = Color(0xFFB8B0A4)
    val accentHair = Color(0xFF2C5F7C)
    val skin = Color(0xFFE8C4A8)
    val cheek = Color(0xFFE08A8A)

    // Paper
    drawRect(color = paper, colorFilter = colorFilter)

    // Panel border
    val pad = w * 0.04f
    drawRoundRect(
        color = ink,
        topLeft = Offset(pad, pad),
        size = Size(w - pad * 2, h - pad * 2),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 2.5f),
        colorFilter = colorFilter,
    )

    // Background sky band
    clipRect(pad + 2, pad + 2, w - pad - 2, h * 0.42f) {
        drawRect(
            color = Color(0xFF9EC9E0),
            topLeft = Offset(pad, pad),
            size = Size(w - pad * 2, h * 0.42f),
            colorFilter = colorFilter,
        )
        // Soft clouds
        drawCircle(
            color = Color.White.copy(alpha = 0.75f),
            radius = w * 0.08f,
            center = Offset(w * 0.22f, h * 0.18f),
            colorFilter = colorFilter,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = w * 0.06f,
            center = Offset(w * 0.72f, h * 0.22f),
            colorFilter = colorFilter,
        )
    }

    // Mid ground tone / hatching
    val midY = h * 0.38f
    drawRect(
        color = tone.copy(alpha = 0.35f),
        topLeft = Offset(pad + 2, midY),
        size = Size(w - pad * 2 - 4, h * 0.22f),
        colorFilter = colorFilter,
    )
    val hatchStep = 6f
    var hx = pad + 4
    while (hx < w - pad - 4) {
        drawLine(
            color = inkSoft.copy(alpha = 0.25f),
            start = Offset(hx, midY),
            end = Offset(hx + 10f, midY + h * 0.22f),
            strokeWidth = 1f,
            colorFilter = colorFilter,
        )
        hx += hatchStep
    }

    // Character — simple portrait silhouette
    val cx = w * 0.34f
    val cy = h * 0.58f
    // Hair back
    drawCircle(
        color = accentHair,
        radius = w * 0.13f,
        center = Offset(cx, cy - h * 0.10f),
        colorFilter = colorFilter,
    )
    // Face
    drawOval(
        color = skin,
        topLeft = Offset(cx - w * 0.09f, cy - h * 0.12f),
        size = Size(w * 0.18f, h * 0.22f),
        colorFilter = colorFilter,
    )
    // Hair front bangs
    drawArc(
        color = accentHair,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = true,
        topLeft = Offset(cx - w * 0.11f, cy - h * 0.20f),
        size = Size(w * 0.22f, h * 0.18f),
        colorFilter = colorFilter,
    )
    // Eyes
    drawCircle(
        color = ink,
        radius = w * 0.012f,
        center = Offset(cx - w * 0.03f, cy - h * 0.02f),
        colorFilter = colorFilter,
    )
    drawCircle(
        color = ink,
        radius = w * 0.012f,
        center = Offset(cx + w * 0.035f, cy - h * 0.02f),
        colorFilter = colorFilter,
    )
    // Cheek blush
    drawCircle(
        color = cheek.copy(alpha = 0.55f),
        radius = w * 0.018f,
        center = Offset(cx - w * 0.055f, cy + h * 0.02f),
        colorFilter = colorFilter,
    )
    drawCircle(
        color = cheek.copy(alpha = 0.55f),
        radius = w * 0.018f,
        center = Offset(cx + w * 0.055f, cy + h * 0.02f),
        colorFilter = colorFilter,
    )
    // Collar / shoulders
    val body = Path().apply {
        moveTo(cx - w * 0.14f, cy + h * 0.14f)
        quadraticTo(cx, cy + h * 0.10f, cx + w * 0.14f, cy + h * 0.14f)
        lineTo(cx + w * 0.16f, h - pad - 4)
        lineTo(cx - w * 0.16f, h - pad - 4)
        close()
    }
    drawPath(path = body, color = Color(0xFF2A3A55), colorFilter = colorFilter)

    // Speech bubble (right)
    val bx = w * 0.52f
    val by = h * 0.18f
    val bw = w * 0.40f
    val bh = h * 0.28f
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(bx, by),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(12f, 12f),
        colorFilter = colorFilter,
    )
    drawRoundRect(
        color = ink,
        topLeft = Offset(bx, by),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = 2f),
        colorFilter = colorFilter,
    )
    // Bubble tail
    val tail = Path().apply {
        moveTo(bx + bw * 0.18f, by + bh)
        lineTo(bx + bw * 0.08f, by + bh + h * 0.07f)
        lineTo(bx + bw * 0.32f, by + bh)
        close()
    }
    drawPath(path = tail, color = Color.White, colorFilter = colorFilter)
    drawPath(path = tail, color = ink, style = Stroke(width = 2f), colorFilter = colorFilter)

    // Fake text lines in bubble
    val lineColor = inkSoft
    val textLeft = bx + bw * 0.12f
    val textRight = bx + bw * 0.88f
    val lineYs = listOf(0.28f, 0.48f, 0.68f)
    lineYs.forEachIndexed { i, frac ->
        val y = by + bh * frac
        val end = if (i == 2) textLeft + (textRight - textLeft) * 0.55f else textRight
        drawLine(
            color = lineColor,
            start = Offset(textLeft, y),
            end = Offset(end, y),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round,
            colorFilter = colorFilter,
        )
    }

    // Speed lines (action corner)
    for (i in 0..5) {
        val y0 = h * (0.55f + i * 0.06f)
        drawLine(
            color = inkSoft.copy(alpha = 0.35f),
            start = Offset(w * 0.62f, y0),
            end = Offset(w - pad - 4, y0 - h * 0.04f),
            strokeWidth = 1.4f,
            colorFilter = colorFilter,
        )
    }
}

private fun getColorValue(currentColor: Int, color: Int, mask: Long, bitShift: Int): Int {
    return (color shl bitShift) or (currentColor and mask.inv().toInt())
}

private const val ALPHA_MASK: Long = 0xFF000000
private const val RED_MASK: Long = 0x00FF0000
private const val GREEN_MASK: Long = 0x0000FF00
private const val BLUE_MASK: Long = 0x000000FF
