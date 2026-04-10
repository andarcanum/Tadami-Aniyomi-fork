package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALMangaSingleMediaResult(
    val data: ALMangaSingleMediaData,
)

@Serializable
data class ALMangaSingleMediaData(
    @SerialName("Media")
    val media: ALMangaSingleMedia?,
)

@Serializable
data class ALMangaSingleMedia(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ItemCover,
    val description: String?,
    val format: String?,
    val status: String?,
    val startDate: ALFuzzyDate,
    val chapters: Long?,
    val averageScore: Int?,
    val staff: ALStaff,
)
