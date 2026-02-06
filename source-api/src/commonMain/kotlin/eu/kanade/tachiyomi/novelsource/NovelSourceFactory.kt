package eu.kanade.tachiyomi.novelsource

/**
 * A factory for creating sources at runtime.
 */
interface NovelSourceFactory {
    /**
     * Create a new copy of the sources.
     */
    fun createSources(): List<NovelSource>
}
