package eu.kanade.tachiyomi.animesource.model

/**
 * Payload of a reel (contract v25): a decodable video stream or a still image served through
 * the video URL slot.
 */
enum class FeedMediaKind {
    VIDEO,
    STILL,
}
