package eu.kanade.tachiyomi.ui.entries.novel

import tachiyomi.domain.items.novelchapter.model.NovelChapter

enum class TranslationBatchScope {
    SELECTED,
    DOWNLOADED,
    UNREAD,
    FIRST_N_VISIBLE,
    RANGE,
}

data class TranslationBatchSelection(
    val chapterIdsToEnqueue: List<Long>,
    val skippedAlreadyTranslatedCount: Int,
)

/**
 * Resolves chapter ids to enqueue for a translation batch.
 *
 * RANGE is POSITIONAL: [rangeStart]/[rangeEnd] are 1-based positions in the passed list,
 * which must already be sorted in the desired visible order (see [TranslationBatchScope.RANGE]).
 * Out-of-range ends clamp to the last position; a range lying fully past the end of the list
 * (normalized start > [chapters].size) yields an empty result instead of clamping to the tail.
 *
 * When [limit] > 0 it acts as a global post-filter applied across ALL scopes, capping the total
 * number of enqueued ids (protects translation quota for SELECTED/DOWNLOADED/UNREAD).
 */
fun resolveTranslationBatchChapterIds(
    scope: TranslationBatchScope,
    limit: Int,
    chapters: List<NovelChapter>,
    selectedChapterIds: Set<Long>,
    downloadedChapterIds: Set<Long>,
    rangeStart: Int = 1,
    rangeEnd: Int = limit,
): List<Long> {
    val orderedChapters = chapters.asSequence()
    val resolved = when (scope) {
        TranslationBatchScope.SELECTED ->
            orderedChapters
                .filter { it.id in selectedChapterIds }
                .map { it.id }
                .toList()

        TranslationBatchScope.DOWNLOADED ->
            orderedChapters
                .filter { it.id in downloadedChapterIds }
                .map { it.id }
                .toList()

        TranslationBatchScope.UNREAD ->
            orderedChapters
                .filter { !it.read }
                .map { it.id }
                .toList()

        TranslationBatchScope.FIRST_N_VISIBLE ->
            orderedChapters
                .let { sequence ->
                    if (limit > 0) sequence.take(limit) else sequence
                }
                .map { it.id }
                .toList()

        TranslationBatchScope.RANGE -> {
            if (chapters.isEmpty()) {
                return emptyList()
            }

            val normalizedStart = minOf(rangeStart, rangeEnd).coerceAtLeast(1)
            val normalizedEnd = maxOf(rangeStart, rangeEnd).coerceAtLeast(normalizedStart)
            if (normalizedStart > chapters.size) {
                return emptyList()
            }
            val startIndex = (normalizedStart - 1).coerceAtMost(chapters.lastIndex)
            val endIndex = (normalizedEnd - 1).coerceAtMost(chapters.lastIndex)
            if (startIndex > endIndex) {
                emptyList()
            } else {
                orderedChapters
                    .drop(startIndex)
                    .take(endIndex - startIndex + 1)
                    .map { it.id }
                    .toList()
            }
        }
    }
    return if (limit > 0 && scope != TranslationBatchScope.FIRST_N_VISIBLE) {
        resolved.take(limit)
    } else {
        resolved
    }
}

fun filterTranslationBatchChapterIds(
    chapterIds: List<Long>,
    alreadyTranslatedChapterIds: Set<Long>,
    forceRetranslate: Boolean,
): TranslationBatchSelection {
    if (forceRetranslate) {
        return TranslationBatchSelection(
            chapterIdsToEnqueue = chapterIds,
            skippedAlreadyTranslatedCount = 0,
        )
    }

    val filteredChapterIds = chapterIds.filterNot { it in alreadyTranslatedChapterIds }
    return TranslationBatchSelection(
        chapterIdsToEnqueue = filteredChapterIds,
        skippedAlreadyTranslatedCount = chapterIds.size - filteredChapterIds.size,
    )
}
