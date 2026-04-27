package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import tachiyomi.domain.entries.anime.model.AnimeCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.entries.anime.model.Anime as DomainAnime

class AnimeKeyer(
    private val coverCache: AnimeCoverCache = Injekt.get(),
    private val backgroundCache: AnimeBackgroundCache = Injekt.get(),
) : Keyer<DomainAnime> {
    override fun key(data: DomainAnime, options: Options): String {
        return if (options.useBackground) {
            "anime-bg;${data.id};${data.backgroundUrl};${data.backgroundLastModified}"
        } else {
            "anime;${data.id};${data.thumbnailUrl};${data.coverLastModified}"
        }
    }
}

class AnimeCoverKeyer(
    private val coverCache: AnimeCoverCache = Injekt.get(),
) : Keyer<AnimeCover> {
    override fun key(data: AnimeCover, options: Options): String {
        return "anime;${data.animeId};${data.url};${data.lastModified}"
    }
}
