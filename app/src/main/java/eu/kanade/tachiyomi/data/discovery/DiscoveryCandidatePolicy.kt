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
    val blacklistedTags: Set<String> = emptySet(),
    val ignoredGenres: Set<String> = emptySet(),
    val requiredGenres: Set<String> = emptySet(),
    val priorityGenres: Set<String> = emptySet(),
    val unknownGenrePolicy: UnknownGenrePolicy = UnknownGenrePolicy.ALLOW_AS_INCOMPLETE,
    val strictRequired: Boolean = false,
) {
    // Предварительно развёрнутые множества жанров для быстрого сопоставления с учётом RU/EN переводов
    private val expandedBlacklist: Set<String> = expandGenreSet(blacklistedTags.toList())
    private val expandedIgnored: Set<String> = expandGenreSet(ignoredGenres.toList())
    private val expandedRequired: Set<String> = expandGenreSet(requiredGenres.toList())
    private val expandedPriority: Set<String> = expandGenreSet(priorityGenres.toList())

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
        if (item.rowType == DiscoveryRowType.TASTE) {
            val tags = extractFallbackTags(item.reason)
            if (tags.isNotEmpty()) {
                return isForbidden(tags)
            }
        }
        return false
    }

    /**
     * Возвращает true, если рекомендация [item] допущена политикой.
     */
    fun isAllowed(item: DiscoverySuggestion): Boolean = !isForbidden(item)

    /**
     * Оценивает кандидата по всем критериям политики.
     *
     * @param genres список известных жанров кандидата
     * @param fallbackTags запасные теги/жанры (например, из reason строки TASTE), если genres пуст
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
                    if (strictRequired && requiredGenres.isNotEmpty()) {
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
        if (!matchesReq && strictRequired && requiredGenres.isNotEmpty()) {
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
     * Фильтрует список кандидатов ряда с учётом best-effort семантики для requiredGenres:
     * - Запрещённые (блэклист и глобальный игнор) исключаются ВСЕГДА.
     * - Если заданы requiredGenres:
     *     - Выбираются только кандидаты, матчащие requiredGenres (или с неполными метаданными при !strictRequired).
     *     - Если таких нет и !strictRequired: ослабляется требование requiredGenres,
     *       возвращаются все допущенные кандидаты (без блэклиста и игнора).
     */
    fun <T> filterRowCandidates(
        items: List<T>,
        genresSelector: (T) -> List<String>,
        fallbackTagsSelector: (T) -> List<String> = { emptyList() },
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
        if (requiredGenres.isEmpty()) return withoutForbidden.map { it.first }

        // 2. Проверяем required
        val withRequired = withoutForbidden.filter { (_, verdict) ->
            if (verdict is CandidateVerdict.Admitted) {
                verdict.matchesRequired || (verdict.hasIncompleteMetadata && !strictRequired)
            } else {
                false
            }
        }.map { it.first }

        return if (withRequired.isNotEmpty()) {
            withRequired
        } else if (!strictRequired) {
            // Best-effort ослабление: подходящих по requiredGenres тайтлов нет,
            // возвращаем допущенные без блэклиста/игнора, чтобы лента не была пустой.
            withoutForbidden.map { it.first }
        } else {
            emptyList()
        }
    }

    private fun matchesBlacklist(itemGenres: List<String>): String? {
        if (blacklistedTags.isEmpty() || itemGenres.isEmpty()) return null
        return itemGenres.firstOrNull { genre ->
            expandedBlacklist.any { tag -> GenreMatcher.matches(tag, genre) }
        }
    }

    private fun matchesIgnored(itemGenres: List<String>): String? {
        if (ignoredGenres.isEmpty() || itemGenres.isEmpty()) return null
        return itemGenres.firstOrNull { genre ->
            expandedIgnored.any { ignored -> GenreMatcher.matches(ignored, genre) }
        }
    }

    private fun matchesRequired(itemGenres: List<String>): Boolean {
        if (requiredGenres.isEmpty() || itemGenres.isEmpty()) return false
        return itemGenres.any { genre ->
            expandedRequired.any { req -> GenreMatcher.matches(req, genre) }
        }
    }

    private fun matchesPriority(itemGenres: List<String>): Boolean {
        if (priorityGenres.isEmpty() || itemGenres.isEmpty()) return false
        return itemGenres.any { genre ->
            expandedPriority.any { pri -> GenreMatcher.matches(pri, genre) }
        }
    }

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
