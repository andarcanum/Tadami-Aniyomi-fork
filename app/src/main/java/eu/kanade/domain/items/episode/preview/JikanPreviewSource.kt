package eu.kanade.domain.items.episode.preview

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.net.URLEncoder

class JikanPreviewSource : BasePreviewSource() {

    override val id = "jikan"

    override suspend fun getPreviews(request: PreviewRequest): EpisodePreviewResult? {
        val malId = request.malId ?: searchByTitle(request.title) ?: return null

        val previews = mutableMapOf<Int, String>()
        var page = 1
        while (true) {
            delay(400L) // Jikan: не более ~3 запросов в секунду
            val body = getBody("$API/anime/$malId/videos/episodes?page=$page") ?: break
            val response = json.decodeFromString<JikanEpisodes>(body)
            for (ep in response.data) {
                val image = ep.images?.jpg?.image_url ?: continue
                previews[ep.mal_id] = image
            }
            if (response.pagination?.has_next_page != true) break
            page++
        }
        return EpisodePreviewResult(previews = previews).takeIf { previews.isNotEmpty() }
    }

    private suspend fun searchByTitle(title: String): Long? {
        delay(400L)
        val query = URLEncoder.encode(title, "UTF-8")
        val body = getBody("$API/anime?q=$query&limit=1") ?: return null
        return json.decodeFromString<JikanSearch>(body).data.firstOrNull()?.mal_id
    }

    @Serializable
    private data class JikanSearch(val data: List<JikanAnime> = emptyList())

    @Serializable
    private data class JikanAnime(val mal_id: Long)

    @Serializable
    private data class JikanEpisodes(
        val data: List<JikanEpisode> = emptyList(),
        val pagination: JikanPagination? = null,
    )

    @Serializable
    private data class JikanEpisode(val mal_id: Int, val images: JikanImages? = null)

    @Serializable
    private data class JikanImages(val jpg: JikanJpg? = null)

    @Serializable
    private data class JikanJpg(val image_url: String? = null)

    @Serializable
    private data class JikanPagination(val has_next_page: Boolean = false)

    companion object {
        private const val API = "https://api.jikan.moe/v4"
    }
}
