package eu.kanade.tachiyomi.data.discovery

internal const val SOURCE_COOLOFF_MS: Long = 2 * 3600_000L // 2 hours
internal const val SOURCE_EVICTION_MS: Long = 6 * 3600_000L // 6 hours

internal data class SourceFailureRecord(
    val sourceId: Long,
    val lastFailedAt: Long,
    val failCount: Int,
)

/**
 * Парсит CSV строку истории сбоев источников вида:
 * "sourceId:timestamp:count,sourceId:timestamp:count"
 */
internal fun parseSourceFailureHistory(rawCsv: String): Map<Long, SourceFailureRecord> {
    if (rawCsv.isBlank()) return emptyMap()
    val result = mutableMapOf<Long, SourceFailureRecord>()
    rawCsv.splitToSequence(',').forEach { entry ->
        val parts = entry.trim().split(':')
        if (parts.size >= 3) {
            val sourceId = parts[0].toLongOrNull()
            val timestamp = parts[1].toLongOrNull()
            val count = parts[2].toIntOrNull()
            if (sourceId != null && timestamp != null && count != null && count > 0) {
                result[sourceId] = SourceFailureRecord(sourceId, timestamp, count)
            }
        }
    }
    return result
}

/**
 * Форматирует карту сбоев в компактный CSV для сохранения в Preference.
 */
internal fun formatSourceFailureHistory(records: Map<Long, SourceFailureRecord>): String {
    if (records.isEmpty()) return ""
    return records.values.joinToString(",") { "${it.sourceId}:${it.lastFailedAt}:${it.failCount}" }
}

/**
 * Рассчитывает коэффициент доступности и надёжности источника (от 0.05 до 1.0).
 * Источники с недавними сбоями временно получают пониженный вес, чтобы лента
 * генерировалась быстро и без зависаний на таймаутах.
 */
internal fun calculateSourceReliabilityFactor(
    sourceId: Long,
    history: Map<Long, SourceFailureRecord>,
    nowMs: Long = System.currentTimeMillis(),
): Double {
    val record = history[sourceId] ?: return 1.0
    val elapsed = nowMs - record.lastFailedAt
    if (elapsed >= SOURCE_COOLOFF_MS || elapsed < 0) return 1.0

    val basePenalty = when (record.failCount) {
        1 -> 0.40
        2 -> 0.20
        else -> 0.05
    }

    val elapsedRatio = (elapsed.toDouble() / SOURCE_COOLOFF_MS).coerceIn(0.0, 1.0)
    return basePenalty + (1.0 - basePenalty) * elapsedRatio
}

/**
 * Обновляет историю сбоев: увеличивает счётчик для упавших источников,
 * уменьшает для успешных, и удаляет устаревшие записи.
 */
internal fun updateSourceFailures(
    currentHistory: Map<Long, SourceFailureRecord>,
    failedIds: Set<Long>,
    successfulIds: Set<Long>,
    nowMs: Long = System.currentTimeMillis(),
): Map<Long, SourceFailureRecord> {
    val mutable = currentHistory.toMutableMap()

    // Удаляем устаревшие записи
    mutable.entries.removeAll { (nowMs - it.value.lastFailedAt) > SOURCE_EVICTION_MS }

    // Регистрируем сбои
    for (id in failedIds) {
        val existing = mutable[id]
        val nextCount = (existing?.failCount ?: 0) + 1
        mutable[id] = SourceFailureRecord(id, nowMs, nextCount)
    }

    // Поощряем успешные прогоны
    for (id in successfulIds) {
        val existing = mutable[id] ?: continue
        val nextCount = existing.failCount - 1
        if (nextCount <= 0) {
            mutable.remove(id)
        } else {
            mutable[id] = existing.copy(failCount = nextCount)
        }
    }

    return mutable
}
