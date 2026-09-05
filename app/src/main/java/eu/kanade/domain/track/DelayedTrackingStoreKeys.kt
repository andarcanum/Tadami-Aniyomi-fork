package eu.kanade.domain.track

/**
 * Stable keys for the delayed tracking stores.
 *
 * The legacy scheme keyed entries by the bare track row `_id`: those ids come from DIFFERENT
 * autoincrement tables per medium (`manga_sync._id` vs `anime_sync._id`, both starting at 1) and
 * used to live in a SHARED prefs file, so one medium's delayed job could resolve the other
 * medium's track and push an episode as a chapter (and vice versa). The `_id` also changes on
 * every `ON CONFLICT REPLACE` re-bind, silently orphaning pending entries. The
 * (entryId, trackerId) pair is unique per track (`UNIQUE(manga_id, sync_id)`) and stable across
 * re-binds.
 */
internal object DelayedTrackingStoreKeys {

    fun build(entryId: Long, trackerId: Long): String = "$entryId:$trackerId"

    /** Returns (entryId, trackerId), or null for legacy bare-id / malformed keys. */
    fun parse(rawKey: String): Pair<Long, Long>? {
        val separator = rawKey.indexOf(':')
        if (separator <= 0 || separator == rawKey.lastIndex) return null
        val entryId = rawKey.substring(0, separator).toLongOrNull() ?: return null
        val trackerId = rawKey.substring(separator + 1).toLongOrNull() ?: return null
        return entryId to trackerId
    }
}
