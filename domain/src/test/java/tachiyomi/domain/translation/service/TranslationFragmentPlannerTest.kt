package tachiyomi.domain.translation.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TranslationFragmentPlannerTest {
    @Test
    fun `long source splitting is bounded reversible and surrogate safe`() {
        val source = ("Words 漢字 😀 and spaces " + System.lineSeparator()).repeat(300)
        val fragments = TranslationFragmentPlanner.plan(listOf(source), 64)
        fragments.all { it.sourceText.length <= 64 } shouldBe true
        fragments.any {
            Character.isLowSurrogate(it.sourceText.first()) ||
                Character.isHighSurrogate(it.sourceText.last())
        } shouldBe
            false
        TranslationFragmentPlanner.sourceSegments(fragments) shouldBe listOf(source)
        TranslationFragmentPlanner.sourceFingerprint(listOf(source)) shouldBe
            TranslationFragmentPlanner.sourceFingerprint(TranslationFragmentPlanner.sourceSegments(fragments))
        val batches = TranslationFragmentPlanner.batches(fragments, 0, 5, 150)
        batches.all { it.size <= 5 && it.sumOf { part -> part.sourceText.length } <= 150 } shouldBe true
    }

    @Test
    fun `partial long block is not overlaid and hash preserves boundaries`() {
        val fragments = TranslationFragmentPlanner.plan(listOf("Word ".repeat(50)), 64)
        TranslationFragmentPlanner.assemble(
            fragments.mapIndexed { index, part ->
                if (index == 0) part.copy(translatedText = "Text") else part
            },
        ) shouldBe emptyMap()
        (
            TranslationFragmentPlanner.sourceFingerprint(listOf("ab", "c")) ==
                TranslationFragmentPlanner.sourceFingerprint(listOf("a", "bc"))
            ) shouldBe false
    }
}
