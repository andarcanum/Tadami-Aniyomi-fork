package eu.kanade.tachiyomi.novelsource.model

data class NovelFilterList(val list: List<NovelFilter<*>>) : List<NovelFilter<*>> by list {
    constructor(vararg fs: NovelFilter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())
}
