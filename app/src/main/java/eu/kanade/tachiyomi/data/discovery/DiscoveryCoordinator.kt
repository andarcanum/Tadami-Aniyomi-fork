package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

data class DiscoveryFeed(
    val mediaType: DiscoveryMediaType,
    val rows: Map<DiscoveryRowType, List<DiscoveryRowItem>>,
    val failedRows: Set<DiscoveryRowType>,
    val generatedAt: Long,
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
        onRowAvailable: suspend (DiscoveryRowType, List<DiscoveryRowItem>) -> Unit,
    ): DiscoveryFeed = supervisorScope {
        val channel = Channel<Pair<DiscoveryRowType, List<DiscoveryRowItem>?>>(Channel.UNLIMITED)
        val failed = mutableSetOf<DiscoveryRowType>()
        val excluded = context.libraryCleanTitles + context.historyCleanTitles + context.hiddenCleanTitles
        val rawCandidates = mutableMapOf<DiscoveryRowType, List<DiscoveryRowItem>>()
        val finalRows = mutableMapOf<DiscoveryRowType, List<DiscoveryRowItem>>()

        val jobs = rowBuilders.map { builder ->
            launch {
                val items = try {
                    builder.build(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat { "[DiscoveryCoordinator] row ${builder.rowType} FAILED: ${e.message}" }
                    null
                }
                channel.send(builder.rowType to items)
            }
        }

        repeat(rowBuilders.size) {
            val (type, items) = channel.receive()
            if (items == null) {
                failed += type
            } else {
                rawCandidates[type] = items

                // Пересчитываем все полученные ряды в строгом порядке приоритета enum: LIKE > TASTE > TREND > SOURCE.
                val seenSoFar = mutableSetOf<String>()
                val updatedRows = mutableListOf<Pair<DiscoveryRowType, List<DiscoveryRowItem>>>()

                for (pType in DiscoveryRowType.entries) {
                    val candidates = rawCandidates[pType] ?: continue
                    val selected = selectRowItems(
                        candidates,
                        excluded,
                        seenSoFar,
                        context.recentCleanTitles,
                        context.shownCutoffMap,
                        context.currentFeedCleanTitles,
                    )
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

        jobs.forEach { it.join() }

        DiscoveryFeed(
            mediaType = context.mediaType,
            rows = finalRows,
            failedRows = failed,
            generatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun buildFeed(context: DiscoveryBuildContext): DiscoveryFeed =
        streamFeed(context) { _, _ -> }

    private fun selectRowItems(
        items: List<DiscoveryRowItem>,
        excluded: Set<String>,
        seen: Set<String>,
        recentCleanTitles: Set<String>,
        shownCutoffMap: Map<String, Long>,
        currentFeedCleanTitles: Set<String> = emptySet(),
    ): List<DiscoveryRowItem> {
        val valid = items
            .filterNot { item ->
                val keys = listOf(item.cleanTitle) + item.altCleanTitles
                keys.any { it.isBlank() || it in excluded || it in seen }
            }
            .distinctBy { it.cleanTitle }

        val clustered = filterFranchiseClustering(valid, maxPerSeries = 2)

        return if (recentCleanTitles.isNotEmpty()) {
            val fresh = clustered.filterNot { it.cleanTitle in recentCleanTitles }
            if (fresh.size >= rowLimit) {
                fresh.take(rowLimit)
            } else {
                // Добираем показанными: дольше всего не показанные первыми; без таймстампа — в конец.
                // Тайтлы ТЕКУЩЕЙ ленты (ручной рефреш) в добор не возвращаются —
                // наполнение ряда реально сменяется, а не «новые + всё старое».
                val stale = clustered
                    .filter { it.cleanTitle in recentCleanTitles && it.cleanTitle !in currentFeedCleanTitles }
                    .sortedBy { shownCutoffMap[it.cleanTitle] ?: Long.MAX_VALUE }
                (fresh + stale).take(rowLimit)
            }
        } else {
            clustered.take(rowLimit)
        }
    }
}
