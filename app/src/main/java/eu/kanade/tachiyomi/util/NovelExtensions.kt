package eu.kanade.tachiyomi.util

import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.source.local.entries.novel.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import java.time.Instant

/** BRN-12: novel analogue of Manga.removeCovers (MangaExtensions:43) / Anime.removeCovers. */
fun Novel.removeCovers(coverCache: NovelCoverCache = Injekt.get()): Novel {
    if (isLocal()) return this
    return if (coverCache.deleteFromCache(this, true) > 0) {
        copy(coverLastModified = Instant.now().toEpochMilli())
    } else {
        this
    }
}

suspend fun Novel.editCover(
    stream: InputStream,
    updateNovel: UpdateNovel = Injekt.get(),
    coverCache: NovelCoverCache = Injekt.get(),
) {
    // Custom covers work for any entry, matching manga/anime behavior. The
    // favorite-only guard made the flow silently do nothing for non-library
    // novels (picked an image, nothing changed, no snackbar).
    coverCache.setCustomCoverToCache(this, stream)
    updateNovel.awaitUpdateCoverLastModified(id)
}
