package eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch

class GlobalNovelSearchScreenModel(
    initialQuery: String = "",
) : NovelSearchScreenModel(
    State(
        searchQuery = initialQuery,
    ),
) {

    init {
        if (initialQuery.isNotBlank()) {
            search()
        }
    }
}
