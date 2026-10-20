package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository

class TasteSignalRecorderAltKeyTest {

    @Test
    fun `consumedAltKey normalizes alt title and skips blanks and duplicates`() {
        consumedAltKey("Kimetsu no Yaiba", DiscoveryMeta(null, emptyList(), "Demon Slayer", null))
            .shouldBe("demon slayer")
        consumedAltKey("Kimetsu no Yaiba", DiscoveryMeta(null, emptyList(), "Kimetsu no Yaiba", null))
            .shouldBe(null)
        consumedAltKey("Kimetsu no Yaiba", DiscoveryMeta(null, emptyList(), "  ", null))
            .shouldBe(null)
        consumedAltKey("Kimetsu no Yaiba", null).shouldBe(null)
    }

    @Test
    fun `recordConsumed writes primary and alt keys`() = runTest {
        val repo = RecordingFakeRepository()
        TasteSignalRecorder.recordConsumed(
            repository = repo,
            mediaType = DiscoveryMediaType.MANGA,
            title = "Клинок, рассекающий демонов",
            sourceId = -1L,
            altMetaResolver = { _, _ -> DiscoveryMeta(null, emptyList(), "Kimetsu no Yaiba", null) },
        )
        repo.recorded.map { it.second }.shouldBe(listOf("клинок рассекающий демонов", "kimetsu no yaiba"))
        repo.recorded.forEach { it.third shouldBe DiscoverySignalType.CONSUMED }
    }

    @Test
    fun `record with consumed item also enriches alt key`() = runTest {
        val repo = RecordingFakeRepository()
        val item = DiscoverySuggestion(
            id = 1L,
            mediaType = DiscoveryMediaType.ANIME,
            rowType = DiscoveryRowType.TREND,
            title = "Kimetsu no Yaiba",
            cleanTitle = "kimetsu no yaiba",
            coverUrl = null,
            reason = null,
            seedTitle = null,
            provider = "anilist",
            score = 0.0,
            position = 0L,
            createdAt = 0L,
        )
        TasteSignalRecorder.record(
            repository = repo,
            item = item,
            signalType = DiscoverySignalType.CONSUMED,
            altMetaResolver = { _, _ -> DiscoveryMeta(null, emptyList(), "Demon Slayer", null) },
        )
        repo.recorded.map { it.second }.shouldBe(listOf("kimetsu no yaiba", "demon slayer"))
    }

    @Test
    fun `non-consumed signals do not hit the resolver`() = runTest {
        val repo = RecordingFakeRepository()
        var resolverCalls = 0
        TasteSignalRecorder.record(
            repository = repo,
            item = suggestionOf("t"),
            signalType = DiscoverySignalType.LIKE,
            altMetaResolver = { _, _ ->
                resolverCalls++
                null
            },
        )
        resolverCalls shouldBe 0
    }

    @Test
    fun `alt resolver cancellation propagates instead of being swallowed`() = runTest {
        val repo = RecordingFakeRepository()
        io.kotest.assertions.throwables.shouldThrow<kotlinx.coroutines.CancellationException> {
            TasteSignalRecorder.recordConsumed(
                repository = repo,
                mediaType = DiscoveryMediaType.MANGA,
                title = "T",
                sourceId = -1L,
                altMetaResolver = { _, _ -> throw kotlinx.coroutines.CancellationException("cancel") },
            )
        }
    }

    @Test
    fun `record returns true on success and false on failure`() = runTest {
        val repo = RecordingFakeRepository()
        val success = TasteSignalRecorder.record(
            repository = repo,
            item = suggestionOf("test"),
            signalType = DiscoverySignalType.LIKE,
        )
        success shouldBe true

        val failingRepo = object : RecordingFakeRepository() {
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
                throw java.io.IOException("db failed")
            }
        }
        val failure = TasteSignalRecorder.record(
            repository = failingRepo,
            item = suggestionOf("test"),
            signalType = DiscoverySignalType.LIKE,
        )
        failure shouldBe false
    }

    @Test
    fun `like signal on item without genres enriches genres via sourceDetailsResolver`() = runTest {
        val repo = RecordingFakeRepository()
        val item = suggestionOf("Solo Leveling").copy(
            sourceId = 42L,
            sourceUrl = "/manga/solo-leveling",
            genres = emptyList(),
        )
        var resolverCalled = false
        val success = TasteSignalRecorder.record(
            repository = repo,
            item = item,
            signalType = DiscoverySignalType.LIKE,
            sourceDetailsResolver = { mediaType, sourceId, sourceUrl ->
                resolverCalled = true
                sourceId shouldBe 42L
                sourceUrl shouldBe "/manga/solo-leveling"
                listOf("Action", "Fantasy")
            },
        )
        success shouldBe true
        resolverCalled shouldBe true
        repo.recordedGenres.last() shouldBe listOf("Action", "Fantasy")
    }

    @Test
    fun `record signal for click enriches empty genres from local repository when available`() = runTest {
        val repo = RecordingFakeRepository()
        val item = suggestionOf("Solo Leveling").copy(
            sourceId = 42L,
            sourceUrl = "/manga/solo-leveling",
            genres = emptyList(),
        )
        var localResolverCalled = false
        var sourceResolverCalled = false
        val success = TasteSignalRecorder.record(
            repository = repo,
            item = item,
            signalType = DiscoverySignalType.CLICK,
            localEntryResolver = { mediaType, sourceId, sourceUrl ->
                localResolverCalled = true
                sourceId shouldBe 42L
                sourceUrl shouldBe "/manga/solo-leveling"
                listOf("Action", "Fantasy")
            },
            sourceDetailsResolver = { _, _, _ ->
                sourceResolverCalled = true
                listOf("NetworkAction")
            },
        )
        success shouldBe true
        localResolverCalled shouldBe true
        sourceResolverCalled shouldBe false
        repo.recordedGenres.last() shouldBe listOf("Action", "Fantasy")
    }

    @Test
    fun `record signal for click does not call sourceDetailsResolver when genres are empty`() = runTest {
        val repo = RecordingFakeRepository()
        val item = suggestionOf("Unknown Manga").copy(
            sourceId = 42L,
            sourceUrl = "/manga/unknown",
            genres = emptyList(),
        )
        var sourceResolverCalled = false
        val success = TasteSignalRecorder.record(
            repository = repo,
            item = item,
            signalType = DiscoverySignalType.CLICK,
            localEntryResolver = { _, _, _ -> emptyList() },
            sourceDetailsResolver = { _, _, _ ->
                sourceResolverCalled = true
                listOf("NetworkAction")
            },
        )
        success shouldBe true
        sourceResolverCalled shouldBe false
        repo.recordedGenres.last() shouldBe emptyList()
    }
}

private fun suggestionOf(title: String) = DiscoverySuggestion(
    id = 1L,
    mediaType = DiscoveryMediaType.ANIME,
    rowType = DiscoveryRowType.TREND,
    title = title,
    cleanTitle = tachiyomi.domain.discovery.model.normalizeDiscoveryTitle(title),
    coverUrl = null,
    reason = null,
    seedTitle = null,
    provider = "anilist",
    score = 0.0,
    position = 0L,
    createdAt = 0L,
)

private open class RecordingFakeRepository : DiscoveryRepository {
    val recorded = mutableListOf<Triple<DiscoveryMediaType, String, DiscoverySignalType>>()
    val recordedGenres = mutableListOf<List<String>>()

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
        recorded += Triple(mediaType, cleanTitle, signalType)
        recordedGenres += genres
    }

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
    override suspend fun restoreHiddenEntries(mediaType: DiscoveryMediaType, entries: List<DiscoveryHiddenEntry>) = Unit
    override suspend fun restoreBlacklistEntries(
        mediaType: DiscoveryMediaType,
        entries: List<DiscoveryBlacklistEntry>,
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
    override suspend fun markShown(
        mediaType: DiscoveryMediaType,
        cleanTitles: Collection<String>,
        timestamp: Long,
    ) = Unit
    override suspend fun clearShown(mediaType: DiscoveryMediaType) = Unit
    override suspend fun hasUnboundSourceRows(): Boolean = false
    override suspend fun getSignals(mediaType: DiscoveryMediaType): List<DiscoverySignal> = emptyList()
    override fun subscribeConsumed(mediaType: DiscoveryMediaType): Flow<Set<String>> = MutableStateFlow(emptySet())
    override suspend fun removeSignal(mediaType: DiscoveryMediaType, cleanTitle: String) = Unit
    override suspend fun clearSignals(mediaType: DiscoveryMediaType) = Unit
    override suspend fun clearAllSignals() = Unit
    override suspend fun restoreSignals(signals: List<DiscoverySignal>) = Unit
}
