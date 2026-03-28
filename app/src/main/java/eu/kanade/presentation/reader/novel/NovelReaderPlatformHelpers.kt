package eu.kanade.presentation.reader.novel

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

internal fun resolveBatteryLevelPercent(
    level: Int,
    scale: Int,
): Int? {
    if (level < 0 || scale <= 0) return null
    return ((level * 100f) / scale.toFloat()).roundToInt().coerceIn(0, 100)
}

internal fun currentTimeString(context: Context): String {
    return DateFormat.getTimeFormat(context).format(Date())
}

internal fun sampleReaderBackgroundLuminance(path: String): Float? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val maxDimension = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val sampleSize = (maxDimension / 96).coerceAtLeast(1)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val sampled = BitmapFactory.decodeFile(path, options) ?: return@runCatching null
        try {
            val width = sampled.width.coerceAtLeast(1)
            val height = sampled.height.coerceAtLeast(1)
            val stepX = (width / 12).coerceAtLeast(1)
            val stepY = (height / 12).coerceAtLeast(1)
            var luminanceSum = 0f
            var sampledPixels = 0
            var x = 0
            while (x < width) {
                var y = 0
                while (y < height) {
                    val pixel = sampled.getPixel(x, y)
                    val color = Color(pixel)
                    luminanceSum += color.luminance()
                    sampledPixels++
                    y += stepY
                }
                x += stepX
            }
            if (sampledPixels == 0) null else (luminanceSum / sampledPixels).coerceIn(0f, 1f)
        } finally {
            sampled.recycle()
        }
    }.getOrNull()
}

internal fun readBatteryLevel(
    context: Context,
    batteryIntent: Intent? = null,
): Int {
    val levelFromIntent = resolveBatteryLevelPercent(
        level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
        scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1,
    )
    if (levelFromIntent != null) return levelFromIntent

    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val directLevel = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    if (directLevel in 0..100) return directLevel

    val fallbackIntent = batteryIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    resolveBatteryLevelPercent(
        level = fallbackIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
        scale = fallbackIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1,
    )?.let { return it }
    return 100
}
