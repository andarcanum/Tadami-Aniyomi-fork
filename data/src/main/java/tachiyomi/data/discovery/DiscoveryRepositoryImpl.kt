package tachiyomi.data.discovery

import data.Discovery_signals
import data.Discovery_signalsQueries
import data.Discovery_suggestions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository

object DiscoveryMapper {
    fun map(row: Discovery_suggestions): DiscoverySuggestion = DiscoverySuggestion(
        id = row.id,
        mediaType = DiscoveryMediaType.fromKey(row.media_type) ?: DiscoveryMediaType.ANIME,
        rowType = DiscoveryRowType.fromKey(row.row_type) ?: DiscoveryRowType.LIKE,
        title = row.title,
        cleanTitle = row.clean_title,
        coverUrl = row.cover_url,
        reason = row.reason,
        seedTitle = row.seed_title,
        provider = row.provider,
        score = row.score,
        position = row.position,
        createdAt = row.created_at,
        sourceId = row.source_id,
        sourceUrl = row.source_url,
        genres = row.genres?.splitToSequence(",")
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.toList().orEmpty(),
    )
}

class DiscoveryRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : DiscoveryRepository {

    override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> =
        handler.subscribeToList { db ->
            db.discovery_suggestionsQueries.selectAllByMedia(mediaType.key)
        }.map { rows -> rows.map(DiscoveryMapper::map) }

    override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> =
        handler.subscribeToList { db ->
            db.discovery_hiddenQueries.selectAllByMedia(mediaType.key) { _, clean_title, _ -> clean_title }
        }.map { titles -> titles.toSet() }

    override suspend fun replaceRows(
        mediaType: DiscoveryMediaType,
        rowType: DiscoveryRowType,
        items: List<DiscoverySuggestion>,
    ) {
        handler.await(inTransaction = true) { db ->
            db.discovery_suggestionsQueries.deleteByMediaAndRow(mediaType.key, rowType.key)
            items.forEachIndexed { index, item ->
                db.discovery_suggestionsQueries.insert(
                    media_type = mediaType.key,
                    row_type = rowType.key,
                    title = item.title,
                    clean_title = item.cleanTitle,
                    cover_url = item.coverUrl,
                    reason = item.reason,
                    seed_title = item.seedTitle,
                    provider = item.provider,
                    score = item.score,
                    position = index.toLong(),
                    created_at = item.createdAt,
                    source_id = item.sourceId,
                    source_url = item.sourceUrl,
                    genres = item.genres.joinToString(","),
                )
            }
        }
    }

    override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String> =
        handler.awaitList { db ->
            db.discovery_hiddenQueries.selectAllByMedia(mediaType.key) { _, clean_title, _ -> clean_title }
        }.toSet()

    override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) {
        handler.await { db ->
            db.discovery_hiddenQueries.insertOrIgnore(mediaType.key, cleanTitle, System.currentTimeMillis())
        }
    }

    override suspend fun unhide(mediaType: DiscoveryMediaType, cleanTitle: String) {
        handler.await { db -> db.discovery_hiddenQueries.delete(mediaType.key, cleanTitle) }
    }

    override suspend fun clearHidden(mediaType: DiscoveryMediaType) {
        handler.await { db -> db.discovery_hiddenQueries.deleteAllByMedia(mediaType.key) }
    }

    override fun subscribeBlacklist(mediaType: DiscoveryMediaType): Flow<Set<String>> =
        handler.subscribeToList { db ->
            db.discovery_blacklist_tagsQueries.selectAllByMedia(mediaType.key) { _, tag, _ -> tag }
        }.map { tags -> tags.toSet() }

    override suspend fun getBlacklistedTags(mediaType: DiscoveryMediaType): Set<String> =
        handler.awaitList { db ->
            db.discovery_blacklist_tagsQueries.selectAllByMedia(mediaType.key) { _, tag, _ -> tag }
        }.toSet()

    override suspend fun blacklistTag(mediaType: DiscoveryMediaType, tag: String) {
        handler.await { db ->
            db.discovery_blacklist_tagsQueries.insertOrIgnore(mediaType.key, tag, System.currentTimeMillis())
        }
    }

    override suspend fun unblacklistTag(mediaType: DiscoveryMediaType, tag: String) {
        handler.await { db -> db.discovery_blacklist_tagsQueries.delete(mediaType.key, tag) }
    }

    override suspend fun clearBlacklist(mediaType: DiscoveryMediaType) {
        handler.await { db -> db.discovery_blacklist_tagsQueries.deleteAllByMedia(mediaType.key) }
    }

    override suspend fun hasUnboundSourceRows(): Boolean =
        handler.await { db -> db.discovery_suggestionsQueries.countUnboundSourceRows().executeAsOne() } > 0

    override suspend fun getHiddenEntries(mediaType: DiscoveryMediaType): List<DiscoveryHiddenEntry> =
        handler.awaitList { db ->
            db.discovery_hiddenQueries.selectAllByMedia(mediaType.key) { _, clean_title, hidden_at ->
                DiscoveryHiddenEntry(clean_title, hidden_at)
            }
        }

    override suspend fun getBlacklistEntries(mediaType: DiscoveryMediaType): List<DiscoveryBlacklistEntry> =
        handler.awaitList { db ->
            db.discovery_blacklist_tagsQueries.selectAllByMedia(mediaType.key) { _, tag, added_at ->
                DiscoveryBlacklistEntry(tag, added_at)
            }
        }

    override suspend fun restoreHiddenEntries(
        mediaType: DiscoveryMediaType,
        entries: List<DiscoveryHiddenEntry>,
    ) {
        handler.await(inTransaction = true) { db ->
            entries.forEach {
                db.discovery_hiddenQueries.insertOrIgnore(mediaType.key, it.cleanTitle, it.hiddenAt)
            }
        }
    }

    override suspend fun restoreBlacklistEntries(
        mediaType: DiscoveryMediaType,
        entries: List<DiscoveryBlacklistEntry>,
    ) {
        handler.await(inTransaction = true) { db ->
            entries.forEach {
                db.discovery_blacklist_tagsQueries.insertOrIgnore(mediaType.key, it.tag, it.addedAt)
            }
        }
    }

    override suspend fun lastUpdatedAt(mediaType: DiscoveryMediaType): Long? =
        handler.awaitOne { db -> db.discovery_suggestionsQueries.lastUpdatedAt(mediaType.key) }.takeIf { it > 0L }

    override suspend fun getShownTitles(mediaType: DiscoveryMediaType, windowMillis: Long): Set<String> {
        val since = System.currentTimeMillis() - windowMillis
        return handler.awaitList { db ->
            db.discovery_exposuresQueries.selectExposedSince(mediaType.key, since)
        }.toSet()
    }

    override suspend fun getShownTitlesWithTimestamp(
        mediaType: DiscoveryMediaType,
        windowMillis: Long,
    ): Map<String, Long> {
        val since = System.currentTimeMillis() - windowMillis
        return handler.awaitList { db ->
            db.discovery_exposuresQueries.selectExposedWithTimeSince(mediaType.key, since) {
                    clean_title,
                    latest_exposed_at,
                ->
                clean_title to latest_exposed_at
            }
        }.toMap()
    }

    override suspend fun getShownWithCount(
        mediaType: DiscoveryMediaType,
        windowMillis: Long,
    ): List<Triple<String, Long, Int>> {
        val since = System.currentTimeMillis() - windowMillis
        return handler.awaitList { db ->
            db.discovery_exposuresQueries.selectExposedWithCountSince(mediaType.key, since) {
                    clean_title,
                    latest_exposed_at,
                    count,
                ->
                Triple(clean_title, latest_exposed_at, count.toInt())
            }
        }
    }

    override suspend fun recordExposure(
        mediaType: DiscoveryMediaType,
        cleanTitles: Collection<String>,
        timestamp: Long,
    ) {
        if (cleanTitles.isEmpty()) return
        val cutoff = timestamp - (48 * 3600_000L)
        val dedupeIntervalMillis = 15 * 60_000L // 15 minutes deduplication window
        handler.await(inTransaction = true) { db ->
            db.discovery_exposuresQueries.cleanupOld(cutoff)
            for (cleanTitle in cleanTitles) {
                if (cleanTitle.isNotBlank()) {
                    val latest = db.discovery_exposuresQueries
                        .selectLatestExposedAt(mediaType.key, cleanTitle)
                        .executeAsOneOrNull()
                        ?.latest
                    if (latest == null || (timestamp - latest) >= dedupeIntervalMillis) {
                        db.discovery_exposuresQueries.insert(mediaType.key, cleanTitle, timestamp)
                    }
                }
            }
        }
    }

    override suspend fun suppressRecentlyConsumed(
        mediaType: DiscoveryMediaType,
        cleanTitles: Collection<String>,
        timestamp: Long,
    ) {
        if (cleanTitles.isEmpty()) return
        val cutoff = timestamp - (48 * 3600_000L)
        handler.await(inTransaction = true) { db ->
            db.discovery_suppressionsQueries.cleanupOld(cutoff)
            for (cleanTitle in cleanTitles) {
                if (cleanTitle.isNotBlank()) {
                    db.discovery_suppressionsQueries.upsert(mediaType.key, cleanTitle, timestamp)
                }
            }
        }
    }

    override suspend fun getRecentExclusions(
        mediaType: DiscoveryMediaType,
        windowMillis: Long,
    ): Set<String> {
        val since = System.currentTimeMillis() - windowMillis
        return handler.await(inTransaction = true) { db ->
            val exposed = db.discovery_exposuresQueries.selectExposedSince(mediaType.key, since).executeAsList()
            val suppressed = db.discovery_suppressionsQueries.selectSuppressedSince(
                mediaType.key,
                since,
            ).executeAsList()
            (exposed + suppressed).toSet()
        }
    }

    override suspend fun getRecentExclusionsWithTimestamp(
        mediaType: DiscoveryMediaType,
        windowMillis: Long,
    ): Map<String, Long> {
        val since = System.currentTimeMillis() - windowMillis
        return handler.await(inTransaction = true) { db ->
            val result = mutableMapOf<String, Long>()
            db.discovery_exposuresQueries.selectExposedWithTimeSince(mediaType.key, since) {
                    clean_title,
                    latest_exposed_at,
                ->
                result[clean_title] = latest_exposed_at
            }.executeAsList()
            db.discovery_suppressionsQueries.selectSuppressedWithTimeSince(mediaType.key, since) {
                    clean_title,
                    suppressed_at,
                ->
                val existing = result[clean_title] ?: 0L
                result[clean_title] = maxOf(existing, suppressed_at)
            }.executeAsList()
            result
        }
    }

    override suspend fun markShown(
        mediaType: DiscoveryMediaType,
        cleanTitles: Collection<String>,
        timestamp: Long,
    ) {
        recordExposure(mediaType, cleanTitles, timestamp)
    }

    override suspend fun clearShown(mediaType: DiscoveryMediaType) {
        handler.await(inTransaction = true) { db ->
            db.discovery_exposuresQueries.deleteAllByMedia(mediaType.key)
            db.discovery_suppressionsQueries.deleteAllByMedia(mediaType.key)
        }
    }

    // ==================== Taste Learning Engine ====================

    private fun signalMapper(
        mediaType: String,
        cleanTitle: String,
        title: String,
        signal: String,
        @Suppress("UNUSED_PARAMETER") weight: Double,
        genres: String?,
        provider: String?,
        sourceKey: String?,
        createdAt: Long,
    ): DiscoverySignal = DiscoverySignal(
        mediaType = DiscoveryMediaType.fromKey(mediaType) ?: DiscoveryMediaType.ANIME,
        cleanTitle = cleanTitle,
        title = title,
        signalType = DiscoverySignalType.fromKey(signal) ?: DiscoverySignalType.CLICK,
        genres = genres
            ?.splitToSequence(",")
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.toList()
            .orEmpty(),
        provider = provider,
        sourceKey = sourceKey,
        createdAt = createdAt,
    )

    override suspend fun getSignals(mediaType: DiscoveryMediaType): List<DiscoverySignal> =
        handler.awaitList { db ->
            db.discovery_signalsQueries.selectAllByMedia(mediaType.key, ::signalMapper)
        }

    override fun subscribeConsumed(mediaType: DiscoveryMediaType): Flow<Set<String>> =
        handler.subscribeToList { db ->
            db.discovery_signalsQueries.selectConsumedByMedia(mediaType.key, ::signalMapper)
        }.map { signals -> signals.mapTo(HashSet()) { it.cleanTitle } }

    private val lastSignalTimestamp = java.util.concurrent.atomic.AtomicLong(0L)

    private fun nextMonotonicTimestamp(timestamp: Long): Long {
        while (true) {
            val current = lastSignalTimestamp.get()
            val base = if (timestamp > 0L) timestamp else System.currentTimeMillis()
            val next = if (base > current) base else current + 1
            if (lastSignalTimestamp.compareAndSet(current, next)) {
                return next
            }
        }
    }

    override suspend fun recordSignal(
        mediaType: DiscoveryMediaType,
        cleanTitle: String,
        title: String,
        signalType: DiscoverySignalType,
        genres: List<String>,
        provider: String?,
        sourceKey: String?,
        timestamp: Long,
    ) {
        val eventTimestamp = nextMonotonicTimestamp(timestamp)
        handler.await(inTransaction = true) { db ->
            // Старшинство: слабый сигнал не затирает уже записанный сильный
            // (клик после лайка не понижает вес).
            val existing = db.discovery_signalsQueries
                .selectByTitle(mediaType.key, cleanTitle, ::signalMapper)
                .executeAsOneOrNull()
            val effectiveNext = DiscoverySignalType.resolveEffectiveSignal(existing?.signalType, signalType)
            if (!DiscoverySignalType.overrides(existing?.signalType, effectiveNext)) {
                return@await
            }
            val mergedMeta = mergeSignalMetadata(
                existing = existing,
                incomingTitle = title,
                incomingGenres = genres,
                incomingProvider = provider,
                incomingSourceKey = sourceKey,
            )
            db.discovery_signalsQueries.upsert(
                media_type = mediaType.key,
                clean_title = cleanTitle,
                title = mergedMeta.title,
                signal = effectiveNext.key,
                weight = effectiveNext.weight,
                genres = mergedMeta.genres.joinToString(","),
                provider = mergedMeta.provider,
                source_key = mergedMeta.sourceKey,
                created_at = eventTimestamp,
            )
            enforceSignalCaps(db.discovery_signalsQueries, mediaType)
        }
    }

    override suspend fun enrichSignalMetadataIfCurrent(
        mediaType: DiscoveryMediaType,
        cleanTitle: String,
        expectedCreatedAt: Long,
        genres: List<String>,
        provider: String?,
        sourceKey: String?,
    ): Boolean = handler.await(inTransaction = true) { db ->
        val existing = db.discovery_signalsQueries
            .selectByTitle(mediaType.key, cleanTitle, ::signalMapper)
            .executeAsOneOrNull()
        if (existing == null || existing.createdAt != expectedCreatedAt ||
            existing.signalType != DiscoverySignalType.LIKE
        ) {
            return@await false
        }
        val mergedMeta = mergeSignalMetadata(
            existing = existing,
            incomingTitle = existing.title,
            incomingGenres = genres,
            incomingProvider = provider,
            incomingSourceKey = sourceKey,
        )
        db.discovery_signalsQueries.updateMetadataIfMatchesCreatedAt(
            genres = mergedMeta.genres.joinToString(","),
            provider = mergedMeta.provider,
            source_key = mergedMeta.sourceKey,
            media_type = mediaType.key,
            clean_title = cleanTitle,
            created_at = expectedCreatedAt,
        )
        true
    }

    override suspend fun removeSignal(mediaType: DiscoveryMediaType, cleanTitle: String) {
        handler.await { db -> db.discovery_signalsQueries.delete(mediaType.key, cleanTitle) }
    }

    override suspend fun clearSignals(mediaType: DiscoveryMediaType) {
        handler.await { db -> db.discovery_signalsQueries.deleteAllByMedia(mediaType.key) }
    }

    override suspend fun clearAllSignals() {
        handler.await { db -> db.discovery_signalsQueries.deleteAll() }
    }

    override suspend fun restoreSignals(signals: List<DiscoverySignal>) {
        if (signals.isEmpty()) return
        handler.await(inTransaction = true) { db ->
            signals.forEach { signal ->
                // Старшинство и при restore: бэкапный слабый сигнал не затирает
                // локальный сильный (устройство A: HIDE; устройство B: LIKE →
                // после restore остаётся HIDE — пользователь на A скрыл осознанно).
                val existing = db.discovery_signalsQueries
                    .selectByTitle(signal.mediaType.key, signal.cleanTitle, ::signalMapper)
                    .executeAsOneOrNull()
                val effectiveNext = DiscoverySignalType.resolveEffectiveSignal(existing?.signalType, signal.signalType)
                if (!DiscoverySignalType.overrides(existing?.signalType, effectiveNext)) {
                    return@forEach
                }
                val mergedMeta = mergeSignalMetadata(
                    existing = existing,
                    incomingTitle = signal.title,
                    incomingGenres = signal.genres,
                    incomingProvider = signal.provider,
                    incomingSourceKey = signal.sourceKey,
                )
                db.discovery_signalsQueries.upsert(
                    media_type = signal.mediaType.key,
                    clean_title = signal.cleanTitle,
                    title = mergedMeta.title,
                    signal = effectiveNext.key,
                    weight = effectiveNext.weight,
                    genres = mergedMeta.genres.joinToString(","),
                    provider = mergedMeta.provider,
                    source_key = mergedMeta.sourceKey,
                    created_at = signal.createdAt,
                )
            }
            // Тот же LRU-cap, что у live-записей: restore старого большого бэкапа
            // не должен раздувать таблицу сверх лимита.
            DiscoveryMediaType.entries.forEach { mediaType ->
                enforceSignalCaps(db.discovery_signalsQueries, mediaType)
            }
        }
    }

    private fun enforceSignalCaps(
        queries: Discovery_signalsQueries,
        mediaType: DiscoveryMediaType,
    ) {
        val liveCount = queries.countLiveByMedia(mediaType.key).executeAsOne()
        if (liveCount > SIGNALS_CAP_PER_MEDIA) {
            queries.evictOldestLive(
                mediaType.key,
                mediaType.key,
                liveCount - SIGNALS_CAP_PER_MEDIA,
            )
        }
        val consumedCount = queries.countConsumedByMedia(mediaType.key).executeAsOne()
        if (consumedCount > CONSUMED_CAP_PER_MEDIA) {
            queries.evictOldestConsumed(
                mediaType.key,
                mediaType.key,
                consumedCount - CONSUMED_CAP_PER_MEDIA,
            )
        }
    }

    private companion object {
        const val SIGNALS_CAP_PER_MEDIA = 500L
        const val CONSUMED_CAP_PER_MEDIA = 5000L
    }
}

internal data class MergedSignalMetadata(
    val title: String,
    val genres: List<String>,
    val provider: String?,
    val sourceKey: String?,
)

internal fun mergeSignalMetadata(
    existing: DiscoverySignal?,
    incomingTitle: String,
    incomingGenres: List<String>,
    incomingProvider: String?,
    incomingSourceKey: String?,
): MergedSignalMetadata {
    val title = incomingTitle.takeIf { it.isNotBlank() } ?: existing?.title.orEmpty()
    val genres = incomingGenres.ifEmpty { existing?.genres.orEmpty() }
    val provider = incomingProvider?.takeIf { it.isNotBlank() } ?: existing?.provider
    val sourceKey = incomingSourceKey?.takeIf { it.isNotBlank() } ?: existing?.sourceKey
    return MergedSignalMetadata(
        title = title,
        genres = genres,
        provider = provider,
        sourceKey = sourceKey,
    )
}
