package tachiyomi.domain.items.episode.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.library.service.LibraryPreferences

class SetAnimeDefaultEpisodeFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
    private val getFavorites: GetAnimeFavorites,
) {

    suspend fun await(anime: Anime) {
        withNonCancellableContext {
            with(libraryPreferences) {
                setAnimeEpisodeFlags.awaitSetAllFlags(
                    animeId = anime.id,
                    unseenFilter = filterEpisodeBySeen().get(),
                    downloadedFilter = filterEpisodeByDownloaded().get(),
                    bookmarkedFilter = filterEpisodeByBookmarked().get(),
                    fillermarkedFilter = filterEpisodeByFillermarked().get(),
                    sortingMode = sortEpisodeBySourceOrNumber().get(),
                    sortingDirection = sortEpisodeByAscendingOrDescending().get(),
                    displayMode = displayEpisodeByNameOrNumber().get(),
                    showPreviews = showEpisodeThumbnailPreviews().get(),
                    showSummaries = showEpisodeSummaries().get(),
                )
            }
        }
    }

    suspend fun awaitAll() {
        withNonCancellableContext {
            val episodeFlags = with(libraryPreferences) {
                0L
                    .setFlag(filterEpisodeBySeen().get(), Anime.EPISODE_UNSEEN_MASK)
                    .setFlag(filterEpisodeByDownloaded().get(), Anime.EPISODE_DOWNLOADED_MASK)
                    .setFlag(filterEpisodeByBookmarked().get(), Anime.EPISODE_BOOKMARKED_MASK)
                    .setFlag(filterEpisodeByFillermarked().get(), Anime.EPISODE_FILLERMARKED_MASK)
                    .setFlag(sortEpisodeBySourceOrNumber().get(), Anime.EPISODE_SORTING_MASK)
                    .setFlag(sortEpisodeByAscendingOrDescending().get(), Anime.EPISODE_SORT_DIR_MASK)
                    .setFlag(displayEpisodeByNameOrNumber().get(), Anime.EPISODE_DISPLAY_MASK)
                    .setFlag(showEpisodeThumbnailPreviews().get(), Anime.EPISODE_PREVIEWS_MASK)
                    .setFlag(showEpisodeSummaries().get(), Anime.EPISODE_SUMMARIES_MASK)
            }
            setAnimeEpisodeFlags.awaitSetAllFlags(
                getFavorites.await().map { anime ->
                    AnimeUpdate(
                        id = anime.id,
                        episodeFlags = episodeFlags,
                    )
                },
            )
        }
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
