package eu.kanade.tachiyomi.data.discovery

/**
 * Результат резолва участия плагинов в подборках «Для тебя»:
 * упорядоченный пул для ряда SOURCE и первичный источник для TASTE/TREND.
 */
data class SourceParticipation(
    val sourceIds: List<Long>,
    val primarySourceId: Long,
)

/**
 * CSV ключей плагинов (pkgName расширений / id novel-плагинов) → множество.
 * Ключи не содержат запятых, мусорные пустые токены пропускаются.
 */
internal fun parseKeyCsv(raw: String): Set<String> =
    raw.splitToSequence(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

/** Множество ключей → CSV в порядке следования, без дублей и пустых. */
internal fun formatKeyCsv(keys: Collection<String>): String =
    keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

/**
 * Группирует источники по ключу владеющего плагина. Источники без плагина
 * (встроенный OmniSource −42, локальный 0, сироты) отбрасываются: они не плагины
 * и не могут участвовать в подборках расширений.
 */
internal fun groupSourcesByPlugin(
    sourceIds: Set<Long>,
    pluginIdOf: (Long) -> String?,
): Map<String, List<Long>> =
    sourceIds.mapNotNull { id -> pluginIdOf(id)?.let { key -> id to key } }
        .groupBy(keySelector = { it.second }, valueTransform = { it.first })

/**
 * Единая точка правды по составу источников ленты.
 *
 * @param weightOrder установленные источники медиатипа: вес библиотеки desc, id asc,
 *   хвост нулевого веса — id asc.
 * allowed = weightOrder − excludedIds;
 * auto берёт top [autoTop], manual — все разрешённые (allowed).
 * primary = lastUsed (если задан и не исключён) → первый из sourceIds → fallback (если не исключён) → -1.
 */
internal fun resolveSourceParticipation(
    mode: String,
    excludedIds: Set<Long>,
    weightOrder: List<Long>,
    lastUsedId: Long,
    fallbackId: Long,
    autoTop: Int = 3,
): SourceParticipation {
    val allowed = weightOrder.filterNot { it in excludedIds }
    val sourceIds = if (mode == "manual") allowed else allowed.take(autoTop)
    val primary = when {
        lastUsedId > 0 && lastUsedId !in excludedIds -> lastUsedId
        sourceIds.isNotEmpty() -> sourceIds.first()
        fallbackId > 0 && fallbackId !in excludedIds -> fallbackId
        else -> -1L
    }
    return SourceParticipation(sourceIds = sourceIds, primarySourceId = primary)
}
