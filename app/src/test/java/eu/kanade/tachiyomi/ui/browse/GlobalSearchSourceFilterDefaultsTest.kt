package eu.kanade.tachiyomi.ui.browse

import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSourceFilter
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.MangaSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.MangaSourceFilter
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSourceFilter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GlobalSearchSourceFilterDefaultsTest {

    @Test
    fun `anime global search defaults to all sources`() {
        AnimeSearchScreenModel.State().sourceFilter shouldBe AnimeSourceFilter.All
    }

    @Test
    fun `manga global search defaults to all sources`() {
        MangaSearchScreenModel.State().sourceFilter shouldBe MangaSourceFilter.All
    }

    @Test
    fun `novel global search defaults to all sources`() {
        NovelSearchScreenModel.State().sourceFilter shouldBe NovelSourceFilter.All
    }
}
