package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryFactState
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.model.DiscoveryTitleMemory

/**
 * Пользовательские негативные сигналы «Для тебя» в backup (A-цикл):
 * скрытые тайтлы и теговый блэклист. Кэш подборок (discovery_suggestions)
 * не бэкапится — он регенерируем.
 */
@Serializable
data class BackupDiscoveryHidden(
    @ProtoNumber(1) val mediaType: String,
    @ProtoNumber(2) val cleanTitle: String,
    @ProtoNumber(3) val hiddenAt: Long = 0L,
)

@Serializable
data class BackupDiscoveryTag(
    @ProtoNumber(1) val mediaType: String,
    @ProtoNumber(2) val tag: String,
    @ProtoNumber(3) val addedAt: Long = 0L,
)

/** Taste Learning Engine: сигнал-лог вкусов в backup (переживает переустановку). */
@Serializable
data class BackupDiscoverySignal(
    @ProtoNumber(1) val mediaType: String,
    @ProtoNumber(2) val cleanTitle: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val signal: String,
    @ProtoNumber(5) val genres: String = "",
    @ProtoNumber(6) val provider: String? = null,
    @ProtoNumber(7) val sourceKey: String? = null,
    @ProtoNumber(8) val createdAt: Long = 0L,
    @ProtoNumber(9) val identityKey: String? = null,
    @ProtoNumber(10) val trustedAliases: String = "",
    @ProtoNumber(11) val sourceId: Long? = null,
    @ProtoNumber(12) val sourceUrl: String? = null,
    @ProtoNumber(13) val metadataChangedAt: Long = 0L,
    @ProtoNumber(14) val positiveSignal: String? = null,
    @ProtoNumber(15) val positiveCreatedAt: Long = 0L,
)

@Serializable
data class BackupDiscoveryTitleMemory(
    @ProtoNumber(1) val mediaType: String,
    @ProtoNumber(2) val identityKey: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val primaryCleanTitle: String = "",
    @ProtoNumber(5) val sourceId: Long? = null,
    @ProtoNumber(6) val sourceUrl: String? = null,
    @ProtoNumber(7) val trustedAliases: String = "",
    @ProtoNumber(8) val metadataChangedAt: Long = 0L,
    @ProtoNumber(9) val openedActive: Boolean = false,
    @ProtoNumber(10) val openedChangedAt: Long = 0L,
    @ProtoNumber(11) val startedActive: Boolean = false,
    @ProtoNumber(12) val startedChangedAt: Long = 0L,
    @ProtoNumber(13) val consumedActive: Boolean = false,
    @ProtoNumber(14) val consumedChangedAt: Long = 0L,
    @ProtoNumber(15) val blockedUntil: Long = 0L,
)

@Serializable
data class BackupDiscoveryTasteResetEpoch(
    @ProtoNumber(1) val mediaType: String,
    @ProtoNumber(2) val tasteResetAt: Long = 0L,
)

fun DiscoveryHiddenEntry.toBackupDiscoveryHidden(mediaTypeKey: String) = BackupDiscoveryHidden(
    mediaType = mediaTypeKey,
    cleanTitle = cleanTitle,
    hiddenAt = hiddenAt,
)

fun DiscoveryBlacklistEntry.toBackupDiscoveryTag(mediaTypeKey: String) = BackupDiscoveryTag(
    mediaType = mediaTypeKey,
    tag = tag,
    addedAt = addedAt,
)

fun DiscoverySignal.toBackupDiscoverySignal() = BackupDiscoverySignal(
    mediaType = mediaType.key,
    cleanTitle = cleanTitle,
    title = title,
    signal = signalType.key,
    genres = genres.joinToString(","),
    provider = provider,
    sourceKey = sourceKey,
    createdAt = createdAt,
    identityKey = identityKey,
    trustedAliases = trustedAliases.joinToString("\n"),
    sourceId = sourceId,
    sourceUrl = sourceUrl,
    metadataChangedAt = metadataChangedAt,
    positiveSignal = positiveKind?.key,
    positiveCreatedAt = positiveCreatedAt,
)

fun BackupDiscoverySignal.toDomainSignal(): DiscoverySignal? {
    val media = DiscoveryMediaType.fromKey(mediaType) ?: return null
    val type = DiscoverySignalType.fromKey(signal) ?: return null
    return DiscoverySignal(
        mediaType = media,
        cleanTitle = cleanTitle,
        title = title.ifBlank { cleanTitle },
        signalType = type,
        genres = genres.splitToSequence(",")
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .toList(),
        provider = provider,
        sourceKey = sourceKey,
        createdAt = createdAt,
        identityKey = identityKey?.takeIf { it.isNotBlank() },
        trustedAliases = trustedAliases.splitToSequence("\n")
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .toSet(),
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        metadataChangedAt = if (metadataChangedAt > 0L) metadataChangedAt else createdAt,
        positiveKind = DiscoverySignalType.fromKey(positiveSignal),
        positiveCreatedAt = positiveCreatedAt,
    )
}

fun DiscoveryTitleMemory.toBackupDiscoveryTitleMemory() = BackupDiscoveryTitleMemory(
    mediaType = identity.mediaType.key,
    identityKey = identity.identityKey,
    title = identity.title,
    primaryCleanTitle = identity.primaryCleanTitle,
    sourceId = identity.sourceId,
    sourceUrl = identity.sourceUrl,
    trustedAliases = identity.trustedAliases.joinToString("\n"),
    metadataChangedAt = identity.metadataChangedAt,
    openedActive = opened.active,
    openedChangedAt = opened.changedAt,
    startedActive = started.active,
    startedChangedAt = started.changedAt,
    consumedActive = consumed.active,
    consumedChangedAt = consumed.changedAt,
    blockedUntil = blockedUntil,
)

fun BackupDiscoveryTitleMemory.toDomainTitleMemory(): DiscoveryTitleMemory? {
    val media = DiscoveryMediaType.fromKey(mediaType) ?: return null
    if (identityKey.isBlank()) return null
    val primary = primaryCleanTitle.ifBlank { cleanTitleFallback(title, identityKey) }
    return DiscoveryTitleMemory(
        identity = DiscoveryTitleIdentity(
            mediaType = media,
            identityKey = identityKey,
            title = title.ifBlank { primary },
            primaryCleanTitle = primary,
            sourceId = sourceId,
            sourceUrl = sourceUrl,
            trustedAliases = trustedAliases.splitToSequence("\n")
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .toSet(),
            metadataChangedAt = metadataChangedAt,
        ),
        opened = DiscoveryFactState(openedActive, openedChangedAt),
        started = DiscoveryFactState(startedActive, startedChangedAt),
        consumed = DiscoveryFactState(consumedActive, consumedChangedAt),
        blockedUntil = blockedUntil,
    )
}

private fun cleanTitleFallback(title: String, identityKey: String): String =
    title.trim().lowercase().ifBlank { identityKey }

fun BackupDiscoveryHidden.toDomainEntry() = DiscoveryHiddenEntry(
    cleanTitle = cleanTitle,
    hiddenAt = hiddenAt,
)

fun BackupDiscoveryTag.toDomainEntry() = DiscoveryBlacklistEntry(
    tag = tag,
    addedAt = addedAt,
)
