package eu.kanade.domain.metadata.interactor

import eu.kanade.domain.anime.interactor.buildMediumPosterFallback
import eu.kanade.domain.anime.interactor.chooseBestPosterUrl
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.anilist.dto.ALManga
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.metadata.MangaExternalMetadataCache
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.model.MangaTrack

class GetMangaMetadata(
    private val metadataCache: MangaExternalMetadataCache,
    private val anilistApi: AnilistApi,
    private val shikimori: Shikimori,
    private val shikimoriApi: ShikimoriApi,
    private val getMangaTracks: GetMangaTracks,
    private val preferences: UiPreferences,
) {
    suspend fun await(manga: Manga): ExternalMetadata? {
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
                mediaId = manga.id,
                title = manga.title,
                description = manga.description,
            ),
        )
    }

    private fun createAnilistAdapter(): MetadataAdapter<MangaTrack, MangaTrackSearch> {
        return object : MetadataAdapter<MangaTrack, MangaTrackSearch> {
            override val contentType = MetadataContentType.MANGA
            override val source = MetadataSource.ANILIST
            override val trackerId = TrackerManager.ANILIST

            override suspend fun getTracks(mediaId: Long): List<MangaTrack> {
                return getMangaTracks.await(mediaId)
            }

            override fun trackTrackerId(track: MangaTrack): Long = track.trackerId

            override fun trackRemoteId(track: MangaTrack): Long = track.remoteId

            override suspend fun fetchById(remoteId: Long): MangaTrackSearch? {
                return withIOContext {
                    anilistApi.searchMangaById(remoteId)?.toTrack()
                }
            }

            override suspend fun search(query: String): List<MangaTrackSearch> = withIOContext {
                anilistApi.search(query)
            }

            override suspend fun map(
                target: MetadataTarget,
                remote: MangaTrackSearch,
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

    private fun createShikimoriAdapter(): MetadataAdapter<MangaTrack, MangaTrackSearch> {
        return object : MetadataAdapter<MangaTrack, MangaTrackSearch> {
            override val contentType = MetadataContentType.MANGA
            override val source = MetadataSource.SHIKIMORI
            override val trackerId = shikimori.id

            override suspend fun getTracks(mediaId: Long): List<MangaTrack> {
                return getMangaTracks.await(mediaId)
            }

            override fun trackTrackerId(track: MangaTrack): Long = track.trackerId

            override fun trackRemoteId(track: MangaTrack): Long = track.remoteId

            override suspend fun fetchById(remoteId: Long): MangaTrackSearch? {
                return withIOContext {
                    shikimoriApi.getMangaById(remoteId).toMangaTrack(shikimori.id)
                }
            }

            override suspend fun search(query: String): List<MangaTrackSearch> = withIOContext {
                shikimoriApi.search(query)
            }

            override suspend fun map(
                target: MetadataTarget,
                remote: MangaTrackSearch,
                searchQuery: String,
                isManualMatch: Boolean,
            ): ExternalMetadata {
                val coverUrl = remote.cover_url
                return ExternalMetadata(
                    contentType = contentType,
                    source = source,
                    mediaId = target.mediaId,
                    remoteId = remote.remote_id,
                    score = remote.score,
                    format = remote.publishing_type,
                    status = remote.publishing_status,
                    coverUrl = coverUrl,
                    coverUrlFallback = coverUrl,
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
