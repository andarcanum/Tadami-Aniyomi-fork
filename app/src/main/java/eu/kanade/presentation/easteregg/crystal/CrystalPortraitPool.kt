package eu.kanade.presentation.easteregg.crystal

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

internal class CrystalPortraitPool private constructor(
    val bitmap: Bitmap,
    val style: CrystalPortrait,
    val cells: List<Cell>,
    val firstBirth: FloatArray,
) {
    internal data class Cell(
        val source: Rect,
        val nx: Float,
        val ny: Float,
        val birth: Float,
        val alpha: Float,
        val vx: Float,
        val vy: Float,
        val curl: Float,
        val phase: Float,
        var target: Int,
    )

    companion object {
        /** Called on Dispatchers.Default/IO, not from a draw callback. */
        fun prepare(resources: Resources, portrait: CrystalPortrait, front: CrystalDissolveStyle): CrystalPortraitPool {
            val bitmap =
                requireNotNull(
                    BitmapFactory.decodeResource(
                        resources,
                        portrait.drawable,
                        BitmapFactory.Options().apply {
                            inScaled = false
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        },
                    ),
                ) { "Crystal portrait could not be decoded" }
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val step = ceil(w / 78.0).toInt()
            data class Sample(
                val rect: Rect,
                val nx: Float,
                val ny: Float,
                val id: Int,
                val threshold: Float,
                val alpha: Float,
            )
            val samples = ArrayList<Sample>()
            for (y in 0 until h step step) {
                for (x in 0 until w step step) {
                    val cw = min(step, w - x)
                    val ch = min(step, h - y)
                    var alphaSum = 0
                    for (yy in y until y + ch) for (xx in x until x + cw) alphaSum += pixels[yy * w + xx] ushr 24
                    if (alphaSum == 0) continue
                    val nx = (x + cw * 0.5f) / w
                    val ny = (y + ch * 0.5f) / h
                    val id = samples.size
                    val threshold = when (front) {
                        CrystalDissolveStyle.Layers -> 1f - ny
                        CrystalDissolveStyle.Heart -> hypot((nx - 0.5f) * 0.72f, ny - 0.39f)
                        CrystalDissolveStyle.Aurora -> 1f - ny + 0.11f * sin(nx * 10f + ny * 2.5f) +
                            0.04f * cos(nx * 5.2f)
                    } + (CrystalSceneGeometry.hash(id) - 0.5f) * 0.024f
                    samples += Sample(Rect(x, y, x + cw, y + ch), nx, ny, id, threshold, alphaSum / (cw * ch * 255f))
                }
            }
            require(samples.size >= CrystalSceneGeometry.NODE_COUNT) {
                "Crystal portrait has insufficient visible pixels"
            }
            val lo = samples.minOf { it.threshold }
            val span = (samples.maxOf { it.threshold } - lo).coerceAtLeast(0.0001f)
            val cells = samples.sortedBy { it.threshold }.map { c ->
                Cell(
                    c.rect, c.nx, c.ny, 0.01f + 0.98f * (c.threshold - lo) / span, c.alpha,
                    (CrystalSceneGeometry.hash(c.id + 13) - 0.5f) * 16f,
                    14f + CrystalSceneGeometry.hash(c.id + 171) * 18f,
                    5f + CrystalSceneGeometry.hash(c.id + 217) * 9f,
                    CrystalSceneGeometry.hash(c.id + 918) * (2 * PI).toFloat(),
                    if (c.id % 19 == 0) (c.id / 19) % 82 else -1,
                )
            }
            // Missing anchors use an unassigned cell, never steal another star's sole particle.
            for (node in 0 until 82) {
                if (cells.none { it.target == node }) {
                    cells.first { it.target < 0 }.target = node
                }
            }
            val firstBirth = FloatArray(82) { node -> cells.filter { it.target == node }.minOf { it.birth } }
            return CrystalPortraitPool(bitmap, portrait, cells, firstBirth)
        }
    }
}
