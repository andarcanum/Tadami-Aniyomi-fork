package eu.kanade.presentation.library.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryDisplayMode

class NovelLibraryAuroraDisplaySpecTest {

    @Test
    fun `list mode resolves to single-column list layout`() {
        val spec = resolveNovelLibraryAuroraDisplaySpec(
            displayMode = LibraryDisplayMode.List,
            columns = 4,
        )

        spec.isList shouldBe true
        spec.fixedColumns shouldBe 1
        spec.adaptiveMinCellDp shouldBe null
        spec.showMetadata shouldBe true
    }

    @Test
    fun `comfortable grid uses wider adaptive cells when columns is auto`() {
        val spec = resolveNovelLibraryAuroraDisplaySpec(
            displayMode = LibraryDisplayMode.ComfortableGrid,
            columns = 0,
        )

        spec.isList shouldBe false
        spec.fixedColumns shouldBe null
        spec.adaptiveMinCellDp shouldBe 180
        spec.showMetadata shouldBe true
        spec.gridCardAspectRatio shouldBe 0.66f
    }

    @Test
    fun `cover-only grid hides progress text`() {
        val spec = resolveNovelLibraryAuroraDisplaySpec(
            displayMode = LibraryDisplayMode.CoverOnlyGrid,
            columns = 0,
        )

        spec.isList shouldBe false
        spec.showMetadata shouldBe false
    }

    @Test
    fun `compact and comfortable grids use different card geometry`() {
        val compact = resolveNovelLibraryAuroraDisplaySpec(
            displayMode = LibraryDisplayMode.CompactGrid,
            columns = 0,
        )
        val comfortable = resolveNovelLibraryAuroraDisplaySpec(
            displayMode = LibraryDisplayMode.ComfortableGrid,
            columns = 0,
        )

        compact.gridCardAspectRatio shouldBe 0.56f
        comfortable.gridCardAspectRatio shouldBe 0.66f
    }
}
