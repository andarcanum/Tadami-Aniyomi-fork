package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

class DiscoveryFillAuditTest {
    private val media = DiscoveryMediaType.MANGA

    private fun item(title: String, genres: List<String> = listOf("Fantasy")) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "fixture",
        score = 1.0,
        sourceUrl = "/$title",
        genres = genres,
    )

    private fun context(
        library: Set<String> = emptySet(),
        recent: Set<String> = emptySet(),
    ) = DiscoveryBuildContext(
        mediaType = media,
        seeds = emptyList(),
        libraryCleanTitles = library,
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        recentCleanTitles = recent,
    )

    @Test
    fun `thirty candidates minus known titles leave two final cards`() {
        val raw = (1..30).map { item("title$it") }
        val library = (1..28).map { "title$it".lowercase() }.toSet()
        val audit = buildDiscoveryFillAudit(
            mediaType = media,
            rawCandidates = mapOf(DiscoveryRowType.TASTE to raw),
            finalRows = mapOf(DiscoveryRowType.TASTE to raw.takeLast(2)),
            context = context(library = library),
            detailsUsed = 8,
            detailsAllowance = 48,
        )

        val row = audit.rows.single()
        row.listFetched shouldBe 30
        row.policyAdmitted shouldBe 30
        row.excludedKnown shouldBe 28
        row.final shouldBe 2
        audit.finalUnique shouldBe 2
        audit.detailsUsed shouldBe 8
        audit.detailsAllowance shouldBe 48
    }

    @Test
    fun `cross row duplicate is counted once in the later row`() {
        val shared = item("Shared")
        val tasteOnly = item("TasteOnly")
        val sourceOnly = item("SourceOnly")
        val audit = buildDiscoveryFillAudit(
            mediaType = media,
            rawCandidates = mapOf(
                DiscoveryRowType.TASTE to listOf(shared, tasteOnly),
                DiscoveryRowType.SOURCE to listOf(shared, sourceOnly),
            ),
            finalRows = mapOf(
                DiscoveryRowType.TASTE to listOf(shared, tasteOnly),
                DiscoveryRowType.SOURCE to listOf(sourceOnly),
            ),
            context = context(),
        )

        audit.rows.first { it.rowType == DiscoveryRowType.TASTE }.crossRowDuplicates shouldBe 0
        val source = audit.rows.first { it.rowType == DiscoveryRowType.SOURCE }
        source.crossRowDuplicates shouldBe 1
        source.policyAdmitted shouldBe 2
        source.final shouldBe 1
        audit.finalUnique shouldBe 3
    }

    @Test
    fun `fresh and stale cards are reported separately`() {
        val shown = item("Shown")
        val fresh = item("Fresh")
        val audit = buildDiscoveryFillAudit(
            mediaType = media,
            rawCandidates = mapOf(DiscoveryRowType.TASTE to listOf(shown, fresh)),
            finalRows = mapOf(DiscoveryRowType.TASTE to listOf(shown, fresh)),
            context = context(recent = setOf("shown")),
        )

        val row = audit.rows.single()
        row.final shouldBe 2
        row.fresh shouldBe 1
        row.staleBackfilled shouldBe 1
        audit.freshUnique shouldBe 1
        audit.staleBackfilled shouldBe 1
    }
}
