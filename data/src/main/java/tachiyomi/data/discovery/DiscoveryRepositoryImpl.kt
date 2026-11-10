package tachiyomi.data.discovery

import data.Discovery_publication
import data.Discovery_signalsQueries
import data.Discovery_suggestions
import data.Discovery_suggestionsQueries
import data.Discovery_title_memory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.discovery.model.DISCOVERY_HOME_BATCH_SIZE
import tachiyomi.domain.discovery.model.DiscoveryBatchReceipt
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryEligibilitySnapshot
import tachiyomi.domain.discovery.model.DiscoveryFactKind
import tachiyomi.domain.discovery.model.DiscoveryFactState
import tachiyomi.domain.discovery.model.DiscoveryGenerationToken
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryNegativeVote
import tachiyomi.domain.discovery.model.DiscoveryPositiveReceipt
import tachiyomi.domain.discovery.model.DiscoveryPositiveVote
import tachiyomi.domain.discovery.model.DiscoveryPreparedPool
import tachiyomi.domain.discovery.model.DiscoveryPublicationOutcome
import tachiyomi.domain.discovery.model.DiscoveryPublicationSnapshot
import tachiyomi.domain.discovery.model.DiscoveryPublishReceipt
import tachiyomi.domain.discovery.model.DiscoveryRetirementPolicy
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySourceCursor
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTasteMetadata
import tachiyomi.domain.discovery.model.DiscoveryTasteRecord
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.model.DiscoveryTitleMemory
import tachiyomi.domain.discovery.model.DiscoveryTopUpReceipt
import tachiyomi.domain.discovery.model.DiscoveryTraversalState
import tachiyomi.domain.discovery.model.blockedUntil
import tachiyomi.domain.discovery.model.canonicalDiscoveryKey
import tachiyomi.domain.discovery.model.canonicalStorageKey
import tachiyomi.domain.discovery.model.identityKeys
import tachiyomi.domain.discovery.model.legacyDiscoveryKey
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.discovery.model.selectNextDiscoveryBatch
import tachiyomi.domain.discovery.repository.DiscoveryCandidateValidator
import tachiyomi.domain.discovery.repository.DiscoveryKnownTitlesProvider
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.domain.discovery.service.DiscoveryMutationFence

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
        identityKey = row.identity_key,
        trustedAliases = splitAliases(row.trusted_aliases),
        releaseStatus = row.release_status?.let { tachiyomi.domain.discovery.model.DiscoveryReleaseStatus.fromKey(it) },
        metadataVerifiedAt = row.metadata_verified_at,
    )

    internal fun splitAliases(raw: String?): Set<String> =
        raw?.splitToSequence("\n")
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.toSet()
            .orEmpty()
}

private fun DiscoveryTitleIdentity.asTitleKeys(): Set<String> = buildSet {
    add(identityKey)
    add(legacyDiscoveryKey(mediaType, primaryCleanTitle))
    addAll(trustedAliases)
}

private fun Discovery_suggestionsQueries.selectAllMapped(media: String): List<DiscoverySuggestion> =
    selectAllByMedia(media).executeAsList().map(DiscoveryMapper::map)

class DiscoveryRepositoryImpl(
    private val handler: MangaDatabaseHandler,
    private val knownTitlesProvider: DiscoveryKnownTitlesProvider,
    private val candidateValidator: DiscoveryCandidateValidator,
    private val now: () -> Long = System::currentTimeMillis,
) : DiscoveryRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> =
        handler.subscribeToList { db ->
            db.discovery_suggestionsQueries.selectAllByMedia(mediaType.key)
        }.map { rows -> rows.map(DiscoveryMapper::map) }

    override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> =
        handler.subscribeToList { db ->
            db.discovery_hiddenQueries.selectAllByMedia(mediaType.key) { _, clean_title, _, _, _, _ -> clean_title }
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
                    identity_key = item.identityKey,
                    trusted_aliases = item.trustedAliases.joinToString("\n"),
                    release_status = item.releaseStatus?.key,
                    metadata_verified_at = item.metadataVerifiedAt,
                )
            }
        }
    }

    override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String> =
        handler.awaitList { db ->
            db.discovery_hiddenQueries.selectAllByMedia(mediaType.key) { _, clean_title, _, _, _, _ -> clean_title }
        }.toSet()

    override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) {
        val timestamp = now()
        handler.await(inTransaction = true) { db ->
            db.discovery_hiddenQueries.upsertActive(mediaType.key, cleanTitle, timestamp, timestamp, cleanTitle)
            bumpProjection(db, mediaType)
        }
    }

    override suspend fun unhide(mediaType: DiscoveryMediaType, cleanTitle: String) {
        val timestamp = now()
        handler.await(inTransaction = true) { db ->
            db.discovery_hiddenQueries.upsertTombstone(mediaType.key, cleanTitle, timestamp, timestamp, cleanTitle)
            bumpProjection(db, mediaType)
        }
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
            db.discovery_blacklist_tagsQueries.insertOrIgnore(mediaType.key, tag, now())
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
            db.discovery_hiddenQueries.selectAllByMedia(mediaType.key) { _, clean_title, hidden_at, _, _, _ ->
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
                db.discovery_hiddenQueries.insertOrIgnore(
                    mediaType.key,
                    it.cleanTitle,
                    it.hiddenAt,
                    it.hiddenAt,
                    it.cleanTitle,
                )
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
        val since = now() - windowMillis
        return handler.awaitList { db ->
            db.discovery_exposuresQueries.selectExposedSince(mediaType.key, since)
        }.toSet()
    }

    override suspend fun getShownTitlesWithTimestamp(
        mediaType: DiscoveryMediaType,
        windowMillis: Long,
    ): Map<String, Long> {
        val since = now() - windowMillis
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
        val since = now() - windowMillis
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
        val dedupeIntervalMillis = 15 * 60_000L
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
        val since = now() - windowMillis
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
        val since = now() - windowMillis
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

    // ==================== Taste Learning Engine (legacy compatibility) ====================

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
        primaryCleanTitle: String,
        identityKey: String?,
        sourceId: Long?,
        sourceUrl: String?,
        trustedAliases: String?,
        metadataChangedAt: Long,
        positiveSignal: String?,
        positiveCreatedAt: Long,
    ): DiscoverySignal = DiscoverySignal(
        mediaType = DiscoveryMediaType.fromKey(mediaType) ?: DiscoveryMediaType.ANIME,
        cleanTitle = primaryCleanTitle.ifBlank { cleanTitle },
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
        identityKey = identityKey ?: cleanTitle.takeIf { it.startsWith(STORAGE_PREFIX) }
            ?.removePrefix(STORAGE_PREFIX),
        trustedAliases = DiscoveryMapper.splitAliases(trustedAliases),
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        metadataChangedAt = metadataChangedAt,
        positiveKind = DiscoverySignalType.fromKey(positiveSignal),
        positiveCreatedAt = positiveCreatedAt,
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
            val base = if (timestamp > 0L) timestamp else now()
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
        recordSignalWithReceipt(mediaType, cleanTitle, title, signalType, genres, provider, sourceKey, timestamp)
    }

    override suspend fun recordSignalWithReceipt(
        mediaType: DiscoveryMediaType,
        cleanTitle: String,
        title: String,
        signalType: DiscoverySignalType,
        genres: List<String>,
        provider: String?,
        sourceKey: String?,
        timestamp: Long,
    ): Long? {
        return handler.await(inTransaction = true) { db ->
            val existing = db.discovery_signalsQueries
                .selectByTitle(mediaType.key, cleanTitle, ::signalMapper)
                .executeAsOneOrNull()
            val effectiveNext = DiscoverySignalType.resolveEffectiveSignal(existing?.signalType, signalType)
            if (!DiscoverySignalType.overrides(existing?.signalType, effectiveNext)) {
                return@await null
            }
            val eventTimestamp = nextMonotonicTimestamp(timestamp)
            val mergedMeta = mergeSignalMetadata(
                existing = existing,
                incomingTitle = title,
                incomingGenres = genres,
                incomingProvider = provider,
                incomingSourceKey = sourceKey,
            )
            val positiveKind = when {
                effectiveNext in POSITIVE_KINDS -> effectiveNext
                else -> existing?.positiveKind
            }
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
                primary_clean_title = existing?.cleanTitle ?: cleanTitle,
                identity_key = existing?.identityKey,
                source_id = existing?.sourceId,
                source_url = existing?.sourceUrl,
                trusted_aliases = existing?.trustedAliases?.joinToString("\n").orEmpty(),
                metadata_changed_at = existing?.metadataChangedAt ?: eventTimestamp,
                positive_signal = positiveKind?.key,
                positive_created_at = if (positiveKind != null) eventTimestamp else existing?.positiveCreatedAt ?: 0L,
            )
            enforceSignalCaps(db.discovery_signalsQueries, mediaType)
            bumpProjection(db, mediaType)
            eventTimestamp
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
            trusted_aliases = existing.trustedAliases.joinToString("\n"),
            source_id = existing.sourceId,
            source_url = existing.sourceUrl,
            identity_key = existing.identityKey,
            metadata_changed_at = now(),
            media_type = mediaType.key,
            clean_title = cleanTitle,
            positive_created_at = existing.positiveCreatedAt,
        )
        true
    }

    override suspend fun removeSignal(mediaType: DiscoveryMediaType, cleanTitle: String) {
        handler.await { db -> db.discovery_signalsQueries.delete(mediaType.key, cleanTitle) }
    }

    override suspend fun undoHide(mediaType: DiscoveryMediaType, cleanTitle: String) {
        val timestamp = now()
        handler.await(inTransaction = true) { db ->
            db.discovery_hiddenQueries.upsertTombstone(mediaType.key, cleanTitle, timestamp, timestamp, cleanTitle)
            val existing = db.discovery_signalsQueries
                .selectByTitle(mediaType.key, cleanTitle, ::signalMapper)
                .executeAsOneOrNull()
            if (existing?.signalType == DiscoverySignalType.HIDE) {
                val positive = existing.positiveKind
                if (positive == null) {
                    db.discovery_signalsQueries.delete(mediaType.key, cleanTitle)
                } else {
                    db.discovery_signalsQueries.upsert(
                        media_type = mediaType.key,
                        clean_title = cleanTitle,
                        title = existing.title,
                        signal = positive.key,
                        weight = positive.weight,
                        genres = existing.genres.joinToString(","),
                        provider = existing.provider,
                        source_key = existing.sourceKey,
                        created_at = existing.createdAt,
                        primary_clean_title = existing.cleanTitle,
                        identity_key = existing.identityKey,
                        source_id = existing.sourceId,
                        source_url = existing.sourceUrl,
                        trusted_aliases = existing.trustedAliases.joinToString("\n"),
                        metadata_changed_at = existing.metadataChangedAt,
                        positive_signal = positive.key,
                        positive_created_at = existing.positiveCreatedAt,
                    )
                }
            }
            bumpProjection(db, mediaType)
        }
    }

    override suspend fun clearSignals(mediaType: DiscoveryMediaType) {
        handler.await(inTransaction = true) { db ->
            db.discovery_signalsQueries.deleteAllByMedia(mediaType.key)
            bumpProjection(db, mediaType)
        }
    }

    override suspend fun clearAllSignals() {
        handler.await { db -> db.discovery_signalsQueries.deleteAll() }
    }

    override suspend fun restoreSignals(signals: List<DiscoverySignal>) {
        if (signals.isEmpty()) return
        handler.await(inTransaction = true) { db ->
            signals.forEach { signal ->
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
                    primary_clean_title = signal.cleanTitle,
                    identity_key = signal.identityKey,
                    source_id = signal.sourceId,
                    source_url = signal.sourceUrl,
                    trusted_aliases = signal.trustedAliases.joinToString("\n"),
                    metadata_changed_at = signal.metadataChangedAt,
                    positive_signal = signal.positiveKind?.key,
                    positive_created_at = signal.positiveCreatedAt,
                )
            }
            DiscoveryMediaType.entries.forEach { mediaType ->
                enforceSignalCaps(db.discovery_signalsQueries, mediaType)
            }
        }
    }

    // ==================== Publication / memory (v2) ====================

    override fun observePublication(media: DiscoveryMediaType): Flow<DiscoveryPublicationSnapshot> =
        handler.subscribeToList { db -> db.discovery_publicationQueries.selectByMedia(media.key) }
            .map { publicationRows ->
                DiscoveryMutationFence.withMutation(media) {
                    val known = knownTitlesProvider.snapshot(media)
                    handler.await { db ->
                        val suggestions = db.discovery_suggestionsQueries.selectAllMapped(media.key)
                        buildSnapshot(
                            media = media,
                            row = publicationRows.firstOrNull(),
                            suggestions = suggestions,
                            known = known,
                            memory = readMemory(db, media),
                            hidden = readHidden(db, media),
                            blacklist = readBlacklist(db, media),
                        )
                    }
                }
            }

    override fun observeTitleMemory(media: DiscoveryMediaType): Flow<List<DiscoveryTitleMemory>> =
        handler.subscribeToList { db -> db.discovery_title_memoryQueries.selectAllByMedia(media.key, ::memoryMapper) }

    override fun observeTaste(media: DiscoveryMediaType): Flow<List<DiscoveryTasteRecord>> =
        handler.subscribeToList { db -> db.discovery_signalsQueries.selectAllByMedia(media.key, ::signalMapper) }
            .map { signals -> signals.mapNotNull(::toTasteRecord) }

    override suspend fun publication(media: DiscoveryMediaType): DiscoveryPublicationSnapshot =
        DiscoveryMutationFence.withMutation(media) {
            val known = knownTitlesProvider.snapshot(media)
            handler.await { db ->
                val row = db.discovery_publicationQueries.selectByMedia(media.key).executeAsOneOrNull()
                val suggestions = db.discovery_suggestionsQueries.selectAllMapped(media.key)
                buildSnapshot(
                    media = media,
                    row = row,
                    suggestions = suggestions,
                    known = known,
                    memory = readMemory(db, media),
                    hidden = readHidden(db, media),
                    blacklist = readBlacklist(db, media),
                )
            }
        }

    override suspend fun eligibility(media: DiscoveryMediaType): DiscoveryEligibilitySnapshot =
        DiscoveryMutationFence.withMutation(media) {
            val known = knownTitlesProvider.snapshot(media)
            val memory = handler.awaitList { db ->
                db.discovery_title_memoryQueries.selectAllByMedia(media.key, ::memoryMapper)
            }
            val hidden = handler.awaitList { db ->
                db.discovery_hiddenQueries.selectAllByMedia(media.key) { _, clean_title, _, _, _, _ -> clean_title }
            }.toSet()
            buildEligibility(media, known, memory, hidden)
        }

    override suspend fun resolveIdentity(identity: DiscoveryTitleIdentity): DiscoveryTitleIdentity {
        val cleanTitle = identity.primaryCleanTitle.ifBlank { normalizeDiscoveryTitle(identity.title) }
        val identityKey = identity.identityKey.ifBlank {
            canonicalDiscoveryKey(identity.mediaType, identity.sourceId, identity.sourceUrl, cleanTitle)
        }
        return identity.copy(primaryCleanTitle = cleanTitle, identityKey = identityKey)
    }

    override suspend fun installConfigurationKey(media: DiscoveryMediaType, key: String) {
        handler.await(inTransaction = true) { db ->
            db.discovery_publicationQueries.ensureRow(media.key)
            db.discovery_publicationQueries.installConfigurationKeyIfChanged(key, media.key, key)
        }
    }

    override suspend fun reserveGeneration(
        media: DiscoveryMediaType,
        requestId: String,
        expectedGeneration: Long,
        configurationKey: String,
    ): DiscoveryGenerationToken? = handler.await(inTransaction = true) { db ->
        db.discovery_publicationQueries.ensureRow(media.key)
        val row = db.discovery_publicationQueries.selectByMedia(media.key).executeAsOne()
        if (row.generation != expectedGeneration) {
            return@await null
        }
        val revision = row.owner_revision + 1
        db.discovery_publicationQueries.reserveOwner(requestId, revision, media.key, expectedGeneration)
        DiscoveryGenerationToken(
            mediaType = media,
            requestId = requestId,
            ownerRevision = revision,
            expectedGeneration = expectedGeneration,
            configurationKey = configurationKey,
        )
    }

    override suspend fun revokeGeneration(media: DiscoveryMediaType, requestId: String) {
        handler.await(inTransaction = true) { db ->
            db.discovery_publicationQueries.revokeOwner(media.key, requestId)
        }
    }

    override suspend fun publishGeneration(
        token: DiscoveryGenerationToken,
        prepared: DiscoveryPreparedPool,
        retirement: DiscoveryRetirementPolicy,
    ): DiscoveryPublishReceipt = DiscoveryMutationFence.withMutation(token.mediaType) {
        val known = knownTitlesProvider.snapshot(token.mediaType)
        handler.await(inTransaction = true) { db ->
            db.discovery_publicationQueries.ensureRow(token.mediaType.key)
            val publication = db.discovery_publicationQueries.selectByMedia(token.mediaType.key).executeAsOne()
            if (publication.generation != token.expectedGeneration ||
                publication.owner_request_id != token.requestId ||
                publication.owner_revision != token.ownerRevision
            ) {
                return@await DiscoveryPublishReceipt.Superseded(publication.generation)
            }

            val committedAt = now()
            val oldRows = db.discovery_suggestionsQueries.selectAllMapped(token.mediaType.key)
            val memory = db.discovery_title_memoryQueries
                .selectAllByMedia(token.mediaType.key, ::memoryMapper)
                .executeAsList()
            val hidden = db.discovery_hiddenQueries
                .selectAllByMedia(token.mediaType.key) { _, clean_title, _, _, _, _ -> clean_title }
                .executeAsList()
                .toSet()
            val blacklist = db.discovery_blacklist_tagsQueries
                .selectAllByMedia(token.mediaType.key) { _, tag, _ -> tag }
                .executeAsList()
                .toSet()
            val eligibility = buildEligibility(token.mediaType, known, memory, hidden)

            val preparedSuggestions = when (prepared) {
                is DiscoveryPreparedPool.Fresh -> prepared.suggestions
                is DiscoveryPreparedPool.VerifiedEmpty -> {
                    if (!isVerifiedEmpty(prepared)) {
                        return@await DiscoveryPublishReceipt.NoAdmissibleItems
                    }
                    emptyList()
                }
            }

            val oldKeys = oldRows.flatMap { it.identityKeys() }.toSet()
            val accepted = prepareCandidates(
                media = token.mediaType,
                suggestions = preparedSuggestions,
                oldPoolKeys = oldKeys,
                eligibility = eligibility,
                blacklist = blacklist,
            )
            val oldEligibleCount = eligibleSuggestions(
                media = token.mediaType,
                rows = oldRows,
                known = known,
                memory = memory,
                hidden = hidden,
                blacklist = blacklist,
            ).size
            if (token.configurationKey == publication.configuration_key &&
                oldEligibleCount > 0 &&
                accepted.size < oldEligibleCount
            ) {
                db.discovery_publicationQueries.revokeOwner(token.mediaType.key, token.requestId)
                return@await DiscoveryPublishReceipt.KeptExisting(accepted.size, oldEligibleCount)
            }
            if (prepared is DiscoveryPreparedPool.Fresh && accepted.isEmpty()) {
                return@await DiscoveryPublishReceipt.NoAdmissibleItems
            }

            val activeKeys = decodeKeys(publication.active_keys)
            val issuedKeys = decodeKeys(publication.issued_keys)
            val seenKeys = activeKeys + issuedKeys
            val exposedTitles = db.discovery_exposuresQueries
                .selectExposedWithTimeSince(token.mediaType.key, publication.committed_at) { clean_title, _ ->
                    clean_title
                }
                .executeAsList()
                .toSet()

            oldRows.forEach { old ->
                // Insurance: Home teaser writes exposures on focus/interaction, but items in active/issued batch count as seen to guarantee unseen titles are never retired in background.
                val seen = old.cleanTitle in exposedTitles || old.identityKeys().any { it in seenKeys }
                val blockedUntil = retirement.blockedUntil(committedAt, seen)
                if (blockedUntil != null) {
                    val identity = identityOf(token.mediaType, old)
                    db.discovery_title_memoryQueries.upsertIdentity(
                        media_type = identity.mediaType.key,
                        identity_key = identity.identityKey,
                        title = identity.title,
                        primary_clean_title = identity.primaryCleanTitle,
                        source_id = identity.sourceId,
                        source_url = identity.sourceUrl,
                        trusted_aliases = identity.trustedAliases.joinToString("\n"),
                        metadata_changed_at = identity.metadataChangedAt,
                    )
                    db.discovery_title_memoryQueries.retireBlocked(
                        blockedUntil,
                        token.mediaType.key,
                        identity.identityKey,
                    )
                }
            }

            db.discovery_suggestionsQueries.deleteAllByMedia(token.mediaType.key)
            accepted.forEachIndexed { index, item ->
                db.discovery_suggestionsQueries.insert(
                    media_type = token.mediaType.key,
                    row_type = item.rowType.key,
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
                    identity_key = item.identityKey,
                    trusted_aliases = item.trustedAliases.joinToString("\n"),
                    release_status = item.releaseStatus?.key,
                    metadata_verified_at = item.metadataVerifiedAt,
                )
            }

            val outcome = when (prepared) {
                is DiscoveryPreparedPool.VerifiedEmpty -> DiscoveryPublicationOutcome.EXHAUSTED
                is DiscoveryPreparedPool.Fresh -> if (prepared.partial) {
                    DiscoveryPublicationOutcome.PARTIAL
                } else {
                    DiscoveryPublicationOutcome.READY
                }
            }
            val cursors = when (prepared) {
                is DiscoveryPreparedPool.Fresh -> prepared.cursors
                is DiscoveryPreparedPool.VerifiedEmpty -> prepared.cursors
            }
            db.discovery_publicationQueries.commitPublication(
                configuration_key = token.configurationKey,
                committed_at = committedAt,
                outcome = outcome.name,
                published_by_request_id = token.requestId,
                cursors = encodeCursors(cursors),
                media_type = token.mediaType.key,
                owner_request_id = token.requestId,
                owner_revision = token.ownerRevision,
                generation = token.expectedGeneration,
            )
            val updatedPublication = db.discovery_publicationQueries.selectByMedia(token.mediaType.key).executeAsOne()
            val storedRows = db.discovery_suggestionsQueries.selectAllMapped(token.mediaType.key)
            DiscoveryPublishReceipt.Published(
                buildSnapshot(
                    media = token.mediaType,
                    row = updatedPublication,
                    suggestions = storedRows,
                    known = known,
                    memory = memory,
                    hidden = hidden,
                    blacklist = blacklist,
                ),
            )
        }
    }

    override suspend fun nextHomeBatch(
        media: DiscoveryMediaType,
        generation: Long,
        rankedKeys: List<String>,
    ): DiscoveryBatchReceipt = DiscoveryMutationFence.withMutation(media) {
        val known = knownTitlesProvider.snapshot(media)
        handler.await(inTransaction = true) { db ->
            val publication = db.discovery_publicationQueries.selectByMedia(media.key).executeAsOneOrNull()
                ?: return@await DiscoveryBatchReceipt.NeedsSourceRefresh(0L)
            if (publication.generation != generation) {
                return@await DiscoveryBatchReceipt.Superseded(publication.generation)
            }
            if (publication.traversal != DiscoveryTraversalState.VALID.name) {
                return@await DiscoveryBatchReceipt.NeedsSourceRefresh(generation)
            }
            val rows = db.discovery_suggestionsQueries.selectAllMapped(media.key)
            val memory = readMemory(db, media)
            val hidden = readHidden(db, media)
            val blacklist = readBlacklist(db, media)
            val eligible = eligibleSuggestions(media, rows, known, memory, hidden, blacklist)
            val ordered = orderByRankedKeys(eligible, rankedKeys)
            val current = decodeKeys(publication.active_keys)
            val issued = decodeKeys(publication.issued_keys)
            val selection = selectNextDiscoveryBatch(
                ranked = ordered,
                current = current,
                issued = issued,
                exclusions = DiscoveryEligibilitySnapshot(media, now(), emptySet(), emptySet()),
            )
            if (selection.isEmpty()) {
                return@await DiscoveryBatchReceipt.NeedsSourceRefresh(generation)
            }
            val selectedKeys = selection.map { it.identityKey ?: legacyDiscoveryKey(media, it.cleanTitle) }
            db.discovery_publicationQueries.updateHomeBatch(
                active_keys = selectedKeys.joinToString("\n"),
                issued_keys = (issued + selectedKeys).joinToString("\n"),
                media_type = media.key,
            )
            DiscoveryBatchReceipt.Available(
                buildSnapshot(
                    media = media,
                    row = db.discovery_publicationQueries.selectByMedia(media.key).executeAsOne(),
                    suggestions = rows,
                    known = known,
                    memory = memory,
                    hidden = hidden,
                    blacklist = blacklist,
                ),
            )
        }
    }

    override suspend fun reconcileHomeBatch(
        media: DiscoveryMediaType,
        generation: Long,
        rankedKeys: List<String>,
    ): DiscoveryBatchReceipt = DiscoveryMutationFence.withMutation(media) {
        val known = knownTitlesProvider.snapshot(media)
        handler.await(inTransaction = true) { db ->
            val publication = db.discovery_publicationQueries.selectByMedia(media.key).executeAsOneOrNull()
                ?: return@await DiscoveryBatchReceipt.NeedsSourceRefresh(0L)
            if (publication.generation != generation) {
                return@await DiscoveryBatchReceipt.Superseded(publication.generation)
            }
            if (publication.traversal != DiscoveryTraversalState.VALID.name) {
                return@await DiscoveryBatchReceipt.NeedsSourceRefresh(generation)
            }
            val rows = db.discovery_suggestionsQueries.selectAllMapped(media.key)
            val memory = readMemory(db, media)
            val hidden = readHidden(db, media)
            val blacklist = readBlacklist(db, media)
            val eligible = eligibleSuggestions(media, rows, known, memory, hidden, blacklist)
            val ordered = orderByRankedKeys(eligible, rankedKeys)
            val currentKeys = decodeKeys(publication.active_keys)
            val byKey = ordered.associateBy { it.identityKey ?: legacyDiscoveryKey(media, it.cleanTitle) }
            val kept = currentKeys.mapNotNull { byKey[it] }
            val keptKeys = kept.mapTo(HashSet()) { it.identityKey ?: legacyDiscoveryKey(media, it.cleanTitle) }
            val issued = decodeKeys(publication.issued_keys)
            val fill = selectNextDiscoveryBatch(
                ranked = ordered.filter { (it.identityKey ?: legacyDiscoveryKey(media, it.cleanTitle)) !in keptKeys },
                current = emptySet(),
                issued = issued,
                exclusions = DiscoveryEligibilitySnapshot(media, now(), emptySet(), emptySet()),
                limit = (DISCOVERY_HOME_BATCH_SIZE - kept.size).coerceAtLeast(0),
            )
            val updatedActive = kept + fill
            val updatedKeys = updatedActive.map { it.identityKey ?: legacyDiscoveryKey(media, it.cleanTitle) }
            if (updatedKeys != currentKeys) {
                db.discovery_publicationQueries.updateHomeBatch(
                    active_keys = updatedKeys.joinToString("\n"),
                    issued_keys = (issued + updatedKeys).joinToString("\n"),
                    media_type = media.key,
                )
            }
            DiscoveryBatchReceipt.Available(
                buildSnapshot(
                    media = media,
                    row = db.discovery_publicationQueries.selectByMedia(media.key).executeAsOne(),
                    suggestions = rows,
                    known = known,
                    memory = memory,
                    hidden = hidden,
                    blacklist = blacklist,
                ),
            )
        }
    }

    override suspend fun topUpPool(
        media: DiscoveryMediaType,
        expectedGeneration: Long,
        additions: List<DiscoverySuggestion>,
        cursors: List<DiscoverySourceCursor>,
        maxPoolSize: Int,
    ): DiscoveryTopUpReceipt = DiscoveryMutationFence.withMutation(media) {
        val known = knownTitlesProvider.snapshot(media)
        handler.await(inTransaction = true) { db ->
            db.discovery_publicationQueries.ensureRow(media.key)
            val publication = db.discovery_publicationQueries.selectByMedia(media.key).executeAsOne()
            if (publication.generation != expectedGeneration) {
                return@await DiscoveryTopUpReceipt.Superseded(publication.generation)
            }
            if (publication.owner_request_id != null) {
                return@await DiscoveryTopUpReceipt.Busy
            }

            val memory = readMemory(db, media)
            val hidden = readHidden(db, media)
            val blacklist = readBlacklist(db, media)
            val eligibility = buildEligibility(media, known, memory, hidden)

            val rows = db.discovery_suggestionsQueries.selectAllMapped(media.key)
            val eligible = eligibleSuggestions(media, rows, known, memory, hidden, blacklist)
            val room = maxPoolSize - eligible.size
            if (room <= 0) {
                return@await DiscoveryTopUpReceipt.NothingToAdd
            }

            // Prune rows from discovery_suggestions that are consumed or known (not hidden/blocked)
            val knownKeys = known.flatMap { it.asTitleKeys() }.toSet()
            val memoryByKey = memory.associateBy { it.identity.identityKey }
            val prunableRows = rows.filter { row ->
                val mem = row.identityKey?.let { memoryByKey[it] }
                val isConsumed = mem?.consumed?.active == true
                val isKnown = row.identityKeys().any { it in knownKeys }
                isConsumed || isKnown
            }
            prunableRows.forEach { row ->
                db.discovery_suggestionsQueries.deleteById(row.id)
            }
            val remainingRows = if (prunableRows.isEmpty()) {
                rows
            } else {
                rows.filterNot {
                    it.id in
                        prunableRows.map { p -> p.id }
                }
            }

            val oldPoolKeys = remainingRows.flatMap { it.identityKeys() }.toSet()
            val accepted = prepareCandidates(
                media = media,
                suggestions = additions,
                oldPoolKeys = oldPoolKeys,
                eligibility = eligibility,
                blacklist = blacklist,
            ).take(room)

            if (accepted.isEmpty()) {
                return@await DiscoveryTopUpReceipt.NothingToAdd
            }

            val maxPosition = remainingRows.maxOfOrNull { it.position } ?: 0L
            accepted.forEachIndexed { index, item ->
                db.discovery_suggestionsQueries.insert(
                    media_type = media.key,
                    row_type = item.rowType.key,
                    title = item.title,
                    clean_title = item.cleanTitle,
                    cover_url = item.coverUrl,
                    reason = item.reason,
                    seed_title = item.seedTitle,
                    provider = item.provider,
                    score = item.score,
                    position = maxPosition + 1L + index,
                    created_at = item.createdAt,
                    source_id = item.sourceId,
                    source_url = item.sourceUrl,
                    genres = item.genres.joinToString(","),
                    identity_key = item.identityKey,
                    trusted_aliases = item.trustedAliases.joinToString("\n"),
                    release_status = item.releaseStatus?.key,
                    metadata_verified_at = item.metadataVerifiedAt,
                )
            }

            val existingCursors = decodeCursors(publication.cursors)
            val mergedCursors = (
                existingCursors.associateBy { "${it.sourceId}:${it.queryKey}" } +
                    cursors.associateBy { "${it.sourceId}:${it.queryKey}" }
                ).values.toList()

            db.discovery_publicationQueries.updateCursors(encodeCursors(mergedCursors), media.key)
            bumpProjection(db, media)

            val updatedRows = db.discovery_suggestionsQueries.selectAllMapped(media.key)
            val updatedPub = db.discovery_publicationQueries.selectByMedia(media.key).executeAsOne()
            val snapshot = buildSnapshot(media, updatedPub, updatedRows, known, memory, hidden, blacklist)
            DiscoveryTopUpReceipt.ToppedUp(
                added = accepted.size,
                eligibleAfter = snapshot.eligibleSuggestions.size,
                snapshot = snapshot,
            )
        }
    }

    override suspend fun recordTaste(
        identity: DiscoveryTitleIdentity,
        kind: DiscoverySignalType,
        metadata: DiscoveryTasteMetadata,
    ): DiscoveryPositiveReceipt? {
        if (kind !in POSITIVE_KINDS) return null
        return handler.await(inTransaction = true) { db ->
            val resolved = resolveIdentity(identity)
            val storageKey = canonicalStorageKey(resolved.identityKey)
            val existing = db.discovery_signalsQueries
                .selectByTitle(resolved.mediaType.key, storageKey, ::signalMapper)
                .executeAsOneOrNull()
            val voteAt = now()
            val previousPositive = existing?.positiveKind
            val effectiveKind = when {
                previousPositive == null -> kind
                previousPositive == DiscoverySignalType.LIKE && kind == DiscoverySignalType.CLICK -> previousPositive
                previousPositive == DiscoverySignalType.ADD && kind != DiscoverySignalType.ADD -> previousPositive
                kind.ordinal >= previousPositive.ordinal -> kind
                else -> previousPositive
            }
            val refreshVote = previousPositive == null || kind != DiscoverySignalType.CLICK
            val positiveCreatedAt = if (refreshVote) voteAt else existing?.positiveCreatedAt ?: voteAt
            val hideActive = existing?.signalType == DiscoverySignalType.HIDE
            db.discovery_signalsQueries.upsert(
                media_type = resolved.mediaType.key,
                clean_title = storageKey,
                title = metadata.title.ifBlank { resolved.title },
                signal = if (hideActive) DiscoverySignalType.HIDE.key else effectiveKind.key,
                weight = if (hideActive) DiscoverySignalType.HIDE.weight else effectiveKind.weight,
                genres = metadata.genres.joinToString(","),
                provider = metadata.provider,
                source_key = metadata.sourceKey,
                created_at = if (hideActive) existing?.createdAt ?: voteAt else positiveCreatedAt,
                primary_clean_title = metadata.primaryCleanTitle.ifBlank { resolved.primaryCleanTitle },
                identity_key = resolved.identityKey,
                source_id = metadata.sourceId ?: resolved.sourceId,
                source_url = metadata.sourceUrl ?: resolved.sourceUrl,
                trusted_aliases = metadata.trustedAliases.joinToString("\n"),
                metadata_changed_at = metadata.changedAt,
                positive_signal = effectiveKind.key,
                positive_created_at = positiveCreatedAt,
            )
            enforceSignalCaps(db.discovery_signalsQueries, resolved.mediaType)
            bumpProjection(db, resolved.mediaType)
            DiscoveryPositiveReceipt(
                identityKey = resolved.identityKey,
                effectiveKind = effectiveKind,
                positiveCreatedAt = positiveCreatedAt,
            )
        }
    }

    override suspend fun enrichTasteMetadata(
        media: DiscoveryMediaType,
        identityKey: String,
        expectedPositiveAt: Long,
        metadata: DiscoveryTasteMetadata,
    ): Boolean = handler.await(inTransaction = true) { db ->
        val storageKey = canonicalStorageKey(identityKey)
        val existing = db.discovery_signalsQueries
            .selectByTitle(media.key, storageKey, ::signalMapper)
            .executeAsOneOrNull()
            ?: return@await false
        if (existing.positiveKind == null || existing.positiveCreatedAt != expectedPositiveAt) {
            return@await false
        }
        db.discovery_signalsQueries.updateMetadataIfMatchesCreatedAt(
            genres = metadata.genres.joinToString(","),
            provider = metadata.provider,
            source_key = metadata.sourceKey,
            trusted_aliases = metadata.trustedAliases.joinToString("\n"),
            source_id = metadata.sourceId,
            source_url = metadata.sourceUrl,
            identity_key = identityKey,
            metadata_changed_at = metadata.changedAt,
            media_type = media.key,
            clean_title = storageKey,
            positive_created_at = expectedPositiveAt,
        )
        bumpProjection(db, media)
        true
    }

    override suspend fun getTasteRecords(media: DiscoveryMediaType): List<DiscoveryTasteRecord> =
        handler.awaitList { db -> db.discovery_signalsQueries.selectAllByMedia(media.key, ::signalMapper) }
            .mapNotNull(::toTasteRecord)

    override suspend fun recordFact(identity: DiscoveryTitleIdentity, kind: DiscoveryFactKind) {
        val resolved = resolveIdentity(identity)
        val changedAt = now()
        handler.await(inTransaction = true) { db ->
            db.discovery_title_memoryQueries.ensureRow(
                media_type = resolved.mediaType.key,
                identity_key = resolved.identityKey,
                title = resolved.title,
                primary_clean_title = resolved.primaryCleanTitle,
                source_id = resolved.sourceId,
                source_url = resolved.sourceUrl,
                trusted_aliases = resolved.trustedAliases.joinToString("\n"),
                metadata_changed_at = resolved.metadataChangedAt,
            )
            setFact(db, resolved, kind, active = true, changedAt = changedAt)
            bumpProjection(db, resolved.mediaType)
        }
    }

    override suspend fun clearFact(media: DiscoveryMediaType, key: String, kind: DiscoveryFactKind) {
        val changedAt = now()
        val identity = DiscoveryTitleIdentity(
            mediaType = media,
            identityKey = key,
            title = key,
            primaryCleanTitle = key,
        )
        handler.await(inTransaction = true) { db ->
            db.discovery_title_memoryQueries.ensureRow(
                media_type = media.key,
                identity_key = key,
                title = key,
                primary_clean_title = key,
                source_id = null,
                source_url = null,
                trusted_aliases = "",
                metadata_changed_at = changedAt,
            )
            setFact(db, identity, kind, active = false, changedAt = changedAt)
            bumpProjection(db, media)
        }
    }

    override suspend fun clearTaste(media: DiscoveryMediaType?) {
        val timestamp = now()
        handler.await(inTransaction = true) { db ->
            if (media == null) {
                db.discovery_signalsQueries.deleteAll()
                DiscoveryMediaType.entries.forEach { type ->
                    db.discovery_publicationQueries.ensureRow(type.key)
                    db.discovery_publicationQueries.setTasteResetAt(timestamp, type.key)
                }
            } else {
                db.discovery_signalsQueries.deleteAllByMedia(media.key)
                db.discovery_publicationQueries.ensureRow(media.key)
                db.discovery_publicationQueries.setTasteResetAt(timestamp, media.key)
                bumpProjection(db, media)
            }
        }
    }

    override suspend fun clearRetirementBlocks(): Int = handler.await(inTransaction = true) { db ->
        val currentTime = now()
        val totalBlocked = DiscoveryMediaType.entries.sumOf { media ->
            db.discovery_title_memoryQueries.countActiveBlocked(media.key, currentTime).executeAsOne()
        }.toInt()
        db.discovery_title_memoryQueries.clearAllBlocks()
        DiscoveryMediaType.entries.forEach { media ->
            bumpProjection(db, media)
        }
        totalBlocked
    }

    override suspend fun restoreTitleMemory(records: List<DiscoveryTitleMemory>) {
        if (records.isEmpty()) return
        handler.await(inTransaction = true) { db ->
            records.forEach { record ->
                val identity = record.identity
                db.discovery_title_memoryQueries.ensureRow(
                    identity.mediaType.key,
                    identity.identityKey,
                    identity.title,
                    identity.primaryCleanTitle,
                    identity.sourceId,
                    identity.sourceUrl,
                    identity.trustedAliases.joinToString("\n"),
                    identity.metadataChangedAt,
                )
                setFact(db, identity, DiscoveryFactKind.OPENED, record.opened.active, record.opened.changedAt)
                setFact(db, identity, DiscoveryFactKind.STARTED, record.started.active, record.started.changedAt)
                setFact(
                    db,
                    identity,
                    DiscoveryFactKind.EXPLICIT_CONSUMED,
                    record.consumed.active,
                    record.consumed.changedAt,
                )
                if (record.blockedUntil > 0L) {
                    val clampedBlockedUntil = minOf(
                        record.blockedUntil,
                        now() + tachiyomi.domain.discovery.model.DISCOVERY_RETIRE_MS,
                    )
                    db.discovery_title_memoryQueries.retireBlocked(
                        clampedBlockedUntil,
                        identity.mediaType.key,
                        identity.identityKey,
                    )
                }
                bumpProjection(db, identity.mediaType)
            }
        }
    }

    override suspend fun restoreTasteRecords(records: List<DiscoveryTasteRecord>) {
        if (records.isEmpty()) return
        handler.await(inTransaction = true) { db ->
            records.forEach { record ->
                db.discovery_publicationQueries.ensureRow(record.mediaType.key)
                val resetAt = db.discovery_publicationQueries
                    .selectByMedia(record.mediaType.key)
                    .executeAsOne()
                    .taste_reset_at
                if ((record.positive?.createdAt ?: 0L) in 1 until resetAt) return@forEach
                if ((record.negative?.createdAt ?: 0L) in 1 until resetAt) return@forEach
                val storageKey = canonicalStorageKey(record.identityKey)
                val existing = db.discovery_signalsQueries
                    .selectByTitle(record.mediaType.key, storageKey, ::signalMapper)
                    .executeAsOneOrNull()
                val existingNegativeAt = existing?.takeIf { it.signalType == DiscoverySignalType.HIDE }?.createdAt ?: 0L
                val positive = listOfNotNull(
                    existing?.positiveKind?.let { it to existing.positiveCreatedAt },
                    record.positive?.let { it.kind to it.createdAt },
                ).maxByOrNull { it.second }
                val negativeAt = maxOf(existingNegativeAt, record.negative?.createdAt ?: 0L)
                val hideActive = negativeAt > 0L && negativeAt >= (positive?.second ?: 0L)
                val effectiveKind = if (hideActive) DiscoverySignalType.HIDE else positive?.first
                db.discovery_signalsQueries.upsert(
                    media_type = record.mediaType.key,
                    clean_title = storageKey,
                    title = record.metadata.title,
                    signal = effectiveKind?.key ?: DiscoverySignalType.CLICK.key,
                    weight = effectiveKind?.weight ?: 0.0,
                    genres = record.metadata.genres.joinToString(","),
                    provider = record.metadata.provider,
                    source_key = record.metadata.sourceKey,
                    created_at = if (hideActive) negativeAt else positive?.second ?: 0L,
                    primary_clean_title = record.metadata.primaryCleanTitle,
                    identity_key = record.identityKey,
                    source_id = record.metadata.sourceId,
                    source_url = record.metadata.sourceUrl,
                    trusted_aliases = record.metadata.trustedAliases.joinToString("\n"),
                    metadata_changed_at = maxOf(existing?.metadataChangedAt ?: 0L, record.metadata.changedAt),
                    positive_signal = positive?.first?.key,
                    positive_created_at = positive?.second ?: 0L,
                )
                bumpProjection(db, record.mediaType)
            }
        }
    }

    override suspend fun restoreTasteResetEpochs(epochs: Map<DiscoveryMediaType, Long>) {
        if (epochs.isEmpty()) return
        handler.await(inTransaction = true) { db ->
            epochs.forEach { (media, epoch) ->
                db.discovery_publicationQueries.ensureRow(media.key)
                val current = db.discovery_publicationQueries.selectByMedia(media.key).executeAsOne().taste_reset_at
                if (epoch > current) {
                    db.discovery_publicationQueries.setTasteResetAt(epoch, media.key)
                }
            }
        }
    }

    // ==================== Helpers ====================

    private fun setFact(
        db: tachiyomi.data.Database,
        identity: DiscoveryTitleIdentity,
        kind: DiscoveryFactKind,
        active: Boolean,
        changedAt: Long,
    ) {
        val flag = if (active) 1L else 0L
        when (kind) {
            DiscoveryFactKind.OPENED -> db.discovery_title_memoryQueries.setOpenedFact(
                flag,
                changedAt,
                identity.mediaType.key,
                identity.identityKey,
                changedAt,
            )

            DiscoveryFactKind.STARTED -> db.discovery_title_memoryQueries.setStartedFact(
                flag,
                changedAt,
                identity.mediaType.key,
                identity.identityKey,
                changedAt,
            )

            DiscoveryFactKind.EXPLICIT_CONSUMED -> db.discovery_title_memoryQueries.setConsumedFact(
                flag,
                changedAt,
                identity.mediaType.key,
                identity.identityKey,
                changedAt,
            )
        }
    }

    private fun bumpProjection(db: tachiyomi.data.Database, media: DiscoveryMediaType) {
        db.discovery_publicationQueries.ensureRow(media.key)
        db.discovery_publicationQueries.bumpProjection(media.key)
    }

    private fun settlePublicationRow(media: DiscoveryMediaType, row: Discovery_publication?): Discovery_publication =
        row ?: Discovery_publication(
            media_type = media.key,
            generation = 0L,
            projection_version = 0L,
            configuration_key = "",
            desired_configuration_key = "",
            published_by_request_id = null,
            committed_at = 0L,
            outcome = null,
            owner_request_id = null,
            owner_revision = 0L,
            active_keys = "",
            issued_keys = "",
            cursors = "",
            home_batch_version = 0L,
            taste_reset_at = 0L,
            backfill_version = 0L,
            traversal = DiscoveryTraversalState.VALID.name,
        )

    private fun readMemory(db: tachiyomi.data.Database, media: DiscoveryMediaType): List<DiscoveryTitleMemory> =
        db.discovery_title_memoryQueries.selectAllByMedia(media.key, ::memoryMapper).executeAsList()

    private fun readHidden(db: tachiyomi.data.Database, media: DiscoveryMediaType): Set<String> =
        db.discovery_hiddenQueries.selectAllByMedia(media.key) { _, clean_title, _, _, _, _ -> clean_title }
            .executeAsList()
            .toSet()

    private fun readBlacklist(db: tachiyomi.data.Database, media: DiscoveryMediaType): Set<String> =
        db.discovery_blacklist_tagsQueries.selectAllByMedia(media.key) { _, tag, _ -> tag }
            .executeAsList()
            .toSet()

    private fun buildSnapshot(
        media: DiscoveryMediaType,
        row: Discovery_publication?,
        suggestions: List<DiscoverySuggestion>,
        known: Set<DiscoveryTitleIdentity>,
        memory: List<DiscoveryTitleMemory>,
        hidden: Set<String>,
        blacklist: Set<String>,
    ): DiscoveryPublicationSnapshot {
        val publication = settlePublicationRow(media, row)
        val eligibility = buildEligibility(media, known, memory, hidden)
        val blocked = eligibility.excludedIdentityKeys + eligibility.excludedLookupKeys
        val eligible = suggestions.filter { candidate ->
            candidate.mediaType == media &&
                candidate.identityKeys().none { it in blocked } &&
                candidateValidator.isAllowed(candidate, blacklist)
        }
        return DiscoveryPublicationSnapshot(
            mediaType = media,
            generation = publication.generation,
            projectionVersion = publication.projection_version,
            configurationKey = publication.configuration_key,
            desiredConfigurationKey = publication.desired_configuration_key,
            publishedByRequestId = publication.published_by_request_id,
            committedAt = publication.committed_at,
            outcome = publication.outcome?.let { DiscoveryPublicationOutcome.valueOf(it) },
            suggestions = suggestions,
            eligibleSuggestions = eligible,
            homeBatchKeys = decodeKeys(publication.active_keys).toList(),
            homeBatchVersion = publication.home_batch_version,
            issuedKeys = decodeKeys(publication.issued_keys),
            traversal = if (publication.traversal == DiscoveryTraversalState.CORRUPT.name) {
                DiscoveryTraversalState.CORRUPT
            } else {
                DiscoveryTraversalState.VALID
            },
            cursors = decodeCursors(publication.cursors),
        )
    }

    private fun eligibleSuggestions(
        media: DiscoveryMediaType,
        rows: List<DiscoverySuggestion>,
        known: Set<DiscoveryTitleIdentity>,
        memory: List<DiscoveryTitleMemory>,
        hidden: Set<String>,
        blacklist: Set<String>,
    ): List<DiscoverySuggestion> {
        val eligibility = buildEligibility(media, known, memory, hidden)
        val blocked = eligibility.excludedIdentityKeys + eligibility.excludedLookupKeys
        return rows.filter { candidate ->
            candidate.mediaType == media &&
                candidate.identityKeys().none { it in blocked } &&
                candidateValidator.isAllowed(candidate, blacklist)
        }
    }

    private fun buildEligibility(
        media: DiscoveryMediaType,
        known: Set<DiscoveryTitleIdentity>,
        memory: List<DiscoveryTitleMemory>,
        hidden: Set<String>,
    ): DiscoveryEligibilitySnapshot {
        val currentTime = now()
        val identityKeys = HashSet<String>()
        val lookupKeys = HashSet<String>()
        known.forEach { identity ->
            identityKeys += identity.identityKey
            identityKeys += identity.trustedAliases
            lookupKeys += legacyDiscoveryKey(identity.mediaType, identity.primaryCleanTitle)
        }
        memory.forEach { record ->
            val excluded = record.opened.active || record.started.active ||
                record.consumed.active || record.blockedUntil > currentTime
            if (excluded) {
                identityKeys += record.identity.identityKey
                identityKeys += record.identity.trustedAliases
                lookupKeys += legacyDiscoveryKey(record.identity.mediaType, record.identity.primaryCleanTitle)
            }
        }
        hidden.forEach { title ->
            identityKeys += title
            lookupKeys += legacyDiscoveryKey(media, title)
        }
        return DiscoveryEligibilitySnapshot(
            mediaType = media,
            capturedAt = currentTime,
            excludedIdentityKeys = identityKeys,
            excludedLookupKeys = lookupKeys,
        )
    }

    private fun prepareCandidates(
        media: DiscoveryMediaType,
        suggestions: List<DiscoverySuggestion>,
        oldPoolKeys: Set<String>,
        eligibility: DiscoveryEligibilitySnapshot,
        blacklist: Set<String>,
    ): List<DiscoverySuggestion> {
        val blocked = HashSet<String>(oldPoolKeys)
        blocked += eligibility.excludedIdentityKeys
        blocked += eligibility.excludedLookupKeys
        val seen = HashSet<String>()
        val accepted = ArrayList<DiscoverySuggestion>(suggestions.size)
        for (candidate in suggestions) {
            if (candidate.mediaType != media) continue
            if (!candidateValidator.isAllowed(candidate, blacklist)) continue
            val resolved = candidate.copy(
                identityKey = candidate.identityKey ?: canonicalDiscoveryKey(
                    media,
                    candidate.sourceId,
                    candidate.sourceUrl,
                    candidate.cleanTitle,
                ),
            )
            val keys = resolved.identityKeys()
            if (keys.any { it in blocked }) continue
            if (keys.any { it in seen }) continue
            seen += keys
            accepted += resolved
        }
        return accepted
    }

    private fun isVerifiedEmpty(prepared: DiscoveryPreparedPool.VerifiedEmpty): Boolean {
        if (prepared.expectedQueryKeys.isEmpty()) return false
        if (prepared.evidence.map { it.queryKey }.toSet() != prepared.expectedQueryKeys) return false
        return prepared.evidence.all {
            it.transportOk && it.rawEnd && it.pending == 0 && it.unattempted == 0 &&
                it.metadataFailures == 0 && !it.budgetStopped && !it.duplicatePageStopped
        }
    }

    private fun orderByRankedKeys(
        eligible: List<DiscoverySuggestion>,
        rankedKeys: List<String>,
    ): List<DiscoverySuggestion> {
        if (rankedKeys.isEmpty()) return eligible
        val byKey = HashMap<String, DiscoverySuggestion>()
        eligible.forEach { candidate ->
            candidate.identityKeys().forEach { key -> byKey.putIfAbsent(key, candidate) }
        }
        return rankedKeys.mapNotNull { byKey[it] }.distinctBy { it.id }
    }

    private fun identityOf(media: DiscoveryMediaType, suggestion: DiscoverySuggestion): DiscoveryTitleIdentity {
        val cleanTitle = suggestion.cleanTitle
        return DiscoveryTitleIdentity(
            mediaType = media,
            identityKey = suggestion.identityKey
                ?: canonicalDiscoveryKey(media, suggestion.sourceId, suggestion.sourceUrl, cleanTitle),
            title = suggestion.title,
            primaryCleanTitle = cleanTitle,
            sourceId = suggestion.sourceId,
            sourceUrl = suggestion.sourceUrl,
            trustedAliases = suggestion.trustedAliases,
            metadataChangedAt = suggestion.metadataVerifiedAt ?: 0L,
        )
    }

    private fun memoryMapper(
        mediaType: String,
        identityKey: String,
        title: String,
        primaryCleanTitle: String,
        sourceId: Long?,
        sourceUrl: String?,
        trustedAliases: String?,
        metadataChangedAt: Long,
        openedActive: Long,
        openedChangedAt: Long,
        startedActive: Long,
        startedChangedAt: Long,
        consumedActive: Long,
        consumedChangedAt: Long,
        blockedUntil: Long,
    ): DiscoveryTitleMemory = DiscoveryTitleMemory(
        identity = DiscoveryTitleIdentity(
            mediaType = DiscoveryMediaType.fromKey(mediaType) ?: DiscoveryMediaType.ANIME,
            identityKey = identityKey,
            title = title,
            primaryCleanTitle = primaryCleanTitle,
            sourceId = sourceId,
            sourceUrl = sourceUrl,
            trustedAliases = DiscoveryMapper.splitAliases(trustedAliases),
            metadataChangedAt = metadataChangedAt,
        ),
        opened = DiscoveryFactState(openedActive != 0L, openedChangedAt),
        started = DiscoveryFactState(startedActive != 0L, startedChangedAt),
        consumed = DiscoveryFactState(consumedActive != 0L, consumedChangedAt),
        blockedUntil = blockedUntil,
    )

    private fun toTasteRecord(signal: DiscoverySignal): DiscoveryTasteRecord? {
        val identityKey = signal.identityKey ?: return null
        return DiscoveryTasteRecord(
            mediaType = signal.mediaType,
            identityKey = identityKey,
            positive = signal.positiveKind?.let { DiscoveryPositiveVote(it, signal.positiveCreatedAt) },
            negative = if (signal.signalType == DiscoverySignalType.HIDE) {
                DiscoveryNegativeVote(signal.createdAt)
            } else {
                null
            },
            metadata = DiscoveryTasteMetadata(
                title = signal.title,
                primaryCleanTitle = signal.cleanTitle,
                genres = signal.genres,
                provider = signal.provider,
                sourceKey = signal.sourceKey,
                sourceId = signal.sourceId,
                sourceUrl = signal.sourceUrl,
                trustedAliases = signal.trustedAliases,
                changedAt = signal.metadataChangedAt,
            ),
        )
    }

    private fun encodeCursors(cursors: List<DiscoverySourceCursor>): String =
        if (cursors.isEmpty()) "" else json.encodeToString(cursors)

    private fun decodeCursors(raw: String?): List<DiscoverySourceCursor> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<DiscoverySourceCursor>>(raw) }.getOrDefault(emptyList())
    }

    private fun decodeKeys(raw: String?): Set<String> =
        raw.orEmpty().splitToSequence("\n").mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()

    private fun enforceSignalCaps(
        queries: Discovery_signalsQueries,
        mediaType: DiscoveryMediaType,
    ) {
        val liveCount = queries.countLiveByMedia(mediaType.key).executeAsOne()
        if (liveCount > SIGNALS_CAP_PER_MEDIA) {
            queries.evictOldestLive(mediaType.key, mediaType.key, liveCount - SIGNALS_CAP_PER_MEDIA)
        }
        val consumedCount = queries.countConsumedByMedia(mediaType.key).executeAsOne()
        if (consumedCount > CONSUMED_CAP_PER_MEDIA) {
            queries.evictOldestConsumed(mediaType.key, mediaType.key, consumedCount - CONSUMED_CAP_PER_MEDIA)
        }
    }

    private companion object {
        const val SIGNALS_CAP_PER_MEDIA = 500L
        const val CONSUMED_CAP_PER_MEDIA = 5000L
        const val STORAGE_PREFIX = "@D2/"
        val POSITIVE_KINDS = setOf(DiscoverySignalType.CLICK, DiscoverySignalType.LIKE, DiscoverySignalType.ADD)
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
