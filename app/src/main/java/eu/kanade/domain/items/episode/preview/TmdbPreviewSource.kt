package eu.kanade.domain.items.episode.preview

import kotlinx.serialization.Serializable
import java.net.URLEncoder

class TmdbPreviewSource(
    private val apiKey: String,
) : BasePreviewSource() {

    override val id = "tmdb"

    override suspend fun getPreviews(request: PreviewRequest): EpisodePreviewResult? {
        if (apiKey.isBlank()) return null

        // 1. Поиск сериала по названию
        val query = URLEncoder.encode(request.title, "UTF-8")
        val searchBody = getBody("$API/search/tv?api_key=$apiKey&query=$query") ?: return null
        val show = json.decodeFromString<TmdbSearch>(searchBody).results.firstOrNull() ?: return null

        // 2. Сезоны — для маппинга абсолютной нумерации аниме
        val detailBody = getBody("$API/tv/${show.id}?api_key=$apiKey") ?: return null
        val seasons = json.decodeFromString<TmdbShowDetail>(detailBody).seasons
            .filter { it.season_number > 0 && it.episode_count > 0 }
            .sortedBy { it.season_number }
        if (seasons.isEmpty()) return null

        // 3. Абсолютный номер серии -> (сезон, серия в сезоне)
        val absoluteToSeason = buildMap {
            var absolute = 1
            for (season in seasons) {
                repeat(season.episode_count) { i ->
                    put(absolute++, season.season_number to (i + 1))
                }
            }
        }

        // 4. Грузим только нужные сезоны
        val neededSeasons = request.episodeNumbers
            .mapNotNull { absoluteToSeason[it]?.first }
            .distinct()

        val stills = mutableMapOf<Int, String>()
        for (seasonNumber in neededSeasons) {
            val seasonBody = getBody("$API/tv/${show.id}/season/$seasonNumber?api_key=$apiKey")
                ?: continue
            for (ep in json.decodeFromString<TmdbSeason>(seasonBody).episodes) {
                val absolute = absoluteToSeason.entries
                    .firstOrNull { it.value == seasonNumber to ep.episode_number }
                    ?.key ?: continue
                ep.still_path?.let { stills[absolute] = "$IMAGES/w500$it" }
            }
        }
        return EpisodePreviewResult(previews = stills).takeIf { stills.isNotEmpty() }
    }

    @Serializable
    private data class TmdbSearch(val results: List<TmdbShow> = emptyList())

    @Serializable
    private data class TmdbShow(val id: Long)

    @Serializable
    private data class TmdbShowDetail(val seasons: List<TmdbSeasonInfo> = emptyList())

    @Serializable
    private data class TmdbSeasonInfo(val season_number: Int, val episode_count: Int)

    @Serializable
    private data class TmdbSeason(val episodes: List<TmdbEpisode> = emptyList())

    @Serializable
    private data class TmdbEpisode(val episode_number: Int, val still_path: String? = null)

    companion object {
        private const val API = "https://api.themoviedb.org/3"
        private const val IMAGES = "https://image.tmdb.org/t/p"
    }
}
