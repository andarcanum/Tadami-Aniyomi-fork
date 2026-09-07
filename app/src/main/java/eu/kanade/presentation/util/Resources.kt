package eu.kanade.presentation.util

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Create a BitmapPainter from a drawable resource.
 * Use this only if [androidx.compose.ui.res.painterResource] doesn't work.
 *
 * @param id the resource identifier
 *
 * @return the bitmap associated with the resource
 */
// H14: process-level cache by (id, densityDpi) - remember(id) is per composition slot, so
// every lazy-grid card decoded the SAME drawable into a fresh bitmap on each item bind (the
// library cover placeholder ran this per visible card, again on every scroll recycle).
// Composition-thread (main) access only.
private val resourceBitmapPainterCache = HashMap<Pair<Int, Int>, BitmapPainter>()

@Composable
fun rememberResourceBitmapPainter(@DrawableRes id: Int): BitmapPainter {
    val context = LocalContext.current
    val densityDpi = context.resources.configuration.densityDpi
    return remember(id, densityDpi) {
        resourceBitmapPainterCache.getOrPut(id to densityDpi) {
            val drawable = ContextCompat.getDrawable(context, id)
                ?: throw Resources.NotFoundException()
            BitmapPainter(drawable.toBitmap().asImageBitmap())
        }
    }
}
