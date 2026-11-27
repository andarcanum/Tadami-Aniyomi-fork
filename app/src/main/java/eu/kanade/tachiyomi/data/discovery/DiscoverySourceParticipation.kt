package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import tachiyomi.domain.discovery.model.DiscoveryMediaType

/**
 * Результат резолва участия плагинов в подборках «Для тебя»:
 * упорядоченный пул для ряда SOURCE и первичный источник для TASTE/TREND.
 */
data class SourceParticipation(
    val sourceIds: List<Long>,
    val primarySourceId: Long,
)

/** Результат сверки установленных плагинов с известным снимком [knownKeys]. */
internal data class PluginKnownReconciliation(
    val excludedKeys: Set<String>,
    val knownKeys: Set<String>,
    val changedExcluded: Boolean,
    val changedKnown: Boolean,
)

/**
 * Защищает ручной список источников от автодобавления вновь установленных плагинов:
 * - Если [installedKeys] пуст или [mode] != "manual" — состояние не меняется.
 * - При первом входе в ручной режим ([knownKeys] пуст) фиксирует текущие установленные плагины
 *   как базовый снимок без их исключения.
 * - Далее любой новый ключ (`key !in knownKeys`) автоматически добавляется в [excludedKeys] и [knownKeys].
 */
internal fun reconcileKnownPlugins(
    mode: String,
    installedKeys: Collection<String>,
    excludedKeys: Set<String>,
    knownKeys: Set<String>,
): PluginKnownReconciliation {
    val installedSet = installedKeys.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    if (installedSet.isEmpty() || mode != "manual") {
        return PluginKnownReconciliation(
            excludedKeys = excludedKeys,
            knownKeys = knownKeys,
            changedExcluded = false,
            changedKnown = false,
        )
    }
    if (knownKeys.isEmpty()) {
        return PluginKnownReconciliation(
            excludedKeys = excludedKeys,
            knownKeys = installedSet,
            changedExcluded = false,
            changedKnown = true,
        )
    }
    val newKeys = installedSet - knownKeys
    if (newKeys.isEmpty()) {
        return PluginKnownReconciliation(
            excludedKeys = excludedKeys,
            knownKeys = knownKeys,
            changedExcluded = false,
            changedKnown = false,
        )
    }
    val nextExcluded = excludedKeys + newKeys
    val nextKnown = knownKeys + installedSet
    return PluginKnownReconciliation(
        excludedKeys = nextExcluded,
        knownKeys = nextKnown,
        changedExcluded = nextExcluded != excludedKeys,
        changedKnown = nextKnown != knownKeys,
    )
}

/** Применяет [reconcileKnownPlugins] к prefs для [mediaType] и персистит изменения. */
internal fun reconcileDiscoverySourcePreferences(
    preferences: DiscoveryPreferences,
    mediaType: DiscoveryMediaType,
    installedKeys: Collection<String>,
): PluginKnownReconciliation = preferences.withSourcePreferencesLock(mediaType) {
    val mode = preferences.discoverySourceMode(mediaType).get()
    val excludedPref = preferences.discoverySourceExcluded(mediaType)
    val knownPref = preferences.discoverySourceKnown(mediaType)
    val result = reconcileKnownPlugins(
        mode = mode,
        installedKeys = installedKeys,
        excludedKeys = parseKeyCsv(excludedPref.get()),
        knownKeys = parseKeyCsv(knownPref.get()),
    )
    if (result.changedExcluded) {
        excludedPref.set(formatKeyCsv(result.excludedKeys))
    }
    if (result.changedKnown) {
        knownPref.set(formatKeyCsv(result.knownKeys))
    }
    result
}

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
