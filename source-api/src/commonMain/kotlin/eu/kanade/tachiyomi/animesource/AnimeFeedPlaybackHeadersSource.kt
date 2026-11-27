package eu.kanade.tachiyomi.animesource

/** Optional dynamic, public playback-header capability (contract v30). */
interface AnimeFeedPlaybackHeadersSource {

    /**
     * Returns the current playback User-Agent, Origin and Referer as a cheap local snapshot.
     * Never export Cookie, Authorization, session tokens or other credentials: media requests
     * can follow redirects outside the source origin. The host filters this public allowlist.
     * An empty map is authoritative; sources without this capability retain legacy headers.
     */
    fun playbackHeaders(): Map<String, String>
}
