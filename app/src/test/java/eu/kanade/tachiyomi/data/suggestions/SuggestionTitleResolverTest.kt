package eu.kanade.tachiyomi.data.suggestions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuggestionTitleResolverTest {

    @Test
    fun `isFranchiseDuplicate returns true for identical cleaned titles`() {
        assertTrue(SuggestionTitleResolver.isFranchiseDuplicate("Spear of Fate", "Spear of Fate"))
    }

    @Test
    fun `isFranchiseDuplicate returns true for titles that differ only in volume markers`() {
        assertTrue(
            SuggestionTitleResolver.isFranchiseDuplicate(
                "Re:Zero kara Hajimeru Isekai Seikatsu",
                "Re:Zero kara Hajimeru Isekai Seikatsu (Vol. 1)",
            ),
        )
    }

    @Test
    fun `isFranchiseDuplicate returns false for similar but distinct titles`() {
        // After raising threshold 0.90 -> 0.95, "Spear of Fate" vs "Spear of Destiny"
        // should be considered distinct works, not franchise duplicates.
        assertFalse(
            SuggestionTitleResolver.isFranchiseDuplicate("Spear of Fate", "Spear of Destiny"),
        )
    }

    @Test
    fun `isFranchiseDuplicate returns false for sequels with overlapping words`() {
        // "Solo Leveling" vs "Solo Leveling Side Stories" should be distinct
        // since they are different series and the user likely wants both as suggestions.
        assertFalse(
            SuggestionTitleResolver.isFranchiseDuplicate("Solo Leveling", "Solo Leveling Side Stories"),
        )
    }

    @Test
    fun `isFranchiseDuplicate returns false for completely unrelated titles`() {
        assertFalse(
            SuggestionTitleResolver.isFranchiseDuplicate("Attack on Titan", "Re:Zero"),
        )
    }

    @Test
    fun `isFranchiseDuplicate handles Cyrillic input correctly`() {
        // Two completely different Cyrillic titles should not be flagged.
        assertFalse(
            SuggestionTitleResolver.isFranchiseDuplicate("Копьё Судьбы", "Атака Титанов"),
        )
    }

    @Test
    fun `isFranchiseDuplicate returns false for blank input`() {
        assertFalse(SuggestionTitleResolver.isFranchiseDuplicate("", "Spear of Fate"))
        assertFalse(SuggestionTitleResolver.isFranchiseDuplicate("Spear of Fate", ""))
        assertFalse(SuggestionTitleResolver.isFranchiseDuplicate("", ""))
    }

    @Test
    fun `scoreMatch returns 100 for exact case-insensitive match`() {
        assertEquals(100, SuggestionTitleResolver.scoreMatch("Spear of Fate", "spear of fate"))
    }

    @Test
    fun `scoreMatch returns 75 for prefix match`() {
        assertEquals(75, SuggestionTitleResolver.scoreMatch("Spear", "Spear of Fate"))
    }

    @Test
    fun `scoreMatch returns 50 for contains match`() {
        assertEquals(50, SuggestionTitleResolver.scoreMatch("Fate", "Spear of Fate"))
    }

    @Test
    fun `scoreMatch returns positive for partial token overlap`() {
        val score = SuggestionTitleResolver.scoreMatch("Spear of Destiny", "Spear of Fate")
        assert(score in 1..49) { "Expected token jaccard score 1..49, got $score" }
    }

    @Test
    fun `resolveCandidates includes slug from ranobelib URL`() {
        // Use the "Original:" pattern the parser recognises. Cyrillic
        // originals are passed through the metadataAlternativeTitles list.
        val candidates = SuggestionTitleResolver.resolveCandidates(
            title = "Копьё Судьбы",
            description = "Original: Spear of Fate",
            url = "https://ranobelib.me/ru/book/264961--spear-of-fate",
            metadataAlternativeTitles = listOf("Копьё Судьбы"),
        )
        assertTrue(candidates.contains("Копьё Судьбы"))
        assertTrue(candidates.contains("Spear of Fate"))
        assertTrue(candidates.contains("spear of fate"))
    }

    @Test
    fun `cleanTitle strips volume markers and brackets`() {
        val cleaned = SuggestionTitleResolver.cleanTitle("Re:Zero Vol. 1 (Light Novel)")
        assertEquals("re zero", cleaned)
    }

    @Test
    fun `scoreBaseTitleMatch rejects accidental stopword and substring hits`() {
        // Common preposition "of" must never bind unrelated titles together
        assertEquals(0, SuggestionTitleResolver.scoreBaseTitleMatch("Sword of Destiny", "Chronicles of Narnia"))
        // Single significant token out of 3 is too weak
        assertEquals(0, SuggestionTitleResolver.scoreBaseTitleMatch("Spear of Destiny", "Spear of Fate"))
        // Substring "ice" inside "practice" must not match as containment
        assertEquals(0, SuggestionTitleResolver.scoreBaseTitleMatch("Ice", "Evil Practice Game"))
        // Real matches still pass cleanly
        assertEquals(100, SuggestionTitleResolver.scoreBaseTitleMatch("Re:Zero (Light Novel)", "re zero"))
        assert(SuggestionTitleResolver.scoreBaseTitleMatch("Стальной алхимик", "Стальной алхимик: Братство") >= 75)
        assert(SuggestionTitleResolver.scoreBaseTitleMatch("Sword Art Online", "Sword Art Online: Alicization") >= 55)
    }

    @Test
    fun `resolveCandidates ignores punctuation garbage extracted from Russian descriptions`() {
        val russianDescription = """
            Юный герой, живущий в далёкой деревне, однажды находит таинственный артефакт.
            С этого дня его жизнь, полная тревог, испытаний и неожиданных встреч, меняется навсегда.
            В 2024 году, несмотря на все трудности, он отправляется в путь.
        """.trimIndent()

        val candidates = SuggestionTitleResolver.resolveCandidates(
            title = "Копьё Судьбы",
            description = russianDescription,
            metadataAlternativeTitles = listOf(" , . , . ", "   "),
        )

        assertEquals(listOf("Копьё Судьбы"), candidates)
    }

    @Test
    fun `resolveCandidates keeps valid standalone Latin title line from description`() {
        val description = """
            Mushoku Tensei: Jobless Reincarnation
            История о перерождении в фэнтезийном мире, полном магии и приключений.
        """.trimIndent()

        val candidates = SuggestionTitleResolver.resolveCandidates(
            title = "Реинкарнация безработного",
            description = description,
        )

        assertTrue(candidates.contains("Реинкарнация безработного"))
        assertTrue(candidates.contains("Mushoku Tensei: Jobless Reincarnation"))
    }
}
