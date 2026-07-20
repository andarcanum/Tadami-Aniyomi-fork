package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.manga.model.Manga as DomainManga

class MangaKeyer : Keyer<DomainManga> {
    override fun key(data: DomainManga, options: Options): String {
        return "manga;${data.id};${data.thumbnailUrl};${data.coverLastModified}"
    }
}

class MangaCoverKeyer : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String {
        return "manga;${data.mangaId};${data.url};${data.lastModified}"
    }
}
