package eu.kanade.tachiyomi.data.library.updateerror

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

enum class LibraryUpdateErrorMedia {
    Manga,
    Anime,
    Novel,
}

enum class LibraryUpdateErrorRunType {
    Manual,
    Automatic,
}

data class LibraryUpdateErrorRecord(
    val id: Long,
    val media: LibraryUpdateErrorMedia,
    val entryId: Long,
    val title: String,
    val sourceId: Long,
    val sourceName: String,
    val thumbnailUrl: String?,
    val message: String,
    val runType: LibraryUpdateErrorRunType,
    val occurredAt: Long,
)

object LibraryUpdateErrorStore {
    private const val MAX_ERRORS_PER_MEDIA = 500
    private const val PREFS_NAME = "library_update_errors"
    private const val KEY_ERRORS = "errors"
    private const val KEY_LAST_TAB = "last_tab"

    private val ids = AtomicLong(0L)

    fun getLastSelectedTab(): LibraryUpdateErrorMedia {
        val name = prefs().getString(KEY_LAST_TAB, null) ?: return LibraryUpdateErrorMedia.Novel
        return runCatching { LibraryUpdateErrorMedia.valueOf(name) }.getOrDefault(LibraryUpdateErrorMedia.Novel)
    }

    fun setLastSelectedTab(media: LibraryUpdateErrorMedia) {
        prefs().edit().putString(KEY_LAST_TAB, media.name).apply()
    }
    private val _errors = MutableStateFlow(loadPersistedErrors())
    val errors: StateFlow<List<LibraryUpdateErrorRecord>> = _errors.asStateFlow()

    fun upsert(
        media: LibraryUpdateErrorMedia,
        entryId: Long,
        title: String,
        sourceId: Long,
        sourceName: String,
        thumbnailUrl: String?,
        message: String?,
        runType: LibraryUpdateErrorRunType,
    ) {
        val safeMessage = message
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown error"

        mutate { current ->
            val withoutPrevious = current.filterNot { it.media == media && it.entryId == entryId }
            val next = LibraryUpdateErrorRecord(
                id = ids.incrementAndGet(),
                media = media,
                entryId = entryId,
                title = title,
                sourceId = sourceId,
                sourceName = sourceName,
                thumbnailUrl = thumbnailUrl,
                message = safeMessage,
                runType = runType,
                occurredAt = Instant.now().toEpochMilli(),
            )
            (withoutPrevious + next).trimAndSort()
        }
    }

    fun markResolved(media: LibraryUpdateErrorMedia, entryId: Long) {
        mutate { current ->
            current.filterNot { it.media == media && it.entryId == entryId }
        }
    }

    fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val idsSet = ids.toHashSet()
        mutate { current -> current.filterNot { it.id in idsSet } }
    }

    fun delete(id: Long) {
        delete(listOf(id))
    }

    fun clear(media: LibraryUpdateErrorMedia? = null) {
        mutate { current ->
            if (media == null) emptyList() else current.filterNot { it.media == media }
        }
    }

    @Synchronized
    private fun mutate(block: (List<LibraryUpdateErrorRecord>) -> List<LibraryUpdateErrorRecord>) {
        val next = block(_errors.value).trimAndSort()
        _errors.value = next
        persist(next)
    }

    private fun List<LibraryUpdateErrorRecord>.trimAndSort(): List<LibraryUpdateErrorRecord> {
        return groupBy { it.media }
            .flatMap { (_, records) -> records.sortedByDescending { it.occurredAt }.take(MAX_ERRORS_PER_MEDIA) }
            .sortedWith(compareBy<LibraryUpdateErrorRecord> { it.media.ordinal }.thenByDescending { it.occurredAt })
    }

    private fun loadPersistedErrors(): List<LibraryUpdateErrorRecord> {
        return runCatching {
            val raw = prefs().getString(KEY_ERRORS, null).orEmpty()
            if (raw.isBlank()) return emptyList()

            val records = JSONArray(raw).let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            LibraryUpdateErrorRecord(
                                id = item.getLong("id"),
                                media = LibraryUpdateErrorMedia.valueOf(item.getString("media")),
                                entryId = item.getLong("entryId"),
                                title = item.getString("title"),
                                sourceId = item.getLong("sourceId"),
                                sourceName = item.getString("sourceName"),
                                thumbnailUrl = item.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                                message = item.getString("message"),
                                runType = LibraryUpdateErrorRunType.valueOf(item.getString("runType")),
                                occurredAt = item.getLong("occurredAt"),
                            ),
                        )
                    }
                }
            }.trimAndSort()
            ids.set(records.maxOfOrNull { it.id } ?: 0L)
            records
        }.getOrDefault(emptyList())
    }

    private fun persist(records: List<LibraryUpdateErrorRecord>) {
        runCatching {
            val array = JSONArray()
            records.forEach { record ->
                array.put(
                    JSONObject()
                        .put("id", record.id)
                        .put("media", record.media.name)
                        .put("entryId", record.entryId)
                        .put("title", record.title)
                        .put("sourceId", record.sourceId)
                        .put("sourceName", record.sourceName)
                        .put("thumbnailUrl", record.thumbnailUrl.orEmpty())
                        .put("message", record.message)
                        .put("runType", record.runType.name)
                        .put("occurredAt", record.occurredAt),
                )
            }
            prefs().edit().putString(KEY_ERRORS, array.toString()).apply()
        }
    }

    private fun prefs() = Injekt.get<Application>().getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
}
