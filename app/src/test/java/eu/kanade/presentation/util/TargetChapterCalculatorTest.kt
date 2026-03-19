package eu.kanade.presentation.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class TargetChapterCalculatorTest {

    @Test
    fun `returns first unread chapter when multiple unread exist`() {
        val chapters = listOf(
            Chapter.create().copy(id = 1, read = true, chapterNumber = 1.0),
            Chapter.create().copy(id = 2, read = true, chapterNumber = 2.0),
            Chapter.create().copy(id = 3, read = false, chapterNumber = 3.0),
            Chapter.create().copy(id = 4, read = false, chapterNumber = 4.0),
            Chapter.create().copy(id = 5, read = false, chapterNumber = 5.0),
        )

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 2
    }

    @Test
    fun `returns next chapter after last read when all before are read`() {
        val chapters = listOf(
            Chapter.create().copy(id = 1, read = true, chapterNumber = 1.0),
            Chapter.create().copy(id = 2, read = true, chapterNumber = 2.0),
            Chapter.create().copy(id = 3, read = true, chapterNumber = 3.0),
            Chapter.create().copy(id = 4, read = false, chapterNumber = 4.0),
            Chapter.create().copy(id = 5, read = false, chapterNumber = 5.0),
        )

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 3
    }

    @Test
    fun `returns last chapter when all are read`() {
        val chapters = listOf(
            Chapter.create().copy(id = 1, read = true, chapterNumber = 1.0),
            Chapter.create().copy(id = 2, read = true, chapterNumber = 2.0),
            Chapter.create().copy(id = 3, read = true, chapterNumber = 3.0),
        )

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 2
    }

    @Test
    fun `returns first chapter when none are read`() {
        val chapters = listOf(
            Chapter.create().copy(id = 1, read = false, chapterNumber = 1.0),
            Chapter.create().copy(id = 2, read = false, chapterNumber = 2.0),
            Chapter.create().copy(id = 3, read = false, chapterNumber = 3.0),
        )

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 0
    }

    @Test
    fun `returns 0 for empty list`() {
        val chapters = emptyList<Chapter>()

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 0
    }

    @Test
    fun `returns 0 for single unread chapter`() {
        val chapters = listOf(
            Chapter.create().copy(id = 1, read = false, chapterNumber = 1.0),
        )

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 0
    }

    @Test
    fun `returns 0 for single read chapter`() {
        val chapters = listOf(
            Chapter.create().copy(id = 1, read = true, chapterNumber = 1.0),
        )

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 0
    }

    @Test
    fun `works with Episode seen field`() {
        val episodes = listOf(
            Episode.create().copy(id = 1, seen = true, episodeNumber = 1.0),
            Episode.create().copy(id = 2, seen = true, episodeNumber = 2.0),
            Episode.create().copy(id = 3, seen = false, episodeNumber = 3.0),
        )

        val result = TargetChapterCalculator.calculate(episodes) { it.seen }

        result shouldBe 2
    }

    @Test
    fun `works with NovelChapter read field`() {
        val chapters = listOf(
            NovelChapter.create().copy(id = 1, read = true, chapterNumber = 1.0),
            NovelChapter.create().copy(id = 2, read = false, chapterNumber = 2.0),
            NovelChapter.create().copy(id = 3, read = false, chapterNumber = 3.0),
        )

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 1
    }

    @Test
    fun `handles interleaved read and unread chapters`() {
        val chapters = listOf(
            Chapter.create().copy(id = 1, read = true, chapterNumber = 1.0),
            Chapter.create().copy(id = 2, read = false, chapterNumber = 2.0),
            Chapter.create().copy(id = 3, read = true, chapterNumber = 3.0),
            Chapter.create().copy(id = 4, read = false, chapterNumber = 4.0),
        )

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 1
    }

    @Test
    fun `handles large list with target near the end`() {
        val chapters = (1..500).map { i ->
            Chapter.create().copy(id = i.toLong(), read = i < 498, chapterNumber = i.toDouble())
        }

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 497
    }

    @Test
    fun `handles large list with all read`() {
        val chapters = (1..1000).map { i ->
            Chapter.create().copy(id = i.toLong(), read = true, chapterNumber = i.toDouble())
        }

        val result = TargetChapterCalculator.calculate(chapters) { it.read }

        result shouldBe 999
    }
}
