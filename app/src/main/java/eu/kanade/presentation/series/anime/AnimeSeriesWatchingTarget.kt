package eu.kanade.presentation.series.anime

import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries

data class AnimeSeriesWatchingTarget(
    val anime: LibraryAnime,
    val episode: Episode,
)

fun resolveAnimeSeriesWatchingTarget(
    series: LibraryAnimeSeries,
    episodes: List<Pair<LibraryAnime, List<Episode>>>,
): AnimeSeriesWatchingTarget? {
    val activeAnime = series.activeAnime ?: return null
    val activeEntryIndex = episodes.indexOfFirst { it.first.anime.id == activeAnime.id }
        .takeIf { it >= 0 }
        ?: 0

    var fallbackTarget: AnimeSeriesWatchingTarget? = null
    for ((anime, animeEpisodes) in episodes.drop(activeEntryIndex)) {
        val episode = resolveAnimeResumeEpisode(animeEpisodes) ?: continue
        val target = AnimeSeriesWatchingTarget(
            anime = anime,
            episode = episode,
        )

        if (fallbackTarget == null) {
            fallbackTarget = target
        }

        if (animeEpisodes.any { !it.seen }) {
            return target
        }
    }

    return fallbackTarget
}

internal val animeEpisodeResumeComparator: Comparator<Episode> =
    compareBy<Episode> { it.episodeNumber }
        .thenBy { it.sourceOrder }
        .thenBy { it.id }

internal fun resolveAnimeResumeEpisode(
    episodes: List<Episode>,
    fromEpisodeId: Long? = null,
): Episode? {
    return resolveAnimeResumeEpisodeFromSorted(
        sortedEpisodes = episodes.sortedWith(animeEpisodeResumeComparator),
        fromEpisodeId = fromEpisodeId,
    )
}

internal fun resolveAnimeResumeEpisodeFromSorted(
    sortedEpisodes: List<Episode>,
    fromEpisodeId: Long? = null,
): Episode? {
    if (sortedEpisodes.isEmpty()) return null

    if (fromEpisodeId != null) {
        val currentIndex = sortedEpisodes.indexOfFirst { it.id == fromEpisodeId }
        if (currentIndex >= 0) {
            return sortedEpisodes[currentIndex]
        }
    }

    sortedEpisodes.firstOrNull { it.lastSecondSeen > 0L && !it.seen }?.let { return it }

    val lastSeenIndex = sortedEpisodes.indexOfLast { it.seen || it.lastSecondSeen > 0L }
    if (lastSeenIndex >= 0) {
        sortedEpisodes.drop(lastSeenIndex + 1).firstOrNull { !it.seen }?.let { return it }
        return sortedEpisodes[lastSeenIndex]
    }

    return sortedEpisodes.firstOrNull { !it.seen } ?: sortedEpisodes.firstOrNull()
}
