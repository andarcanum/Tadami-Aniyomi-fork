package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.anime.model.Anime as DomainAnime

class AnimeKeyer : Keyer<DomainAnime> {
    override fun key(data: DomainAnime, options: Options): String {
        return if (options.useBackground) {
            "anime-bg;${data.id};${data.backgroundUrl};${data.backgroundLastModified}"
        } else {
            "anime;${data.id};${data.thumbnailUrl};${data.coverLastModified}"
        }
    }
}

class AnimeCoverKeyer : Keyer<AnimeCover> {
    override fun key(data: AnimeCover, options: Options): String {
        return "anime;${data.animeId};${data.url};${data.lastModified}"
    }
}
