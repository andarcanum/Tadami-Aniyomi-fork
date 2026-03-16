package eu.kanade.presentation.entries.anime.components.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeHeroContentTest {

    @Test
    fun `anime hero primary action layout keeps explicit height and horizontal padding`() {
        resolveAnimeHeroPrimaryActionLayoutSpec() shouldBe AnimeHeroPrimaryActionLayoutSpec(
            heightDp = 52,
            horizontalPaddingDp = 14,
        )
    }
}
