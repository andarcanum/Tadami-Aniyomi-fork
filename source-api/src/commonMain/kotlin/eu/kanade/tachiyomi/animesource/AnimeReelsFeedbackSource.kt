package eu.kanade.tachiyomi.animesource

/**
 * Capability interface (feed contract v18): a feed source that accepts per-video feedback so
 * the remote service can adapt its recommendations to the viewer's taste.
 *
 * The host detects support with `rawSource is AnimeReelsFeedbackSource` (instanceof) — the
 * same marker-interface pattern as [AnimeCreatorFeedSource]; no default members are added to
 * existing interfaces, so old plugins keep working untouched.
 */
interface AnimeReelsFeedbackSource {

    /**
     * A video the user just watched. Reported once when the clip is left (swiped away) or
     * finishes. [secondsWatched] is the actual playback time and [duration] the full clip
     * length, both in seconds.
     */
    suspend fun onVideoViewed(itemId: String, secondsWatched: Double, duration: Double)

    /**
     * The user liked ([liked] = true) or unliked ([liked] = false) a video. Whether unlike
     * translates to a distinct remote action is up to the source.
     */
    suspend fun onVideoLiked(itemId: String, liked: Boolean)
}
