package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.shouldBeLessThan
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

class TasteSignalRecorderNegativeFeedbackTest {

    private class NegativeFeedbackRecordingRepo : FakeDiscoveryRepositoryBase() {
        val hiddenTitles = mutableListOf<String>()
        val recordedSignals = mutableListOf<RecordedSignalEntry>()

        data class RecordedSignalEntry(
            val mediaType: DiscoveryMediaType,
            val cleanTitle: String,
            val signalType: DiscoverySignalType,
            val genres: List<String>,
            val provider: String?,
            val sourceKey: String?,
        )

        override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) {
            hiddenTitles += cleanTitle
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
            recordedSignals += RecordedSignalEntry(mediaType, cleanTitle, signalType, genres, provider, sourceKey)
            return timestamp
        }
    }

    @Test
    fun `record HIDE calls both repository hide and recordSignalWithReceipt with negative weight`() = runTest {
        val repo = NegativeFeedbackRecordingRepo()
        val item = DiscoverySuggestion(
            id = 1L,
            mediaType = DiscoveryMediaType.MANGA,
            rowType = DiscoveryRowType.LIKE,
            title = "Unwanted Horror Manga",
            cleanTitle = "unwanted horror manga",
            coverUrl = "https://example.com/cover.jpg",
            reason = "Horror",
            seedTitle = null,
            provider = "MangaDex",
            score = 8.5,
            position = 0L,
            createdAt = 1_000L,
            sourceId = 12345L,
            sourceUrl = "/manga/12345",
            genres = listOf("Horror", "Gore"),
        )

        val recorded = TasteSignalRecorder.record(
            repository = repo,
            item = item,
            signalType = DiscoverySignalType.HIDE,
        )

        recorded shouldBe true
        repo.hiddenTitles shouldBe listOf("unwanted horror manga")
        repo.recordedSignals.size shouldBe 1
        val signal = repo.recordedSignals.first()
        signal.cleanTitle shouldBe "unwanted horror manga"
        signal.signalType shouldBe DiscoverySignalType.HIDE
        signal.genres shouldBe listOf("Horror", "Gore")
        signal.provider shouldBe "MangaDex"
    }

    @Test
    fun `foldLearnedTasteProfile penalizes genres from HIDE signals`() {
        val now = 1_000_000_000L
        val signals = listOf(
            DiscoverySignal(
                mediaType = DiscoveryMediaType.MANGA,
                cleanTitle = "fantasy action 1",
                title = "Fantasy Action 1",
                signalType = DiscoverySignalType.LIKE, // weight +0.8
                genres = listOf("fantasy", "action"),
                provider = "MangaDex",
                sourceKey = "mangadex",
                createdAt = now,
            ),
            DiscoverySignal(
                mediaType = DiscoveryMediaType.MANGA,
                cleanTitle = "horror gore 1",
                title = "Horror Gore 1",
                signalType = DiscoverySignalType.HIDE, // weight -1.0
                genres = listOf("horror", "gore"),
                provider = "MangaDex",
                sourceKey = "mangadex",
                createdAt = now,
            ),
        )

        val profile = foldLearnedTasteProfile(signals = signals, nowMs = now)

        // Fantasy and action should be positive
        val fantasyWeight = profile.genres.firstOrNull { it.first == "fantasy" }?.second ?: 0.0
        val horrorWeight = profile.genres.firstOrNull { it.first == "horror" }?.second ?: 0.0

        fantasyWeight shouldBe 0.8
        horrorWeight shouldBeLessThan 0.0
    }

    @Test
    fun `foldLearnedTasteProfile zeroes out vetoGenres`() {
        val now = 1_000_000_000L
        val signals = listOf(
            DiscoverySignal(
                mediaType = DiscoveryMediaType.MANGA,
                cleanTitle = "action romance",
                title = "Action Romance",
                signalType = DiscoverySignalType.LIKE,
                genres = listOf("action", "romance"),
                provider = "MangaDex",
                sourceKey = "mangadex",
                createdAt = now,
            ),
        )

        val profile = foldLearnedTasteProfile(
            signals = signals,
            nowMs = now,
            vetoGenres = setOf("romance"),
        )

        profile.genres.map { it.first } shouldBe listOf("action")
    }
}
