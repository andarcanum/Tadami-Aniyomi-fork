package eu.kanade.tachiyomi.ui.entries.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaScreenModelScanlatorSelectionTest {

    @Test
    fun `resolveSelectedScanlator returns null when all scanlators are visible`() {
        resolveSelectedScanlator(
            availableScanlators = setOf("Team A", "Team B"),
            excludedScanlators = emptySet(),
        ) shouldBe null
    }

    @Test
    fun `resolveSelectedScanlator returns only included scanlator`() {
        resolveSelectedScanlator(
            availableScanlators = setOf("Team A", "Team B"),
            excludedScanlators = setOf("Team B"),
        ) shouldBe "Team A"
    }

    @Test
    fun `resolveSelectedScanlator ignores exclusions outside available set`() {
        resolveSelectedScanlator(
            availableScanlators = setOf("Team A", "Team B"),
            excludedScanlators = setOf("Unknown", "Team B"),
        ) shouldBe "Team A"
    }

    @Test
    fun `resolveSelectedScanlator returns null when no scanlators remain`() {
        resolveSelectedScanlator(
            availableScanlators = setOf("Team A"),
            excludedScanlators = setOf("Team A"),
        ) shouldBe null
    }

    @Test
    fun `resolveExcludedScanlatorsForSelection clears exclusions for all branches`() {
        resolveExcludedScanlatorsForSelection(
            selectedScanlator = null,
            availableScanlators = setOf("Team A", "Team B"),
        ) shouldBe emptySet()
    }

    @Test
    fun `resolveExcludedScanlatorsForSelection excludes all except selected branch`() {
        resolveExcludedScanlatorsForSelection(
            selectedScanlator = "Team B",
            availableScanlators = setOf("Team A", "Team B", "Team C"),
        ) shouldBe setOf("Team A", "Team C")
    }

    @Test
    fun `resolveExcludedScanlatorsForSelection handles extra spaces in available values`() {
        resolveExcludedScanlatorsForSelection(
            selectedScanlator = "Team B",
            availableScanlators = setOf(" Team A ", "Team B ", "Team C"),
        ) shouldBe setOf("Team A", "Team C")
    }

    @Test
    fun `resolveExcludedScanlatorsForSelection keeps all branches when selected is unknown`() {
        resolveExcludedScanlatorsForSelection(
            selectedScanlator = "Unknown",
            availableScanlators = setOf("Team A", "Team B"),
        ) shouldBe emptySet()
    }

    @Test
    fun `resolveDefaultExcludedScanlatorsByChapterCount selects largest branch by default`() {
        resolveDefaultExcludedScanlatorsByChapterCount(
            scanlatorChapterCounts = mapOf(
                "Team A" to 3,
                "Team B" to 10,
                "Team C" to 7,
            ),
            availableScanlators = setOf("Team A", "Team B", "Team C"),
            excludedScanlators = emptySet(),
        ) shouldBe setOf("Team A", "Team C")
    }

    @Test
    fun `resolveDefaultExcludedScanlatorsByChapterCount keeps existing selection`() {
        resolveDefaultExcludedScanlatorsByChapterCount(
            scanlatorChapterCounts = mapOf(
                "Team A" to 3,
                "Team B" to 10,
            ),
            availableScanlators = setOf("Team A", "Team B"),
            excludedScanlators = setOf("Team A"),
        ) shouldBe null
    }
}
