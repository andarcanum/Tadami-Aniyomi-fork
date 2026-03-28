package eu.kanade.domain.entries.anime.model

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.anime.model.Anime

// TODO: move these into the domain model
val Anime.downloadedFilter: TriState
    get() = when (downloadedFilterRaw) {
        Anime.EPISODE_SHOW_DOWNLOADED -> TriState.ENABLED_IS
        Anime.EPISODE_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
        else -> TriState.DISABLED
    }

val Anime.seasonDownloadedFilter: TriState
    get() = when (seasonDownloadedFilterRaw) {
        Anime.SEASON_SHOW_DOWNLOADED -> TriState.ENABLED_IS
        Anime.SEASON_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
        else -> TriState.DISABLED
    }

fun Anime.effectiveDownloadedFilter(downloadedOnly: Boolean): TriState {
    return if (downloadedOnly) TriState.ENABLED_IS else downloadedFilter
}

fun Anime.effectiveSeasonDownloadedFilter(downloadedOnly: Boolean): TriState {
    return if (downloadedOnly) TriState.ENABLED_IS else seasonDownloadedFilter
}

fun Anime.episodesFiltered(downloadedOnly: Boolean): Boolean {
    return unseenFilter != TriState.DISABLED ||
        effectiveDownloadedFilter(downloadedOnly) != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED ||
        fillermarkedFilter != TriState.DISABLED
}

fun Anime.seasonsFiltered(downloadedOnly: Boolean): Boolean {
    return effectiveSeasonDownloadedFilter(downloadedOnly) != TriState.DISABLED ||
        seasonUnseenFilter != TriState.DISABLED ||
        seasonStartedFilter != TriState.DISABLED ||
        seasonCompletedFilter != TriState.DISABLED ||
        seasonBookmarkedFilter != TriState.DISABLED ||
        seasonFillermarkedFilter != TriState.DISABLED
}

fun Anime.toSAnime(): SAnime = SAnime.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    it.background_url = backgroundUrl
    it.fetch_type = fetchType
    it.season_number = seasonNumber
    it.initialized = initialized
}

fun Anime.copyFrom(other: SAnime): Anime {
    val author = other.author ?: author
    val artist = other.artist ?: artist
    val description = other.description ?: description
    val genres = if (other.genre != null) {
        other.getGenres()
    } else {
        genre
    }
    val thumbnailUrl = other.thumbnail_url ?: thumbnailUrl
    val backgroundUrl = other.background_url ?: backgroundUrl
    return this.copy(
        author = author,
        artist = artist,
        description = description,
        genre = genres,
        thumbnailUrl = thumbnailUrl,
        backgroundUrl = backgroundUrl,
        status = other.status.toLong(),
        updateStrategy = other.update_strategy,
        fetchType = other.fetch_type,
        seasonNumber = other.season_number,
        initialized = other.initialized && initialized,
    )
}

fun SAnime.toDomainAnime(sourceId: Long): Anime {
    return Anime.create().copy(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = getGenres(),
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        backgroundUrl = background_url,
        updateStrategy = update_strategy,
        fetchType = fetch_type,
        seasonNumber = season_number,
        initialized = initialized,
        source = sourceId,
    )
}

fun Anime.hasCustomCover(coverCache: AnimeCoverCache): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}

fun Anime.hasCustomBackground(backgroundCache: AnimeBackgroundCache): Boolean {
    return backgroundCache.getCustomBackgroundFile(id).exists()
}
