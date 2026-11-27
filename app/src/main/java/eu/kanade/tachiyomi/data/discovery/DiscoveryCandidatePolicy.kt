package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

/**
 * Описывает политику обработки кандидатов без известных жанров (пустой список genres).
 */
enum class UnknownGenrePolicy {
    /**
     * Неизвестные жанры допускаются с пометкой [CandidateVerdict.Admitted.hasIncompleteMetadata] = true.
     * Не удовлетворяют явному требованию [DiscoveryCandidatePolicy.requiredGenres],
     * но при best-effort режиме не отсекаются, если не нарушают чёрные списки.
     */
    ALLOW_AS_INCOMPLETE,

    /**
     * Строго отклонять кандидатов без известных жанров.
     */
    DISALLOW,
}

enum class RequiredMatchMode { ANY, ALL }
enum class RequiredEnforcement { STRICT, BEST_EFFORT }

/**
 * Неизменяемая спецификация жанровых фильтров пользователя (G01 / G02).
 */
data class GenreFilterSpec(
    val ignoredGenres: Set<String> = emptySet(),
    val requiredGenres: Set<String> = emptySet(),
    val priorityGenres: Set<String> = emptySet(),
    val requiredMatchMode: RequiredMatchMode = RequiredMatchMode.ALL,
    val requiredEnforcement: RequiredEnforcement = RequiredEnforcement.STRICT,
    val unknownGenrePolicy: UnknownGenrePolicy = UnknownGenrePolicy.ALLOW_AS_INCOMPLETE,
)

internal fun effectiveGenreConstraints(values: Set<String>): Set<String> = values.mapNotNullTo(
    LinkedHashSet(),
) { value ->
    val valid = GenreMatcher.normalizeRawConstraint(value) ?: return@mapNotNullTo null
    valid
}

internal fun GenreFilterSpec.effective(): GenreFilterSpec = copy(
    ignoredGenres = effectiveGenreConstraints(ignoredGenres),
    requiredGenres = effectiveGenreConstraints(requiredGenres),
    priorityGenres = effectiveGenreConstraints(priorityGenres),
)

/**
 * Причина отклонения кандидата политикой [DiscoveryCandidatePolicy].
 */
sealed interface RejectionReason {
    /** Кандидат содержит жанр/тег из per-media блэклиста. */
    data class Blacklisted(val tag: String) : RejectionReason

    /** Кандидат содержит жанр из глобального списка игнорируемых жанров. */
    data class GloballyIgnored(val genre: String) : RejectionReason

    /** Кандидат не удовлетворяет обязательным жанрам (в строгом режиме). */
    data class MissingRequiredGenre(val required: Set<String>) : RejectionReason

    /** Кандидат не имеет жанров, а политика требует их обязательного наличия. */
    data object UnknownGenresDisallowed : RejectionReason
}

/**
 * Результат проверки кандидата политикой [DiscoveryCandidatePolicy].
 */
sealed interface CandidateVerdict {
    data class Admitted(
        val hasIncompleteMetadata: Boolean = false,
        val matchesRequired: Boolean = false,
        val hasPriorityBoost: Boolean = false,
        val scoreMultiplier: Double = 1.0,
    ) : CandidateVerdict

    data class Rejected(
        val reason: RejectionReason,
    ) : CandidateVerdict
}

/**
 * Единый компонент жанровой политики рекомендаций (PR 6 / Finding #11).
 *
 * Семантика настроек:
 * - [blacklistedTags] (per-media blacklist): строго исключает известные совпадения. Никогда не ослабляется.
 * - [ignoredGenres] (global ignored): строго исключает известные совпадения. Никогда не ослабляется.
 * - [priorityGenres]: повышает скор (х1.5), никогда не отсекает тайтлы.
 * - [requiredGenres]: единая best-effort политика (если кандидатов с обязательными жанрами нет,
 *   мягкий фильтр ослабляется, чтобы лента не голодала; если strictRequired = true — отсекает строго).
 * - Неизвестные жанры (пустой список): отдельное состояние [UnknownGenrePolicy.ALLOW_AS_INCOMPLETE],
 *   а не «доказанное соответствие».
 */
class DiscoveryCandidatePolicy(
    blacklistedTags: Set<String> = emptySet(),
    ignoredGenres: Set<String> = emptySet(),
    requiredGenres: Set<String> = emptySet(),
    priorityGenres: Set<String> = emptySet(),
    val unknownGenrePolicy: UnknownGenrePolicy = UnknownGenrePolicy.ALLOW_AS_INCOMPLETE,
    val strictRequired: Boolean = false,
    val requiredMatchMode: RequiredMatchMode = RequiredMatchMode.ALL,
    val requiredEnforcement: RequiredEnforcement =
        if (strictRequired) RequiredEnforcement.STRICT else RequiredEnforcement.BEST_EFFORT,
) {
    val blacklistedTags: Set<String> = effectiveGenreConstraints(blacklistedTags)
    val ignoredGenres: Set<String> = effectiveGenreConstraints(ignoredGenres)
    val requiredGenres: Set<String> = effectiveGenreConstraints(requiredGenres)
    val priorityGenres: Set<String> = effectiveGenreConstraints(priorityGenres)

    constructor(
        spec: GenreFilterSpec,
        blacklistedTags: Set<String> = emptySet(),
    ) : this(
        blacklistedTags = blacklistedTags,
        ignoredGenres = spec.ignoredGenres,
        requiredGenres = spec.requiredGenres,
        priorityGenres = spec.priorityGenres,
        unknownGenrePolicy = spec.unknownGenrePolicy,
        strictRequired = spec.requiredEnforcement == RequiredEnforcement.STRICT,
        requiredMatchMode = spec.requiredMatchMode,
        requiredEnforcement = spec.requiredEnforcement,
    )

    val isStrictRequired: Boolean
        get() = strictRequired || requiredEnforcement == RequiredEnforcement.STRICT

    /** Combined hard-exclusion set (per-media blacklist + globally ignored genres). */
    val hardExcludedGenres: Set<String>
        get() = blacklistedTags + ignoredGenres

    /** True when the user configured any genre/tag that must never appear in recommendations. */
    val hasHardExclusions: Boolean
        get() = blacklistedTags.isNotEmpty() || ignoredGenres.isNotEmpty()

    private val compiledBlacklist = CompiledConstraintGroup.compile(expandGenreSet(this.blacklistedTags.toList()))
    private val compiledIgnored = CompiledConstraintGroup.compile(expandGenreSet(this.ignoredGenres.toList()))
    private val compiledRequiredByGenre: List<CompiledConstraintGroup> = this.requiredGenres.map {
        CompiledConstraintGroup.compile(expandGenreSet(listOf(it)))
    }
    private val compiledPriorityByGenre: List<CompiledConstraintGroup> = this.priorityGenres.map {
        CompiledConstraintGroup.compile(expandGenreSet(listOf(it)))
    }

    /**
     * Возвращает true, если переданный список жанров содержит запрещённый (блэклист или глобальный игнор).
     */
    fun isForbidden(genres: List<String>): Boolean {
        if (genres.isEmpty()) return false
        return matchesBlacklist(genres) != null || matchesIgnored(genres) != null
    }

    /**
     * Возвращает true, если рекомендация [item] содержит запрещённый жанр.
     */
    fun isForbidden(item: DiscoverySuggestion): Boolean {
        if (item.genres.isNotEmpty()) {
            return isForbidden(item.genres)
        }
        return false
    }

    /**
     * Возвращает true, если рекомендация [item] совпадает с приоритетными жанрами пользователя.
     */
    fun hasPriorityBoost(item: DiscoverySuggestion): Boolean {
        if (priorityGenres.isEmpty()) return false
        val verdict = evaluate(item.genres)
        return verdict is CandidateVerdict.Admitted && verdict.hasPriorityBoost
    }

    /**
     * Возвращает true, если рекомендация [item] допущена политикой (прошла blacklist, ignored и strict required).
     */
    fun isAllowed(item: DiscoverySuggestion): Boolean = evaluate(item.genres) is CandidateVerdict.Admitted

    /**
     * Возвращает true, если рекомендация не нарушает жёстких запретов (blacklist и global ignored).
     */
    fun passesHardExclusions(item: DiscoverySuggestion): Boolean = !isForbidden(item)

    /**
     * Оценивает кандидата по всем критериям политики.
     *
     * @param genres список известных жанров кандидата
     * @param fallbackTags запасные теги/жанры, если genres пуст
     */
    fun evaluate(
        genres: List<String>,
        fallbackTags: List<String> = emptyList(),
    ): CandidateVerdict {
        val effectiveGenres = if (genres.isNotEmpty()) genres else fallbackTags

        // 1. Проверка per-media blacklist (СТРОГО, не ослабляется)
        val blacklistedMatch = matchesBlacklist(effectiveGenres)
        if (blacklistedMatch != null) {
            return CandidateVerdict.Rejected(RejectionReason.Blacklisted(blacklistedMatch))
        }

        // 2. Проверка global ignored (СТРОГО, не ослабляется)
        val ignoredMatch = matchesIgnored(effectiveGenres)
        if (ignoredMatch != null) {
            return CandidateVerdict.Rejected(RejectionReason.GloballyIgnored(ignoredMatch))
        }

        // 3. Неизвестные жанры
        if (effectiveGenres.isEmpty()) {
            return when (unknownGenrePolicy) {
                UnknownGenrePolicy.DISALLOW -> CandidateVerdict.Rejected(RejectionReason.UnknownGenresDisallowed)
                UnknownGenrePolicy.ALLOW_AS_INCOMPLETE -> {
                    if (isStrictRequired && requiredGenres.isNotEmpty()) {
                        CandidateVerdict.Rejected(RejectionReason.MissingRequiredGenre(requiredGenres))
                    } else {
                        CandidateVerdict.Admitted(
                            hasIncompleteMetadata = true,
                            matchesRequired = false,
                            hasPriorityBoost = false,
                            scoreMultiplier = 1.0,
                        )
                    }
                }
            }
        }

        // 4. Обязательные жанры (requiredGenres)
        val matchesReq = matchesRequired(effectiveGenres)
        if (!matchesReq && isStrictRequired && requiredGenres.isNotEmpty()) {
            return CandidateVerdict.Rejected(RejectionReason.MissingRequiredGenre(requiredGenres))
        }

        // 5. Приоритетные жанры (priorityGenres)
        val matchesPri = matchesPriority(effectiveGenres)
        val scoreMultiplier = if (matchesPri) 1.5 else 1.0

        return CandidateVerdict.Admitted(
            hasIncompleteMetadata = false,
            matchesRequired = matchesReq,
            hasPriorityBoost = matchesPri,
            scoreMultiplier = scoreMultiplier,
        )
    }

    /**
     * Фильтрует список кандидатов ряда с учётом best-effort / strict семантики для requiredGenres:
     * - Запрещённые (блэклист и глобальный игнор) исключаются ВСЕГДА.
     * - Если заданы requiredGenres:
     *     - Выбираются точные совпадения, а в best-effort при нехватке до [fillTarget] —
     *       частичные совпадения, затем кандидаты с неизвестными жанрами и остальные допустимые.
     *     - При [fillTarget] == null сохраняется строгая тиерная выборка без добора нижних тиеров.
     *     - Если isStrictRequired: возвращается пустой список (никогда не ослабляется).
     */
    fun <T> filterRowCandidates(
        items: List<T>,
        genresSelector: (T) -> List<String>,
        fallbackTagsSelector: (T) -> List<String> = { emptyList() },
        scoreSelector: ((T) -> Double)? = null,
        fillTarget: Int? = null,
    ): List<T> {
        if (items.isEmpty()) return emptyList()

        val evaluated = items.map { item ->
            item to evaluate(genresSelector(item), fallbackTagsSelector(item))
        }

        // 1. Исключаем все строго запрещённые (Blacklist, GloballyIgnored, UnknownGenresDisallowed)
        val withoutForbidden = evaluated.filter { (_, verdict) ->
            when (verdict) {
                is CandidateVerdict.Admitted -> true
                is CandidateVerdict.Rejected -> when (verdict.reason) {
                    is RejectionReason.Blacklisted,
                    is RejectionReason.GloballyIgnored,
                    is RejectionReason.UnknownGenresDisallowed,
                    -> false
                    is RejectionReason.MissingRequiredGenre -> false
                }
            }
        }

        if (withoutForbidden.isEmpty()) return emptyList()

        // 2. Без requiredGenres — ранжируем весь допустимый список единым образом
        if (requiredGenres.isEmpty()) {
            return rankCandidateTier(
                candidates = withoutForbidden,
                genresSelector = genresSelector,
                fallbackTagsSelector = fallbackTagsSelector,
                scoreSelector = scoreSelector,
                orderByPartialRequired = false,
            )
        }

        val exactRequired = withoutForbidden.filter { (_, verdict) ->
            verdict is CandidateVerdict.Admitted && verdict.matchesRequired
        }
        if (isStrictRequired) {
            return rankCandidateTier(
                candidates = exactRequired,
                genresSelector = genresSelector,
                fallbackTagsSelector = fallbackTagsSelector,
                scoreSelector = scoreSelector,
                orderByPartialRequired = false,
            )
        }

        val exactSet = exactRequired.mapTo(HashSet()) { it.first }
        val nonExact = withoutForbidden.filterNot { it.first in exactSet }
        val partialEntries = nonExact.mapNotNull { entry ->
            val effectiveGenres = genresSelector(entry.first).ifEmpty { fallbackTagsSelector(entry.first) }
            val matchCount = countRequiredMatches(effectiveGenres)
            if (matchCount > 0) entry to matchCount else null
        }
        val partialSet = partialEntries.mapTo(HashSet()) { it.first.first }
        val incompleteEntries = nonExact.filter { (item, verdict) ->
            item !in partialSet &&
                verdict is CandidateVerdict.Admitted &&
                !verdict.matchesRequired &&
                verdict.hasIncompleteMetadata
        }
        val incompleteSet = incompleteEntries.mapTo(HashSet()) { it.first }
        val otherEntries = nonExact.filter { (item, _) -> item !in partialSet && item !in incompleteSet }

        if (fillTarget == null) {
            return when {
                exactRequired.isNotEmpty() -> rankCandidateTier(
                    exactRequired,
                    genresSelector,
                    fallbackTagsSelector,
                    scoreSelector,
                    orderByPartialRequired = false,
                )
                partialEntries.isNotEmpty() -> rankCandidateTier(
                    partialEntries.sortedByDescending { it.second }.map { it.first },
                    genresSelector,
                    fallbackTagsSelector,
                    scoreSelector,
                    orderByPartialRequired = true,
                )
                incompleteEntries.isNotEmpty() -> rankCandidateTier(
                    incompleteEntries,
                    genresSelector,
                    fallbackTagsSelector,
                    scoreSelector,
                    orderByPartialRequired = false,
                )
                else -> rankCandidateTier(
                    withoutForbidden,
                    genresSelector,
                    fallbackTagsSelector,
                    scoreSelector,
                    orderByPartialRequired = false,
                )
            }
        }

        // Best-effort добор до fillTarget с сохранением строгого приоритета тиеров:
        // exact > partial (по числу совпавших required) > incomplete > прочие допустимые.
        val target = fillTarget.coerceAtLeast(0)
        val combined = ArrayList<T>(minOf(target, withoutForbidden.size))
        fun appendTier(entries: List<Pair<T, CandidateVerdict>>, orderByPartial: Boolean) {
            if (combined.size >= target || entries.isEmpty()) return
            val ranked = rankCandidateTier(
                entries,
                genresSelector,
                fallbackTagsSelector,
                scoreSelector,
                orderByPartialRequired = orderByPartial,
            )
            val need = target - combined.size
            combined.addAll(if (ranked.size > need) ranked.take(need) else ranked)
        }
        appendTier(exactRequired, orderByPartial = false)
        appendTier(partialEntries.sortedByDescending { it.second }.map { it.first }, orderByPartial = true)
        appendTier(incompleteEntries, orderByPartial = false)
        appendTier(otherEntries, orderByPartial = false)
        return combined
    }

    private fun <T> rankCandidateTier(
        candidates: List<Pair<T, CandidateVerdict>>,
        genresSelector: (T) -> List<String>,
        fallbackTagsSelector: (T) -> List<String>,
        scoreSelector: ((T) -> Double)?,
        orderByPartialRequired: Boolean,
    ): List<T> {
        if (candidates.isEmpty()) return emptyList()
        if (priorityGenres.isEmpty() && scoreSelector == null) {
            return candidates.map { it.first }
        }

        val extractScore: (T) -> Double = scoreSelector
            ?: { item -> (item as? DiscoveryRowItem)?.score ?: (item as? DiscoverySuggestion)?.score ?: 0.0 }

        val scores = candidates.map { extractScore(it.first) }
        val minScore = scores.minOrNull() ?: 0.0
        val maxScore = scores.maxOrNull() ?: 0.0
        val hasSpread = maxScore > minScore

        return candidates
            .map { (item, verdict) ->
                val effectiveGenres = genresSelector(item).ifEmpty { fallbackTagsSelector(item) }
                val priorityMatches = if (verdict is CandidateVerdict.Admitted && verdict.hasPriorityBoost) {
                    countPriorityMatches(effectiveGenres).coerceAtLeast(1)
                } else {
                    0
                }
                val partialMatches = if (orderByPartialRequired) countRequiredMatches(effectiveGenres) else 0
                val rawScore = extractScore(item)
                val normalizedScore = if (hasSpread) (rawScore - minScore) / (maxScore - minScore) else 0.0
                val effectiveRank = priorityMatches.toDouble() + (normalizedScore * 0.5)
                Triple(item, partialMatches, effectiveRank)
            }
            .sortedWith(
                if (orderByPartialRequired) {
                    compareByDescending<Triple<T, Int, Double>> { it.second }
                        .thenByDescending { it.third }
                } else {
                    compareByDescending { it.third }
                },
            )
            .map { it.first }
    }

    private fun matchesBlacklist(itemGenres: List<String>): String? {
        if (compiledBlacklist.forms.isEmpty() || itemGenres.isEmpty()) return null
        return itemGenres.firstOrNull { genre ->
            compiledBlacklist.matches(GenreMatcher.compile(genre))
        }
    }

    private fun matchesIgnored(itemGenres: List<String>): String? {
        if (compiledIgnored.forms.isEmpty() || itemGenres.isEmpty()) return null
        return itemGenres.firstOrNull { genre ->
            compiledIgnored.matches(GenreMatcher.compile(genre))
        }
    }

    internal fun matchesRequired(itemGenres: List<String>): Boolean {
        if (requiredGenres.isEmpty()) return true
        if (itemGenres.isEmpty()) return false
        val matchCount = countRequiredMatches(itemGenres)
        return when (requiredMatchMode) {
            RequiredMatchMode.ANY -> matchCount > 0
            RequiredMatchMode.ALL -> matchCount == compiledRequiredByGenre.size
        }
    }

    internal fun countRequiredMatchTier(itemGenres: List<String>): Int = countRequiredMatches(itemGenres)

    private fun countRequiredMatches(itemGenres: List<String>): Int =
        countGroupMatches(compiledRequiredByGenre, itemGenres)

    private fun countPriorityMatches(itemGenres: List<String>): Int =
        countGroupMatches(compiledPriorityByGenre, itemGenres)

    private fun countGroupMatches(
        groups: List<CompiledConstraintGroup>,
        itemGenres: List<String>,
    ): Int {
        if (groups.isEmpty() || itemGenres.isEmpty()) return 0
        return if (groups.size == 1 || itemGenres.size == 1) {
            groups.count { group ->
                itemGenres.any { group.matches(GenreMatcher.compile(it)) }
            }
        } else {
            val compiledGenres = itemGenres.map { GenreMatcher.compile(it) }
            groups.count { group -> compiledGenres.any { group.matches(it) } }
        }
    }

    private fun matchesPriority(itemGenres: List<String>): Boolean = countPriorityMatches(itemGenres) > 0

    companion object {
        fun extractFallbackTags(reason: String?): List<String> {
            if (reason.isNullOrBlank()) return emptyList()
            return reason.splitToSequence(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }
    }
}

private class CompiledConstraintGroup(
    val canonicals: Set<String>,
    val forms: Set<String>,
) {
    fun matches(genre: GenreMatcher.CompiledGenre): Boolean = genre.pool.isNotEmpty() && (
        GenreMatcher.setsOverlap(genre.canonicals, canonicals) ||
            GenreMatcher.setsOverlap(genre.pool, forms)
        )

    companion object {
        val EMPTY = CompiledConstraintGroup(emptySet(), emptySet())

        fun compile(constraints: Collection<String>): CompiledConstraintGroup {
            if (constraints.isEmpty()) return EMPTY
            val canonicals = HashSet<String>()
            val forms = HashSet<String>()
            for (constraint in constraints) {
                val compiled = GenreMatcher.compile(constraint)
                if (compiled.pool.isEmpty()) continue
                canonicals.addAll(compiled.canonicals)
                forms.addAll(compiled.pool)
            }
            return if (forms.isEmpty()) {
                EMPTY
            } else {
                CompiledConstraintGroup(
                    canonicals = if (canonicals.isEmpty()) emptySet() else canonicals,
                    forms = forms,
                )
            }
        }
    }
}
