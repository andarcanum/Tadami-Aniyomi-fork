package eu.kanade.tachiyomi.extension.novel.kotlin

import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

interface KotlinNovelFilterAdapter {
    fun toNovelFilterList(mangaFilters: FilterList): NovelFilterList
    fun toMangaFilterList(novelFilters: NovelFilterList, catalogueSource: CatalogueSource): FilterList
}

class KotlinNovelFilterAdapterImpl : KotlinNovelFilterAdapter {
    override fun toNovelFilterList(mangaFilters: FilterList): NovelFilterList {
        return NovelFilterList(mangaFilters.map { it.toNovelFilter() })
    }

    override fun toMangaFilterList(novelFilters: NovelFilterList, catalogueSource: CatalogueSource): FilterList {
        val originalFilters = catalogueSource.getFilterList()
        copyNovelFilterStateToMangaFilters(novelFilters.toList(), originalFilters.toList())
        return originalFilters
    }

    @Suppress("UNCHECKED_CAST")
    private fun Filter<*>.toNovelFilter(): NovelFilter<*> {
        return when (this) {
            is Filter.Header -> NovelFilter.Header(name)
            is Filter.Separator -> NovelFilter.Separator(name)
            is Filter.CheckBox -> object : NovelFilter.CheckBox(name, state) {}
            is Filter.TriState -> object : NovelFilter.TriState(name, state) {}
            is Filter.Text -> object : NovelFilter.Text(name, state) {}
            is Filter.Select<*> -> object : NovelFilter.Select<Any?>(name, values as Array<Any?>, state) {}
            is Filter.Group<*> -> object : NovelFilter.Group<NovelFilter<*>>(
                name,
                state.filterIsInstance<Filter<*>>().map { it.toNovelFilter() },
            ) {}
            is Filter.Sort -> object : NovelFilter.Sort(
                name,
                values,
                state?.let { NovelFilter.Sort.Selection(it.index, it.ascending) },
            ) {}
        }
    }

    private fun copyNovelFilterStateToMangaFilters(
        source: List<NovelFilter<*>>,
        destination: List<Filter<*>>,
    ) {
        val matchedSourceIndices = mutableSetOf<Int>()
        val matchedDestinationIndices = mutableSetOf<Int>()

        // Pass 1: positional match (handles identically ordered filters and duplicate names correctly)
        for (i in source.indices) {
            val sourceFilter = source[i]
            if (i < destination.size && i !in matchedDestinationIndices &&
                sourceFilter.isStateCompatibleWith(destination[i])
            ) {
                sourceFilter.copyStateTo(destination[i])
                matchedSourceIndices.add(i)
                matchedDestinationIndices.add(i)
            }
        }

        // Pass 2: fallback by compatible name and type for any shifted/reordered filters
        source.forEachIndexed { i, sourceFilter ->
            if (i in matchedSourceIndices) return@forEachIndexed
            val destinationIndex = destination
                .asSequence()
                .withIndex()
                .firstOrNull { (destIdx, destinationFilter) ->
                    destIdx !in matchedDestinationIndices && sourceFilter.isStateCompatibleWith(destinationFilter)
                }
                ?.index
                ?: return@forEachIndexed
            sourceFilter.copyStateTo(destination[destinationIndex])
            matchedSourceIndices.add(i)
            matchedDestinationIndices.add(destinationIndex)
        }
    }

    private fun NovelFilter<*>.isStateCompatibleWith(other: Filter<*>): Boolean {
        if (name != other.name) return false
        return when (this) {
            is NovelFilter.Header -> other is Filter.Header
            is NovelFilter.Separator -> other is Filter.Separator
            is NovelFilter.CheckBox -> other is Filter.CheckBox
            is NovelFilter.Switch -> other is Filter.CheckBox
            is NovelFilter.TriState -> other is Filter.TriState
            is NovelFilter.XCheckBox -> other is Filter.TriState
            is NovelFilter.Text -> other is Filter.Text
            is NovelFilter.Select<*> -> other is Filter.Select<*>
            is NovelFilter.Picker<*> -> other is Filter.Select<*>
            is NovelFilter.Sort -> other is Filter.Sort
            is NovelFilter.Group<*> -> other is Filter.Group<*>
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun NovelFilter<*>.copyStateTo(destination: Filter<*>) {
        when {
            this is NovelFilter.CheckBox && destination is Filter.CheckBox -> destination.state = state
            this is NovelFilter.Switch && destination is Filter.CheckBox -> destination.state = state
            this is NovelFilter.TriState && destination is Filter.TriState -> destination.state = state
            this is NovelFilter.XCheckBox && destination is Filter.TriState -> destination.state = state
            this is NovelFilter.Text && destination is Filter.Text -> destination.state = state
            this is NovelFilter.Select<*> && destination is Filter.Select<*> -> destination.state = state
            this is NovelFilter.Picker<*> && destination is Filter.Select<*> -> destination.state = state
            this is NovelFilter.Sort && destination is Filter.Sort -> {
                destination.state = state?.let { Filter.Sort.Selection(it.index, it.ascending) }
            }
            this is NovelFilter.Group<*> && destination is Filter.Group<*> -> {
                val sourceChildren = state.filterIsInstance<NovelFilter<*>>()
                val destinationChildren = destination.state.filterIsInstance<Filter<*>>()
                copyNovelFilterStateToMangaFilters(sourceChildren, destinationChildren)
            }
        }
    }
}
