package eu.kanade.tachiyomi.ui.reader.model

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.data.database.models.manga.Chapter
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.manga.model.Manga

/**
 * State for the one-time «THE END» plate shown when the last chapter of a truly
 * completed manga gets read. See docs/plans/2026-08-02_reader_finale.md.
 */
@Immutable
data class ReaderFinaleState(
    val manga: Manga,
    val chapterCount: Int,
    val daysOnShelf: Long?,
    val finishedOn: String,
    val nightVeil: Boolean,
)

/**
 * Pure gate for the finale plate. THE END must not lie, so the entry's own
 * (possibly custom) status has to say COMPLETED: ongoing series whose local
 * chapter list is merely exhausted show the regular "no next chapter"
 * notification instead and never re-trigger as new chapters land.
 */
fun shouldCelebrateFinale(
    manga: Manga,
    chapters: List<Chapter>,
    chapterWasUnread: Boolean,
    cardEnabled: Boolean,
    alreadyShownForManga: Boolean,
): Boolean =
    cardEnabled &&
        chapterWasUnread &&
        !alreadyShownForManga &&
        manga.displayStatus == SManga.COMPLETED.toLong() &&
        chapters.isNotEmpty() &&
        chapters.all { it.read }

/**
 * Whole days between adding to the library and [nowMs]. Null when the entry is not
 * in the library ([Manga.dateAdded] unset) or was added today, so the card never
 * claims "0 days".
 */
fun daysOnShelf(dateAdded: Long, nowMs: Long = System.currentTimeMillis()): Long? {
    if (dateAdded <= 0L || dateAdded > nowMs) return null
    return ((nowMs - dateAdded) / MILLIS_PER_DAY).takeIf { it > 0 }
}

private const val MILLIS_PER_DAY = 86_400_000L
