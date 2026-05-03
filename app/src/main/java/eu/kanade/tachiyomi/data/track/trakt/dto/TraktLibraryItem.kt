/*
 * Adapted from Animetail (Animetailapp/Animetail) — Apache-2.0 licensed.
 * Original source:
 *   app/src/main/java/eu/kanade/tachiyomi/data/track/trakt/dto/TraktLibraryItem.kt
 */
package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.Serializable

@Serializable
data class TraktLibraryItem(
    val traktId: Long,
    val title: String? = null,
    val progress: Int = 0,
)
