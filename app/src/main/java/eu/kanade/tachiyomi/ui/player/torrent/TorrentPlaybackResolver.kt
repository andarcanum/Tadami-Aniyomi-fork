package eu.kanade.tachiyomi.ui.player.torrent

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import aniyomi.core.common.torrent.DisabledTorrServerException
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import aniyomi.core.common.torrent.model.Torrent
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileNotFoundException

class TorrentPlaybackResolver(
    private val contentResolver: ContentResolver,
    private val torrentPreferences: TorrentPreferences = Injekt.get(),
    private val torrentServerApi: TorrentServerApi = Injekt.get(),
    private val torrentServerUtils: TorrentServerUtils = Injekt.get(),
) {
    fun isTorrentLikeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { url.toUri() }.getOrNull() ?: return false
        return url.startsWith("magnet:", ignoreCase = true) ||
            isTorrentFileUri(uri) ||
            isTorrServerUrl(uri)
    }

    fun isEnabledTorrentUrl(url: String?): Boolean {
        return torrentPreferences.torrServerEnable().get() && isTorrentLikeUrl(url)
    }

    fun isTorrServerUrl(url: String): Boolean {
        return runCatching { isTorrServerUrl(url.toUri()) }.getOrDefault(false)
    }

    fun extractMagnetIndex(url: String): Int {
        return url.substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .firstOrNull { it.substringBefore('=') == "index" }
            ?.substringAfter('=', missingDelimiterValue = "")
            ?.toIntOrNull()
            ?: 0
    }

    suspend fun resolve(videoUrl: String?, title: String): String {
        val url = videoUrl.orEmpty()
        if (!isTorrentLikeUrl(url)) return url
        if (!torrentPreferences.torrServerEnable().get()) throw DisabledTorrServerException()

        val started = TorrentServerService.startAndWait(timeoutSeconds = 10)
        if (!started) throw DisabledTorrServerException()
        torrentServerUtils.setTrackersList()

        val uri = url.toUri()
        return when {
            isTorrServerUrl(uri) -> resolveTorrServerUrl(uri, title)
            url.startsWith("magnet:", ignoreCase = true) -> resolveMagnet(url, title, extractMagnetIndex(url))
            uri.scheme.equals("content", ignoreCase = true) -> resolveContentUri(uri, title)
            isTorrentFileUri(uri) -> resolveTorrentUrl(url, title)
            else -> url
        }
    }

    private fun isTorrentFileUri(uri: Uri): Boolean {
        val pathLooksTorrent = uri.path?.endsWith(".torrent", ignoreCase = true) == true
        if (pathLooksTorrent) return true
        if (!uri.scheme.equals("content", ignoreCase = true)) return false
        return contentResolver.getType(uri)?.equals("application/x-bittorrent", ignoreCase = true) == true
    }

    private fun isTorrServerUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return (host == "127.0.0.1" || host == "localhost") && uri.path?.contains("/stream/") == true
    }

    private suspend fun resolveTorrServerUrl(uri: Uri, title: String): String {
        val hash = uri.getQueryParameter("link")
        val index = uri.getQueryParameter("index")?.toIntOrNull() ?: 0
        if (!hash.isNullOrBlank()) {
            val name = uri.lastPathSegment?.ifBlank { title } ?: title
            return torrentServerUtils.getTorrentPlayLink(
                Torrent(
                    title = name,
                    hash = hash,
                ),
                index,
            )
        }
        if (uri.port == torrentServerApi.getPort() && torrentServerApi.echo().isNotEmpty()) {
            return uri.toString()
        }
        throw DisabledTorrServerException()
    }

    private suspend fun resolveMagnet(url: String, title: String, index: Int): String {
        val torrent = torrentServerApi.addTorrent(
            link = url,
            title = title,
            save = false,
        )
        return torrentServerUtils.getTorrentPlayLink(torrent, index)
    }

    private suspend fun resolveTorrentUrl(url: String, title: String): String {
        val torrent = torrentServerApi.addTorrent(
            link = url,
            title = title,
            save = false,
        )
        return torrentServerUtils.getTorrentPlayLink(torrent, 0)
    }

    private suspend fun resolveContentUri(uri: Uri, title: String): String {
        val stream = contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        val torrent = torrentServerApi.uploadTorrent(
            file = stream,
            title = title.ifBlank { uri.lastPathSegment ?: "local.torrent" },
            save = false,
        )
        return torrentServerUtils.getTorrentPlayLink(torrent, 0)
    }
}
