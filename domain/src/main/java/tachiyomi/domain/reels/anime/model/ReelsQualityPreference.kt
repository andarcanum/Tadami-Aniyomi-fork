package tachiyomi.domain.reels.anime.model

/**
 * A Telegram quality preference.
 *
 * [Source] is the message's main file, not a promise of a camera original or 1080p. A
 * [Resolution] is a request for an exact short-edge label (480/720/1080 in the global UI); a
 * manual per-item override may use any positive short edge the item actually offers.
 */
sealed interface ReelsQualityPreference {

    data object Source : ReelsQualityPreference

    data class Resolution(val shortEdge: Int) : ReelsQualityPreference {
        init {
            require(shortEdge > 0) { "Resolution shortEdge must be positive" }
        }
    }
}
