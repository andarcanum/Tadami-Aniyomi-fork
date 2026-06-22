package eu.kanade.tachiyomi.torrentutils

import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo

expect object TorrentUtils {
    suspend fun getTorrentInfo(link: String, title: String = ""): TorrentInfo
}
