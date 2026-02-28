package eu.kanade.tachiyomi.data.coil

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.max

/**
 * Static image blur transformation.
 *
 * Unlike Compose runtime blur modifiers, this runs once while decoding and is cacheable.
 */
class StaticBlurTransformation(
    private val radiusPx: Int,
) : Transformation() {

    private val safeRadiusPx = radiusPx.coerceIn(1, 80)

    override val cacheKey: String = "${this::class.qualifiedName}(radiusPx=$safeRadiusPx)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return blurWithFallbackBoxBlur(input, safeRadiusPx)
    }

    private fun blurWithFallbackBoxBlur(input: Bitmap, radius: Int): Bitmap {
        val sample = 4
        val sampledWidth = max(1, input.width / sample)
        val sampledHeight = max(1, input.height / sample)
        val sampled = Bitmap.createScaledBitmap(input, sampledWidth, sampledHeight, true)
            .copy(Bitmap.Config.ARGB_8888, true)

        boxBlurInPlace(sampled, max(1, radius / sample))
        return Bitmap.createScaledBitmap(sampled, input.width, input.height, true)
    }

    private fun boxBlurInPlace(bitmap: Bitmap, radius: Int) {
        if (radius <= 0) return

        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        val source = IntArray(size)
        val temp = IntArray(size)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)

        val div = radius * 2 + 1

        for (y in 0 until height) {
            val row = y * width
            var a = 0
            var r = 0
            var g = 0
            var b = 0

            for (i in -radius..radius) {
                val x = i.coerceIn(0, width - 1)
                val color = source[row + x]
                a += color ushr 24
                r += (color shr 16) and 0xFF
                g += (color shr 8) and 0xFF
                b += color and 0xFF
            }

            for (x in 0 until width) {
                temp[row + x] =
                    ((a / div) shl 24) or
                        ((r / div) shl 16) or
                        ((g / div) shl 8) or
                        (b / div)

                val removeX = (x - radius).coerceIn(0, width - 1)
                val addX = (x + radius + 1).coerceIn(0, width - 1)
                val removeColor = source[row + removeX]
                val addColor = source[row + addX]

                a += (addColor ushr 24) - (removeColor ushr 24)
                r += ((addColor shr 16) and 0xFF) - ((removeColor shr 16) and 0xFF)
                g += ((addColor shr 8) and 0xFF) - ((removeColor shr 8) and 0xFF)
                b += (addColor and 0xFF) - (removeColor and 0xFF)
            }
        }

        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0

            for (i in -radius..radius) {
                val y = i.coerceIn(0, height - 1)
                val color = temp[y * width + x]
                a += color ushr 24
                r += (color shr 16) and 0xFF
                g += (color shr 8) and 0xFF
                b += color and 0xFF
            }

            for (y in 0 until height) {
                source[y * width + x] =
                    ((a / div) shl 24) or
                        ((r / div) shl 16) or
                        ((g / div) shl 8) or
                        (b / div)

                val removeY = (y - radius).coerceIn(0, height - 1)
                val addY = (y + radius + 1).coerceIn(0, height - 1)
                val removeColor = temp[removeY * width + x]
                val addColor = temp[addY * width + x]

                a += (addColor ushr 24) - (removeColor ushr 24)
                r += ((addColor shr 16) and 0xFF) - ((removeColor shr 16) and 0xFF)
                g += ((addColor shr 8) and 0xFF) - ((removeColor shr 8) and 0xFF)
                b += (addColor and 0xFF) - (removeColor and 0xFF)
            }
        }

        bitmap.setPixels(source, 0, width, 0, 0, width, height)
    }
}
