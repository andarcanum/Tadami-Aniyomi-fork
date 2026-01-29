package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response for single media query by ID.
 * Used for fetching anime metadata when we already have the Anilist ID.
 */
@Serializable
data class ALSingleMediaResult(
    val data: ALSingleMediaData,
)

@Serializable
data class ALSingleMediaData(
    @SerialName("Media")
    val media: ALSingleMedia?,
)

/**
 * Single media item from Anilist.
 * Contains all the fields needed for anime metadata.
 */
@Serializable
data class ALSingleMedia(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ItemCover,
    val description: String?,
    val format: String?,
    val status: String?,
    val startDate: ALFuzzyDate,
    val episodes: Long?,
    val averageScore: Int?,
    val studios: ALStudios,
)
