package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlin.math.abs

/**
 * Lightweight on-device "For You" taste engine and diversity re-ranker.
 *
 * Consumes local engagement signals already observed by the feed (completions, rewatches, likes,
 * album/offline saves, follows, fast skips, hides, blocked tags) and re-ranks incoming candidate
 * batches for sources without a server recommender and for Mix feeds:
 * - Positive signals: completion >80% (+2), rewatch (+3), like (+4), save (+5), follow (+6).
 * - Negative signals: fast skip <1.5 s (-2), hide video (-5), hide author (-∞), blocked tags (-∞).
 * - Diversity (MMR): avoids >2 consecutive clips by the same author or dominant tag when
 *   alternatives exist.
 * - Exploration: reserves ~20% of slots (every 5th item) for novel/unscored authors & tags so
 *   the feed never collapses into a filter bubble.
 * - Cold-start neutrality: while no signals have been recorded yet, [rerank] preserves the source
 *   order verbatim.
 */
class ReelsTasteEngine(
    private val maxAuthorsTracked: Int = 500,
    private val maxTagsTracked: Int = 1_000,
) {
    private val authorScores = LinkedHashMap<String, Double>(64, 0.75f, true)
    private val tagScores = LinkedHashMap<String, Double>(128, 0.75f, true)
    private val blockedAuthors = HashSet<String>()
    private val blockedTags = HashSet<String>()
    private var signalCount: Int = 0

    @Synchronized
    fun hasSignals(): Boolean =
        signalCount > 0 || blockedAuthors.isNotEmpty() || blockedTags.isNotEmpty()

    @Synchronized
    fun recordCompletion(item: ShortVideoItem) = applyItemSignal(item, WEIGHT_COMPLETION)

    @Synchronized
    fun recordRewatch(item: ShortVideoItem) = applyItemSignal(item, WEIGHT_REWATCH)

    @Synchronized
    fun recordLike(item: ShortVideoItem) = applyItemSignal(item, WEIGHT_LIKE)

    @Synchronized
    fun recordSave(item: ShortVideoItem) = applyItemSignal(item, WEIGHT_SAVE)

    @Synchronized
    fun recordFollow(author: String) {
        val key = normalizeKey(author) ?: return
        signalCount++
        bumpScore(authorScores, key, WEIGHT_FOLLOW, maxAuthorsTracked)
    }

    @Synchronized
    fun recordFastSkip(item: ShortVideoItem) = applyItemSignal(item, WEIGHT_FAST_SKIP)

    @Synchronized
    fun recordHideVideo(item: ShortVideoItem) = applyItemSignal(item, WEIGHT_HIDE_VIDEO)

    @Synchronized
    fun recordHideAuthor(author: String) {
        val key = normalizeKey(author) ?: return
        blockedAuthors.add(key)
        authorScores.remove(key)
        signalCount++
    }

    @Synchronized
    fun unhideAuthor(author: String) {
        val key = normalizeKey(author) ?: return
        blockedAuthors.remove(key)
    }

    @Synchronized
    fun setBlockedTags(tags: Collection<String>) {
        blockedTags.clear()
        tags.mapNotNullTo(blockedTags) { normalizeKey(it) }
    }

    @Synchronized
    fun score(item: ShortVideoItem): Double {
        val authorKey = normalizeKey(item.author)
        if (authorKey != null && authorKey in blockedAuthors) return Double.NEGATIVE_INFINITY
        val normTags = item.tags.mapNotNull { normalizeKey(it) }
        if (normTags.any { it in blockedTags }) return Double.NEGATIVE_INFINITY

        val authorScore = authorKey?.let { authorScores[it] } ?: 0.0
        val tagScore = if (normTags.isEmpty()) {
            0.0
        } else {
            val sum = normTags.sumOf { tagScores[it] ?: 0.0 }
            sum / normTags.size.coerceAtLeast(1)
        }
        return authorScore * AUTHOR_WEIGHT_SHARE + tagScore * TAG_WEIGHT_SHARE
    }

    /**
     * Re-ranks [candidates] using taste affinity + diversity (max 2 consecutive same author or
     * dominant tag) + ~20% exploration slots for novel items.
     *
     * Returns [candidates] untouched when no signals have been recorded yet.
     */
    @Synchronized
    fun <T> rerank(
        candidates: List<T>,
        itemOf: (T) -> ShortVideoItem,
        recentTail: List<ShortVideoItem> = emptyList(),
    ): List<T> {
        if (candidates.size <= 1 || !hasSignals()) return candidates

        val scored = candidates.mapIndexedNotNull { idx, entry ->
            val item = itemOf(entry)
            val s = score(item)
            if (s.isInfinite() && s < 0.0) {
                null
            } else {
                ScoredCandidate(
                    entry = entry,
                    item = item,
                    originalIndex = idx,
                    tasteScore = s,
                    authorKey = normalizeKey(item.author),
                    dominantTag = dominantTagOf(item),
                    isNovel = isNovel(item),
                )
            }
        }
        if (scored.size <= 1) return scored.map { it.entry }

        val remaining = scored.toMutableList()
        val result = ArrayList<T>(scored.size)
        val recentAuthors = ArrayDeque<String?>()
        val recentTags = ArrayDeque<String?>()
        recentTail.takeLast(MAX_CONSECUTIVE_SAME_AUTHOR).forEach { tail ->
            recentAuthors.addLast(normalizeKey(tail.author))
            recentTags.addLast(dominantTagOf(tail))
        }

        var slotIndex = 0
        while (remaining.isNotEmpty()) {
            val isExplorationSlot = slotIndex > 0 && slotIndex % EXPLORATION_EVERY_N == 0
            val pickIdx = selectNextIndex(
                remaining = remaining,
                recentAuthors = recentAuthors,
                recentTags = recentTags,
                preferExploration = isExplorationSlot,
            )
            val chosen = remaining.removeAt(pickIdx)
            result.add(chosen.entry)

            recentAuthors.addLast(chosen.authorKey)
            while (recentAuthors.size > MAX_CONSECUTIVE_SAME_AUTHOR) recentAuthors.removeFirst()
            recentTags.addLast(chosen.dominantTag)
            while (recentTags.size > MAX_CONSECUTIVE_SAME_TAG) recentTags.removeFirst()
            slotIndex++
        }
        return result
    }

    /** Convenience overload for plain [ShortVideoItem] lists. */
    fun rerankItems(
        items: List<ShortVideoItem>,
        recentTail: List<ShortVideoItem> = emptyList(),
    ): List<ShortVideoItem> = rerank(items, { it }, recentTail)

    @Synchronized
    fun reset() {
        authorScores.clear()
        tagScores.clear()
        blockedAuthors.clear()
        signalCount = 0
    }

    private fun <T> selectNextIndex(
        remaining: List<ScoredCandidate<T>>,
        recentAuthors: ArrayDeque<String?>,
        recentTags: ArrayDeque<String?>,
        preferExploration: Boolean,
    ): Int {
        val saturatedAuthor = saturatedKey(recentAuthors, MAX_CONSECUTIVE_SAME_AUTHOR)
        val saturatedTag = saturatedKey(recentTags, MAX_CONSECUTIVE_SAME_TAG)

        var bestIndex = 0
        var bestEffective = Double.NEGATIVE_INFINITY
        for (i in remaining.indices) {
            val cand = remaining[i]
            val violatesAuthor = saturatedAuthor != null && cand.authorKey == saturatedAuthor
            val violatesTag = saturatedTag != null && cand.dominantTag == saturatedTag
            val diversityPenalty = (if (violatesAuthor) AUTHOR_REPEAT_PENALTY else 0.0) +
                (if (violatesTag) TAG_REPEAT_PENALTY else 0.0)
            val explorationBonus = if (preferExploration && cand.isNovel) EXPLORATION_BONUS else 0.0
            // Slight tie-breaker preserving source order for equal scores.
            val tieBreaker = -cand.originalIndex * 1e-6
            val effective = cand.tasteScore + explorationBonus - diversityPenalty + tieBreaker
            if (effective > bestEffective) {
                bestEffective = effective
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun saturatedKey(recent: ArrayDeque<String?>, limit: Int): String? {
        if (recent.size < limit) return null
        val last = recent.lastOrNull() ?: return null
        return if (recent.all { it == last }) last else null
    }

    private fun isNovel(item: ShortVideoItem): Boolean {
        val authorKey = normalizeKey(item.author)
        val authorKnown = authorKey != null && abs(authorScores[authorKey] ?: 0.0) > 0.01
        if (authorKnown) return false
        val tagsKnown = item.tags.any { tag ->
            val key = normalizeKey(tag)
            key != null && abs(tagScores[key] ?: 0.0) > 0.01
        }
        return !tagsKnown
    }

    private fun dominantTagOf(item: ShortVideoItem): String? =
        item.tags.asSequence().mapNotNull { normalizeKey(it) }.firstOrNull()

    private fun applyItemSignal(item: ShortVideoItem, weight: Double) {
        signalCount++
        normalizeKey(item.author)?.let { authorKey ->
            bumpScore(authorScores, authorKey, weight, maxAuthorsTracked)
        }
        val tags = item.tags.asSequence().mapNotNull { normalizeKey(it) }.distinct().take(MAX_TAGS_PER_ITEM).toList()
        if (tags.isNotEmpty()) {
            val perTag = weight / tags.size.coerceAtLeast(1)
            tags.forEach { tag -> bumpScore(tagScores, tag, perTag, maxTagsTracked) }
        }
    }

    private fun bumpScore(
        map: LinkedHashMap<String, Double>,
        key: String,
        delta: Double,
        maxEntries: Int,
    ) {
        val existing = map[key] ?: 0.0
        val updated = (existing * SIGNAL_DECAY + delta).coerceIn(MIN_SCORE, MAX_SCORE)
        map[key] = updated
        while (map.size > maxEntries) {
            val eldest = map.keys.firstOrNull() ?: break
            map.remove(eldest)
        }
    }

    private fun normalizeKey(raw: String?): String? =
        raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private data class ScoredCandidate<T>(
        val entry: T,
        val item: ShortVideoItem,
        val originalIndex: Int,
        val tasteScore: Double,
        val authorKey: String?,
        val dominantTag: String?,
        val isNovel: Boolean,
    )

    companion object {
        const val WEIGHT_COMPLETION = 2.0
        const val WEIGHT_REWATCH = 3.0
        const val WEIGHT_LIKE = 4.0
        const val WEIGHT_SAVE = 5.0
        const val WEIGHT_FOLLOW = 6.0
        const val WEIGHT_FAST_SKIP = -2.0
        const val WEIGHT_HIDE_VIDEO = -5.0

        const val FAST_SKIP_THRESHOLD_MS = 1_500L
        const val MAX_CONSECUTIVE_SAME_AUTHOR = 2
        const val MAX_CONSECUTIVE_SAME_TAG = 2
        const val EXPLORATION_EVERY_N = 5

        private const val AUTHOR_WEIGHT_SHARE = 0.65
        private const val TAG_WEIGHT_SHARE = 0.35
        private const val AUTHOR_REPEAT_PENALTY = 12.0
        private const val TAG_REPEAT_PENALTY = 6.0
        private const val EXPLORATION_BONUS = 15.0
        private const val SIGNAL_DECAY = 0.96
        private const val MIN_SCORE = -30.0
        private const val MAX_SCORE = 30.0
        private const val MAX_TAGS_PER_ITEM = 6
    }
}
