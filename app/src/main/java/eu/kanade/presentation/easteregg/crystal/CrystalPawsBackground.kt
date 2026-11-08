package eu.kanade.presentation.easteregg.crystal

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import eu.kanade.presentation.theme.AuroraTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import android.graphics.Canvas as NativeCanvas

/** Decorative background only; the existing host owns placement and foreground layering. */
@Composable
internal fun CrystalPawsBackground(
    modifier: Modifier = Modifier,
    palette: CrystalPalette = CrystalPalette.Moon,
    animate: Boolean = true,
) {
    val painter = remember { CrystalPawTrailPainter() }
    val colors = AuroraTheme.colors
    val policy = rememberCrystalMotionPolicy()
    val motion = animate && policy.systemAnimations && !colors.isEInk
    val time by rememberCrystalTime(motion)
    val tint = when {
        colors.isEInk -> colors.textPrimary
        colors.isDark -> Color(palette.gold)
        else -> lerp(Color(palette.gold), Color(0xFF604626), 0.65f)
    }.toArgb()
    Canvas(modifier) {
        drawIntoCanvas { compose ->
            val canvas = compose.nativeCanvas
            val save = canvas.save()
            canvas.scale(density, density)
            painter.draw(
                canvas,
                size.width / density,
                size.height / density,
                time,
                tint,
                !motion,
                colors.isEInk,
            )
            canvas.restoreToCount(save)
        }
    }
}

internal class CrystalPawTrailPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Exact small pad and four ellipses from CrystalJewelPainter.paw, without its jewel finish.
    private val center = Path().apply {
        moveTo(32f, 29.5f)
        cubicTo(24f, 23.5f, 18.5f, 26.5f, 14.8f, 34.2f)
        cubicTo(11.2f, 41.8f, 13.5f, 50.5f, 22.2f, 51.5f)
        cubicTo(26.2f, 52f, 28.5f, 47.8f, 32f, 47.8f)
        cubicTo(35.5f, 47.8f, 37.8f, 52f, 41.8f, 51.5f)
        cubicTo(50.5f, 50.5f, 52.8f, 41.8f, 49.2f, 34.2f)
        cubicTo(45.5f, 26.5f, 40f, 23.5f, 32f, 29.5f)
        close()
    }
    private val pads = arrayOf(
        Pad(14.5f, 23.5f, 5.2f, 7.4f, -26f),
        Pad(25.5f, 15.5f, 5.5f, 7.8f, -10f),
        Pad(38.5f, 15.5f, 5.5f, 7.8f, 10f),
        Pad(49.5f, 23.5f, 5.2f, 7.4f, 26f),
    )

    fun draw(
        canvas: NativeCanvas,
        width: Float,
        height: Float,
        timeMs: Float,
        tint: Int,
        reduced: Boolean,
        ink: Boolean,
    ) {
        if (width <= 0 || height <= 0) return
        if (reduced) {
            // Preserve the existing lower-left anchor. No frame source runs in this mode.
            stamp(
                canvas,
                width * 0.15f + STAMP_DP / 2,
                height * 0.76f + STAMP_DP / 2,
                0f,
                tint,
                if (ink) 1f else 0.16f,
                ink,
            )
            return
        }
        val route = CrystalPawMotion.routeVariant(timeMs)
        // Approved normalized gentle route; this renderer has no cover/text geometry.
        val x0 = width * (0.20f + route * 0.12f)
        val y0 = height * 0.91f
        val x1 = width * 0.16f
        val y1 = height * 0.80f
        val x2 = width * 0.75f
        val y2 = height * 0.54f
        val x3 = width * (0.32f + route * 0.12f)
        val y3 = height * 0.22f
        for (index in 0 until CrystalPawMotion.PAW_COUNT) {
            val opacity = CrystalPawMotion.opacityAt(timeMs, index)
            if (opacity <= 0f) continue
            val t = index / (CrystalPawMotion.PAW_COUNT - 1f)
            val u = 1f - t
            val x = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3
            val y = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
            val dx = 3 * u * u * (x1 - x0) + 6 * u * t * (x2 - x1) + 3 * t * t * (x3 - x2)
            val dy = 3 * u * u * (y1 - y0) + 6 * u * t * (y2 - y1) + 3 * t * t * (y3 - y2)
            val normal = atan2(dy, dx) + PI.toFloat() / 2
            val side = if (index % 2 == 0) -1f else 1f
            stamp(
                canvas,
                x + cos(normal) * side * 8f,
                y + sin(normal) * side * 8f,
                normal + side * 0.06f,
                tint,
                opacity,
                false,
            )
        }
    }

    private fun stamp(
        canvas: NativeCanvas,
        x: Float,
        y: Float,
        angle: Float,
        tint: Int,
        opacity: Float,
        outline: Boolean,
    ) {
        paint.color = tint
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        paint.style = if (outline) Paint.Style.STROKE else Paint.Style.FILL
        // The native canvas already uses logical dp; the 64-unit stamp scale keeps this at 1dp.
        paint.strokeWidth = if (outline) 64f / STAMP_DP else 0f
        val save = canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle * 180f / PI.toFloat())
        canvas.scale(STAMP_DP / 64f, STAMP_DP / 64f)
        canvas.translate(-32f, -32f)
        canvas.drawPath(center, paint)
        for (pad in pads) {
            val toeSave = canvas.save()
            canvas.rotate(pad.angle, pad.x, pad.y)
            canvas.drawOval(pad.x - pad.rx, pad.y - pad.ry, pad.x + pad.rx, pad.y + pad.ry, paint)
            canvas.restoreToCount(toeSave)
        }
        canvas.restoreToCount(save)
    }

    private class Pad(val x: Float, val y: Float, val rx: Float, val ry: Float, val angle: Float)

    private companion object {
        const val STAMP_DP = 21f
    }
}
