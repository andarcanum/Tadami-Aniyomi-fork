package eu.kanade.tachiyomi.torrentutils

import aniyomi.core.common.torrent.TorrentServerApi
import eu.kanade.tachiyomi.torrentutils.model.DeadTorrentException
import eu.kanade.tachiyomi.torrentutils.model.DisabledTorrServerException
import eu.kanade.tachiyomi.torrentutils.model.TorrentFile
import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.SocketTimeoutException

actual object TorrentUtils {
    actual suspend fun getTorrentInfo(link: String, title: String): TorrentInfo {
        val api = Injekt.get<TorrentServerApi>()
        if (api.getPort() <= 0 || api.echo().isEmpty()) {
            throw DisabledTorrServerException()
        }
        val torrent = try {
            api.addTorrent(
                link = link,
                title = title,
                save = false,
            )
        } catch (e: SocketTimeoutException) {
            throw DeadTorrentException()
        }
        val hash = torrent.hash ?: throw DeadTorrentException()
        val trackers = torrent.trackers.orEmpty()
        val files = torrent.fileStats.orEmpty().mapNotNull { file ->
            val index = file.id ?: return@mapNotNull null
            TorrentFile(
                path = file.path,
                indexFile = index,
                size = file.length,
                torrentHash = hash,
                trackers = trackers,
            )
        }
        if (files.isEmpty()) throw DeadTorrentException()
        return TorrentInfo(
            title = torrent.title,
            files = files,
            hash = hash,
            size = torrent.torrentSize ?: files.sumOf { it.size },
            trackers = trackers,
        )
    }
}
