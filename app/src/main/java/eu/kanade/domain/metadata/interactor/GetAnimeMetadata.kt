package eu.kanade.domain.metadata.interactor

import eu.kanade.domain.anime.interactor.buildMediumPosterFallback
import eu.kanade.domain.anime.interactor.chooseBestPosterUrl
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.metadata.AnimeExternalMetadataCache
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack

class GetAnimeMetadata(
    private val metadataCache: AnimeExternalMetadataCache,
    private val anilistApi: AnilistApi,
    private val shikimori: Shikimori,
    private val shikimoriApi: ShikimoriApi,
    private val getAnimeTracks: GetAnimeTracks,
    private val preferences: UiPreferences,
) {
    suspend fun await(anime: Anime): ExternalMetadata? {
        val metadataSource = preferences.metadataSource().get()
        if (metadataSource == MetadataSource.NONE) {
            return null
        }

        val adapter = when (metadataSource) {
            MetadataSource.ANILIST -> createAnilistAdapter()
            MetadataSource.SHIKIMORI -> createShikimoriAdapter()
            MetadataSource.NONE -> return null
        }

        return MetadataResolver(metadataCache, adapter).await(
            MetadataTarget(
                mediaId = anime.id,
                title = anime.title,
                description = anime.description,
            ),
        )
    }

    private fun createAnilistAdapter(): MetadataAdapter<AnimeTrack, AnimeTrackSearch> {
        return object : MetadataAdapter<AnimeTrack, AnimeTrackSearch> {
            override val contentType = MetadataContentType.ANIME
            override val source = MetadataSource.ANILIST
            override val trackerId = TrackerManager.ANILIST

            override suspend fun getTracks(mediaId: Long): List<AnimeTrack> {
                return getAnimeTracks.await(mediaId)
            }

            override fun trackTrackerId(track: AnimeTrack): Long = track.trackerId

            override fun trackRemoteId(track: AnimeTrack): Long = track.remoteId

            override suspend fun fetchById(remoteId: Long): AnimeTrackSearch? {
                return withIOContext {
                    anilistApi.searchAnimeById(remoteId)?.toTrack()
                }
            }

            override suspend fun search(query: String): List<AnimeTrackSearch> = withIOContext {
                anilistApi.searchAnime(query)
            }

            override fun remoteId(remote: AnimeTrackSearch): Long = remote.remote_id

            override fun candidateTitles(remote: AnimeTrackSearch): List<String> {
                return buildList {
                    add(remote.title)
                    addAll(remote.alternative_titles)
                }
            }

            override suspend fun map(
                target: MetadataTarget,
                remote: AnimeTrackSearch,
                searchQuery: String,
                isManualMatch: Boolean,
            ): ExternalMetadata {
                val coverUrlFallback = buildMediumPosterFallback(remote.cover_url)
                return ExternalMetadata(
                    contentType = contentType,
                    source = source,
                    mediaId = target.mediaId,
                    remoteId = remote.remote_id,
                    score = remote.score.takeIf { it > 0 }?.div(10.0),
                    format = remote.publishing_type,
                    status = remote.publishing_status,
                    coverUrl = chooseBestPosterUrl(remote.cover_url, coverUrlFallback),
                    coverUrlFallback = coverUrlFallback,
                    searchQuery = searchQuery,
                    updatedAt = System.currentTimeMillis(),
                    isManualMatch = isManualMatch,
                )
            }

            override fun isNotAuthenticated(error: Throwable): Boolean {
                return error.message.orEmpty().contains("Anilist", ignoreCase = true) &&
                    error.message.orEmpty().contains("Not authenticated", ignoreCase = true)
            }
        }
    }

    private fun createShikimoriAdapter(): MetadataAdapter<AnimeTrack, AnimeTrackSearch> {
        return object : MetadataAdapter<AnimeTrack, AnimeTrackSearch> {
            override val contentType = MetadataContentType.ANIME
            override val source = MetadataSource.SHIKIMORI
            override val trackerId = shikimori.id

            override suspend fun getTracks(mediaId: Long): List<AnimeTrack> {
                return getAnimeTracks.await(mediaId)
            }

            override fun trackTrackerId(track: AnimeTrack): Long = track.trackerId

            override fun trackRemoteId(track: AnimeTrack): Long = track.remoteId

            override suspend fun fetchById(remoteId: Long): AnimeTrackSearch? {
                return withIOContext {
                    shikimoriApi.getAnimeById(remoteId).toAnimeTrack(shikimori.id)
                }
            }

            override suspend fun search(query: String): List<AnimeTrackSearch> = withIOContext {
                shikimoriApi.searchAnime(query)
            }

            override fun remoteId(remote: AnimeTrackSearch): Long = remote.remote_id

            override fun candidateTitles(remote: AnimeTrackSearch): List<String> {
                return buildList {
                    add(remote.title)
                    addAll(remote.alternative_titles)
                }
            }

            override suspend fun map(
                target: MetadataTarget,
                remote: AnimeTrackSearch,
                searchQuery: String,
                isManualMatch: Boolean,
            ): ExternalMetadata {
                val apiCoverUrl = remote.cover_url
                val htmlPoster = withIOContext { shikimoriApi.parsePosterFromHtml(remote.remote_id) }
                val coverUrl = chooseBestPosterUrl(htmlPoster, apiCoverUrl)
                return ExternalMetadata(
                    contentType = contentType,
                    source = source,
                    mediaId = target.mediaId,
                    remoteId = remote.remote_id,
                    score = remote.score,
                    format = remote.publishing_type,
                    status = remote.publishing_status,
                    coverUrl = coverUrl,
                    coverUrlFallback = apiCoverUrl,
                    searchQuery = searchQuery,
                    updatedAt = System.currentTimeMillis(),
                    isManualMatch = isManualMatch,
                )
            }

            override fun isNotAuthenticated(error: Throwable): Boolean {
                return error.message.orEmpty().contains("Shikimori", ignoreCase = true) &&
                    error.message.orEmpty().contains("Not authenticated", ignoreCase = true)
            }
        }
    }
}
