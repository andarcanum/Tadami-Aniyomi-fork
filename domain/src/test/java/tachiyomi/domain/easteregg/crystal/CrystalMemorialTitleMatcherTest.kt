package tachiyomi.domain.easteregg.crystal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale

class CrystalMemorialTitleMatcherTest {

    private val matcher = CrystalTitleMatcher()

    @Test
    fun unicodeSupplementaryPunctuationAndNelAreHandledWithoutRegexFlags() {
        val separator = String(Character.toChars(0x10100))
        match("My" + separator + " Roommate" + '' + "is a Cat").adaptation shouldBe
            CrystalAdaptation(CrystalWork.ROOMMATE_CAT, CrystalMedia.ANIME)
    }

    @Test
    fun `all reviewed Sakurasou aliases match across its three adaptations`() {
        val aliases = listOf(
            "Кошечка из Сакурасо",
            "Sakurasou no Pet na Kanojo",
            "Sakura-sou no Pet na Kanojo",
            "The Pet Girl of Sakurasou",
            "さくら荘のペットな彼女",
            "樱花庄的宠物女孩",
        )
        for (media in CrystalMedia.entries) {
            for (alias in aliases) {
                match(alias, media).adaptation shouldBe CrystalAdaptation(CrystalWork.SAKURASOU, media)
                match(alias, media).reason shouldBe CrystalTitleMatchReason.MATCHED_ALIAS
            }
        }
    }

    @Test
    fun `all reviewed Roommate aliases match anime and manga only`() {
        val aliases = listOf(
            "My Roommate is a Cat",
            "Doukyonin wa Hiza, Tokidoki, Atama no Ue.",
            "同居人はひざ、時々、頭のうえ。",
            "Домашний питомец, иногда сидящий на моей голове",
        )
        for (media in listOf(CrystalMedia.ANIME, CrystalMedia.MANGA)) {
            for (alias in aliases) {
                match(alias, media).adaptation shouldBe CrystalAdaptation(CrystalWork.ROOMMATE_CAT, media)
            }
        }
        for (alias in aliases) {
            match(alias, CrystalMedia.NOVEL).reason shouldBe CrystalTitleMatchReason.UNSUPPORTED_MEDIA
            match(alias, CrystalMedia.NOVEL).adaptation shouldBe null
        }
    }

    @Test
    fun `normalization handles NFKC punctuation whitespace and Cyrillic yo`() {
        match("  ＳＡＫＵＲＡＳＯＵ　ＮＯ　ＰＥＴ　ＮＡ　ＫＡＮＯＪＯ! ").adaptation shouldBe
            CrystalAdaptation(CrystalWork.SAKURASOU, CrystalMedia.ANIME)
        match("Домашний питомец — иногда сидящий на моёй голове!").adaptation shouldBe
            CrystalAdaptation(CrystalWork.ROOMMATE_CAT, CrystalMedia.ANIME)
        match("同居人はひざ 時々 頭のうえ").adaptation shouldBe
            CrystalAdaptation(CrystalWork.ROOMMATE_CAT, CrystalMedia.ANIME)
    }

    @Test
    fun `case normalization is independent of Turkish default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            match("MY ROOMMATE IS A CAT").adaptation shouldBe
                CrystalAdaptation(CrystalWork.ROOMMATE_CAT, CrystalMedia.ANIME)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `raw alternative title can match and is retained for diagnostics`() {
        val result = matcher.match(
            CrystalTitleInput(CrystalMedia.MANGA, "Source title", listOf("Кошечка из Сакурасо")),
        )
        result.adaptation shouldBe CrystalAdaptation(CrystalWork.SAKURASOU, CrystalMedia.MANGA)
        result.matchedTitle shouldBe "Кошечка из Сакурасо"
    }

    @Test
    fun `substrings sequels and unrelated or decorated titles are not fuzzy matches`() {
        for (title in listOf(
            "",
            "Pet Girl",
            "Sakurasou",
            "My Roommate is a Cat 2",
            "About My Roommate is a Cat",
            "My Roommate is a Cat ♥",
        )) {
            match(title).reason shouldBe CrystalTitleMatchReason.NO_MATCH
        }
    }

    @Test
    fun `aliases of different works in one entry are ambiguous`() {
        val result = matcher.match(
            CrystalTitleInput(CrystalMedia.ANIME, "Sakurasou no Pet na Kanojo", listOf("My Roommate is a Cat")),
        )
        result.adaptation shouldBe null
        result.reason shouldBe CrystalTitleMatchReason.AMBIGUOUS_TITLE
    }

    @Test
    fun `verified anime AniList bindings match without an alias`() {
        for ((id, work) in listOf(13759L to CrystalWork.SAKURASOU, 103874L to CrystalWork.ROOMMATE_CAT)) {
            val result = matcher.match(
                CrystalTitleInput(
                    CrystalMedia.ANIME,
                    "Different source spelling",
                    trustedBindings = listOf(CrystalCatalogBinding("anilist", CrystalMedia.ANIME, id)),
                ),
            )
            result.adaptation shouldBe CrystalAdaptation(work, CrystalMedia.ANIME)
            result.reason shouldBe CrystalTitleMatchReason.MATCHED_CATALOG
        }
    }

    @Test
    fun `known conflicting binding rejects an alias rather than reassigning the work`() {
        val result = matcher.match(
            CrystalTitleInput(
                CrystalMedia.ANIME,
                "Sakurasou no Pet na Kanojo",
                trustedBindings = listOf(CrystalCatalogBinding("anilist", CrystalMedia.ANIME, 103874L)),
            ),
        )
        result.adaptation shouldBe null
        result.reason shouldBe CrystalTitleMatchReason.CONFLICTING_BINDING
    }

    @Test
    fun `multiple conflicting trusted bindings fail closed`() {
        val result = matcher.match(
            CrystalTitleInput(
                CrystalMedia.ANIME,
                "Unreviewed title",
                trustedBindings = listOf(
                    CrystalCatalogBinding("anilist", CrystalMedia.ANIME, 13759L),
                    CrystalCatalogBinding("anilist", CrystalMedia.ANIME, 103874L),
                ),
            ),
        )
        result.reason shouldBe CrystalTitleMatchReason.CONFLICTING_BINDING
        result.adaptation shouldBe null
    }

    @Test
    fun `known bindings cannot change an entry media identity`() {
        val result = matcher.match(
            CrystalTitleInput(
                CrystalMedia.MANGA,
                "Sakurasou no Pet na Kanojo",
                trustedBindings = listOf(CrystalCatalogBinding("anilist", CrystalMedia.ANIME, 13759L)),
            ),
        )
        result.reason shouldBe CrystalTitleMatchReason.CONFLICTING_BINDING
    }

    @Test
    fun `unverified catalog IDs do not magically identify a work`() {
        val unverified = CrystalCatalogBinding("anilist", CrystalMedia.MANGA, 13759L)
        matcher.match(CrystalTitleInput(CrystalMedia.MANGA, "Unknown", trustedBindings = listOf(unverified)))
            .reason shouldBe CrystalTitleMatchReason.NO_MATCH
        matcher.match(
            CrystalTitleInput(CrystalMedia.MANGA, "Кошечка из Сакурасо", trustedBindings = listOf(unverified)),
        )
            .reason shouldBe CrystalTitleMatchReason.MATCHED_ALIAS
    }

    @Test
    fun `callers can inject reviewed mappings and explicit non-target bindings`() {
        val mapped = CrystalCatalogBinding("test-catalog", CrystalMedia.NOVEL, 1L)
        val excluded = CrystalCatalogBinding("test-catalog", CrystalMedia.NOVEL, 2L)
        val extended = CrystalTitleMatcher(mapOf(mapped to CrystalWork.SAKURASOU, excluded to null))
        extended.match(CrystalTitleInput(CrystalMedia.NOVEL, "Unknown", trustedBindings = listOf(mapped)))
            .adaptation shouldBe CrystalAdaptation(CrystalWork.SAKURASOU, CrystalMedia.NOVEL)
        extended.match(CrystalTitleInput(CrystalMedia.NOVEL, "Кошечка из Сакурасо", trustedBindings = listOf(excluded)))
            .reason shouldBe CrystalTitleMatchReason.CONFLICTING_BINDING
        extended.match(
            CrystalTitleInput(
                CrystalMedia.ANIME,
                "Unknown",
                trustedBindings = listOf(CrystalCatalogBinding("anilist", CrystalMedia.ANIME, 13759L)),
            ),
        ).reason shouldBe CrystalTitleMatchReason.MATCHED_CATALOG
    }

    @Test
    fun `injected mapping cannot authorize unsupported Roommate novel`() {
        val binding = CrystalCatalogBinding("test-catalog", CrystalMedia.NOVEL, 1L)
        val extended = CrystalTitleMatcher(mapOf(binding to CrystalWork.ROOMMATE_CAT))
        val result = extended.match(CrystalTitleInput(CrystalMedia.NOVEL, "Unknown", trustedBindings = listOf(binding)))
        result.reason shouldBe CrystalTitleMatchReason.UNSUPPORTED_MEDIA
        result.adaptation shouldBe null
    }

    private fun match(title: String, media: CrystalMedia = CrystalMedia.ANIME) =
        matcher.match(CrystalTitleInput(media, title))
}
