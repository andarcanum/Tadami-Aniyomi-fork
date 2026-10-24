package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem

/**
 * Real offline copies of favorite reels (B3.3): the file lives in the app-private
 * filesDir/reels_offline and the offline playlist prefers it over the network URL. Names are
 * reversible (`sourceId_<Uri-encoded videoId>.<ext>`) so the stored set can be listed without
 * side metadata.
 */
interface ReelsOfflineStore {

    /**
     * Why a download did not succeed — the UI shows distinct quota, network and unsupported
     * (adaptive manifest) messages.
     */
    enum class DownloadResult { SAVED, QUOTA, NETWORK, UNSUPPORTED }

    fun isStored(sourceId: Long, videoId: String): Boolean

    /** Playable file:// URL of a stored copy, or null. */
    fun localUrl(sourceId: Long, videoId: String): String?

    /** All stored (sourceId, videoId) pairs. */
    fun storedPairs(): Set<Pair<Long, String>>

    /** Total bytes occupied by stored copies (quota visibility, audit H8). */
    fun usedBytes(): Long

    /**
     * Downloads the reel's base URL to the store. [DownloadResult.QUOTA] when the copy would
     * push the store past [MAX_OFFLINE_BYTES], [DownloadResult.NETWORK] on transport failures,
     * [DownloadResult.UNSUPPORTED] for adaptive manifests (HLS/DASH) that need a media
     * downloader rather than a single file copy.
     */
    suspend fun download(item: ShortVideoItem, sourceId: Long): DownloadResult

    suspend fun delete(sourceId: Long, videoId: String): Boolean

    /** Removes every copy of one source; returns the deleted file count (cleanup cascade). */
    suspend fun deleteBySource(sourceId: Long): Int

    /** Removes ALL copies; returns the freed bytes (user-initiated storage cleanup). */
    suspend fun clearAll(): Long

    companion object {
        // Same ceiling as the playback cache: the store never grows without bound.
        const val MAX_OFFLINE_BYTES = 1_000_000_000L
    }
}
