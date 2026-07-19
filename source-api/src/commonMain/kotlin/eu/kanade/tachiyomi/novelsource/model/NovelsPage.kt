package eu.kanade.tachiyomi.novelsource.model

class NovelsPage(val novels: List<SNovel>, val hasNextPage: Boolean) {

    @Deprecated("NovelsPage is now a regular class")
    operator fun component1(): List<SNovel> = novels

    @Deprecated("NovelsPage is now a regular class")
    operator fun component2(): Boolean = hasNextPage

    @Deprecated("NovelsPage is now a regular class")
    fun copy(
        novels: List<SNovel> = this.novels,
        hasNextPage: Boolean = this.hasNextPage,
    ): NovelsPage = NovelsPage(
        novels = novels,
        hasNextPage = hasNextPage,
    )
}
