package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.novelsource.model.SNovel

/**
 * Paged chapter-list fetching for the jaomix-family plugins.
 *
 * Screens reach sources through [NovelConfigurableJsSource], which wraps the runtime
 * [NovelJsSource]; a cast to the concrete class never succeeds, which silently killed jaomix
 * adjacent-page loading in the reader and the jaomix paging detection on the entry screen.
 * This capability interface is implemented by [NovelJsSource] and delegated by the wrapper, so
 * `as? NovelJaomixPagedSource` works for both.
 */
interface NovelJaomixPagedSource {
    fun isJaomixPagedPlugin(): Boolean

    suspend fun getChapterListPage(
        novel: SNovel,
        page: Int,
    ): NovelPluginChapterListPage?
}
