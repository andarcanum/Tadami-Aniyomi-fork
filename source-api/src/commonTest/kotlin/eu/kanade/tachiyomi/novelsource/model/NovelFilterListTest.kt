package eu.kanade.tachiyomi.novelsource.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class NovelFilterListTest {

    @Test
    fun `empty constructor creates empty list`() {
        val list = NovelFilterList()

        list.size shouldBe 0
    }

    @Test
    fun `vararg constructor creates list`() {
        val list = NovelFilterList(
            object : NovelFilter.Text("Query") {},
            object : NovelFilter.CheckBox("Flag") {},
        )

        list.size shouldBe 2
        list[0].name shouldBe "Query"
        list[1].name shouldBe "Flag"
    }
}
