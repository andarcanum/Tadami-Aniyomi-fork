package tachiyomi.domain.source.novel.model

import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.fail

@Execution(ExecutionMode.CONCURRENT)
class StubNovelSourceTest {

    @Test
    fun `toString uses name and lang when valid`() {
        val source = StubNovelSource(id = 1L, lang = "en", name = "Novel")

        source.toString() shouldBe "Novel (EN)"
    }

    @Test
    fun `toString uses id when invalid`() {
        val source = StubNovelSource(id = 2L, lang = "", name = "")

        source.toString() shouldBe "2"
    }

    @Test
    fun `throws when accessing details`() = runTest {
        val source = StubNovelSource(id = 3L, lang = "en", name = "Novel")

        try {
            source.getNovelDetails(SNovel.create())
            fail("Expected SourceNotInstalledException")
        } catch (e: SourceNotInstalledException) {
            // expected
        }
    }

    @Test
    fun `throws when accessing chapters`() = runTest {
        val source = StubNovelSource(id = 3L, lang = "en", name = "Novel")

        try {
            source.getChapterList(SNovel.create())
            fail("Expected SourceNotInstalledException")
        } catch (e: SourceNotInstalledException) {
            // expected
        }
        try {
            source.getChapterText(SNovelChapter.create())
            fail("Expected SourceNotInstalledException")
        } catch (e: SourceNotInstalledException) {
            // expected
        }
    }
}
