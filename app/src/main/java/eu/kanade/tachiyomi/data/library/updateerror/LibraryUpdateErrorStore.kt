package eu.kanade.tachiyomi.data.library.updateerror

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    // I12: coalescing window for disk persistence (see schedulePersist).
    private const val PERSIST_DEBOUNCE_MILLIS = 500L

    private val ids = AtomicLong(0L)

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getLastSelectedTab(): LibraryUpdateErrorMedia {
        val name = prefs().getString(KEY_LAST_TAB, null) ?: return LibraryUpdateErrorMedia.Novel
        return runCatching { LibraryUpdateErrorMedia.valueOf(name) }.getOrDefault(LibraryUpdateErrorMedia.Novel)
    }

    fun setLastSelectedTab(media: LibraryUpdateErrorMedia) {
        prefs().edit().putString(KEY_LAST_TAB, media.name).apply()
    }

    // I13: the persisted blob (disk read + JSON parse of up to 1500 records) used to load
    // synchronously during object init on whichever thread touched the store first - that is
    // MAIN via the error screen model's constructor. Load once on IO instead; until it lands
    // the flow serves an empty list (the screen renders its loading state anyway).
    private val _errors = MutableStateFlow<List<LibraryUpdateErrorRecord>>(emptyList())
    val errors: StateFlow<List<LibraryUpdateErrorRecord>> = _errors.asStateFlow()

    init {
        storeScope.launch {
            val persisted = loadPersistedErrors()
            if (persisted.isEmpty()) return@launch
            val persistedMaxId = persisted.maxOf { it.id }
            mutate { current ->
                if (current.isEmpty()) {
                    ids.updateAndGet { maxOf(it, persistedMaxId) }
                    persisted
                } else {
                    // Live writers already assigned ids from a fresh counter before the load
                    // landed; re-key the persisted records (ids are internal) to avoid id
                    // collisions, and let live records win on (media, entryId).
                    val liveKeys = current.mapTo(HashSet()) { it.media to it.entryId }
                    persisted
                        .filterNot { (it.media to it.entryId) in liveKeys }
                        .map { it.copy(id = ids.incrementAndGet()) } + current
                }
            }
        }
    }

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
            // I12: no trimAndSort here - mutate() already sorts (this was the second of two
            // full sorts per upsert).
            withoutPrevious + next
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
        val candidate = block(_errors.value)
        val next = candidate.trimAndSort()
        // Hot path guard: library update jobs call markResolved() for every successfully
        // updated entry, usually when there is nothing to remove. Skip the full re-sort and
        // JSON rewrite when the list did not actually change.
        if (next == _errors.value) return
        _errors.value = next
        schedulePersist(next)
    }

    // I12: coalesced persistence - every real mutation used to re-serialize the WHOLE list
    // (up to 1500 records) to JSON synchronously on the caller's thread (including MAIN for
    // UI deletes); a dead source with hundreds of failures meant hundreds of full rewrites.
    // In-memory state updates immediately; the disk write is debounced onto the IO scope.
    private var persistJob: Job? = null

    private fun schedulePersist(records: List<LibraryUpdateErrorRecord>) {
        persistJob?.cancel()
        persistJob = storeScope.launch {
            delay(PERSIST_DEBOUNCE_MILLIS)
            persist(records)
        }
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
