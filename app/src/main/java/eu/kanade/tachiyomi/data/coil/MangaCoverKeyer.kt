package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import tachiyomi.domain.entries.manga.model.MangaCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.entries.manga.model.Manga as DomainManga

class MangaKeyer(
    private val coverCache: MangaCoverCache = Injekt.get(),
) : Keyer<DomainManga> {
    override fun key(data: DomainManga, options: Options): String {
        return "manga;${data.id};${data.thumbnailUrl};${data.coverLastModified}"
    }
}

class MangaCoverKeyer(
    private val coverCache: MangaCoverCache = Injekt.get(),
) : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String {
        return "manga;${data.mangaId};${data.url};${data.lastModified}"
    }
}
