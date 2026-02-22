package eu.kanade.tachiyomi.ui.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelScreenModelScanlatorSelectionTest {

    @Test
    fun `resolveSelectedScanlator returns null when all scanlators are visible`() {
        resolveSelectedNovelScanlator(
            availableScanlators = setOf("Team A", "Team B"),
            excludedScanlators = emptySet(),
        ) shouldBe null
    }

    @Test
    fun `resolveSelectedScanlator returns only included scanlator`() {
        resolveSelectedNovelScanlator(
            availableScanlators = setOf("Team A", "Team B"),
            excludedScanlators = setOf("Team B"),
        ) shouldBe "Team A"
    }

    @Test
    fun `resolveSelectedScanlator returns null when no scanlators remain`() {
        resolveSelectedNovelScanlator(
            availableScanlators = setOf("Team A"),
            excludedScanlators = setOf("Team A"),
        ) shouldBe null
    }

    @Test
    fun `resolveExcludedScanlatorsForSelection clears exclusions for all branches`() {
        resolveNovelExcludedScanlatorsForSelection(
            selectedScanlator = null,
            availableScanlators = setOf("Team A", "Team B"),
        ) shouldBe emptySet()
    }

    @Test
    fun `resolveExcludedScanlatorsForSelection excludes all except selected branch`() {
        resolveNovelExcludedScanlatorsForSelection(
            selectedScanlator = "Team B",
            availableScanlators = setOf("Team A", "Team B", "Team C"),
        ) shouldBe setOf("Team A", "Team C")
    }

    @Test
    fun `resolveExcludedScanlatorsForSelection handles extra spaces in available values`() {
        resolveNovelExcludedScanlatorsForSelection(
            selectedScanlator = "Team B",
            availableScanlators = setOf(" Team A ", "Team B ", "Team C"),
        ) shouldBe setOf("Team A", "Team C")
    }

    @Test
    fun `resolveDefaultNovelExcludedScanlatorsByChapterCount selects largest branch by default`() {
        resolveDefaultNovelExcludedScanlatorsByChapterCount(
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
    fun `resolveDefaultNovelExcludedScanlatorsByChapterCount keeps existing selection`() {
        resolveDefaultNovelExcludedScanlatorsByChapterCount(
            scanlatorChapterCounts = mapOf(
                "Team A" to 3,
                "Team B" to 10,
            ),
            availableScanlators = setOf("Team A", "Team B"),
            excludedScanlators = setOf("Team A"),
        ) shouldBe null
    }

    @Test
    fun `resolveDeferredDefaultNovelExcludedScanlators selects largest branch after refresh`() {
        resolveDeferredDefaultNovelExcludedScanlators(
            shouldAttemptAutoSelection = true,
            storedExcludedScanlators = emptySet(),
            availableScanlators = setOf("Team A", "Team B", "Team C"),
            scanlatorChapterCounts = mapOf(
                "Team A" to 2,
                "Team B" to 9,
                "Team C" to 4,
            ),
        ) shouldBe setOf("Team A", "Team C")
    }

    @Test
    fun `resolveDeferredDefaultNovelExcludedScanlators does nothing when selection already exists`() {
        resolveDeferredDefaultNovelExcludedScanlators(
            shouldAttemptAutoSelection = true,
            storedExcludedScanlators = setOf("Team A"),
            availableScanlators = setOf("Team A", "Team B"),
            scanlatorChapterCounts = mapOf(
                "Team A" to 2,
                "Team B" to 9,
            ),
        ) shouldBe null
    }

    @Test
    fun `resolveDeferredDefaultNovelExcludedScanlators skips when auto selection is disabled`() {
        resolveDeferredDefaultNovelExcludedScanlators(
            shouldAttemptAutoSelection = false,
            storedExcludedScanlators = emptySet(),
            availableScanlators = setOf("Team A", "Team B"),
            scanlatorChapterCounts = mapOf(
                "Team A" to 2,
                "Team B" to 9,
            ),
        ) shouldBe null
    }
}
