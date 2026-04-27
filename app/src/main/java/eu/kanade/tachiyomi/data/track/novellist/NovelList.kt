package eu.kanade.tachiyomi.data.track.novellist

import android.app.Application
import android.graphics.Color
import com.tadami.aurora.R
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.manga.model.MangaTrack as DomainTrack

class NovelList(id: Long) : BaseTracker(id, "NovelList"), MangaTracker {

    private val json: Json by injectLazy()
    private val insertTrack: InsertNovelTrack by injectLazy()
    private val baseUrl = "https://novellist-be-960019704910.asia-east1.run.app"

    override fun getLogo() = R.drawable.ic_tracker_novellist

    override fun getLogoColor() = Color.rgb(33, 150, 243)

    override fun getStatusListManga() = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)

    override fun getStatusForManga(status: Long): StringResource? {
        return when (status) {
            READING -> MR.strings.reading
            COMPLETED -> MR.strings.completed
            ON_HOLD -> MR.strings.on_hold
            DROPPED -> MR.strings.dropped
            PLAN_TO_READ -> MR.strings.plan_to_read
            else -> null
        }
    }

    override fun getReadingStatus() = READING

    override fun getRereadingStatus() = READING

    override fun getCompletionStatus() = COMPLETED

    override fun getScoreList(): ImmutableList<String> = persistentListOf(
        "10", "9", "8", "7", "6", "5", "4", "3", "2", "1", "",
    )

    override fun indexToScore(index: Int): Double {
        return if (index == 10) 0.0 else (10 - index).toDouble()
    }

    override fun get10PointScore(track: DomainTrack): Double = track.score

    override fun displayScore(track: DomainTrack): String {
        return if (track.score == 0.0) "-" else track.score.toInt().toString()
    }

    private fun mapStatusToApi(status: Long): String {
        return when (status) {
            READING -> "IN_PROGRESS"
            COMPLETED -> "COMPLETED"
            ON_HOLD -> "PLANNED"
            DROPPED -> "DROPPED"
            PLAN_TO_READ -> "PLANNED"
            else -> "IN_PROGRESS"
        }
    }

    private fun mapStatusFromApi(status: String): Long {
        return when (status) {
            "IN_PROGRESS" -> READING
            "COMPLETED" -> COMPLETED
            "PLANNED" -> PLAN_TO_READ
            "DROPPED" -> DROPPED
            else -> READING
        }
    }

    private fun getUuidFromTrack(track: MangaTrack): String {
        return track.tracking_url.substringAfter("#", "")
    }

    private fun buildAuthenticatedRequest(url: String): Request.Builder {
        val token = getPassword()
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Language", "en-US,en;q=0.5")
            .addHeader("Origin", "https://www.novellist.co")
            .addHeader("Referer", "https://www.novellist.co/")
            .addHeader("Sec-Fetch-Dest", "empty")
            .addHeader("Sec-Fetch-Mode", "cors")
            .addHeader("Sec-Fetch-Site", "cross-site")
    }

    private suspend fun sendOptionsRequest(url: String, method: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .method("OPTIONS", null)
                .addHeader("Accept", "*/*")
                .addHeader("Access-Control-Request-Method", method)
                .addHeader("Access-Control-Request-Headers", "authorization,content-type")
                .addHeader("Origin", "https://www.novellist.co")
                .addHeader("Referer", "https://www.novellist.co/")
                .addHeader("Sec-Fetch-Dest", "empty")
                .addHeader("Sec-Fetch-Mode", "cors")
                .addHeader("Sec-Fetch-Site", "cross-site")
                .build()
            client.newCall(request).awaitSuccess()
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { "OPTIONS preflight failed: ${e.message}" }
        }
    }

    override suspend fun update(track: MangaTrack, didReadChapter: Boolean): MangaTrack {
        val uuid = getUuidFromTrack(track)
        if (uuid.isEmpty()) return track

        val url = "$baseUrl/api/users/current/reading-list/$uuid"
        sendOptionsRequest(url, "PUT")

        val requestBody = buildJsonObject {
            put("chapter_count", track.last_chapter_read.toInt())
            put("status", mapStatusToApi(track.status))
            if (track.score > 0) {
                put("rating", track.score.toInt())
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val request = buildAuthenticatedRequest(url)
            .put(requestBody)
            .build()

        client.newCall(request).awaitSuccess()
        return track
    }

    override suspend fun register(item: MangaTrack, mangaId: Long) {
        item.manga_id = mangaId
        try {
            bind(item, false)
            if (item.status == PLAN_TO_READ) {
                item.status = READING
            }
            update(item)
            item.toNovelTrack(idRequired = false)?.let { insertTrack.await(it) }
        } catch (e: Throwable) {
            withUIContext { Injekt.get<Application>().toast(e.message) }
        }
    }

    override suspend fun bind(track: MangaTrack, hasReadChapters: Boolean): MangaTrack {
        val uuid = getUuidFromTrack(track)
        if (uuid.isEmpty()) return track

        val url = "$baseUrl/api/users/current/reading-list/$uuid"
        sendOptionsRequest(url, "PUT")

        val requestBody = buildJsonObject {
            put("status", if (hasReadChapters) "IN_PROGRESS" else "PLANNED")
            put("chapter_count", track.last_chapter_read.toInt())
            put("rating", 0)
            put("note", "")
        }.toString().toRequestBody("application/json".toMediaType())

        val request = buildAuthenticatedRequest(url)
            .put(requestBody)
            .build()

        client.newCall(request).awaitSuccess()

        track.status = if (hasReadChapters) READING else PLAN_TO_READ
        return track
    }

    override suspend fun searchManga(query: String): List<MangaTrackSearch> {
        val requestBody = buildJsonObject {
            put("page", 1)
            put("sort_order", "MOST_TRENDING")
            put("title_search_query", query)
            put("language", "UNKNOWN")
            putJsonArray("label_ids") {}
            putJsonArray("excluded_label_ids") {}
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/novels/filter")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Origin", "https://www.novellist.co")
            .addHeader("Referer", "https://www.novellist.co/")
            .build()

        return try {
            val response = client.newCall(request).awaitSuccess()
            val responseBody = response.body.string()

            val jsonArray = json.decodeFromString<List<JsonObject>>(responseBody)
            jsonArray.map { obj ->
                val track = MangaTrackSearch.create(id)
                val idString = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                track.remote_id = idString.hashCode().toLong().let { if (it < 0) -it else it }
                track.title = obj["english_title"]?.jsonPrimitive?.contentOrNull
                    ?: obj["raw_title"]?.jsonPrimitive?.contentOrNull
                    ?: obj["title"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                track.cover_url = obj["cover_image_link"]?.jsonPrimitive?.contentOrNull
                    ?: obj["image_url"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                track.summary = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: idString
                track.tracking_url = "https://www.novellist.co/novel/$slug#$idString"
                track.publishing_status = obj["status"]?.jsonPrimitive?.contentOrNull ?: ""
                track
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "NovelList search failed" }
            emptyList()
        }
    }

    override suspend fun refresh(track: MangaTrack): MangaTrack {
        val uuid = getUuidFromTrack(track)
        if (uuid.isEmpty()) return track

        val url = "$baseUrl/api/users/current/reading-list/$uuid"
        val request = buildAuthenticatedRequest(url)
            .get()
            .build()

        return try {
            val response = client.newCall(request).awaitSuccess()
            with(json) {
                val obj = response.parseAs<JsonObject>()
                track.status = mapStatusFromApi(obj["status"]?.jsonPrimitive?.contentOrNull ?: "IN_PROGRESS")
                track.last_chapter_read = obj["chapter_count"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
                track.score = obj["rating"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
            }
            track
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "NovelList refresh failed" }
            track
        }
    }

    override suspend fun login(username: String, password: String) {
        if (password.isBlank()) {
            throw Exception("Access token is required. Please login via WebView first.")
        }
        saveCredentials(username.ifBlank { "NovelList User" }, password)
    }

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L

        const val LOGIN_URL = "https://www.novellist.co/login"
    }
}
