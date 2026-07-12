package eu.kanade.domain.items.episode.preview

import kotlinx.serialization.Serializable
import java.net.URLEncoder

class KitsuPreviewSource : BasePreviewSource() {

    override val id = "kitsu"

    override suspend fun getPreviews(request: PreviewRequest): EpisodePreviewResult? {
        val kitsuId = request.kitsuId
            ?: request.malId?.let { mapFromMal(it) }
            ?: searchByTitle(request.title)
            ?: return null

        val previews = mutableMapOf<Int, String>()
        var offset = 0
        while (true) {
            val body = getBody(
                "$API/anime/$kitsuId/episodes?page[limit]=20&page[offset]=$offset",
            ) ?: break
            val page = json.decodeFromString<KitsuEpisodes>(body)
            for (ep in page.data) {
                val number = ep.attributes.number ?: continue
                val thumb = ep.attributes.thumbnail?.original ?: continue
                previews[number] = thumb
            }
            if (page.data.size < 20) break
            if (previews.keys.containsAll(request.episodeNumbers)) break
            offset += 20
        }
        return EpisodePreviewResult(previews = previews).takeIf { previews.isNotEmpty() }
    }

    private suspend fun mapFromMal(malId: Long): Long? {
        val body = getBody(
            "$API/mappings?filter[externalSite]=myanimelist/anime&filter[externalId]=$malId&include=item",
        ) ?: return null
        return json.decodeFromString<KitsuMappings>(body)
            .included.firstOrNull { it.type == "anime" }?.id?.toLongOrNull()
    }

    private suspend fun searchByTitle(title: String): Long? {
        val query = URLEncoder.encode(title, "UTF-8")
        val body = getBody("$API/anime?filter[text]=$query&page[limit]=1") ?: return null
        return json.decodeFromString<KitsuSearch>(body).data.firstOrNull()?.id?.toLongOrNull()
    }

    @Serializable
    private data class KitsuSearch(val data: List<KitsuResource> = emptyList())

    @Serializable
    private data class KitsuResource(val id: String)

    @Serializable
    private data class KitsuMappings(val included: List<KitsuIncluded> = emptyList())

    @Serializable
    private data class KitsuIncluded(val id: String, val type: String)

    @Serializable
    private data class KitsuEpisodes(val data: List<KitsuEpisode> = emptyList())

    @Serializable
    private data class KitsuEpisode(val attributes: KitsuEpisodeAttrs)

    @Serializable
    private data class KitsuEpisodeAttrs(
        val number: Int? = null,
        val thumbnail: KitsuThumb? = null,
    )

    @Serializable
    private data class KitsuThumb(val original: String? = null)

    companion object {
        private const val API = "https://kitsu.io/api/edge"
    }
}
