package eu.kanade.presentation.entries.manga.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScanlatorBranchSelectorTest {

    @Test
    fun `resolve selector items hides All when requested`() {
        resolveScanlatorBranchSelectorItems(
            scanlatorChapterCounts = mapOf(
                "SeRa" to 1432,
                "OneSecond Evil Corp." to 1432,
                "Dodo" to 400,
            ),
            showAllOption = false,
        ).map { it.key } shouldBe listOf(
            "OneSecond Evil Corp.",
            "SeRa",
            "Dodo",
        )
    }

    @Test
    fun `resolve selector items keeps All first by default`() {
        resolveScanlatorBranchSelectorItems(
            scanlatorChapterCounts = mapOf(
                "SeRa" to 1432,
                "OneSecond Evil Corp." to 1432,
            ),
            showAllOption = true,
        ).map { it.key } shouldBe listOf(
            ALL_SCANLATOR_BRANCH_KEY,
            "OneSecond Evil Corp.",
            "SeRa",
        )
    }
}
