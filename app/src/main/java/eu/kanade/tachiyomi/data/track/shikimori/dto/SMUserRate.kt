package eu.kanade.tachiyomi.data.track.shikimori.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SMUserRate(
    val id: Long,
    val score: Int,
    val status: String,
    val episodes: Int = 0,
    @SerialName("target_id")
    val targetId: Long,
    @SerialName("target_type")
    val targetType: String,
)
