package eu.kanade.domain.source.manga.model

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import tachiyomi.domain.source.manga.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// BRM-9: `.icon` is read on MAIN during composition for every source row, and every access
// re-rendered the cached Drawable into a NEW Bitmap (allocation storm while scrolling the
// sources list). Bounded LRU per source id; icons change only on extension (re)install, so a
// small stale window after an update is acceptable (no invalidation hook exists for Drawables).
private val mangaIconCache = object : LruCache<Long, ImageBitmap>(64) {}

val Source.icon: ImageBitmap?
    get() {
        mangaIconCache.get(id)?.let { return it }
        val bitmap = Injekt.get<MangaExtensionManager>().getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
        if (bitmap != null) mangaIconCache.put(id, bitmap)
        return bitmap
    }
