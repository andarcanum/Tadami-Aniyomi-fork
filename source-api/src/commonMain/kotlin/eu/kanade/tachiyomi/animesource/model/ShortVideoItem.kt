package eu.kanade.tachiyomi.animesource.model

import androidx.compose.runtime.Immutable
import java.io.Serializable

// All fields are vals and treated as immutable after construction — the annotation lets
// Compose skip reel pages whose inputs have not changed (annotation only, ABI-neutral).
@Immutable
data class ShortVideoItem(
    val id: String,
    val title: String? = null,
    val author: String? = null,
    // Guaranteed playable URL: the lowest/only quality variant the source provides.
    // videoUrlHd is an optional higher-quality upgrade; never swap the two —
    // the player must always be able to fall back to videoUrl.
    val videoUrl: String,
    val videoUrlHd: String? = null,
    val posterUrl: String,
    // Vertical (9:16) poster variant when the source provides one (e.g. RedGIFs `vposter`).
    val posterUrlVertical: String? = null,
    val durationSec: Float? = null,
    val hasAudio: Boolean = false,
    val tags: List<String> = emptyList(),
    // Watch page URL; used for sharing instead of the raw CDN video link.
    val webUrl: String? = null,
    val viewsCount: Long? = null,
    val likesCount: Long? = null,
    val isAuthorVerified: Boolean = false,
    val createdAtEpochSec: Long? = null,
) : Serializable
