package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

/** Where the candidates of one row were lost before the final feed was published. */
internal data class DiscoveryRowFillAudit(
    val rowType: DiscoveryRowType,
    val listFetched: Int,
    val policyAdmitted: Int,
    val excludedKnown: Int,
    val crossRowDuplicates: Int,
    val franchiseClustered: Int,
    val fresh: Int,
    val staleBackfilled: Int,
    val final: Int,
)

internal data class DiscoveryFillAudit(
    val mediaType: DiscoveryMediaType,
    val rows: List<DiscoveryRowFillAudit>,
    val finalUnique: Int,
    val freshUnique: Int,
    val staleBackfilled: Int,
    val detailsUsed: Int,
    val detailsAllowance: Int,
) {
    fun logLine(): String = buildString {
        append("[DiscoveryAudit] media=").append(mediaType)
        append(" final=").append(finalUnique)
        append(" fresh=").append(freshUnique)
        append(" stale=").append(staleBackfilled)
        append(" details=").append(detailsUsed).append('/').append(detailsAllowance)
        for (row in rows) {
            append(' ').append(row.rowType).append('(')
            append("raw=").append(row.listFetched)
            append(",admitted=").append(row.policyAdmitted)
            append(",known=").append(row.excludedKnown)
            append(",dupes=").append(row.crossRowDuplicates)
            append(",cluster=").append(row.franchiseClustered)
            append(",final=").append(row.final)
            append(",fresh=").append(row.fresh)
            append(",stale=").append(row.staleBackfilled)
            append(')')
        }
    }
}

/**
 * Recomputes the feed funnel from the builder output and the coordinator result.
 *
 * Pure: it performs no filtering side effects and needs no Android or network
 * state, so the same numbers can be asserted in unit tests. Counters mirror the
 * coordinator stages (policy -> known exclusions -> cross-row dedupe -> franchise
 * clustering -> freshness backfill) to make starvation diagnosable from one log line.
 */
internal fun buildDiscoveryFillAudit(
    mediaType: DiscoveryMediaType,
    rawCandidates: Map<DiscoveryRowType, List<DiscoveryRowItem>>,
    finalRows: Map<DiscoveryRowType, List<DiscoveryRowItem>>,
    context: DiscoveryBuildContext,
    detailsUsed: Int = 0,
    detailsAllowance: Int = 0,
): DiscoveryFillAudit {
    val excludedKnown = context.libraryCleanTitles + context.historyCleanTitles + context.hiddenCleanTitles
    val seen = HashSet<String>()
    val rows = mutableListOf<DiscoveryRowFillAudit>()

    for (rowType in DiscoveryRowType.entries) {
        val raw = rawCandidates[rowType] ?: continue
        val admitted = context.candidatePolicy.filterRowCandidates(raw, { it.genres })
        val afterKnown = admitted.filterNot { candidate ->
            candidate.keys().any { it in excludedKnown }
        }
        val afterCrossRow = afterKnown.filterNot { candidate ->
            candidate.keys().any { it in seen }
        }
        val unique = afterCrossRow.distinctBy { it.cleanTitle }
        val clustered = filterFranchiseClustering(unique, maxPerSeries = 2)
        val final = finalRows[rowType].orEmpty()
        val fresh = final.count { it.cleanTitle !in context.recentCleanTitles }

        rows += DiscoveryRowFillAudit(
            rowType = rowType,
            listFetched = raw.size,
            policyAdmitted = admitted.size,
            excludedKnown = admitted.size - afterKnown.size,
            crossRowDuplicates = afterKnown.size - afterCrossRow.size,
            franchiseClustered = unique.size - clustered.size,
            fresh = fresh,
            staleBackfilled = final.size - fresh,
            final = final.size,
        )
        final.forEach { seen += it.keys() }
    }

    val allFinal = finalRows.values.flatten()
    val freshAll = allFinal.filterNot { it.cleanTitle in context.recentCleanTitles }
    return DiscoveryFillAudit(
        mediaType = mediaType,
        rows = rows,
        finalUnique = allFinal.distinctBy { it.cleanTitle }.size,
        freshUnique = freshAll.distinctBy { it.cleanTitle }.size,
        staleBackfilled = allFinal.size - freshAll.size,
        detailsUsed = detailsUsed,
        detailsAllowance = detailsAllowance,
    )
}

private fun DiscoveryRowItem.keys(): List<String> = listOf(cleanTitle) + altCleanTitles
