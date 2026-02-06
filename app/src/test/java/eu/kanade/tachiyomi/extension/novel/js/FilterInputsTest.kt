package eu.kanade.tachiyomi.extension.novel.js

import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FilterInputsTest {

    @Test
    fun `toNovelFilterList maps lnreader filters`() {
        val inputs = listOf(
            FilterInput.Text(id = "q", name = "Query", value = "title"),
            FilterInput.Picker(id = "lang", name = "Lang", options = listOf("en", "ru"), selectedIndex = 1),
            FilterInput.Checkbox(id = "free", name = "Free", checked = true),
            FilterInput.Switch(id = "adult", name = "Adult", checked = false),
            FilterInput.XCheckbox(id = "status", name = "Status", state = -1),
        )

        val filters = inputs.toNovelFilterList()

        filters.size shouldBe 5

        val text = filters[0] as NovelFilter.Text
        text.name shouldBe "Query"
        text.state shouldBe "title"

        val picker = filters[1] as NovelFilter.Select<*>
        picker.name shouldBe "Lang"
        picker.state shouldBe 1

        val checkbox = filters[2] as NovelFilter.CheckBox
        checkbox.name shouldBe "Free"
        checkbox.state shouldBe true

        val switch = filters[3] as NovelFilter.CheckBox
        switch.name shouldBe "Adult"
        switch.state shouldBe false

        val xcheckbox = filters[4] as NovelFilter.TriState
        xcheckbox.name shouldBe "Status"
        xcheckbox.state shouldBe NovelFilter.TriState.STATE_EXCLUDE
    }

    @Test
    fun `xcheckbox falls back to ignore on unknown state`() {
        val inputs = listOf(
            FilterInput.XCheckbox(id = "status", name = "Status", state = 5),
        )

        val filters = inputs.toNovelFilterList()

        val xcheckbox = filters[0] as NovelFilter.TriState
        xcheckbox.state shouldBe NovelFilter.TriState.STATE_IGNORE
    }
}
