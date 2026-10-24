package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

data class DiscoveryRowItem(
    val title: String,
    val cleanTitle: String,
    val coverUrl: String?,
    val reason: String?,
    val seedTitle: String?,
    val provider: String,
    val score: Double,
    val sourceId: Long? = null,
    val sourceUrl: String? = null,
    val genres: List<String> = emptyList(),
    val altCleanTitles: List<String> = emptyList(),
)

data class DiscoveryFeedbackSeed(
    val title: String,
    val cleanTitle: String,
    val genres: List<String> = emptyList(),
    val timestamp: Long = 0L,
)

data class DiscoveryBuildContext(
    val mediaType: DiscoveryMediaType,
    val seeds: List<DiscoverySeedInput>,
    val feedbackSeeds: List<DiscoveryFeedbackSeed> = emptyList(),
    val libraryCleanTitles: Set<String>,
    val historyCleanTitles: Set<String>,
    val hiddenCleanTitles: Set<String>,
    val tasteProfile: List<Pair<String, Double>> = emptyList(),
    /** B2: теги из tag-blacklist — жанры профиля и айтемов с ними не проходят. */
    val blacklistedTags: Set<String> = emptySet(),
    /** V3: глобально игнорируемые жанры (преф). */
    val ignoredGenres: Set<String> = emptySet(),
    /** V1: статус-фильтр трендов (пусто = без фильтра, все статусы). */
    val releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus> = emptySet(),
    /** V3: приоритетные жанры — буст скора, не отсекают. */
    val priorityGenres: Set<String> = emptySet(),
    /** V3: обязательные жанры — только тайтлы с ними (best-effort, пустой результат → без фильтра). */
    val requiredGenres: Set<String> = emptySet(),
    /** G01: полная спецификация жанровых фильтров. */
    val genreFilterSpec: GenreFilterSpec = GenreFilterSpec(
        ignoredGenres = ignoredGenres,
        requiredGenres = requiredGenres,
        priorityGenres = priorityGenres,
    ),
    val sourceId: Long = -1L,
    /** C1: источники библиотеки, упорядоченные по весу (топ-3) — для ряда SOURCE. */
    val sourceIds: List<Long> = emptyList(),
    /** Эффективные веса источников (applySourceAffinity) для взвешенного распределения слотов в ряду SOURCE. */
    val sourceWeights: Map<Long, Double> = emptyMap(),
    val recentCleanTitles: Set<String> = emptySet(),
    /** cleanTitle → таймстамп последнего показа (48h окно): порядок stale-добора в координаторе. */
    val shownCutoffMap: Map<String, Long> = emptyMap(),
    /** Тайтлы ТЕКУЩЕЙ ленты (ручной рефреш): stale-добор их не возвращает — наполнение реально сменяется. */
    val currentFeedCleanTitles: Set<String> = emptySet(),
    val pageOffset: Int = 1,
    /**
     * Shared deadline and details allowance for this media run. Rows must not
     * create their own budgets: TASTE and SOURCE consume the same one.
     */
    val verificationBudget: DiscoveryVerificationBudget? = null,
) {
    val candidatePolicy: DiscoveryCandidatePolicy
        get() {
            val effectiveIgnored =
                if (genreFilterSpec.ignoredGenres.isNotEmpty()) genreFilterSpec.ignoredGenres else ignoredGenres
            val effectiveRequired =
                if (genreFilterSpec.requiredGenres.isNotEmpty()) genreFilterSpec.requiredGenres else requiredGenres
            val effectivePriority =
                if (genreFilterSpec.priorityGenres.isNotEmpty()) genreFilterSpec.priorityGenres else priorityGenres
            val effectiveSpec = genreFilterSpec.copy(
                ignoredGenres = effectiveIgnored,
                requiredGenres = effectiveRequired,
                priorityGenres = effectivePriority,
            )
            return DiscoveryCandidatePolicy(
                spec = effectiveSpec,
                blacklistedTags = blacklistedTags,
            )
        }
}

interface DiscoveryRowBuilder {
    val rowType: DiscoveryRowType
    val isDegraded: Boolean get() = false
    suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem>
}
