package eu.kanade.tachiyomi.ui.home

internal object ExtensionUpdateCounts {
    fun sum(manga: Int, anime: Int, novel: Int): Int = manga + anime + novel
}
