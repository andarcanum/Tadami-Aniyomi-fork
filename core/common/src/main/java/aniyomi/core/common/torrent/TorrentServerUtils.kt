package aniyomi.core.common.torrent

import aniyomi.core.common.torrent.model.Torrent
import xyz.secozzi.torrserver.TorrServer
import java.io.File
import java.net.URLEncoder

class TorrentServerUtils(
    private val preferences: TorrentPreferences,
    private val api: TorrentServerApi,
) {
    fun setTrackersList() {
        val trackers = preferences.torrServerTrackers().get()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(",\n")
        TorrServer.addTrackers(trackers)
    }

    fun getTorrentPlayLink(torr: Torrent, index: Int): String {
        return buildTorrentPlayLink(api.hostUrl, torr, index)
    }

    companion object {
        fun buildTorrentPlayLink(hostUrl: String, torr: Torrent, index: Int): String {
            val file = torr.fileStats?.firstOrNull { it.id == index }
            val name = file?.let { File(it.path).name } ?: torr.title
            return "$hostUrl/stream/${name.urlEncode()}?link=${torr.hash.orEmpty()}&index=$index&play"
        }

        private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
    }
}
