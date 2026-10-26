package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test

class ReelsSessionContextTest {

    private class TestSelect(name: String, values: Array<String>, state: Int = 0) :
        AnimeFilter.Select<String>(name, values, state)

    private class TestCheckBox(name: String, state: Boolean = false) :
        AnimeFilter.CheckBox(name, state)

    private class TestFeedSource : AnimeFeedSource {
        override val id: Long = 101L
        override val name: String = "Test"
        override val lang: String = "en"
        override val isFeedSource: Boolean = true
        override val supportsTags: Boolean = true

        override fun getFilterList(): AnimeFilterList = AnimeFilterList(
            TestSelect("Mode", arrayOf("All", "Shorts", "Trending"), 0),
            TestCheckBox("HD Only", false),
        )

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)
    }

    @Test
    fun freezeFilters_isolates_from_subsequent_ui_mutations() {
        val source = TestFeedSource()
        val uiFilters = source.getFilterList()
        (uiFilters[0] as AnimeFilter.Select<*>).state = 1
        (uiFilters[1] as AnimeFilter.CheckBox).state = true

        val frozen = ReelsSessionContext.freezeFilters(source, uiFilters)

        assertEquals(1, (frozen.filters[0] as AnimeFilter.Select<*>).state)
        assertEquals(true, (frozen.filters[1] as AnimeFilter.CheckBox).state)

        // Mutate original UI filters
        (uiFilters[0] as AnimeFilter.Select<*>).state = 2
        (uiFilters[1] as AnimeFilter.CheckBox).state = false

        // Frozen copy remains unaffected
        assertEquals(1, (frozen.filters[0] as AnimeFilter.Select<*>).state)
        assertEquals(true, (frozen.filters[1] as AnimeFilter.CheckBox).state)
    }

    @Test
    fun computeContextKey_is_deterministic_and_sensitive_to_inputs() {
        val key1 = ReelsSessionContext.computeContextKey(
            sourceId = 101L,
            filterFingerprint = "0=Mode:select=1;1=HD Only:check=true",
            continuityKey = "tg_v27",
        )
        val key2 = ReelsSessionContext.computeContextKey(
            sourceId = 101L,
            filterFingerprint = "0=Mode:select=1;1=HD Only:check=true",
            continuityKey = "tg_v27",
        )
        val keyDiffFilter = ReelsSessionContext.computeContextKey(
            sourceId = 101L,
            filterFingerprint = "0=Mode:select=0;1=HD Only:check=true",
            continuityKey = "tg_v27",
        )
        val keyDiffContinuity = ReelsSessionContext.computeContextKey(
            sourceId = 101L,
            filterFingerprint = "0=Mode:select=1;1=HD Only:check=true",
            continuityKey = "tg_v28",
        )

        assertEquals(key1, key2)
        assertNotEquals(key1, keyDiffFilter)
        assertNotEquals(key1, keyDiffContinuity)
        assertEquals(64, key1.length) // 256-bit hex
    }
}
