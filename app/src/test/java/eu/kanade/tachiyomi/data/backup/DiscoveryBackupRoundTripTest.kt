package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoverySignal
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoverySignal
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.toDomainEntry
import eu.kanade.tachiyomi.data.backup.models.toDomainSignal
import eu.kanade.tachiyomi.data.backup.restore.restorers.DiscoveryRestorer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository

class DiscoveryBackupRoundTripTest {

    @Test
    fun `discovery entries survive proto round trip`() {
        val backup = Backup(
            backupDiscoveryHidden = listOf(
                BackupDiscoveryHidden("anime", "solo leveling", 1_700_000_000_000L),
                BackupDiscoveryHidden("novel", "overlord", 1_700_000_000_001L),
            ),
            backupDiscoveryBlacklistTags = listOf(
                BackupDiscoveryTag("manga", "Гарем", 1_700_000_000_002L),
            ),
        )

        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)

        decoded.backupDiscoveryHidden shouldBe backup.backupDiscoveryHidden
        decoded.backupDiscoveryBlacklistTags shouldBe backup.backupDiscoveryBlacklistTags
    }

    @Test
    fun `empty backup decodes without discovery data`() {
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), Backup())
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        decoded.backupDiscoveryHidden shouldBe emptyList()
        decoded.backupDiscoveryBlacklistTags shouldBe emptyList()
    }

    @Test
    fun `mappers preserve all fields`() {
        val hidden = DiscoveryHiddenEntry("clean title", 42L)
        hidden.toBackupDiscoveryHidden("manga").toDomainEntry() shouldBe hidden
        val tag = DiscoveryBlacklistEntry("Фэнтези", 43L)
        tag.toBackupDiscoveryTag("novel").toDomainEntry() shouldBe tag
    }

    @Test
    fun `restorer groups entries by media type and keeps timestamps`() {
        val repo = FakeDiscoveryRepository()
        val restorer = DiscoveryRestorer(repo)

        runBlocking {
            restorer.restoreDiscovery(
                hidden = listOf(
                    BackupDiscoveryHidden("anime", "a1", 10L),
                    BackupDiscoveryHidden("anime", "a2", 11L),
                    BackupDiscoveryHidden("novel", "n1", 12L),
                    BackupDiscoveryHidden("bogus", "x", 13L),
                ),
                tags = listOf(
                    BackupDiscoveryTag("manga", "t1", 20L),
                ),
            )
        }

        repo.restoredHidden[DiscoveryMediaType.ANIME] shouldBe listOf(
            DiscoveryHiddenEntry("a1", 10L),
            DiscoveryHiddenEntry("a2", 11L),
        )
        repo.restoredHidden[DiscoveryMediaType.NOVEL] shouldBe listOf(DiscoveryHiddenEntry("n1", 12L))
        repo.restoredHidden.containsKey(DiscoveryMediaType.MANGA) shouldBe false
        repo.restoredTags[DiscoveryMediaType.MANGA] shouldBe listOf(DiscoveryBlacklistEntry("t1", 20L))
    }

    @Test
    fun `restorer on empty lists performs no writes`() {
        val repo = FakeDiscoveryRepository()
        runBlocking { DiscoveryRestorer(repo).restoreDiscovery(emptyList(), emptyList()) }
        repo.restoredHidden shouldBe emptyMap()
        repo.restoredTags shouldBe emptyMap()
    }

    @Test
    fun `taste signals survive proto round trip`() {
        val backup = Backup(
            backupDiscoverySignals = listOf(
                BackupDiscoverySignal(
                    mediaType = "novel",
                    cleanTitle = "overlord",
                    title = "Overlord",
                    signal = "like",
                    genres = "fantasy,action",
                    provider = "anilist_trend",
                    sourceKey = "org.example.plugin",
                    createdAt = 1_700_000_000_500L,
                ),
            ),
        )
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        decoded.backupDiscoverySignals shouldBe backup.backupDiscoverySignals
    }

    @Test
    fun `signal mappers preserve fields and drop garbage`() {
        val signal = DiscoverySignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.LIKE,
            genres = listOf("fantasy", "action"),
            provider = "anilist_trend",
            sourceKey = "org.example.plugin",
            createdAt = 42L,
        )
        signal.toBackupDiscoverySignal().toDomainSignal() shouldBe signal

        // Битые ключи (мусорный media/signal) не падают — mаппер возвращает null.
        BackupDiscoverySignal(mediaType = "bogus", cleanTitle = "x", signal = "like")
            .toDomainSignal() shouldBe null
        BackupDiscoverySignal(mediaType = "novel", cleanTitle = "x", signal = "bogus")
            .toDomainSignal() shouldBe null
    }

    @Test
    fun `restorer writes taste signals through repository`() {
        val repo = FakeDiscoveryRepository()
        runBlocking {
            DiscoveryRestorer(repo).restoreDiscovery(
                hidden = emptyList(),
                tags = emptyList(),
                signals = listOf(
                    BackupDiscoverySignal(
                        mediaType = "novel",
                        cleanTitle = "overlord",
                        title = "Overlord",
                        signal = "like",
                        genres = "fantasy",
                        createdAt = 42L,
                    ),
                    BackupDiscoverySignal(mediaType = "bogus", cleanTitle = "x", signal = "like"),
                ),
            )
        }
        repo.restoredSignals shouldBe listOf(
            DiscoverySignal(
                mediaType = DiscoveryMediaType.NOVEL,
                cleanTitle = "overlord",
                title = "Overlord",
                signalType = DiscoverySignalType.LIKE,
                genres = listOf("fantasy"),
                provider = null,
                sourceKey = null,
                createdAt = 42L,
            ),
        )
    }
}

private class FakeDiscoveryRepository : DiscoveryRepository {
    val restoredHidden = mutableMapOf<DiscoveryMediaType, List<DiscoveryHiddenEntry>>()
    val restoredTags = mutableMapOf<DiscoveryMediaType, List<DiscoveryBlacklistEntry>>()

    override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> =
        MutableStateFlow(emptyList())

    override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> = MutableStateFlow(emptySet())

    override suspend fun replaceRows(
        mediaType: DiscoveryMediaType,
        rowType: DiscoveryRowType,
        items: List<DiscoverySuggestion>,
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
    override suspend fun getHiddenEntries(mediaType: DiscoveryMediaType): List<DiscoveryHiddenEntry> = emptyList()
    override suspend fun getBlacklistEntries(mediaType: DiscoveryMediaType): List<DiscoveryBlacklistEntry> = emptyList()

    override suspend fun restoreHiddenEntries(mediaType: DiscoveryMediaType, entries: List<DiscoveryHiddenEntry>) {
        restoredHidden[mediaType] = entries
    }

    override suspend fun restoreBlacklistEntries(
        mediaType: DiscoveryMediaType,
        entries: List<DiscoveryBlacklistEntry>,
    ) {
        restoredTags[mediaType] = entries
    }

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

    val restoredSignals = mutableListOf<DiscoverySignal>()
    override suspend fun getSignals(mediaType: DiscoveryMediaType): List<DiscoverySignal> = emptyList()
    override fun subscribeConsumed(mediaType: DiscoveryMediaType): kotlinx.coroutines.flow.Flow<Set<String>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptySet())
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
    override suspend fun removeSignal(mediaType: DiscoveryMediaType, cleanTitle: String) = Unit
    override suspend fun clearSignals(mediaType: DiscoveryMediaType) = Unit
    override suspend fun clearAllSignals() = Unit
    override suspend fun restoreSignals(signals: List<DiscoverySignal>) {
        restoredSignals += signals
    }
}
