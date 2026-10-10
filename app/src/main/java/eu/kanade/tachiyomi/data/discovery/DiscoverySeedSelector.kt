package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle

internal fun selectFeedbackSeeds(
    signals: List<DiscoverySignal>,
    mediaType: DiscoveryMediaType,
    limit: Int = 2,
): List<DiscoveryFeedbackSeed> {
    if (limit <= 0) return emptyList()
    val seen = hashSetOf<String>()
    return signals.asSequence()
        .filter { it.mediaType == mediaType }
        .filter {
            it.signalType == DiscoverySignalType.LIKE || it.signalType == DiscoverySignalType.ADD ||
                it.signalType == DiscoverySignalType.CONSUMED_LIKED ||
                it.signalType == DiscoverySignalType.CONSUMED_ADDED
        }
        .sortedByDescending { it.createdAt }
        .mapNotNull { signal ->
            val cleanTitle = normalizeDiscoveryTitle(signal.cleanTitle.ifBlank { signal.title })
            if (cleanTitle.isBlank()) return@mapNotNull null
            if (!seen.add(cleanTitle)) return@mapNotNull null
            DiscoveryFeedbackSeed(
                title = signal.title.ifBlank { cleanTitle },
                cleanTitle = cleanTitle,
                genres = signal.genres,
                timestamp = signal.createdAt,
            )
        }
        .take(limit)
        .toList()
}

data class DiscoverySeedInput(
    val entryId: Long,
    val title: String,
    val sourceId: Long = -1L,
    val altTitles: List<String> = emptyList(),
    val description: String? = null,
    val author: String? = null,
    val genres: List<String> = emptyList(),
    val dateAdded: Long = 0L,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val lastInteraction: Long? = null,
    /** Статус «Дропнуто» в трекер-сервисе: не является сидом, жанры штрафуются в taste-профиле. */
    val isDropped: Boolean = false,
)

/**
 * C1: топ-[limit] источников библиотеки по числу тайтлов пользователя.
 * Ничья по весу — детерминированный tie-break по возрастанию id.
 */
internal fun rankSourceIds(candidates: List<DiscoverySeedInput>, limit: Int = 3): List<Long> =
    candidates.asSequence()
        .filter { it.sourceId > 0 }
        .groupingBy { it.sourceId }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { it.key }

data class SeedSettings(
    val maxSeeds: Int = 3,
    val useCompleted: Boolean = true,
    val useActive14: Boolean = true,
    val useAdded: Boolean = false,
    val completedWindowDays: Long = 30,
    val activeWindowDays: Long = 14,
)

class DiscoverySeedSelector(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    fun select(
        candidates: List<DiscoverySeedInput>,
        settings: SeedSettings,
        offset: Int = 0,
    ): List<DiscoverySeedInput> {
        val now = nowMs()
        val valid = candidates.filter { it.title.isNotBlank() }.filter { !it.isDropped }
        val completedWindow = now - settings.completedWindowDays * DAY_MS
        val activeWindow = now - settings.activeWindowDays * DAY_MS

        val completed = if (settings.useCompleted) {
            valid.filter { it.isCompleted && (it.completedAt ?: it.lastInteraction ?: 0L) >= completedWindow }
                .sortedByDescending { it.completedAt ?: it.lastInteraction ?: 0L }
        } else {
            emptyList()
        }

        val completedIds = completed.mapTo(HashSet()) { it.entryId }
        val active = if (settings.useActive14) {
            valid.filterNot { it.entryId in completedIds }
                .filter { !it.isCompleted && (it.lastInteraction ?: 0L) >= activeWindow }
                .sortedByDescending { it.lastInteraction ?: 0L }
        } else {
            emptyList()
        }

        val takenIds = completedIds + active.mapTo(HashSet()) { it.entryId }
        val added = if (settings.useAdded) {
            valid.filterNot { it.entryId in takenIds }.sortedByDescending { it.dateAdded }
        } else {
            emptyList()
        }

        val distinct = (completed + active + added).distinctBy { it.entryId }
        val max = settings.maxSeeds.coerceIn(1, 5)
        if (distinct.size <= max || offset <= 0) {
            return distinct.take(max)
        }
        val safeOffset = offset % distinct.size
        return (distinct.drop(safeOffset) + distinct.take(safeOffset)).take(max)
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
