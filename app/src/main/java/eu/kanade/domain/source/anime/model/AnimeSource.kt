package eu.kanade.domain.source.anime.model

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import tachiyomi.domain.source.anime.model.AnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// BRM-9: see the manga model - bounded LRU instead of a fresh toBitmap() per composition read.
private val animeIconCache = object : LruCache<Long, ImageBitmap>(64) {}

val AnimeSource.icon: ImageBitmap?
    get() {
        animeIconCache.get(id)?.let { return it }
        val bitmap = Injekt.get<AnimeExtensionManager>().getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
        if (bitmap != null) animeIconCache.put(id, bitmap)
        return bitmap
    }
