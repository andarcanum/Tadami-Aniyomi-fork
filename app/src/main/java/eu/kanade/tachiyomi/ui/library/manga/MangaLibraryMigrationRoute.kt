package eu.kanade.tachiyomi.ui.library.manga

internal sealed interface MangaLibraryMigrationRoute {
    data object None : MangaLibraryMigrationRoute
    data class Config(val mangaIds: List<Long>) : MangaLibraryMigrationRoute
}

internal fun resolveMangaLibraryMigrationRoute(selectionIds: List<Long>): MangaLibraryMigrationRoute {
    return when (selectionIds.size) {
        0 -> MangaLibraryMigrationRoute.None
        else -> MangaLibraryMigrationRoute.Config(selectionIds)
    }
}
