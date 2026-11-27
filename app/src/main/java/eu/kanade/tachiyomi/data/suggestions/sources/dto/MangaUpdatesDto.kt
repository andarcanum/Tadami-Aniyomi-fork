package eu.kanade.tachiyomi.data.suggestions.sources.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MuSearchResponse(
    val results: List<MuSearchResult> = emptyList(),
)

@Serializable
data class MuSearchResult(
    val record: MuRecord,
    @SerialName("hit_title") val hitTitle: String? = null,
)

@Serializable
data class MuRecord(
    @SerialName("series_id") val seriesId: Long,
    val title: String,
    val type: String? = null,
)

@Serializable
data class MuGenre(
    val genre: String? = null,
)

@Serializable
data class MuSeriesDetail(
    val type: String? = null,
    val adult: Boolean = false,
    val recommendations: List<MuRecommendation> = emptyList(),
    @SerialName("category_recommendations") val categoryRecommendations: List<MuRecommendation> = emptyList(),
    val genres: List<MuGenre> = emptyList(),
    val status: String? = null,
    val completed: Boolean = false,
)

@Serializable
data class MuRecommendation(
    @SerialName("series_name") val seriesName: String,
    @SerialName("series_url") val seriesUrl: String,
    @SerialName("series_id") val seriesId: Long,
    @SerialName("series_image") val seriesImage: MuSeriesImage? = null,
    val weight: Int = 0,
)

@Serializable
data class MuSeriesImage(
    val url: MuImageUrls? = null,
)

@Serializable
data class MuImageUrls(
    val original: String? = null,
    val thumb: String? = null,
)
