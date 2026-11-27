package eu.kanade.tachiyomi.data.suggestions.sources

import eu.kanade.tachiyomi.data.discovery.ShikimoriCover
import eu.kanade.tachiyomi.data.discovery.ShikimoriMediaItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import io.kotest.matchers.shouldBe
import org.junit.Test

class ShikimoriSimilarSourceTest {

    @Test
    fun `selectShikimoriBestMatch prefers highest score across name and russian`() {
        val results = listOf(
            ShikimoriMediaItem(id = 1L, name = "Fullmetal Alchemist: Brotherhood", russian = "Стальной алхимик"),
            ShikimoriMediaItem(id = 2L, name = "Unrelated Show", russian = null),
        )
        selectShikimoriBestMatch(results, listOf("Стальной алхимик"))?.id shouldBe 1L
        selectShikimoriBestMatch(results, listOf("Fullmetal Alchemist: Brotherhood"))?.id shouldBe 1L
        selectShikimoriBestMatch(results, listOf("Bleach")) shouldBe null
        selectShikimoriBestMatch(emptyList(), listOf("Anything")) shouldBe null
    }

    @Test
    fun `similar item maps to suggestion item with shikimori urls`() {
        val item = ShikimoriMediaItem(
            id = 45L,
            name = "anime_45",
            russian = "аниме_45",
            image = ShikimoriCover(original = "/system/animes/original/45.jpg"),
            url = "/animes/45-anime-45",
        )
        val ru = shikimoriSimilarToSuggestion(item, SuggestionMediaType.ANIME, isRussianLocale = true)
        ru?.title shouldBe "аниме_45"
        ru?.thumbnailUrl shouldBe "https://shikimori.one/system/animes/original/45.jpg"
        ru?.providerUrl shouldBe "https://shikimori.one/animes/45-anime-45"
        ru?.providerName shouldBe "Shikimori"
        ru?.reason shouldBe SuggestionReason.EXTERNAL_SHIKIMORI
        ru?.providerId shouldBe null

        val en = shikimoriSimilarToSuggestion(item, SuggestionMediaType.ANIME, isRussianLocale = false)
        en?.title shouldBe "anime_45"
    }

    @Test
    fun `blank title maps to null`() {
        shikimoriSimilarToSuggestion(
            ShikimoriMediaItem(id = 1L, name = "", russian = null),
            SuggestionMediaType.NOVEL,
            isRussianLocale = false,
        ) shouldBe null
    }

    @Test
    fun `novel media type maps to ranobe suggestion`() {
        val item = ShikimoriMediaItem(id = 9L, name = "Re:Zero", russian = "Re:Zero. Жизнь с нуля", url = "/ranobe/9")
        val s = shikimoriSimilarToSuggestion(item, SuggestionMediaType.NOVEL, isRussianLocale = false)
        s?.mediaType shouldBe SuggestionMediaType.NOVEL
        s?.providerUrl shouldBe "https://shikimori.one/ranobe/9"
        s?.searchQueries shouldBe listOf("Re:Zero", "Re:Zero. Жизнь с нуля")
    }
}
