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

data class DiscoveryBuildContext(
    val mediaType: DiscoveryMediaType,
    val seeds: List<DiscoverySeedInput>,
    val libraryCleanTitles: Set<String>,
    val historyCleanTitles: Set<String>,
    val hiddenCleanTitles: Set<String>,
    val tasteProfile: List<Pair<String, Double>> = emptyList(),
    /** B2: теги из tag-blacklist — жанры профиля и айтемов с ними не проходят. */
    val blacklistedTags: Set<String> = emptySet(),
    /** V1: статус-фильтр трендов (пусто = без фильтра, все статусы). */
    val releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus> = emptySet(),
    /** V3: приоритетные жанры — буст скора, не отсекают. */
    val priorityGenres: Set<String> = emptySet(),
    /** V3: обязательные жанры — только тайтлы с ними (best-effort, пустой результат → без фильтра). */
    val requiredGenres: Set<String> = emptySet(),
    val sourceId: Long = -1L,
    /** C1: источники библиотеки, упорядоченные по весу (топ-3) — для ряда SOURCE. */
    val sourceIds: List<Long> = emptyList(),
    val recentCleanTitles: Set<String> = emptySet(),
    /** cleanTitle → таймстамп последнего показа (48h окно): порядок stale-добора в координаторе. */
    val shownCutoffMap: Map<String, Long> = emptyMap(),
    /** Тайтлы ТЕКУЩЕЙ ленты (ручной рефреш): stale-добор их не возвращает — наполнение реально сменяется. */
    val currentFeedCleanTitles: Set<String> = emptySet(),
    val pageOffset: Int = 1,
)

interface DiscoveryRowBuilder {
    val rowType: DiscoveryRowType
    suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem>
}
