package eu.kanade.tachiyomi.ui.reader.novel.dictionary

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.ui.reader.novel.NovelDictionaryResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.io.File
import kotlin.math.ln
import kotlin.math.min

@Serializable
data class NovelDictionaryHistoryEntry(
    val term: String,
    val language: String? = null,
    val targetLanguage: String? = null,
    val preview: String? = null,
    val firstLookupAt: Long,
    val lastLookupAt: Long,
    val lookupCount: Int = 1,
    // --- Version 2 fields. All optional so version 1 files keep loading. ---
    /** Pinned by the user as a word to learn. */
    val isFavorite: Boolean = false,
    /** Title of the novel the word was looked up from, if known. */
    val novelTitle: String? = null,
    /** Chapter name the word was looked up from, if known. */
    val chapterName: String? = null,
    /** Optional short quote surrounding the looked-up term. */
    val quote: String? = null,
    /** Attribution of the dictionary backend that produced the preview. */
    val provider: String? = null,
    /** Last time the word was shown in the flashcard review mode. */
    val lastReviewedAt: Long? = null,
    /** Number of "know it" answers in review mode. */
    val knownCount: Int = 0,
    /** Number of "again" answers in review mode. */
    val againCount: Int = 0,
)

@Serializable
data class NovelDictionaryHistoryFile(
    val version: Int = VERSION,
    val entries: List<NovelDictionaryHistoryEntry> = emptyList(),
) {
    companion object {
        const val VERSION = 2
    }
}

/**
 * Local, persistent history of dictionary word lookups in the novel reader.
 *
 * Stored as JSON in `filesDir/novel_dictionary_history.json`. The history can be exported to
 * and imported from a user-selected document, so readers can back it up or share it.
 *
 * File format version 2 adds favorites, source context (novel/chapter/quote), provider
 * attribution and lightweight review stats. Version 1 files load unchanged (new fields use
 * defaults), and version 2 files are readable by version 1 code (unknown keys are ignored).
 */
object NovelDictionaryHistory {

    private const val FILE_NAME = "novel_dictionary_history.json"
    private const val MAX_ENTRIES = 2000
    private const val PREVIEW_MAX_LENGTH = 160
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    private val lock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun historyFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Builds a short plain-text preview from a dictionary result for history display. */
    fun previewOf(result: NovelDictionaryResult): String? {
        val entry = result.entries.firstOrNull() ?: return null
        val text = runCatching { Jsoup.parse(entry.definitionsHtml).text() }.getOrNull()?.trim()
        if (text.isNullOrEmpty()) return null
        val partOfSpeech = entry.partOfSpeech?.trim()?.takeIf { it.isNotEmpty() }
        val combined = if (partOfSpeech != null && !text.startsWith(partOfSpeech, ignoreCase = true)) {
            "$partOfSpeech \u00b7 $text"
        } else {
            text
        }
        return if (combined.length <= PREVIEW_MAX_LENGTH) {
            combined
        } else {
            combined.take(PREVIEW_MAX_LENGTH - 1) + "\u2026"
        }
    }

    fun record(
        context: Context,
        term: String,
        language: String?,
        targetLanguage: String?,
        preview: String?,
        novelTitle: String? = null,
        chapterName: String? = null,
        quote: String? = null,
        provider: String? = null,
    ) {
        val cleanTerm = term.trim()
        if (cleanTerm.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val current = read(context)
            val key = entryKey(cleanTerm, language)
            val existing = current.entries.firstOrNull { entryKey(it.term, it.language) == key }
            val updatedEntry = if (existing != null) {
                existing.copy(
                    lastLookupAt = now,
                    lookupCount = existing.lookupCount + 1,
                    targetLanguage = targetLanguage ?: existing.targetLanguage,
                    preview = preview ?: existing.preview,
                    novelTitle = novelTitle ?: existing.novelTitle,
                    chapterName = if (novelTitle != null) chapterName else existing.chapterName,
                    quote = quote ?: existing.quote,
                    provider = provider ?: existing.provider,
                )
            } else {
                NovelDictionaryHistoryEntry(
                    term = cleanTerm,
                    language = language,
                    targetLanguage = targetLanguage,
                    preview = preview,
                    firstLookupAt = now,
                    lastLookupAt = now,
                    lookupCount = 1,
                    novelTitle = novelTitle,
                    chapterName = chapterName,
                    quote = quote,
                    provider = provider,
                )
            }
            val remaining = current.entries.filter { entryKey(it.term, it.language) != key }
            val merged = (listOf(updatedEntry) + remaining)
                .sortedByDescending { it.lastLookupAt }
                .take(MAX_ENTRIES)
            write(context, current.copy(entries = merged))
        }
    }

    /** Returns history entries, most recently looked-up first. */
    fun entries(context: Context): List<NovelDictionaryHistoryEntry> {
        return synchronized(lock) {
            read(context).entries.sortedByDescending { it.lastLookupAt }
        }
    }

    /** Marks or unmarks a single entry as favorite. Returns true if the entry was found. */
    fun setFavorite(context: Context, term: String, language: String?, favorite: Boolean): Boolean {
        return mutateEntry(context, term, language) { it.copy(isFavorite = favorite) }
    }

    /** Deletes a single entry. Returns true if the entry was found and removed. */
    fun delete(context: Context, term: String, language: String?): Boolean {
        val key = entryKey(term, language)
        synchronized(lock) {
            val current = read(context)
            val remaining = current.entries.filter { entryKey(it.term, it.language) != key }
            if (remaining.size == current.entries.size) return false
            write(context, current.copy(entries = remaining))
            return true
        }
    }

    /** Records a flashcard review answer for an entry. Returns true if the entry was found. */
    fun recordReview(context: Context, term: String, language: String?, known: Boolean): Boolean {
        val now = System.currentTimeMillis()
        return mutateEntry(context, term, language) {
            it.copy(
                lastReviewedAt = now,
                knownCount = if (known) it.knownCount + 1 else it.knownCount,
                againCount = if (known) it.againCount else it.againCount + 1,
            )
        }
    }

    /**
     * Lightweight mastery estimate in 0..1 based on lookup exposure, review answers and
     * recency. Intentionally not a full SRS: it only drives the small progress ring in the UI.
     */
    fun masteryOf(
        entry: NovelDictionaryHistoryEntry,
        now: Long = System.currentTimeMillis(),
    ): Float {
        val reviews = entry.knownCount + entry.againCount
        val correctness = if (reviews > 0) entry.knownCount.toFloat() / reviews else 0f
        val exposure = min(1f, (ln((entry.lookupCount + 1).toDouble()) / ln(10.0)).toFloat())
        val lastSeen = maxOf(entry.lastLookupAt, entry.lastReviewedAt ?: 0L)
        val days = (now - lastSeen).coerceAtLeast(0L).toFloat() / DAY_MILLIS
        val recency = (1f - days / 30f).coerceIn(0f, 1f)
        return if (reviews > 0) {
            (0.2f * exposure + 0.6f * correctness + 0.2f * recency).coerceIn(0f, 1f)
        } else {
            (0.35f * exposure + 0.15f * recency).coerceIn(0f, 1f)
        }
    }

    /**
     * Builds the flashcard review queue: favorites first, then words that were never or
     * least recently reviewed, then the most frequently looked-up ones. Entries without a
     * stored preview are skipped because the card would have an empty back side.
     */
    fun reviewQueue(
        entries: List<NovelDictionaryHistoryEntry>,
        limit: Int = 12,
    ): List<NovelDictionaryHistoryEntry> {
        if (entries.isEmpty() || limit <= 0) return emptyList()
        return entries
            .filter { !it.preview.isNullOrBlank() }
            .sortedWith(
                compareByDescending<NovelDictionaryHistoryEntry> { it.isFavorite }
                    .thenBy { it.lastReviewedAt ?: 0L }
                    .thenByDescending { it.lookupCount }
                    .thenByDescending { it.lastLookupAt },
            )
            .take(limit)
    }

    fun clear(context: Context) {
        synchronized(lock) {
            historyFile(context).delete()
        }
    }

    /** Exports the history as JSON to [uri]. Returns the number of exported entries. */
    fun exportTo(context: Context, uri: Uri): Result<Int> = runCatching {
        val payload = synchronized(lock) { read(context) }
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot open output document")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(json.encodeToString(NovelDictionaryHistoryFile.serializer(), payload))
        }
        payload.entries.size
    }

    /**
     * Imports history JSON from [uri] and merges it with the local history
     * (lookup counts are summed, timestamps are widened, favorites survive).
     * Returns the number of imported entries.
     */
    fun importFrom(context: Context, uri: Uri): Result<Int> = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open document")
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val imported = json.decodeFromString(NovelDictionaryHistoryFile.serializer(), text)
        synchronized(lock) {
            val current = read(context)
            val byKey = LinkedHashMap<String, NovelDictionaryHistoryEntry>()
            current.entries.forEach { entry -> byKey[entryKey(entry.term, entry.language)] = entry }
            imported.entries.forEach { entry ->
                if (entry.term.isBlank()) return@forEach
                val key = entryKey(entry.term, entry.language)
                val existing = byKey[key]
                byKey[key] = if (existing == null) {
                    entry
                } else {
                    existing.copy(
                        firstLookupAt = minOf(existing.firstLookupAt, entry.firstLookupAt),
                        lastLookupAt = maxOf(existing.lastLookupAt, entry.lastLookupAt),
                        lookupCount = existing.lookupCount + entry.lookupCount,
                        preview = existing.preview ?: entry.preview,
                        targetLanguage = existing.targetLanguage ?: entry.targetLanguage,
                        isFavorite = existing.isFavorite || entry.isFavorite,
                        novelTitle = existing.novelTitle ?: entry.novelTitle,
                        chapterName = existing.chapterName ?: entry.chapterName,
                        quote = existing.quote ?: entry.quote,
                        provider = existing.provider ?: entry.provider,
                        lastReviewedAt = listOfNotNull(existing.lastReviewedAt, entry.lastReviewedAt).maxOrNull(),
                        knownCount = existing.knownCount + entry.knownCount,
                        againCount = existing.againCount + entry.againCount,
                    )
                }
            }
            val merged = byKey.values
                .sortedByDescending { it.lastLookupAt }
                .take(MAX_ENTRIES)
            write(context, current.copy(entries = merged))
        }
        imported.entries.size
    }

    private fun mutateEntry(
        context: Context,
        term: String,
        language: String?,
        transform: (NovelDictionaryHistoryEntry) -> NovelDictionaryHistoryEntry,
    ): Boolean {
        val key = entryKey(term, language)
        synchronized(lock) {
            val current = read(context)
            var found = false
            val updated = current.entries.map { entry ->
                if (entryKey(entry.term, entry.language) == key) {
                    found = true
                    transform(entry)
                } else {
                    entry
                }
            }
            if (found) {
                write(context, current.copy(entries = updated))
            }
            return found
        }
    }

    private fun entryKey(term: String, language: String?): String {
        return term.trim().lowercase() + "\u0000" + language?.trim()?.lowercase().orEmpty()
    }

    private fun read(context: Context): NovelDictionaryHistoryFile {
        val file = historyFile(context)
        if (!file.isFile) return NovelDictionaryHistoryFile()
        return runCatching {
            json.decodeFromString(NovelDictionaryHistoryFile.serializer(), file.readText(Charsets.UTF_8))
        }.getOrElse { NovelDictionaryHistoryFile() }
    }

    private fun write(context: Context, payload: NovelDictionaryHistoryFile) {
        val file = historyFile(context)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(NovelDictionaryHistoryFile.serializer(), payload), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(json.encodeToString(NovelDictionaryHistoryFile.serializer(), payload), Charsets.UTF_8)
            tmp.delete()
        }
    }
}
