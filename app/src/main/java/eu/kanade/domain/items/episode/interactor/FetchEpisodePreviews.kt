package eu.kanade.domain.items.episode.interactor

import eu.kanade.domain.items.episode.preview.AniListPreviewSource
import eu.kanade.domain.items.episode.preview.EpisodePreviewSource
import eu.kanade.domain.items.episode.preview.JikanPreviewSource
import eu.kanade.domain.items.episode.preview.KitsuPreviewSource
import eu.kanade.domain.items.episode.preview.PreviewApiKeys
import eu.kanade.domain.items.episode.preview.PreviewRequest
import eu.kanade.domain.items.episode.preview.ShikimoriPreviewSource
import eu.kanade.domain.items.episode.preview.SimklPreviewSource
import eu.kanade.domain.items.episode.preview.TmdbPreviewSource
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FetchEpisodePreviews(
    private val getAnimeTracks: GetAnimeTracks = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    // Порядок = приоритет: точные пер-эпизодные кадры сначала,
    // пул скриншотов Shikimori — последним.
    private val sources: List<EpisodePreviewSource> = listOf(
        TmdbPreviewSource(PreviewApiKeys.TMDB_API_KEY),
        KitsuPreviewSource(),
        AniListPreviewSource(),
        JikanPreviewSource(),
        SimklPreviewSource(PreviewApiKeys.SIMKL_CLIENT_ID),
        ShikimoriPreviewSource(),
    ),
) {

    /**
     * Дозаполняет previewUrl у серий, которым источник-расширение его не дал.
     * Провайдеры опрашиваются по приоритету, пока не закроются все серии.
     */
    suspend fun await(anime: Anime, episodes: List<Episode>) {
        // Локальный источник сам генерирует превью через FFmpeg
        if (anime.isLocal()) return

        val missing = episodes
            .filter { it.previewUrl.isNullOrBlank() && it.isRecognizedNumber }
            .associateBy { it.episodeNumber.toInt() }
        if (missing.isEmpty()) return

        // Remote id из привязанных трекеров
        val tracks = getAnimeTracks.await(anime.id)
        fun remoteId(trackerId: Long) = tracks.firstOrNull { it.trackerId == trackerId }?.remoteId

        val request = PreviewRequest(
            title = anime.title,
            episodeNumbers = missing.keys.sorted(),
            malId = remoteId(TRACKER_MYANIMELIST) ?: remoteId(TRACKER_SHIKIMORI),
            aniListId = remoteId(TRACKER_ANILIST),
            kitsuId = remoteId(TRACKER_KITSU),
            simklId = remoteId(TRACKER_SIMKL),
        )

        val resolved = mutableMapOf<Int, String>()
        val fallbackPool = mutableListOf<String>()

        for (source in sources) {
            if (resolved.keys.containsAll(missing.keys)) break
            val result = try {
                source.getPreviews(request) ?: continue
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Preview source ${source.id} failed" }
                continue
            }
            result.previews.forEach { (number, url) ->
                if (number in missing && number !in resolved) resolved[number] = url
            }
            fallbackPool += result.fallbackPool
        }

        // Серии без точного превью получают кадры из пула по кругу
        if (fallbackPool.isNotEmpty()) {
            missing.keys
                .filter { it !in resolved }
                .sorted()
                .forEachIndexed { index, number ->
                    resolved[number] = fallbackPool[index % fallbackPool.size]
                }
        }
        if (resolved.isEmpty()) return

        val updates = resolved.mapNotNull { (number, url) ->
            missing[number]?.let { EpisodeUpdate(id = it.id, previewUrl = url) }
        }
        updateEpisode.awaitAll(updates)
    }

    companion object {
        // Сверь значения с eu.kanade.tachiyomi.data.track.TrackerManager
        private const val TRACKER_MYANIMELIST = 1L
        private const val TRACKER_ANILIST = 2L
        private const val TRACKER_KITSU = 3L
        private const val TRACKER_SHIKIMORI = 4L
        private const val TRACKER_SIMKL = 101L
    }
}
