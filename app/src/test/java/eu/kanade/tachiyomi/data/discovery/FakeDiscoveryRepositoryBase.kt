package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.domain.discovery.model.DiscoveryBatchReceipt
import tachiyomi.domain.discovery.model.DiscoveryEligibilitySnapshot
import tachiyomi.domain.discovery.model.DiscoveryFactKind
import tachiyomi.domain.discovery.model.DiscoveryGenerationToken
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPositiveReceipt
import tachiyomi.domain.discovery.model.DiscoveryPreparedPool
import tachiyomi.domain.discovery.model.DiscoveryPublicationSnapshot
import tachiyomi.domain.discovery.model.DiscoveryPublishReceipt
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoveryTasteMetadata
import tachiyomi.domain.discovery.model.DiscoveryTasteRecord
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.model.DiscoveryTitleMemory
import tachiyomi.domain.discovery.model.DiscoveryTraversalState
import tachiyomi.domain.discovery.repository.DiscoveryRepository

/**
 * Test-only defaults for the publication/memory API. Production code must not
 * rely on no-op implementations.
 */
abstract class FakeDiscoveryRepositoryBase : DiscoveryRepository {

    protected fun emptyPublication(media: DiscoveryMediaType): DiscoveryPublicationSnapshot =
        DiscoveryPublicationSnapshot(
            mediaType = media,
            generation = 0L,
            projectionVersion = 0L,
            configurationKey = "",
            desiredConfigurationKey = "",
            publishedByRequestId = null,
            committedAt = 0L,
            outcome = null,
            suggestions = emptyList(),
            eligibleSuggestions = emptyList(),
            homeBatchKeys = emptyList(),
            homeBatchVersion = 0L,
            issuedKeys = emptySet(),
            traversal = DiscoveryTraversalState.VALID,
            cursors = emptyList(),
        )

    override fun observePublication(media: DiscoveryMediaType): Flow<DiscoveryPublicationSnapshot> =
        MutableStateFlow(emptyPublication(media))

    override fun observeTitleMemory(media: DiscoveryMediaType): Flow<List<DiscoveryTitleMemory>> =
        MutableStateFlow(emptyList())

    override fun observeTaste(media: DiscoveryMediaType): Flow<List<DiscoveryTasteRecord>> =
        MutableStateFlow(emptyList())

    override suspend fun publication(media: DiscoveryMediaType): DiscoveryPublicationSnapshot =
        emptyPublication(media)

    override suspend fun eligibility(media: DiscoveryMediaType): DiscoveryEligibilitySnapshot =
        DiscoveryEligibilitySnapshot(media, 0L)

    override suspend fun resolveIdentity(identity: DiscoveryTitleIdentity): DiscoveryTitleIdentity = identity

    override suspend fun installConfigurationKey(media: DiscoveryMediaType, key: String) = Unit

    override suspend fun reserveGeneration(
        media: DiscoveryMediaType,
        requestId: String,
        expectedGeneration: Long,
        configurationKey: String,
    ): DiscoveryGenerationToken? = null

    override suspend fun revokeGeneration(media: DiscoveryMediaType, requestId: String) = Unit

    override suspend fun publishGeneration(
        token: DiscoveryGenerationToken,
        prepared: DiscoveryPreparedPool,
    ): DiscoveryPublishReceipt = DiscoveryPublishReceipt.NoAdmissibleItems

    override suspend fun nextHomeBatch(
        media: DiscoveryMediaType,
        generation: Long,
        rankedKeys: List<String>,
    ): DiscoveryBatchReceipt = DiscoveryBatchReceipt.NeedsSourceRefresh(generation)

    override suspend fun reconcileHomeBatch(
        media: DiscoveryMediaType,
        generation: Long,
        rankedKeys: List<String>,
    ): DiscoveryBatchReceipt = DiscoveryBatchReceipt.NeedsSourceRefresh(generation)

    override suspend fun recordTaste(
        identity: DiscoveryTitleIdentity,
        kind: DiscoverySignalType,
        metadata: DiscoveryTasteMetadata,
    ): DiscoveryPositiveReceipt? = null

    override suspend fun enrichTasteMetadata(
        media: DiscoveryMediaType,
        identityKey: String,
        expectedPositiveAt: Long,
        metadata: DiscoveryTasteMetadata,
    ): Boolean = false

    override suspend fun getTasteRecords(media: DiscoveryMediaType): List<DiscoveryTasteRecord> = emptyList()

    override suspend fun recordFact(identity: DiscoveryTitleIdentity, kind: DiscoveryFactKind) = Unit

    override suspend fun clearFact(media: DiscoveryMediaType, key: String, kind: DiscoveryFactKind) = Unit

    override suspend fun clearTaste(media: DiscoveryMediaType?) = Unit

    override suspend fun restoreTitleMemory(records: List<DiscoveryTitleMemory>) = Unit

    override suspend fun restoreTasteRecords(records: List<DiscoveryTasteRecord>) = Unit

    override suspend fun restoreTasteResetEpochs(epochs: Map<DiscoveryMediaType, Long>) = Unit
    override fun subscribe(
        mediaType: DiscoveryMediaType,
    ): Flow<List<tachiyomi.domain.discovery.model.DiscoverySuggestion>> =
        MutableStateFlow(emptyList())

    override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> = MutableStateFlow(emptySet())

    override suspend fun replaceRows(
        mediaType: DiscoveryMediaType,
        rowType: tachiyomi.domain.discovery.model.DiscoveryRowType,
        items: List<tachiyomi.domain.discovery.model.DiscoverySuggestion>,
    ) = Unit

    override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String> = emptySet()
    override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) = Unit
    override suspend fun unhide(mediaType: DiscoveryMediaType, cleanTitle: String) = Unit
    override suspend fun clearHidden(mediaType: DiscoveryMediaType) = Unit
    override suspend fun lastUpdatedAt(mediaType: DiscoveryMediaType): Long? = null
    override fun subscribeBlacklist(mediaType: DiscoveryMediaType): Flow<Set<String>> = MutableStateFlow(emptySet())
    override suspend fun getBlacklistedTags(mediaType: DiscoveryMediaType): Set<String> = emptySet()
    override suspend fun blacklistTag(mediaType: DiscoveryMediaType, tag: String) = Unit
    override suspend fun unblacklistTag(mediaType: DiscoveryMediaType, tag: String) = Unit
    override suspend fun clearBlacklist(mediaType: DiscoveryMediaType) = Unit
    override suspend fun getHiddenEntries(
        mediaType: DiscoveryMediaType,
    ): List<tachiyomi.domain.discovery.model.DiscoveryHiddenEntry> =
        emptyList()
    override suspend fun getBlacklistEntries(
        mediaType: DiscoveryMediaType,
    ): List<tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry> =
        emptyList()
    override suspend fun restoreHiddenEntries(
        mediaType: DiscoveryMediaType,
        entries: List<tachiyomi.domain.discovery.model.DiscoveryHiddenEntry>,
    ) = Unit
    override suspend fun restoreBlacklistEntries(
        mediaType: DiscoveryMediaType,
        entries: List<tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry>,
    ) = Unit
    override suspend fun getShownTitles(mediaType: DiscoveryMediaType, windowMillis: Long): Set<String> = emptySet()
    override suspend fun getShownTitlesWithTimestamp(
        mediaType: DiscoveryMediaType,
        windowMillis: Long,
    ): Map<String, Long> = emptyMap()
    override suspend fun getShownWithCount(
        mediaType: DiscoveryMediaType,
        windowMillis: Long,
    ): List<Triple<String, Long, Int>> = emptyList()
    override suspend fun recordExposure(
        mediaType: DiscoveryMediaType,
        cleanTitles: Collection<String>,
        timestamp: Long,
    ) = Unit
    override suspend fun suppressRecentlyConsumed(
        mediaType: DiscoveryMediaType,
        cleanTitles: Collection<String>,
        timestamp: Long,
    ) = Unit
    override suspend fun getRecentExclusions(
        mediaType: DiscoveryMediaType,
        windowMillis: Long,
    ): Set<String> = emptySet()
    override suspend fun getRecentExclusionsWithTimestamp(
        mediaType: DiscoveryMediaType,
        windowMillis: Long,
    ): Map<String, Long> = emptyMap()
    override suspend fun markShown(
        mediaType: DiscoveryMediaType,
        cleanTitles: Collection<String>,
        timestamp: Long,
    ) = Unit
    override suspend fun clearShown(mediaType: DiscoveryMediaType) = Unit
    override suspend fun hasUnboundSourceRows(): Boolean = false
    override suspend fun getSignals(
        mediaType: DiscoveryMediaType,
    ): List<tachiyomi.domain.discovery.model.DiscoverySignal> = emptyList()
    override fun subscribeConsumed(mediaType: DiscoveryMediaType): Flow<Set<String>> = MutableStateFlow(emptySet())
    override suspend fun recordSignal(
        mediaType: DiscoveryMediaType,
        cleanTitle: String,
        title: String,
        signalType: DiscoverySignalType,
        genres: List<String>,
        provider: String?,
        sourceKey: String?,
        timestamp: Long,
    ) = Unit
    override suspend fun enrichSignalMetadataIfCurrent(
        mediaType: DiscoveryMediaType,
        cleanTitle: String,
        expectedCreatedAt: Long,
        genres: List<String>,
        provider: String?,
        sourceKey: String?,
    ): Boolean = false
    override suspend fun removeSignal(mediaType: DiscoveryMediaType, cleanTitle: String) = Unit
    override suspend fun clearSignals(mediaType: DiscoveryMediaType) = Unit
    override suspend fun clearAllSignals() = Unit
    override suspend fun restoreSignals(signals: List<tachiyomi.domain.discovery.model.DiscoverySignal>) = Unit
}
