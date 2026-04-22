package eu.kanade.tachiyomi.util

import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream

suspend fun Novel.editCover(
    stream: InputStream,
    updateNovel: UpdateNovel = Injekt.get(),
    coverCache: NovelCoverCache = Injekt.get(),
) {
    if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateNovel.awaitUpdateCoverLastModified(id)
    }
}
