package eu.kanade.tachiyomi.ui.browse.search

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * PARENT-3/BRA-2/BFEED-7/BRN-5 + BRA-13 (red before the fix):
 * positional deserialization with unchecked `json[STATE]!!` primitives crashed the screen model
 * coroutines (process crash) once an extension update reshaped its filter list, silently applied
 * states to the wrong filters, and Group children after a non-filter placeholder lost their
 * state. Corrupted JSON also crashed through the unguarded top-level parse (BRM-7).
 */
class SavedSearchFilterSerializerTest {

    private class TestCheckBox(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)
    private class TestText(name: String, state: String = "") : AnimeFilter.Text(name, state)
    private class TestTriState(
        name: String,
        state: Int = AnimeFilter.TriState.STATE_IGNORE,
    ) : AnimeFilter.TriState(name, state)

    private class TestSort(
        name: String,
        values: Array<String>,
        state: Selection? = null,
    ) : AnimeFilter.Sort(name, values, state)

    private class TestGroup(children: List<Any>) : AnimeFilter.Group<Any>("Group", children)

    private class NCheckBox(name: String, state: Boolean = false) : NovelFilter.CheckBox(name, state)
    private class NText(name: String, state: String = "") : NovelFilter.Text(name, state)

    @Test
    fun `anime round trip preserves states`() {
        val source = AnimeFilterList(
            TestCheckBox("A", true),
            TestText("B", "query"),
            TestTriState("C", AnimeFilter.TriState.STATE_INCLUDE),
        )
        val json = SavedSearchFilterSerializer.serialize(source)

        val target = AnimeFilterList(TestCheckBox("A"), TestText("B"), TestTriState("C"))
        SavedSearchFilterSerializer.deserialize(json, target)

        (target[0] as TestCheckBox).state shouldBe true
        (target[1] as TestText).state shouldBe "query"
        (target[2] as TestTriState).state shouldBe AnimeFilter.TriState.STATE_INCLUDE
    }

    @Test
    fun `type drift skips instead of throwing`() {
        // Red before the fix: a CheckBox JSON at a position that now holds a Text filter made
        // `.content` throw on a boolean primitive - straight out of the screen model coroutine.
        val json = SavedSearchFilterSerializer.serialize(AnimeFilterList(TestCheckBox("A", true)))

        val target = AnimeFilterList(TestText("A"))
        SavedSearchFilterSerializer.deserialize(json, target)

        (target[0] as TestText).state shouldBe ""
    }

    @Test
    fun `name drift skips instead of misapplying`() {
        // Red before the fix: the state of a renamed filter was applied positionally.
        val json = SavedSearchFilterSerializer.serialize(AnimeFilterList(TestCheckBox("Old", true)))

        val target = AnimeFilterList(TestCheckBox("New"))
        SavedSearchFilterSerializer.deserialize(json, target)

        (target[0] as TestCheckBox).state shouldBe false
    }

    @Test
    fun `corrupted json degrades to a no-op`() {
        val target = AnimeFilterList(TestCheckBox("A", false))

        SavedSearchFilterSerializer.deserialize("not json{{", target)
        SavedSearchFilterSerializer.deserialize("[]", target)
        SavedSearchFilterSerializer.deserialize("[{\"type\":\"CHECKBOX\"}]", target) // no name/state

        (target[0] as TestCheckBox).state shouldBe false
    }

    @Test
    fun `header placeholder does not break the following filters`() {
        val source = AnimeFilterList(AnimeFilter.Header("H"), TestCheckBox("A", true))
        val json = SavedSearchFilterSerializer.serialize(source)

        val target = AnimeFilterList(AnimeFilter.Header("H"), TestCheckBox("A"))
        SavedSearchFilterSerializer.deserialize(json, target)

        (target[1] as TestCheckBox).state shouldBe true
    }

    @Test
    fun `group children align by raw index across non-filter placeholders`() {
        // Red before the fix (BRA-13): serialize wrote a JsonNull placeholder for the non-filter
        // child, but deserialize iterated the FILTERED child list against the unfiltered json -
        // the checkbox after the placeholder never got its state back.
        val source = AnimeFilterList(TestGroup(listOf("not-a-filter", TestCheckBox("Inner", true))))
        val json = SavedSearchFilterSerializer.serialize(source)

        val target = AnimeFilterList(TestGroup(listOf("not-a-filter", TestCheckBox("Inner"))))
        SavedSearchFilterSerializer.deserialize(json, target)

        val group = target[0] as TestGroup
        (group.state[1] as TestCheckBox).state shouldBe true
    }

    @Test
    fun `sort selection round trips and a saved null selection stays null`() {
        val source = AnimeFilterList(TestSort("S", arrayOf("a", "b"), AnimeFilter.Sort.Selection(1, false)))
        val json = SavedSearchFilterSerializer.serialize(source)

        val target = AnimeFilterList(TestSort("S", arrayOf("a", "b")))
        SavedSearchFilterSerializer.deserialize(json, target)
        (target[0] as TestSort).state shouldBe AnimeFilter.Sort.Selection(1, false)

        val nullJson = SavedSearchFilterSerializer.serialize(AnimeFilterList(TestSort("S2", arrayOf("a"))))
        val nullTarget = AnimeFilterList(TestSort("S2", arrayOf("a")))
        SavedSearchFilterSerializer.deserialize(nullJson, nullTarget)
        (nullTarget[0] as TestSort).state shouldBe null
    }

    @Test
    fun `novel round trip preserves states`() {
        val source = NovelFilterList(NCheckBox("A", true), NText("B", "q"))
        val json = SavedSearchFilterSerializer.serialize(source)

        val target = NovelFilterList(NCheckBox("A"), NText("B"))
        SavedSearchFilterSerializer.deserialize(json, target)

        (target[0] as NCheckBox).state shouldBe true
        (target[1] as NText).state shouldBe "q"
    }
}
