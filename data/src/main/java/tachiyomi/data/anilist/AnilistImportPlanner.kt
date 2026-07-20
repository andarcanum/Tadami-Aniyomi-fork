package tachiyomi.data.anilist

import tachiyomi.data.anixart.AnixartMatcher

/**
 * Pure planning layer for the AniList importer.
 * Mirrors [tachiyomi.data.shikimori.ShikimoriImportPlanner] for dedup and category mapping.
 */
object AnilistImportPlanner {

    data class Selection(
        val entry: AnilistImportEntry,
        val chosen: AnixartMatcher.SearchCandidate?,
        val enabled: Boolean,
    )

    data class Config(
        val statusCategoryIds: Map<AnilistImportStatus, Long> = emptyMap(),
    )

    data class Action(
        val entry: AnilistImportEntry,
        val candidate: AnixartMatcher.SearchCandidate,
        val categoryIds: Set<Long>,
    )

    data class Plan(
        val actions: List<Action>,
        val skippedDisabled: Int,
        val skippedNoMatch: Int,
        val mergedDuplicates: Int,
    )

    fun plan(selections: List<Selection>, config: Config): Plan {
        var skippedDisabled = 0
        var skippedNoMatch = 0
        var mergedDuplicates = 0
        val byKey = LinkedHashMap<String, Action>()

        for (sel in selections) {
            if (!sel.enabled) {
                skippedDisabled++
                continue
            }
            val candidate = sel.chosen
            if (candidate == null) {
                skippedNoMatch++
                continue
            }

            val status = AnilistImportStatus.fromApi(sel.entry.status)
            val cats = buildSet {
                status?.let { s -> config.statusCategoryIds[s]?.let(::add) }
            }

            val key = candidate.sourceId.toString() + "|" + candidate.url.ifEmpty { candidate.id.toString() }
            val existing = byKey[key]
            if (existing == null) {
                byKey[key] = Action(
                    entry = sel.entry,
                    candidate = candidate,
                    categoryIds = cats,
                )
            } else {
                mergedDuplicates++
                byKey[key] = existing.copy(
                    categoryIds = existing.categoryIds + cats,
                )
            }
        }

        return Plan(
            actions = byKey.values.toList(),
            skippedDisabled = skippedDisabled,
            skippedNoMatch = skippedNoMatch,
            mergedDuplicates = mergedDuplicates,
        )
    }
}
