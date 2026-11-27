package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryEligibilitySnapshot
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryQueryEvidence
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.model.canonicalDiscoveryKey
import tachiyomi.domain.discovery.model.legacyDiscoveryKey
import kotlin.coroutines.ContinuationInterceptor

data class DiscoveryFeed(
    val mediaType: DiscoveryMediaType,
    val rows: Map<DiscoveryRowType, List<DiscoveryRowItem>>,
    val failedRows: Set<DiscoveryRowType>,
    val degradedRows: Set<DiscoveryRowType> = emptySet(),
    val generatedAt: Long,
    /** Builder output before coordinator filtering; diagnostics only. */
    val rawCandidates: Map<DiscoveryRowType, List<DiscoveryRowItem>> = emptyMap(),
    val exhaustionEvidence: List<DiscoveryQueryEvidence>? = null,
    val failedSourceIds: Set<Long> = emptySet(),
    val degradedSourceIds: Set<Long> = emptySet(),
    val successfulSourceIds: Set<Long> = emptySet(),
)

/**
 * Собирает discovery-ленту для одного медиатипа: параллельно запускает строители рядов,
 * затем нормализует и фильтрует результат (библиотека / история / скрытые) и распределяет
 * айтемы по рядам с межрядовым приоритетом [DiscoveryRowType] (LIKE > TASTE > TREND).
 *
 * Контракт деградации: упавший строитель даёт пустой ряд и отметку в [DiscoveryFeed.failedRows];
 * записывать провалившийся ряд в кэш или нет — решает вызывающий (DiscoveryRunner).
 */
class DiscoveryCoordinator(
    private val rowBuilders: List<DiscoveryRowBuilder>,
    private val rowLimit: Int = 50,
) {

    suspend fun streamFeed(
        context: DiscoveryBuildContext,
        collectTimeoutMs: Long? = null,
        onRowAvailable: suspend (DiscoveryRowType, List<DiscoveryRowItem>) -> Unit,
    ): DiscoveryFeed {
        val callerContext = currentCoroutineContext()
        callerContext.ensureActive()
        val dispatcher = callerContext[ContinuationInterceptor] ?: Dispatchers.IO
        val detachedJob = SupervisorJob()
        val parentHandle = callerContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                detachedJob.cancel(
                    cause as? CancellationException ?: CancellationException("Parent cancelled", cause),
                )
            }
        }
        val scope = CoroutineScope(detachedJob + dispatcher)

        val channel = Channel<Triple<DiscoveryRowType, List<DiscoveryRowItem>?, Boolean>>(Channel.UNLIMITED)
        val failed = mutableSetOf<DiscoveryRowType>()
        val degraded = mutableSetOf<DiscoveryRowType>()
        val settledTypes = mutableSetOf<DiscoveryRowType>()
        val excluded = context.libraryCleanTitles + context.historyCleanTitles + context.hiddenCleanTitles
        val rawCandidates = mutableMapOf<DiscoveryRowType, List<DiscoveryRowItem>>()
        val finalRows = mutableMapOf<DiscoveryRowType, List<DiscoveryRowItem>>()

        val jobs = rowBuilders.map { builder ->
            scope.launch {
                var items: List<DiscoveryRowItem>? = null
                try {
                    items = builder.build(context)
                } catch (e: CancellationException) {
                    if (!scope.isActive) throw e
                    logcat { "[DiscoveryCoordinator] row ${builder.rowType} cancelled: ${e.message}" }
                } catch (e: Exception) {
                    logcat { "[DiscoveryCoordinator] row ${builder.rowType} FAILED: ${e.message}" }
                } finally {
                    if (scope.isActive) {
                        channel.trySend(Triple(builder.rowType, items, builder.isDegraded))
                    }
                }
            }
        }

        var completed = 0
        suspend fun receiveSettled() {
            repeat(rowBuilders.size) {
                val (type, items, isDegraded) = channel.receive()
                settledTypes += type
                completed++
                val stage = when (type) {
                    DiscoveryRowType.LIKE -> "Подбор похожего"
                    DiscoveryRowType.TASTE -> "Анализ предпочтений"
                    DiscoveryRowType.TREND -> "Загрузка трендов"
                    DiscoveryRowType.SOURCE -> "Опрос источников"
                }
                DiscoveryProgressTracker.update(
                    isRunning = true,
                    mediaType = context.mediaType,
                    stageText = "$stage ($completed/${rowBuilders.size})",
                    current = completed,
                    total = rowBuilders.size,
                )
                if (items == null) {
                    failed += type
                } else {
                    if (isDegraded) {
                        degraded += type
                    }
                    rawCandidates[type] = items

                    // Пересчитываем все полученные ряды в строгом порядке приоритета enum: LIKE > TASTE > TREND > SOURCE.
                    val seenSoFar = mutableSetOf<String>()
                    val updatedRows = mutableListOf<Pair<DiscoveryRowType, List<DiscoveryRowItem>>>()

                    for (pType in DiscoveryRowType.entries) {
                        val candidates = rawCandidates[pType] ?: continue
                        val eligible = filterEligibleRowItems(
                            items = candidates,
                            mediaType = context.mediaType,
                            rowType = pType,
                            sourcePolicy = context.sourcePolicy,
                            excluded = excluded,
                            seen = seenSoFar,
                            recentCleanTitles = context.recentCleanTitles,
                            currentFeedCleanTitles = context.currentFeedCleanTitles,
                            eligibilitySnapshot = context.eligibilitySnapshot,
                        )
                        val policyFiltered = context.candidatePolicy.filterRowCandidates(
                            eligible,
                            genresSelector = { it.genres },
                            fillTarget = if (!context.candidatePolicy.isStrictRequired) rowLimit else null,
                        )
                        val selected = selectRowItems(policyFiltered)
                        logcat {
                            "[DiscoverySelection] media=${context.mediaType} row=$pType raw=${candidates.size} " +
                                "genre=${policyFiltered.size} selected=${selected.size} " +
                                "excludedCurrent=${context.currentFeedCleanTitles.size} " +
                                "excludedKnown=${excluded.size} seenAcrossRows=${seenSoFar.size}"
                        }
                        selected.forEach {
                            seenSoFar += it.cleanTitle
                            seenSoFar += it.altCleanTitles
                        }

                        if (finalRows[pType] != selected) {
                            finalRows[pType] = selected
                            updatedRows.add(pType to selected)
                        }
                    }

                    for ((updatedType, updatedItems) in updatedRows) {
                        onRowAvailable(updatedType, updatedItems)
                    }
                }
            }
        }

        return try {
            val completedWithinDeadline = if (collectTimeoutMs == null) {
                receiveSettled()
                true
            } else {
                withTimeoutOrNull(collectTimeoutMs.coerceAtLeast(1L)) {
                    receiveSettled()
                } != null
            }

            if (completedWithinDeadline) {
                jobs.forEach { it.join() }
            } else {
                detachedJob.cancel(CancellationException("Discovery collect deadline ${collectTimeoutMs}ms"))
                withContext(NonCancellable) { yield() }
                val unfinished = rowBuilders.map { it.rowType }.filterNot { it in settledTypes }
                failed += unfinished
                logcat {
                    "[DiscoveryCoordinator] collect deadline ${collectTimeoutMs}ms reached; " +
                        "settled=$settledTypes unfinished=$unfinished"
                }
            }

            val settledBuilders = rowBuilders.filter { it.rowType in settledTypes }
            DiscoveryFeed(
                mediaType = context.mediaType,
                rows = finalRows,
                failedRows = failed,
                degradedRows = degraded,
                generatedAt = System.currentTimeMillis(),
                rawCandidates = rawCandidates.toMap(),
                exhaustionEvidence = if (settledBuilders.size == rowBuilders.size &&
                    settledBuilders.all { it.exhaustionEvidence != null }
                ) {
                    settledBuilders.flatMap { it.exhaustionEvidence.orEmpty() }.distinctBy { it.queryKey }
                } else {
                    null
                },
                failedSourceIds = settledBuilders.flatMap { it.failedSourceIds }.toSet(),
                degradedSourceIds = settledBuilders.flatMap { it.degradedSourceIds }.toSet(),
                successfulSourceIds = settledBuilders.flatMap { it.successfulSourceIds }.toSet(),
            )
        } catch (e: CancellationException) {
            detachedJob.cancel(e)
            withContext(NonCancellable) { yield() }
            throw e
        } finally {
            parentHandle?.dispose()
        }
    }

    suspend fun buildFeed(context: DiscoveryBuildContext): DiscoveryFeed =
        streamFeed(context) { _, _ -> }

    private fun filterEligibleRowItems(
        items: List<DiscoveryRowItem>,
        mediaType: DiscoveryMediaType,
        rowType: DiscoveryRowType,
        sourcePolicy: DiscoverySourcePolicy,
        excluded: Set<String>,
        seen: Set<String>,
        recentCleanTitles: Set<String>,
        currentFeedCleanTitles: Set<String> = emptySet(),
        eligibilitySnapshot: DiscoveryEligibilitySnapshot? = null,
    ): List<DiscoveryRowItem> {
        val blockedEligibility = eligibilitySnapshot
            ?.takeIf { it.mediaType == mediaType }
            ?.let { it.excludedIdentityKeys + it.excludedLookupKeys }
            .orEmpty()
        return items
            .filterNot { item ->
                if (sourcePolicy.mediaType != mediaType ||
                    !sourcePolicy.allows(rowType, item.sourceId, item.provider)
                ) {
                    return@filterNot true
                }
                val keys = listOf(item.cleanTitle) + item.altCleanTitles
                if (keys.any {
                        it.isBlank() || it in excluded || it in seen ||
                            it in currentFeedCleanTitles || it in recentCleanTitles
                    }
                ) {
                    return@filterNot true
                }
                if (blockedEligibility.isNotEmpty()) {
                    val canonical = canonicalDiscoveryKey(mediaType, item.sourceId, item.sourceUrl, item.cleanTitle)
                    if (canonical in blockedEligibility) return@filterNot true
                    if (keys.any { key ->
                            key in blockedEligibility || legacyDiscoveryKey(mediaType, key) in blockedEligibility
                        }
                    ) {
                        return@filterNot true
                    }
                }
                false
            }
    }

    private fun selectRowItems(items: List<DiscoveryRowItem>): List<DiscoveryRowItem> =
        filterFranchiseClustering(items.distinctBy { it.cleanTitle }, maxPerSeries = 2).take(rowLimit)
}
