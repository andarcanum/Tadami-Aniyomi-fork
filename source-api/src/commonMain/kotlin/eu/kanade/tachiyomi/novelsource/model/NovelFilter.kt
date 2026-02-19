package eu.kanade.tachiyomi.novelsource.model

sealed class NovelFilter<T>(val name: String, var state: T) {
    open class Header(name: String) : NovelFilter<Any>(name, 0)
    open class Separator(name: String = "") : NovelFilter<Any>(name, 0)
    abstract class Select<V>(name: String, val values: Array<V>, state: Int = 0) : NovelFilter<Int>(
        name,
        state,
    )
    abstract class Text(name: String, state: String = "") : NovelFilter<String>(name, state)
    abstract class CheckBox(name: String, state: Boolean = false) : NovelFilter<Boolean>(
        name,
        state,
    )
    abstract class Switch(name: String, state: Boolean = false) : NovelFilter<Boolean>(
        name,
        state,
    )
    abstract class TriState(name: String, state: Int = STATE_IGNORE) : NovelFilter<Int>(name, state) {
        fun isIgnored() = state == STATE_IGNORE
        fun isIncluded() = state == STATE_INCLUDE
        fun isExcluded() = state == STATE_EXCLUDE

        companion object {
            const val STATE_IGNORE = 0
            const val STATE_INCLUDE = 1
            const val STATE_EXCLUDE = 2
        }
    }

    abstract class Group<V>(name: String, state: List<V>) : NovelFilter<List<V>>(name, state)

    abstract class Picker<V>(name: String, values: Array<V>, state: Int = 0) :
        Select<V>(name, values, state)

    abstract class XCheckBox(name: String, state: Int = STATE_IGNORE) : TriState(name, state) {
        companion object {
            const val STATE_IGNORE = TriState.STATE_IGNORE
            const val STATE_INCLUDE = TriState.STATE_INCLUDE
            const val STATE_EXCLUDE = TriState.STATE_EXCLUDE
        }
    }

    abstract class Sort(name: String, val values: Array<String>, state: Selection? = null) :
        NovelFilter<Sort.Selection?>(name, state) {
        data class Selection(val index: Int, val ascending: Boolean)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NovelFilter<*>) return false

        return name == other.name && state == other.state
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (state?.hashCode() ?: 0)
        return result
    }
}
