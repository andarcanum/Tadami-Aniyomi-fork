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

    // Предварительно развёрнутые множества жанров для быстрого сопоставления с учётом RU/EN переводов
    private val expandedBlacklist: Set<String> = expandGenreSet(this.blacklistedTags.toList())
    private val expandedIgnored: Set<String> = expandGenreSet(this.ignoredGenres.toList())
    private val expandedRequired: Set<String> = expandGenreSet(this.requiredGenres.toList())
    private val expandedPriority: Set<String> = expandGenreSet(this.priorityGenres.toList())
    private val expandedRequiredByGenre: List<Set<String>> = this.requiredGenres.map { expandGenreSet(listOf(it)) }
    private val expandedPriorityByGenre: List<Set<String>> = this.priorityGenres.map { expandGenreSet(listOf(it)) }

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
     *     - Выбираются только кандидаты, матчащие requiredGenres (или с неполными метаданными при !isStrictRequired).
     *     - Если таких нет и !isStrictRequired: ослабляется требование requiredGenres,
     *       возвращаются все допущенные кандидаты (без блэклиста и игнора).
     *     - Если isStrictRequired: возвращается пустой список (никогда не ослабляется).
     */
    fun <T> filterRowCandidates(
        items: List<T>,
        genresSelector: (T) -> List<String>,
        fallbackTagsSelector: (T) -> List<String> = { emptyList() },
        scoreSelector: ((T) -> Double)? = null,
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

        // 2. Проверяем required
        var usingPartialFallback = false
        val admittedCandidates = if (requiredGenres.isEmpty()) {
            withoutForbidden
        } else {
            val exactRequired = withoutForbidden.filter { (_, verdict) ->
                verdict is CandidateVerdict.Admitted && verdict.matchesRequired
            }
            val partialRequired = if (exactRequired.isEmpty() && !isStrictRequired) {
                withoutForbidden.mapNotNull { entry ->
                    val effectiveGenres = genresSelector(entry.first).ifEmpty { fallbackTagsSelector(entry.first) }
                    val matchCount = countRequiredMatches(effectiveGenres)
                    if (matchCount > 0) entry to matchCount else null
                }
            } else {
                emptyList()
            }
            val incompleteRequired = if (exactRequired.isEmpty() && partialRequired.isEmpty() && !isStrictRequired) {
                withoutForbidden.filter { (_, verdict) ->
                    verdict is CandidateVerdict.Admitted && !verdict.matchesRequired && verdict.hasIncompleteMetadata
                }
            } else {
                emptyList()
            }
            when {
                exactRequired.isNotEmpty() -> exactRequired
                partialRequired.isNotEmpty() -> {
                    usingPartialFallback = true
                    partialRequired.sortedByDescending { it.second }.map { it.first }
                }
                incompleteRequired.isNotEmpty() -> incompleteRequired
                !isStrictRequired -> {
                    // Best-effort ослабление: подходящих по requiredGenres тайтлов нет,
                    // возвращаем допущенные без блэклиста/игнора, чтобы лента не была пустой.
                    withoutForbidden
                }
                else -> emptyList()
            }
        }

        if (admittedCandidates.isEmpty()) return emptyList()

        // 3. Если нет priorityGenres и явный scoreSelector не передан, сохраняем порядок (уже отсортирован для partialRequired)
        if (priorityGenres.isEmpty() && scoreSelector == null) {
            return admittedCandidates.map { it.first }
        }

        // 4. Единый стабильный within-row ranker с независимым вкладом количества совпавших priorityGenres
        val extractScore: (T) -> Double = scoreSelector
            ?: { item -> (item as? DiscoveryRowItem)?.score ?: (item as? DiscoverySuggestion)?.score ?: 0.0 }

        val scores = admittedCandidates.map { extractScore(it.first) }
        val minScore = scores.minOrNull() ?: 0.0
        val maxScore = scores.maxOrNull() ?: 0.0
        val hasSpread = maxScore > minScore

        return admittedCandidates
            .map { (item, verdict) ->
                val effectiveGenres = genresSelector(item).ifEmpty { fallbackTagsSelector(item) }
                val priorityMatches = if (verdict is CandidateVerdict.Admitted && verdict.hasPriorityBoost) {
                    countPriorityMatches(effectiveGenres).coerceAtLeast(1)
                } else {
                    0
                }
                val partialMatches = if (usingPartialFallback) countRequiredMatches(effectiveGenres) else 0
                val rawScore = extractScore(item)
                val normalizedScore = if (hasSpread) (rawScore - minScore) / (maxScore - minScore) else 0.0
                // Каждый совпавший priority-жанр поднимает уровень (+1.0 на жанр > макс. вклада score 0.5).
                // Внутри одного числа совпадений сохраняется ранжирование по normalizedScore / исходному порядку.
                val effectiveRank = priorityMatches.toDouble() + (normalizedScore * 0.5)
                Triple(item, partialMatches, effectiveRank)
            }
            .sortedWith(
                if (usingPartialFallback) {
                    compareByDescending<Triple<T, Int, Double>> { it.second }
                        .thenByDescending { it.third }
                } else {
                    compareByDescending { it.third }
                },
            )
            .map { it.first }
    }

    private fun matchesBlacklist(itemGenres: List<String>): String? {
        if (blacklistedTags.isEmpty() || itemGenres.isEmpty()) return null
        return itemGenres.firstOrNull { genre ->
            expandedBlacklist.any { tag -> GenreMatcher.matchesConstraint(tag, genre) }
        }
    }

    private fun matchesIgnored(itemGenres: List<String>): String? {
        if (ignoredGenres.isEmpty() || itemGenres.isEmpty()) return null
        return itemGenres.firstOrNull { genre ->
            expandedIgnored.any { ignored -> GenreMatcher.matchesConstraint(ignored, genre) }
        }
    }

    internal fun matchesRequired(itemGenres: List<String>): Boolean {
        if (requiredGenres.isEmpty()) return true
        if (itemGenres.isEmpty()) return false
        val matchCount = countRequiredMatches(itemGenres)
        return when (requiredMatchMode) {
            RequiredMatchMode.ANY -> matchCount > 0
            RequiredMatchMode.ALL -> matchCount == expandedRequiredByGenre.size
        }
    }

    internal fun countRequiredMatchTier(itemGenres: List<String>): Int = countRequiredMatches(itemGenres)

    private fun countRequiredMatches(itemGenres: List<String>): Int {
        if (requiredGenres.isEmpty() || itemGenres.isEmpty()) return 0
        return expandedRequiredByGenre.count { expanded ->
            itemGenres.any { itemG ->
                expanded.any { exp -> GenreMatcher.matchesConstraint(exp, itemG) }
            }
        }
    }

    private fun countPriorityMatches(itemGenres: List<String>): Int {
        if (priorityGenres.isEmpty() || itemGenres.isEmpty()) return 0
        return expandedPriorityByGenre.count { expanded ->
            itemGenres.any { genre ->
                expanded.any { exp -> GenreMatcher.matchesConstraint(exp, genre) }
            }
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
