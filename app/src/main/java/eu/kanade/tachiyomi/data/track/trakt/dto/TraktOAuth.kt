/*
 * Adapted from Animetail (Animetailapp/Animetail) — Apache-2.0 licensed.
 * Original source:
 *   app/src/main/java/eu/kanade/tachiyomi/data/track/trakt/dto/TraktOAuth.kt
 */
package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.Serializable

@Serializable
data class TraktOAuth(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
    val created_at: Long,
    val token_type: String,
)
