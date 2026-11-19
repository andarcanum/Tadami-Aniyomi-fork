package eu.kanade.tachiyomi.ui.reels.player

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Background-thread downsampled StackBlur [Transformation] for the ambient poster backdrop.
 *
 * Replaces Compose `Modifier.blur(36.dp)` on a full-screen image (which ran a full-res GPU
 * RenderEffect on every scroll frame on Android 12+ and produced zero blur on Android 11 and
 * below). Downsampling to <=[maxDimension] px and running integer StackBlur once takes <1 ms on a
 * worker thread and is cached in Coil's memory cache with zero scroll-time GPU overhead across all
 * API levels.
 */
internal class ReelsBlurTransformation(
    private val radius: Int = DEFAULT_RADIUS,
    private val maxDimension: Int = DEFAULT_MAX_DIMENSION,
) : Transformation() {

    override val cacheKey: String = "reels_stack_blur_${maxDimension}_r$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val srcW = input.width
        val srcH = input.height
        if (srcW <= 1 || srcH <= 1) return input

        val scale = min(1f, maxDimension.toFloat() / max(srcW, srcH).toFloat())
        val targetW = (srcW * scale).roundToInt().coerceAtLeast(2)
        val targetH = (srcH * scale).roundToInt().coerceAtLeast(2)

        val scaled = Bitmap.createScaledBitmap(input, targetW, targetH, true)
        val working = if (scaled.config == Bitmap.Config.ARGB_8888 && scaled.isMutable) {
            scaled
        } else {
            val copy = scaled.copy(Bitmap.Config.ARGB_8888, true)
            if (scaled !== input) scaled.recycle()
            copy ?: return input
        }
        stackBlurInPlace(working, radius.coerceIn(1, 25))
        return working
    }

    override fun equals(other: Any?): Boolean =
        other is ReelsBlurTransformation && radius == other.radius && maxDimension == other.maxDimension

    override fun hashCode(): Int = 31 * radius + maxDimension

    companion object {
        const val DEFAULT_RADIUS = 10
        const val DEFAULT_MAX_DIMENSION = 64

        /** Fast integer StackBlur (Mario Klingemann algorithm) operating directly on ARGB_8888 pixels. */
        internal fun stackBlurInPlace(bitmap: Bitmap, radius: Int) {
            if (radius < 1) return
            val w = bitmap.width
            val h = bitmap.height
            val pix = IntArray(w * h)
            bitmap.getPixels(pix, 0, w, 0, 0, w, h)

            val wm = w - 1
            val hm = h - 1
            val wh = w * h
            val div = radius + radius + 1

            val r = IntArray(wh)
            val g = IntArray(wh)
            val b = IntArray(wh)
            val vmin = IntArray(max(w, h))

            var divsum = (div + 1) shr 1
            divsum *= divsum
            val dv = IntArray(256 * divsum) { it / divsum }

            var yw = 0
            var yi = 0
            val stack = Array(div) { IntArray(3) }
            val r1 = radius + 1

            for (y in 0 until h) {
                var rinsum = 0
                var ginsum = 0
                var binsum = 0
                var routsum = 0
                var goutsum = 0
                var boutsum = 0
                var rsum = 0
                var gsum = 0
                var bsum = 0

                for (i in -radius..radius) {
                    val p = pix[yi + min(wm, max(i, 0))]
                    val sir = stack[i + radius]
                    sir[0] = (p and 0xff0000) shr 16
                    sir[1] = (p and 0x00ff00) shr 8
                    sir[2] = p and 0x0000ff
                    val rbs = r1 - abs(i)
                    rsum += sir[0] * rbs
                    gsum += sir[1] * rbs
                    bsum += sir[2] * rbs
                    if (i > 0) {
                        rinsum += sir[0]
                        ginsum += sir[1]
                        binsum += sir[2]
                    } else {
                        routsum += sir[0]
                        goutsum += sir[1]
                        boutsum += sir[2]
                    }
                }
                var stackpointer = radius
                for (x in 0 until w) {
                    r[yi] = dv[rsum]
                    g[yi] = dv[gsum]
                    b[yi] = dv[bsum]
                    rsum -= routsum
                    gsum -= goutsum
                    bsum -= boutsum
                    val stackstart = stackpointer - radius + div
                    var sir = stack[stackstart % div]
                    routsum -= sir[0]
                    goutsum -= sir[1]
                    boutsum -= sir[2]
                    if (y == 0) vmin[x] = min(x + radius + 1, wm)
                    val p = pix[yw + vmin[x]]
                    sir[0] = (p and 0xff0000) shr 16
                    sir[1] = (p and 0x00ff00) shr 8
                    sir[2] = p and 0x0000ff
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                    rsum += rinsum
                    gsum += ginsum
                    bsum += binsum
                    stackpointer = (stackpointer + 1) % div
                    sir = stack[stackpointer % div]
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                    rinsum -= sir[0]
                    ginsum -= sir[1]
                    binsum -= sir[2]
                    yi++
                }
                yw += w
            }

            for (x in 0 until w) {
                var rinsum = 0
                var ginsum = 0
                var binsum = 0
                var routsum = 0
                var goutsum = 0
                var boutsum = 0
                var rsum = 0
                var gsum = 0
                var bsum = 0
                var yp = -radius * w
                for (i in -radius..radius) {
                    yi = max(0, yp) + x
                    val sir = stack[i + radius]
                    sir[0] = r[yi]
                    sir[1] = g[yi]
                    sir[2] = b[yi]
                    val rbs = r1 - abs(i)
                    rsum += r[yi] * rbs
                    gsum += g[yi] * rbs
                    bsum += b[yi] * rbs
                    if (i > 0) {
                        rinsum += sir[0]
                        ginsum += sir[1]
                        binsum += sir[2]
                    } else {
                        routsum += sir[0]
                        goutsum += sir[1]
                        boutsum += sir[2]
                    }
                    if (i < hm) yp += w
                }
                yi = x
                var stackpointer = radius
                for (y in 0 until h) {
                    pix[yi] = (0xff shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                    rsum -= routsum
                    gsum -= goutsum
                    bsum -= boutsum
                    val stackstart = stackpointer - radius + div
                    var sir = stack[stackstart % div]
                    routsum -= sir[0]
                    goutsum -= sir[1]
                    boutsum -= sir[2]
                    if (x == 0) vmin[y] = min(y + r1, hm) * w
                    val p = x + vmin[y]
                    sir[0] = r[p]
                    sir[1] = g[p]
                    sir[2] = b[p]
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                    rsum += rinsum
                    gsum += ginsum
                    bsum += binsum
                    stackpointer = (stackpointer + 1) % div
                    sir = stack[stackpointer]
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                    rinsum -= sir[0]
                    ginsum -= sir[1]
                    binsum -= sir[2]
                    yi += w
                }
            }

            bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        }
    }
}
