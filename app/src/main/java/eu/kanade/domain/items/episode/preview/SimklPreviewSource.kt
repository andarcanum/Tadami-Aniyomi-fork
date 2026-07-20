package eu.kanade.domain.items.episode.preview

import kotlinx.serialization.Serializable

class SimklPreviewSource(
    private val clientId: String,
) : BasePreviewSource() {

    override val id = "simkl"

    override suspend fun getPreviews(request: PreviewRequest): EpisodePreviewResult? {
        if (clientId.isBlank()) return null

        val simklId = request.simklId
            ?: request.malId?.let { mapFromMal(it) }
            ?: return null

        val body = getBody("$API/anime/episodes/$simklId?client_id=$clientId") ?: return null
        val episodes = json.decodeFromString<List<SimklEpisode>>(body)

        val previews = episodes
            .filter { it.type == "episode" && it.img != null && it.episode != null }
            .associate { it.episode!! to "$IMAGES/${it.img}_w.jpg" }

        return EpisodePreviewResult(previews = previews).takeIf { previews.isNotEmpty() }
    }

    private suspend fun mapFromMal(malId: Long): Long? {
        val body = getBody("$API/search/id?mal=$malId&client_id=$clientId") ?: return null
        return json.decodeFromString<List<SimklFound>>(body).firstOrNull()?.ids?.simkl
    }

    @Serializable
    private data class SimklFound(val ids: SimklIds? = null)

    @Serializable
    private data class SimklIds(val simkl: Long? = null)

    @Serializable
    private data class SimklEpisode(
        val type: String? = null,
        val episode: Int? = null,
        val img: String? = null,
    )

    companion object {
        private const val API = "https://api.simkl.com"
        private const val IMAGES = "https://simkl.in/episodes"
    }
}
