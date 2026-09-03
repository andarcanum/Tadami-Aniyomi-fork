package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelJaomixPagedSourceTest {

    @Test
    fun `runtime source and configurable wrapper expose the jaomix paged capability`() {
        // Screens resolve sources through the NovelConfigurableJsSource wrapper; the old
        // `as? NovelJsSource` casts never succeeded against it, silently killing jaomix
        // adjacent-page loading in the reader and jaomix paging detection on the entry screen.
        NovelJaomixPagedSource::class.java.isAssignableFrom(NovelJsSource::class.java) shouldBe true
        NovelJaomixPagedSource::class.java.isAssignableFrom(NovelConfigurableJsSource::class.java) shouldBe true
    }
}
