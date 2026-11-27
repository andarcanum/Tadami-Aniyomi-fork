package eu.kanade.tachiyomi.ui.browse

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionSearchFilterTest {

    @Test
    fun `parseTerms handles empty and blank tokens without dropping numeric ids`() {
        ExtensionSearchFilter.parseTerms("") shouldBe emptyList()
        ExtensionSearchFilter.parseTerms(" ,  , ") shouldBe emptyList()
        ExtensionSearchFilter.parseTerms("mangadex, 123456 , , RuLib") shouldBe listOf(
            ParsedExtensionSearchTerm("mangadex", null),
            ParsedExtensionSearchTerm("123456", 123456L),
            ParsedExtensionSearchTerm("RuLib", null),
        )
    }

    @Test
    fun `distinctRepoCount handles 0 1 2 and N variants accurately`() {
        ExtensionSearchFilter.distinctRepoCount(emptyList<String>()) { it } shouldBe 0
        ExtensionSearchFilter.distinctRepoCount(listOf("https://a.test")) { it } shouldBe 1
        ExtensionSearchFilter.distinctRepoCount(listOf("https://a.test", "https://a.test")) { it } shouldBe 1
        ExtensionSearchFilter.distinctRepoCount(listOf("https://a.test", "https://b.test")) { it } shouldBe 2
        ExtensionSearchFilter.distinctRepoCount(
            listOf("https://a.test", "https://b.test", "https://a.test", "https://c.test"),
        ) { it } shouldBe 3
    }
}
