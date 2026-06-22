package aniyomi.core.common.torrent

import aniyomi.core.common.torrent.model.Torrent
import aniyomi.core.common.torrent.model.TorrentRequest
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import java.io.InputStream

class TorrentServerApi(
    private val network: NetworkHelper,
    private val json: Json,
) {
    val hostUrl: String
        get() = "http://127.0.0.1:$port"

    @Volatile
    private var port: Int = 0

    fun setPort(value: Int) {
        port = value
    }

    fun getPort(): Int = port

    suspend fun echo(): String {
        if (port <= 0) return ""
        return try {
            network.client.newCall(GET("$hostUrl/echo")).awaitSuccess().body.string()
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Error sending TorrServer echo" }
            ""
        }
    }

    suspend fun addTorrent(
        link: String,
        title: String,
        poster: String = "",
        data: String = "",
        save: Boolean = false,
    ): Torrent {
        check(port > 0) { "TorrServer port is not set" }
        val request = json.encodeToString(
            TorrentRequest(
                action = "add",
                link = link,
                title = title,
                poster = poster,
                data = data,
                saveToDb = save,
            ),
        )
        val response = network.client.newCall(
            POST(
                "$hostUrl/torrents",
                body = request.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
            ),
        ).awaitSuccess()
        return response.use { json.decodeFromStream(it.body.byteStream()) }
    }

    suspend fun uploadTorrent(
        file: InputStream,
        title: String,
        save: Boolean = false,
    ): Torrent {
        check(port > 0) { "TorrServer port is not set" }
        val bytes = file.use { it.readBytes() }
        val fileRequestBody = bytes.toRequestBody("application/x-bittorrent".toMediaTypeOrNull())
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", title, fileRequestBody)
            .addFormDataPart("save", save.toString())
            .addFormDataPart("title", title)
            .build()
        val response = network.client.newCall(
            POST(
                "$hostUrl/torrent/upload",
                body = requestBody,
            ),
        ).awaitSuccess()
        return response.use { json.decodeFromStream(it.body.byteStream()) }
    }
}
