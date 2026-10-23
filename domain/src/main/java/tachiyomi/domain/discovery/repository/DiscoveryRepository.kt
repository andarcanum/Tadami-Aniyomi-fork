package tachiyomi.domain.discovery.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

interface DiscoveryRepository {
    fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>>
    fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>>
    suspend fun replaceRows(mediaType: DiscoveryMediaType, rowType: DiscoveryRowType, items: List<DiscoverySuggestion>)
    suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String>
    suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String)
    suspend fun unhide(mediaType: DiscoveryMediaType, cleanTitle: String)
    suspend fun clearHidden(mediaType: DiscoveryMediaType)
    suspend fun lastUpdatedAt(mediaType: DiscoveryMediaType): Long?

    /** Tag-blacklist «скрыть всё с тегом X» (B2). */
    fun subscribeBlacklist(mediaType: DiscoveryMediaType): Flow<Set<String>>
    suspend fun getBlacklistedTags(mediaType: DiscoveryMediaType): Set<String>
    suspend fun blacklistTag(mediaType: DiscoveryMediaType, tag: String)
    suspend fun unblacklistTag(mediaType: DiscoveryMediaType, tag: String)
    suspend fun clearBlacklist(mediaType: DiscoveryMediaType)

    /** Backup (A): полные строки с таймстампами + идемпотентный батч-restore. */
    suspend fun getHiddenEntries(mediaType: DiscoveryMediaType): List<DiscoveryHiddenEntry>
    suspend fun getBlacklistEntries(mediaType: DiscoveryMediaType): List<DiscoveryBlacklistEntry>
    suspend fun restoreHiddenEntries(mediaType: DiscoveryMediaType, entries: List<DiscoveryHiddenEntry>)
    suspend fun restoreBlacklistEntries(mediaType: DiscoveryMediaType, entries: List<DiscoveryBlacklistEntry>)

    /** 48h uniqueness constraint: persistence of shown recommendations. */
    suspend fun getShownTitles(mediaType: DiscoveryMediaType, windowMillis: Long = 48 * 3600_000L): Set<String>
    suspend fun getShownTitlesWithTimestamp(
        mediaType: DiscoveryMediaType,
        windowMillis: Long = 48 * 3600_000L,
    ): Map<String, Long>

    /** Счётчик показов (cleanTitle, lastShownAt, count) — для мягкого неявного негатива (только реальные экспозиции). */
    suspend fun getShownWithCount(
        mediaType: DiscoveryMediaType,
        windowMillis: Long = 48 * 3600_000L,
    ): List<Triple<String, Long, Int>>

    /** Реальные показы рекомендаций пользователю на экране (видимость >= 50% не менее 1с или Stage focus). */
    suspend fun recordExposure(
        mediaType: DiscoveryMediaType,
        cleanTitles: Collection<String>,
        timestamp: Long = System.currentTimeMillis(),
    )

    /** Временное исключение недавно прочитанных/просмотренных тайтлов (без накрутки счётчика показов). */
    suspend fun suppressRecentlyConsumed(
        mediaType: DiscoveryMediaType,
        cleanTitles: Collection<String>,
        timestamp: Long = System.currentTimeMillis(),
    )

    /** Объединённые недавние исключения: реальные экспозиции + временные супрессии. */
    suspend fun getRecentExclusions(
        mediaType: DiscoveryMediaType,
        windowMillis: Long = 48 * 3600_000L,
    ): Set<String>

    suspend fun getRecentExclusionsWithTimestamp(
        mediaType: DiscoveryMediaType,
        windowMillis: Long = 48 * 3600_000L,
    ): Map<String, Long>

    suspend fun markShown(
        mediaType: DiscoveryMediaType,
        cleanTitles: Collection<String>,
        timestamp: Long = System.currentTimeMillis(),
    )
    suspend fun clearShown(mediaType: DiscoveryMediaType)

    /**
     * Backfill после апгрейда: есть ли SOURCE-строки кэша без plugin-привязки
     * (source_id NULL — кэш старше миграции 58). Для разовой тихой перегенерации ленты.
     */
    suspend fun hasUnboundSourceRows(): Boolean

    // ==================== Taste Learning Engine ====================

    /** Все сигналы медиатипа (старые первыми) — вход fold-профиля вкуса. */
    suspend fun getSignals(mediaType: DiscoveryMediaType): List<DiscoverySignal>

    /** Реактивные consumed-тайтлы медиатипа: «просмотрено» исключается из ленты UI. */
    fun subscribeConsumed(mediaType: DiscoveryMediaType): Flow<Set<String>>

    /** Записать сигнал: старшинство [DiscoverySignalType.overrides] + LRU-cap [maxPerMedia]. */
    suspend fun recordSignal(
        mediaType: DiscoveryMediaType,
        cleanTitle: String,
        title: String,
        signalType: DiscoverySignalType,
        genres: List<String>,
        provider: String?,
        sourceKey: String?,
        timestamp: Long = System.currentTimeMillis(),
    )

    /**
     * Атомарное дообогащение метаданных (жанры, provider, sourceKey) существующего сигнала.
     * Обновляет метаданные ТОЛЬКО если сигнал существует и его [createdAt] совпадает с [expectedCreatedAt].
     * Не создаёт новую строку, не меняет тип сигнала, вес и время создания [createdAt].
     * Возвращает true, если строка найдена и обновлена, иначе false.
     */
    suspend fun enrichSignalMetadataIfCurrent(
        mediaType: DiscoveryMediaType,
        cleanTitle: String,
        expectedCreatedAt: Long,
        genres: List<String>,
        provider: String?,
        sourceKey: String?,
    ): Boolean

    /** Undo сигнала: строка удаляется (профиль возвращается к состоянию «до сигнала»). */
    suspend fun removeSignal(mediaType: DiscoveryMediaType, cleanTitle: String)

    /** Сброс выученного вкуса по медиатипу (не трогает кэш ленты/hidden/blacklist). */
    suspend fun clearSignals(mediaType: DiscoveryMediaType)

    /** Backup (Taste Engine): полный сброс + идемпотентный restore. */
    suspend fun clearAllSignals()
    suspend fun restoreSignals(signals: List<DiscoverySignal>)
}
