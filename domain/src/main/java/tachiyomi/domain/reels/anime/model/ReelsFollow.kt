package tachiyomi.domain.reels.anime.model

import java.util.Date

/**
 * A followed creator on a Reels feed source. Explicit, persistent user data:
 * written even while incognito (unlike browsing history/likes).
 */
data class ReelsFollow(
    val sourceId: Long,
    val creator: String,
    val addedAt: Date,
)
