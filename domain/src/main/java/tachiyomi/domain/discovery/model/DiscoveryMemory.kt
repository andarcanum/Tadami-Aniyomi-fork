package tachiyomi.domain.discovery.model

/** Canonical identity of one work inside a media type. */
data class DiscoveryTitleIdentity(
    val mediaType: DiscoveryMediaType,
    val identityKey: String,
    val title: String,
    val primaryCleanTitle: String,
    val sourceId: Long? = null,
    val sourceUrl: String? = null,
    val trustedAliases: Set<String> = emptySet(),
    val metadataChangedAt: Long = 0L,
)

/** Interaction facts are independent of taste and of the retirement timer. */
enum class DiscoveryFactKind {
    OPENED,
    STARTED,
    EXPLICIT_CONSUMED,
}

data class DiscoveryFactState(
    val active: Boolean,
    val changedAt: Long,
)

data class DiscoveryTitleMemory(
    val identity: DiscoveryTitleIdentity,
    val opened: DiscoveryFactState = DiscoveryFactState(active = false, changedAt = 0L),
    val started: DiscoveryFactState = DiscoveryFactState(active = false, changedAt = 0L),
    val consumed: DiscoveryFactState = DiscoveryFactState(active = false, changedAt = 0L),
    val blockedUntil: Long = 0L,
)

data class DiscoveryPositiveVote(
    val kind: DiscoverySignalType,
    val createdAt: Long,
)

data class DiscoveryNegativeVote(
    val createdAt: Long,
)

data class DiscoveryTasteMetadata(
    val title: String,
    val primaryCleanTitle: String,
    val genres: List<String> = emptyList(),
    val provider: String? = null,
    val sourceKey: String? = null,
    val sourceId: Long? = null,
    val sourceUrl: String? = null,
    val trustedAliases: Set<String> = emptySet(),
    val changedAt: Long = 0L,
)

data class DiscoveryTasteRecord(
    val mediaType: DiscoveryMediaType,
    val identityKey: String,
    val positive: DiscoveryPositiveVote? = null,
    val negative: DiscoveryNegativeVote? = null,
    val metadata: DiscoveryTasteMetadata,
)

data class DiscoveryPositiveReceipt(
    val identityKey: String,
    val effectiveKind: DiscoverySignalType,
    val positiveCreatedAt: Long,
)
