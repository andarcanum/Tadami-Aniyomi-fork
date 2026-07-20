package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SMEntry(
    val id: Long,
    val name: String,
    @SerialName("russian")
    val russian: String? = null,
    val chapters: Long?,
    val episodes: Long?,
    val image: SUEntryCover,
    val score: Double,
    val url: String,
    val status: String,
    val kind: String?,
    @SerialName("aired_on")
    val airedOn: String?,
    // Only present in the by-id endpoints; search results omit these fields.
    val description: String? = null,
    val genres: List<SMGenre>? = null,
) {
    fun toMangaTrack(trackId: Long): MangaTrackSearch {
        return MangaTrackSearch.create(trackId).apply {
            remote_id = this@SMEntry.id
            title = name
            total_chapters = chapters!!
            cover_url = ShikimoriApi.BASE_URL + image.original
            summary = description?.replace(SM_DESCRIPTION_TAG_REGEX, "")?.trim().orEmpty()
            genres = this@SMEntry.genres?.map { g -> g.russian?.takeIf { r -> r.isNotBlank() } ?: g.name }.orEmpty()
            alternative_titles = listOfNotNull(russian?.takeIf { it.isNotBlank() }?.takeIf { it != name })
            score = this@SMEntry.score
            tracking_url = ShikimoriApi.BASE_URL + url
            publishing_status = this@SMEntry.status
            publishing_type = kind ?: "unknown"
            start_date = airedOn ?: ""
        }
    }

    fun toAnimeTrack(trackId: Long): AnimeTrackSearch {
        return AnimeTrackSearch.create(trackId).apply {
            remote_id = this@SMEntry.id
            title = name
            total_episodes = episodes!!
            cover_url = ShikimoriApi.BASE_URL + image.original
            summary = description?.replace(SM_DESCRIPTION_TAG_REGEX, "")?.trim().orEmpty()
            genres = this@SMEntry.genres?.map { g -> g.russian?.takeIf { r -> r.isNotBlank() } ?: g.name }.orEmpty()
            alternative_titles = listOfNotNull(russian?.takeIf { it.isNotBlank() }?.takeIf { it != name })
            score = this@SMEntry.score
            tracking_url = ShikimoriApi.BASE_URL + url
            publishing_status = this@SMEntry.status
            publishing_type = kind ?: "unknown"
            start_date = airedOn ?: ""
        }
    }
}

@Serializable
data class SUEntryCover(
    val original: String,
    val preview: String,
)

/** Strips Shikimori bb-code style markup like [b], [/b], [character=123 Name]. */
private val SM_DESCRIPTION_TAG_REGEX = Regex("\\[[^\\]]*]")

@Serializable
data class SMGenre(
    val name: String = "",
    val russian: String? = null,
)
