package eu.kanade.tachiyomi.extension.novel.js

import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList

sealed interface FilterInput {
    val id: String
    val name: String

    data class Text(
        override val id: String,
        override val name: String,
        val value: String = "",
    ) : FilterInput

    data class Picker(
        override val id: String,
        override val name: String,
        val options: List<String>,
        val selectedIndex: Int = 0,
    ) : FilterInput

    data class Checkbox(
        override val id: String,
        override val name: String,
        val checked: Boolean = false,
    ) : FilterInput

    data class Switch(
        override val id: String,
        override val name: String,
        val checked: Boolean = false,
    ) : FilterInput

    data class XCheckbox(
        override val id: String,
        override val name: String,
        val state: Int = 0,
    ) : FilterInput
}

fun List<FilterInput>.toNovelFilterList(): NovelFilterList {
    return NovelFilterList(map { input -> input.toNovelFilter() })
}

private fun FilterInput.toNovelFilter(): NovelFilter<*> {
    return when (this) {
        is FilterInput.Text -> TextFilter(name, value)
        is FilterInput.Picker -> SelectFilter(name, options, selectedIndex)
        is FilterInput.Checkbox -> CheckBoxFilter(name, checked)
        is FilterInput.Switch -> CheckBoxFilter(name, checked)
        is FilterInput.XCheckbox -> TriStateFilter(name, mapTriState(state))
    }
}

private fun mapTriState(state: Int): Int {
    return when (state) {
        -1 -> NovelFilter.TriState.STATE_EXCLUDE
        1 -> NovelFilter.TriState.STATE_INCLUDE
        0 -> NovelFilter.TriState.STATE_IGNORE
        else -> NovelFilter.TriState.STATE_IGNORE
    }
}

private class TextFilter(name: String, state: String) : NovelFilter.Text(name, state)

private class SelectFilter(
    name: String,
    options: List<String>,
    selectedIndex: Int,
) : NovelFilter.Select<String>(
    name,
    options.toTypedArray(),
    selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
)

private class CheckBoxFilter(name: String, state: Boolean) : NovelFilter.CheckBox(name, state)

private class TriStateFilter(name: String, state: Int) : NovelFilter.TriState(name, state)
