package tachiyomi.domain.reels.anime.model

import java.io.Serializable
import java.util.Date

/**
 * A short video saved as favorite from a Reels feed source.
 */
data class ReelsFavorite(
    val videoId: String,
    val sourceId: Long,
    val title: String?,
    val author: String?,
    val videoUrlHd: String,
    val videoUrlSd: String?,
    val posterUrl: String,
    val posterUrlVertical: String?,
    val webUrl: String?,
    val durationSec: Double?,
    val hasAudio: Boolean,
    val addedAt: Date,
) : Serializable
