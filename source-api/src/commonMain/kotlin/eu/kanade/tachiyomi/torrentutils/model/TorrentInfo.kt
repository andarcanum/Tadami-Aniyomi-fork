package eu.kanade.tachiyomi.torrentutils.model

data class TorrentInfo(
    val title: String,
    val files: List<TorrentFile>,
    val hash: String,
    val size: Long,
    val trackers: List<String> = emptyList(),
) {
    fun setTrackers(trackers: List<String>): TorrentInfo {
        return copy(
            trackers = trackers,
            files = files.map { file ->
                TorrentFile(
                    path = file.path,
                    indexFile = file.indexFile,
                    size = file.size,
                    torrentHash = hash,
                    trackers = trackers,
                )
            },
        )
    }
}

class DeadTorrentException : Exception("Torrent is dead or unavailable")
class DisabledTorrServerException : Exception("TorrServer is disabled or unavailable")
