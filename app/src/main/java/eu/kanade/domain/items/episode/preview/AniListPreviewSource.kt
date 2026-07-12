package eu.kanade.domain.items.episode.preview

import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AniListPreviewSource : BasePreviewSource() {

    override val id = "anilist"

    override suspend fun getPreviews(request: PreviewRequest): EpisodePreviewResult? {
        val payload = buildJsonObject {
            put("query", QUERY)
            putJsonObject("variables") {
                when {
                    request.aniListId != null -> put("id", request.aniListId)
                    request.malId != null -> put("idMal", request.malId)
                    else -> put("search", request.title)
                }
            }
        }.toString()

        val body = requestBody(
            POST(API, body = payload.toRequestBody("application/json".toMediaType())),
        ) ?: return null

        val episodes = json.decodeFromString<AniListResponse>(body)
            .data?.media?.streamingEpisodes ?: return null

        // Названия вида "Episode 12 - Title"
        val previews = mutableMapOf<Int, String>()
        for (ep in episodes) {
            val number = EPISODE_REGEX.find(ep.title.orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull() ?: continue
            ep.thumbnail?.let { previews[number] = it }
        }
        return EpisodePreviewResult(previews = previews).takeIf { previews.isNotEmpty() }
    }

    @Serializable
    private data class AniListResponse(val data: AniListData? = null)

    @Serializable
    private data class AniListData(
        @SerialName("Media") val media: AniListMedia? = null,
    )

    @Serializable
    private data class AniListMedia(val streamingEpisodes: List<AniListEpisode> = emptyList())

    @Serializable
    private data class AniListEpisode(val title: String? = null, val thumbnail: String? = null)

    companion object {
        private const val API = "https://graphql.anilist.co"
        private val QUERY =
            """
            query (${'$'}id: Int, ${'$'}idMal: Int, ${'$'}search: String) {
              Media(id: ${'$'}id, idMal: ${'$'}idMal, search: ${'$'}search, type: ANIME) {
                streamingEpisodes { title thumbnail }
              }
            }
            """.trimIndent()
        private val EPISODE_REGEX = Regex("""(?:Episode|Ep\.?)\s*(\d+)""", RegexOption.IGNORE_CASE)
    }
}
