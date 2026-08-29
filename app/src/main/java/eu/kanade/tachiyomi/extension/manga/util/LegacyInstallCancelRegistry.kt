package eu.kanade.tachiyomi.extension.manga.util

/**
 * Tracks DownloadManager download ids whose install the user cancelled while our own
 * [MangaExtensionInstallActivity] may still be in the foreground: the activity checks this
 * registry and self-finishes instead of reporting an install outcome for a dead download.
 */
object LegacyInstallCancelRegistry {
    private val cancelled = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Long, Boolean>())

    fun markCancelled(downloadId: Long) {
        if (downloadId >= 0) {
            cancelled.add(downloadId)
        }
    }

    fun isCancelled(downloadId: Long): Boolean = cancelled.contains(downloadId)

    fun clear(downloadId: Long) {
        cancelled.remove(downloadId)
    }
}
