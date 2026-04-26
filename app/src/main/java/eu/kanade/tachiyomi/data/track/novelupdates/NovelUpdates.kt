package eu.kanade.tachiyomi.data.track.novelupdates

import android.graphics.Color
import com.tadami.aurora.R
import android.app.Application
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.FormBody
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.core.common.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.manga.model.MangaTrack as DomainTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack

class NovelUpdates(id: Long) : BaseTracker(id, "NovelUpdates"), MangaTracker {

    private val json: Json by injectLazy()
    private val insertTrack: InsertNovelTrack by injectLazy()
    private val baseUrl = "https://www.novelupdates.com"

    override fun getLogo() = R.drawable.ic_tracker_novelupdates

    override fun getLogoColor() = Color.rgb(0, 121, 107)

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

    private fun getAuthHeaders(): Headers {
        val cookies = getPassword()
        return Headers.Builder()
            .add("Cookie", cookies)
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
            .add("Referer", "$baseUrl/")
            .build()
    }

    private suspend fun getNovelId(seriesUrl: String): String? {
        return try {
            val response = client.newCall(GET(seriesUrl, getAuthHeaders())).awaitSuccess()
            val document = response.asJsoup()

            val shortlink = document.select("link[rel=shortlink]").attr("href")
            Regex("p=(\\d+)").find(shortlink)?.groupValues?.get(1)?.let { return it }

            val activityLink = document.select("a[href*=activity-stats]").attr("href")
            Regex("seriesid=(\\d+)").find(activityLink)?.groupValues?.get(1)?.let { return it }

            val postId = document.select("input#mypostid").attr("value")
            if (postId.isNotEmpty()) return postId

            null
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get novel ID from $seriesUrl" }
            null
        }
    }

    private suspend fun getReadingListStatus(novelId: String): Long? {
        return try {
            val response = client.newCall(GET("$baseUrl/series/?p=$novelId", getAuthHeaders())).awaitSuccess()
            val document = response.asJsoup()

            val sticon = document.select("div.sticon")
            if (sticon.select("img[src*=addme.png]").isNotEmpty()) {
                return null
            }

            val listLink = sticon.select("span.sttitle a").attr("href")
            val listId = Regex("list=(\\d+)").find(listLink)?.groupValues?.get(1)?.toLongOrNull() ?: return null
            when (listId) {
                0L -> READING
                1L -> COMPLETED
                2L -> PLAN_TO_READ
                3L -> ON_HOLD
                4L, 5L -> DROPPED
                else -> READING
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get reading list status" }
            null
        }
    }

    private suspend fun getNotesProgress(novelId: String): Int {
        return try {
            val formBody = FormBody.Builder()
                .add("action", "wi_notestagsfic")
                .add("strSID", novelId)
                .build()

            val request = POST("$baseUrl/wp-admin/admin-ajax.php", getAuthHeaders(), formBody)
            val response = client.newCall(request).awaitSuccess()
            val cleanedText = response.body.string().trim().replace(Regex("""\}\s*0+$"""), "}")

            val notes = Regex("\"notes\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)
                ?.groupValues
                ?.get(1)
                ?: return 0
            Regex("total\\s+chapters\\s+read:\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(notes)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: 0
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get notes progress" }
            0
        }
    }

    private suspend fun updateNotesProgress(novelId: String, chapters: Int) {
        try {
            val getNotesBody = FormBody.Builder()
                .add("action", "wi_notestagsfic")
                .add("strSID", novelId)
                .build()

            val getRequest = POST("$baseUrl/wp-admin/admin-ajax.php", getAuthHeaders(), getNotesBody)
            val getResponse = client.newCall(getRequest).awaitSuccess()
            val cleanedText = getResponse.body.string().trim().replace(Regex("""\}\s*0+$"""), "}")

            val notes = Regex("\"notes\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)
                ?.groupValues
                ?.get(1)
                ?: ""
            val tags = Regex("\"tags\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)
                ?.groupValues
                ?.get(1)
                ?: ""

            val chapterPattern = Regex("total\\s+chapters\\s+read:\\s*\\d+", RegexOption.IGNORE_CASE)
            val replacement = "total chapters read: $chapters"
            val updatedNotes = if (chapterPattern.containsMatchIn(notes)) {
                notes.replace(chapterPattern, replacement)
            } else if (notes.isEmpty()) {
                replacement
            } else {
                "$notes<br/>$replacement"
            }

            val updateBody = FormBody.Builder()
                .add("action", "wi_rlnotes")
                .add("strSID", novelId)
                .add("strNotes", updatedNotes)
                .add("strTags", tags)
                .build()

            client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", getAuthHeaders(), updateBody)).awaitSuccess()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update notes progress" }
        }
    }

    private suspend fun moveToList(novelId: String, listId: Long) {
        try {
            client.newCall(GET("$baseUrl/updatelist.php?sid=$novelId&lid=$listId&act=move", getAuthHeaders())).awaitSuccess()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to move to list" }
        }
    }

    private fun statusToListId(status: Long): Long {
        val customListId = resolveReadingListId(
            status = status,
            customEnabled = trackPreferences.novelUpdatesUseCustomListMapping().get(),
            mappingJson = trackPreferences.novelUpdatesCustomListMapping().get(),
        )
        return customListId.toLongOrNull()
            ?: when (status) {
                READING -> 0L
                COMPLETED -> 1L
                PLAN_TO_READ -> 2L
                ON_HOLD -> 3L
                DROPPED -> 4L
                else -> 0L
            }
    }

    override suspend fun update(track: MangaTrack, didReadChapter: Boolean): MangaTrack {
        val novelId = track.remote_id.toString()

        moveToList(novelId, statusToListId(track.status))

        if (track.last_chapter_read > 0) {
            updateNotesProgress(novelId, track.last_chapter_read.toInt())
        }

        return track
    }

    override suspend fun register(item: MangaTrack, mangaId: Long) {
        item.manga_id = mangaId
        try {
            bind(item, false)
            if (item.status == PLAN_TO_READ) {
                item.status = READING
            }
            logcat(LogPriority.DEBUG) { "NovelUpdates register: after bind, remote_id=${item.remote_id}, status=${item.status}, calling update()" }
            update(item)
            logcat(LogPriority.DEBUG) { "NovelUpdates register: update() completed, saving to DB" }
            item.toNovelTrack(idRequired = false)?.let { insertTrack.await(it) }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "NovelUpdates register failed" }
            withUIContext { Injekt.get<Application>().toast(e.message) }
        }
    }

    override suspend fun bind(track: MangaTrack, hasReadChapters: Boolean): MangaTrack {
        val slug = Regex("series/([^/]+)/?").find(track.tracking_url)?.groupValues?.get(1)
        logcat(LogPriority.DEBUG) { "NovelUpdates bind: slug=$slug, initial remote_id=${track.remote_id}" }
        if (slug != null) {
            val novelId = getNovelId("$baseUrl/series/$slug/")
            logcat(LogPriority.DEBUG) { "NovelUpdates bind: getNovelId returned=$novelId" }
            if (novelId != null) {
                track.remote_id = novelId.toLongOrNull() ?: track.remote_id
            }
        }

        val currentStatus = getReadingListStatus(track.remote_id.toString())
        logcat(LogPriority.DEBUG) { "NovelUpdates bind: remote_id=${track.remote_id}, status=$currentStatus, slug=$slug" }
        track.status = currentStatus ?: if (hasReadChapters) READING else PLAN_TO_READ

        val progress = getNotesProgress(track.remote_id.toString())
        if (progress > 0) {
            track.last_chapter_read = progress.toDouble()
        }

        return track
    }

    override suspend fun searchManga(query: String): List<MangaTrackSearch> {
        val encodedQuery = query.replace(" ", "+")
        val url = "$baseUrl/series-finder/?sf=1&sh=$encodedQuery&sort=sdate&order=desc"
        return try {
            val response = client.newCall(GET(url, getAuthHeaders())).awaitSuccess()
            val document = response.asJsoup()
            document.select("div.search_main_box_nu").map { element ->
                val track = MangaTrackSearch.create(id)
                val titleElement = element.select("div.search_title a").first()
                    ?: element.select(".search_title a").first()
                track.title = titleElement?.text()?.trim().orEmpty()
                track.tracking_url = titleElement?.attr("href").orEmpty()

                val sidSpan = element.select("span[id^=sid]").first()
                val sidId = sidSpan?.attr("id")
                val numericId = sidId?.let { Regex("sid(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() }

                val slug = Regex("series/([^/]+)/?").find(track.tracking_url)?.groupValues?.get(1).orEmpty()
                track.remote_id = numericId ?: slug.hashCode().toLong().let { if (it < 0) -it else it }

                val coverImg = element.select("div.search_img_nu img, .search_img_nu img").first()
                track.cover_url = coverImg?.attr("src")?.let { src ->
                    if (src.startsWith("http")) src else "$baseUrl$src"
                }.orEmpty()

                val descContainer = element.select("div.search_body_nu").first()
                val hiddenText = descContainer?.select(".testhide")?.text().orEmpty()
                val visibleText = descContainer?.text()?.replace(hiddenText, "")?.trim().orEmpty()
                track.summary = (visibleText + " " + hiddenText)
                    .replace("... more>>", "")
                    .replace("<<less", "")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                val genreText = element.select("div.search_genre, .search_genre").text()
                track.publishing_status = when {
                    genreText.contains("Completed", ignoreCase = true) -> "Completed"
                    genreText.contains("Ongoing", ignoreCase = true) -> "Ongoing"
                    else -> ""
                }
                track
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "NovelUpdates search failed" }
            emptyList()
        }
    }

    override suspend fun refresh(track: MangaTrack): MangaTrack {
        val novelId = track.remote_id.toString()

        getReadingListStatus(novelId)?.let { track.status = it }

        val progress = getNotesProgress(novelId)
        if (progress > 0) {
            track.last_chapter_read = progress.toDouble()
        }

        return track
    }

    suspend fun getAvailableReadingLists(): List<Pair<String, String>> {
        return try {
            val response = client.newCall(GET("$baseUrl/reading-list/", getAuthHeaders())).awaitSuccess()
            val document = response.asJsoup()
            val lists = mutableListOf<Pair<String, String>>()

            document.select("div#cssmenu li a").forEach { link ->
                val href = link.attr("href")
                val text = link.text().trim()
                if (href.contains("reading-list/?list=")) {
                    Regex("list=(\\d+)").find(href)?.groupValues?.get(1)?.let { listId ->
                        if (text.isNotEmpty()) {
                            lists.add(listId to text)
                        }
                    }
                }
            }

            if (lists.isEmpty()) {
                document.select("div.sticon select.stmove option").forEach { option ->
                    val value = option.attr("value")
                    val text = option.text().trim()
                    if (value.isNotEmpty() && value != "---" && value != "Select..." && text.isNotEmpty()) {
                        lists.add(value to text)
                    }
                }
            }

            lists
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get available reading lists" }
            emptyList()
        }
    }

    override suspend fun login(username: String, password: String) {
        saveCredentials(username, password)
    }

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
    }
}

internal fun resolveReadingListId(
    status: Long,
    customEnabled: Boolean,
    mappingJson: String,
): String {
    val defaultListId = when (status) {
        NovelUpdates.READING -> "0"
        NovelUpdates.COMPLETED -> "1"
        NovelUpdates.PLAN_TO_READ -> "2"
        NovelUpdates.ON_HOLD -> "3"
        NovelUpdates.DROPPED -> "4"
        else -> "0"
    }

    if (!customEnabled || mappingJson.isBlank() || mappingJson == "{}") {
        return defaultListId
    }

    return try {
        val mappings = Json.decodeFromString<Map<String, String>>(mappingJson)
        mappings[status.toString()] ?: defaultListId
    } catch (_: Exception) {
        defaultListId
    }
}
