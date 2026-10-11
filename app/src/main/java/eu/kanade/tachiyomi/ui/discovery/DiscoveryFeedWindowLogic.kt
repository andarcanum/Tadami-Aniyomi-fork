package eu.kanade.tachiyomi.ui.discovery

import tachiyomi.domain.discovery.model.DiscoverySuggestion

internal const val FEED_WINDOW_SIZE = 20

/**
 * Вычисляет следующий оффсет окна ленты и флаг необходимости сетевого рефреша.
 *
 * @param currentOffset текущий оффсет окна (0, 20, 40...)
 * @param poolSize общий размер доступного пула (например, 60 для MIX)
 * @param windowSize размер витрины (дефолт 20)
 * @param canNetworkRefresh разрешено ли сетевое обновление (прошёл ли кулдаун)
 * @return Pair(следующий_оффсет, нужно_ли_сетевое_обновление)
 */
internal fun resolveNextFeedOffset(
    currentOffset: Int,
    poolSize: Int,
    windowSize: Int = FEED_WINDOW_SIZE,
    canNetworkRefresh: Boolean,
): Pair<Int, Boolean> {
    if (poolSize <= windowSize) {
        return if (canNetworkRefresh) {
            0 to true
        } else {
            0 to false
        }
    }

    val nextOffset = currentOffset + windowSize
    return if (nextOffset < poolSize) {
        nextOffset to false
    } else {
        if (canNetworkRefresh) {
            0 to true
        } else {
            0 to false
        }
    }
}

/**
 * Нарезает окно [windowSize] элементов из [pool] начиная с [offset].
 * Если окно выходит за границы пула, циклично добирает элементы с начала пула без дублирования.
 */
internal fun sliceFeedWindow(
    pool: List<DiscoverySuggestion>,
    offset: Int,
    windowSize: Int = FEED_WINDOW_SIZE,
): List<DiscoverySuggestion> {
    if (pool.isEmpty()) return emptyList()
    if (pool.size <= windowSize) return pool

    val safeOffset = (offset.coerceAtLeast(0)) % pool.size
    val firstPart = pool.drop(safeOffset).take(windowSize)
    if (firstPart.size >= windowSize) return firstPart

    val remainingNeeded = windowSize - firstPart.size
    val existingTitles = firstPart.mapTo(HashSet()) { it.cleanTitle }
    val wrappedPart = pool.filterNot { it.cleanTitle in existingTitles }.take(remainingNeeded)
    return firstPart + wrappedPart
}

/**
 * Подбирает кандидата на замену удалённого/скрытого тайтла.
 *
 * Приоритеты:
 * 1. Свежий тайтл (ещё не показанный) от того же источника ([DiscoverySuggestion.provider]).
 * 2. Свежий тайтл из того же ряда ([DiscoverySuggestion.rowType]), сохраняя баланс квоты микса.
 * 3. Любой свежий тайтл из пула.
 * 4. Если все свежие исчерпаны — наименее показанный кандидат от того же источника/ряда.
 */
internal fun selectBackfillCandidate(
    removedItem: DiscoverySuggestion,
    pool: List<DiscoverySuggestion>,
    currentWindowTitles: Set<String>,
    shownTitles: Set<String>,
    excludedTitles: Set<String> = emptySet(),
): DiscoverySuggestion? {
    val eligible = pool.filterNot {
        it.cleanTitle == removedItem.cleanTitle ||
            it.cleanTitle in currentWindowTitles ||
            it.cleanTitle in excludedTitles
    }
    if (eligible.isEmpty()) return null

    val fresh = eligible.filterNot { it.cleanTitle in shownTitles }

    // 1. Свежий от того же провайдера
    val sameProviderFresh = fresh.firstOrNull { it.provider.equals(removedItem.provider, ignoreCase = true) }
    if (sameProviderFresh != null) return sameProviderFresh

    // 2. Свежий из того же ряда
    val sameRowFresh = fresh.firstOrNull { it.rowType == removedItem.rowType }
    if (sameRowFresh != null) return sameRowFresh

    // 3. Любой свежий
    val anyFresh = fresh.firstOrNull()
    if (anyFresh != null) return anyFresh

    // 4. Фолбэк среди ранее показанных: тот же провайдер
    val sameProviderStale = eligible.firstOrNull { it.provider.equals(removedItem.provider, ignoreCase = true) }
    if (sameProviderStale != null) return sameProviderStale

    // 5. Фолбэк среди ранее показанных: тот же ряд
    val sameRowStale = eligible.firstOrNull { it.rowType == removedItem.rowType }
    if (sameRowStale != null) return sameRowStale

    // 6. Любой оставшийся кандидат
    return eligible.firstOrNull()
}

/**
 * Замещает удалённый тайтл на [replacement] в текущей витрине [currentWindow],
 * сохраняя исходную позицию элемента в сетке.
 */
internal fun patchFeedWindowAfterRemoval(
    currentWindow: List<DiscoverySuggestion>,
    removedTitle: String,
    replacement: DiscoverySuggestion?,
): List<DiscoverySuggestion> {
    val norm = removedTitle.trim().lowercase()
    val index = currentWindow.indexOfFirst { it.cleanTitle == norm }
    if (index == -1) return currentWindow

    val updated = currentWindow.toMutableList()
    if (replacement != null) {
        updated[index] = replacement
    } else {
        updated.removeAt(index)
    }
    return updated
}
