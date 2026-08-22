package eu.kanade.tachiyomi.ui.library.novel.quotes

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter

class NovelQuotesListOpsTest {

    private fun item(
        id: Long,
        novel: String,
        chapter: String? = "Глава",
        text: String = "цитата $id",
        note: String = "",
        updated: Long = id,
    ) = NovelHighlightWithChapter(
        highlight = NovelHighlight(
            id = id,
            novelId = id * 10,
            chapterId = id * 100,
            blockIndex = 0,
            charStart = 0,
            charEndExclusive = 10,
            normalizedText = text,
            colorArgb = 0xFFFBC02D,
            note = note,
            createdAt = updated,
            updatedAt = updated,
        ),
        novelTitle = novel,
        chapterName = chapter,
        chapterSourceOrder = 0L,
    )

    @Test
    fun `visible hides orphans`() {
        val items = listOf(item(1, "А"), item(2, "А", chapter = null))
        NovelQuotesListOps.visible(items).map { it.highlight.id } shouldBe listOf(1L)
    }

    @Test
    fun `filter matches quote text case-insensitive`() {
        val items = listOf(item(1, "А", text = "Ветер дует"), item(2, "Б", text = "Дождь"))
        NovelQuotesListOps.filter(items, query = "ветер", bookFilter = null)
            .map { it.highlight.id } shouldBe listOf(1L)
    }

    @Test
    fun `filter matches note too`() {
        val items = listOf(item(1, "А", note = "перечитать"), item(2, "Б"))
        NovelQuotesListOps.filter(items, query = "перечит", bookFilter = null)
            .map { it.highlight.id } shouldBe listOf(1L)
    }

    @Test
    fun `book filter combines with search`() {
        val items = listOf(
            item(1, "А", text = "ветер"),
            item(2, "Б", text = "ветер"),
            item(3, "А", text = "дождь"),
        )
        NovelQuotesListOps.filter(items, query = "ветер", bookFilter = "А")
            .map { it.highlight.id } shouldBe listOf(1L)
    }

    @Test
    fun `sort date newest first`() {
        val items = listOf(item(1, "А", updated = 100), item(2, "Б", updated = 300), item(3, "А", updated = 200))
        NovelQuotesListOps.sorted(items, NovelQuotesSortMode.DATE).map { it.highlight.id } shouldBe listOf(2L, 3L, 1L)
    }

    @Test
    fun `sort title groups books a-z newest first inside`() {
        val items = listOf(
            item(1, "Б", updated = 100),
            item(2, "А", updated = 200),
            item(3, "А", updated = 400),
            item(4, "А", updated = 300),
        )
        NovelQuotesListOps.sorted(items, NovelQuotesSortMode.TITLE).map { it.highlight.id } shouldBe
            listOf(3L, 4L, 2L, 1L)
    }

    @Test
    fun `sections preserve sorted order and merge contiguously`() {
        val sorted = NovelQuotesListOps.sorted(
            listOf(item(1, "Б", updated = 100), item(2, "А", updated = 400), item(3, "А", updated = 200)),
            NovelQuotesSortMode.TITLE,
        )
        val sections = NovelQuotesListOps.sections(sorted)
        sections.map { it.title } shouldBe listOf("А", "Б")
        sections.first().items.map { it.highlight.id } shouldBe listOf(2L, 3L)
    }

    @Test
    fun `counts by novel`() {
        val items = listOf(item(1, "А"), item(2, "А"), item(3, "Б"))
        NovelQuotesListOps.countsByNovel(items) shouldBe mapOf("А" to 2, "Б" to 1)
    }

    @Test
    fun `sort mode parses storage key with fallback`() {
        NovelQuotesSortMode.from("TITLE") shouldBe NovelQuotesSortMode.TITLE
        NovelQuotesSortMode.from(null) shouldBe NovelQuotesSortMode.DATE
        NovelQuotesSortMode.from("garbage") shouldBe NovelQuotesSortMode.DATE
    }
}
