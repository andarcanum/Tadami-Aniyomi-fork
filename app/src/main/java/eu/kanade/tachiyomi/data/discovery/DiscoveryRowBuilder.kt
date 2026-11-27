package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryEligibilitySnapshot
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryQueryEvidence
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy

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
    val releaseStatus: DiscoveryReleaseStatus? = null,
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
    /**
     * Already persisted compatible cards per row. A thin refresh merges them in
     * first, so a partial source response cannot shrink the visible row.
     */
    val existingRowItems: Map<DiscoveryRowType, List<DiscoveryRowItem>> = emptyMap(),
    val pageOffset: Int = 1,
    /** Consecutive catalogue pages harvested from each source, starting at [pageOffset]. */
    val harvestPages: Int = 1,
    /**
     * Shared deadline and details allowance for this media run. Rows must not
     * create their own budgets: TASTE and SOURCE consume the same one.
     */
    val verificationBudget: DiscoveryVerificationBudget? = null,
    /**
     * One required-genre source query shared by TASTE and SOURCE. Rows take
     * disjoint slices of the same result: catalogue work is paid once and the
     * coordinator's cross-row dedupe cannot empty SOURCE.
     */
    val sharedGenreQuery: SharedRequiredGenreQuery? = null,
    val eligibilitySnapshot: DiscoveryEligibilitySnapshot? = null,
    /** Authorization captured for this run; missing inventory never authorizes a native source. */
    val sourcePolicy: DiscoverySourcePolicy = DiscoverySourcePolicy(
        mediaType = mediaType,
        revision = "unavailable",
        inventoryComplete = false,
        allowedPluginKeys = emptySet(),
        pluginMembers = emptyMap(),
    ),
) {
    /** Titles that must not consume row slots: known, shown, and the previous pool. */
    val exclusionKeys: Set<String>
        get() = libraryCleanTitles + historyCleanTitles + hiddenCleanTitles +
            recentCleanTitles + currentFeedCleanTitles +
            eligibilitySnapshot?.excludedIdentityKeys.orEmpty() + eligibilitySnapshot?.excludedLookupKeys.orEmpty()

    val nativeQuerySourceIds: List<Long>
        get() = if (sourcePolicy.mediaType != mediaType) {
            emptyList()
        } else {
            sourceIds.ifEmpty { sourcePolicy.representativeSourceIds }
                .filter(sourcePolicy::allowsNative).distinct()
        }

    fun eligibleCandidates(rowType: DiscoveryRowType, items: List<DiscoveryRowItem>): List<DiscoveryRowItem> {
        if (sourcePolicy.mediaType != mediaType) return emptyList()
        val excluded = exclusionKeys
        return items.filter { sourcePolicy.allows(rowType, it.sourceId, it.provider) }
            .filterNot { it.isExcludedBy(excluded, mediaType) }
    }

    /** Keep BEST_EFFORT lower tiers until the coordinator knows cross-row eligibility. */
    fun admittedCandidates(rowType: DiscoveryRowType, items: List<DiscoveryRowItem>): List<DiscoveryRowItem> {
        val policy = candidatePolicy
        val admitted = eligibleCandidates(rowType, items).filter {
            policy.evaluate(it.genres) is CandidateVerdict.Admitted
        }
        if (policy.priorityGenres.isEmpty()) return admitted
        val rankOnly = DiscoveryCandidatePolicy(
            blacklistedTags = policy.blacklistedTags,
            ignoredGenres = policy.ignoredGenres,
            priorityGenres = policy.priorityGenres,
            unknownGenrePolicy = policy.unknownGenrePolicy,
        )
        return rankOnly.filterRowCandidates(admitted, { it.genres })
    }

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

    /** Source ids whose genre query failed operationally. */
    val failedSourceIds: Set<Long> get() = emptySet()

    /** Source ids whose genre coverage is incomplete by the same rule as [isDegraded]. */
    val degradedSourceIds: Set<Long> get() = emptySet()

    /** Source ids with an observed successful attempt whose coverage is not degraded. */
    val successfulSourceIds: Set<Long> get() = emptySet()

    /** Null means the builder cannot prove an exhaustive catalogue result. */
    val exhaustionEvidence: List<DiscoveryQueryEvidence>? get() = null
    suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem>
}
