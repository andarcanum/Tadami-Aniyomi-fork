package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter
import kotlin.test.assertEquals

class WebtoonSelectionTest {

    @Test
    fun `next transition reports skipped final page before transition`() {
        val chapter = chapter()
        val transition = ChapterTransition.Next(chapter, chapter(2))
        assertEquals(listOf(chapter.pages!!.last(), transition), dispatch(transition))
    }

    @Test
    fun `end of manga also reports skipped final page`() {
        val chapter = chapter()
        val transition = ChapterTransition.Next(chapter, null)
        assertEquals(listOf(chapter.pages!!.last(), transition), dispatch(transition))
    }

    @Test
    fun `previous transition never completes its source chapter`() {
        val transition = ChapterTransition.Prev(chapter(), chapter(2))
        assertEquals(listOf(transition), dispatch(transition))
    }

    @Test
    fun `ordinary page reports only that page`() {
        val page = chapter().pages!!.first()
        assertEquals(listOf(page), dispatch(page))
    }

    @Test
    fun `unloaded and failed final pages are not treated as read`() {
        val chapter = chapter()
        chapter.pages!!.last().status = Page.State.ERROR
        val failed = ChapterTransition.Next(chapter, null)
        assertEquals(listOf(failed), dispatch(failed))
        chapter.state = ReaderChapter.State.Loading
        val loading = ChapterTransition.Next(chapter, null)
        assertEquals(listOf(loading), dispatch(loading))
    }

    @Test
    fun `seamless forward crossing reports skipped final page before next chapter`() {
        val current = chapter()
        val next = chapter(2)
        val nextPage = next.pages!!.first()
        assertEquals(listOf(current.pages!!.last(), nextPage), dispatch(nextPage, current, next))
    }

    @Test
    fun `backwards crossing does not complete the current chapter`() {
        val current = chapter(2)
        val previousPage = chapter().pages!!.last()
        assertEquals(listOf(previousPage), dispatch(previousPage, current, chapter(3)))
    }

    @Test
    fun `stale transition cannot complete another active chapter`() {
        val transition = ChapterTransition.Next(chapter(), null)
        assertEquals(listOf(transition), dispatch(transition, chapter(2)))
    }

    @Test
    fun `restore and switch suppression retains normal selection and preload callbacks`() {
        val current = chapter()
        val next = chapter(2)
        val transition = ChapterTransition.Next(current, next)
        assertEquals(listOf(transition), dispatch(transition, current, next, allowCompletion = false))
        val page = next.pages!!.first()
        assertEquals(listOf(page), dispatch(page, current, next, allowCompletion = false))
    }

    @Test
    fun `already read chapter does not repeat synthetic completion`() {
        val current = chapter().also { it.chapter.read = true }
        val transition = ChapterTransition.Next(current, null)
        assertEquals(listOf(transition), dispatch(transition))
    }

    @Test
    fun `queued and empty final pages do not complete the chapter`() {
        val current = chapter()
        current.pages!!.last().status = Page.State.QUEUE
        val transition = ChapterTransition.Next(current, null)
        assertEquals(listOf(transition), dispatch(transition))
        current.state = ReaderChapter.State.Loaded(emptyList())
        assertEquals(listOf(transition), dispatch(transition))
    }

    @Test
    fun `last page already reported before read flag flips is not repeated`() {
        val current = chapter()
        val transition = ChapterTransition.Next(current, null)
        assertEquals(
            listOf(transition),
            dispatch(transition, lastCompletion = current.pages!!.last()),
        )
    }

    @Test
    fun `same transition retries completion after image becomes ready without repeating preload`() {
        val current = chapter()
        val lastPage = current.pages!!.last()
        lastPage.status = Page.State.QUEUE
        val transition = ChapterTransition.Next(current, null)
        assertEquals(listOf(transition), dispatch(transition))

        lastPage.status = Page.State.READY
        assertEquals(listOf(lastPage), dispatch(transition, selectionChanged = false))
        assertEquals(emptyList(), dispatch(transition, lastCompletion = lastPage, selectionChanged = false))
    }

    @Test
    fun `same transition can complete after restore suppression ends`() {
        val current = chapter()
        val transition = ChapterTransition.Next(current, null)
        assertEquals(listOf(transition), dispatch(transition, allowCompletion = false))
        assertEquals(listOf(current.pages!!.last()), dispatch(transition, selectionChanged = false))
    }

    @Test
    fun `equal but obsolete chapter instance cannot complete active chapter`() {
        val obsolete = chapter()
        val active = chapter()
        val transition = ChapterTransition.Next(obsolete, null)
        assertEquals(listOf(transition), dispatch(transition, current = active))
    }

    private fun dispatch(
        item: Any,
        current: ReaderChapter? = (item as? ChapterTransition)?.from ?: (item as? ReaderPage)?.chapter,
        next: ReaderChapter? = null,
        allowCompletion: Boolean = true,
        lastCompletion: ReaderPage? = null,
        selectionChanged: Boolean = true,
    ): List<Any> = buildList {
        dispatchWebtoonSelection(
            item = item,
            currentChapter = current,
            nextChapter = next,
            allowChapterCompletion = allowCompletion,
            lastCompletionPage = lastCompletion,
            selectionChanged = selectionChanged,
            onPageSelected = { add(it) },
            onTransitionSelected = { add(it) },
        )
    }

    private fun chapter(id: Long = 1): ReaderChapter {
        val chapter = ReaderChapter(Chapter.create().copy(id = id, mangaId = 1, url = "/chapter/$id"))
        chapter.state = ReaderChapter.State.Loaded(
            List(3) { index ->
                ReaderPage(index).also {
                    it.chapter = chapter
                    it.status = Page.State.READY
                }
            },
        )
        return chapter
    }
}
