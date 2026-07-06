package tachiyomi.data.anixart

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class AnixartMatchingCoordinatorTest {

    private fun candidate(id: Long, title: String, sourceId: Long = 1L) =
        AnixartMatcher.SearchCandidate(
            id = id,
            sourceId = sourceId,
            displayTitle = title,
            titles = listOf(title),
            url = "url-$id",
        )

    @Test
    fun `picks best score across queries not last query`() = runBlocking {
        val row = AnixartRow(
            index = 1,
            russianTitle = "Наруто",
            originalTitle = "Naruto",
            alternativeTitles = "",
            favoriteRaw = "",
            statusRaw = "Смотрю",
            ratingRaw = "",
        )
        val search: suspend (String) -> List<AnixartMatcher.SearchCandidate> = { query ->
            when (query) {
                "Naruto" -> listOf(candidate(1L, "Random Manga Spinoff Naruto"))
                "Наруто" -> listOf(candidate(2L, "Наруто"))
                else -> emptyList()
            }
        }

        val match = AnixartMatchingCoordinator.matchRow(row, search)

        match.matchedQuery shouldBe "Наруто"
        match.result.best!!.candidate.id shouldBe 2L
        match.result.confidence shouldBe AnixartMatcher.Confidence.AUTO
    }

    @Test
    fun `dedup removes duplicate source and url`() {
        val dup = candidate(1L, "A", sourceId = 5L)
        val unique = candidate(2L, "B", sourceId = 5L)
        val deduped = AnixartMatchingCoordinator.dedupCandidates(
            listOf(dup, dup.copy(id = 99L), unique),
        )
        deduped.size shouldBe 2
    }

    @Test
    fun `summarize counts confidence buckets`() {
        val report = AnixartMatchingCoordinator.summarize(
            listOf(
                AnixartMatchingCoordinator.RowMatch(
                    AnixartMatcher.MatchResult(AnixartMatcher.Confidence.AUTO, null, emptyList()),
                    "q",
                ),
                AnixartMatchingCoordinator.RowMatch(
                    AnixartMatcher.MatchResult(AnixartMatcher.Confidence.NEEDS_REVIEW, null, emptyList()),
                    "q",
                ),
                AnixartMatchingCoordinator.RowMatch(
                    AnixartMatcher.MatchResult(AnixartMatcher.Confidence.NO_MATCH, null, emptyList()),
                    null,
                ),
            ),
        )
        report.total shouldBe 3
        report.auto shouldBe 1
        report.needsReview shouldBe 1
        report.noMatch shouldBe 1
    }
}
