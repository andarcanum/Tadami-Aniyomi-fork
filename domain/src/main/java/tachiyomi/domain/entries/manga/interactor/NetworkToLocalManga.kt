package tachiyomi.domain.entries.manga.interactor

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository

class NetworkToLocalManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(manga: Manga): Manga {
        val localManga = getManga(manga.url, manga.source)
        return when {
            localManga == null -> {
                val insertedId = insertManga(manga)
                if (insertedId != null) {
                    manga.copy(id = insertedId)
                } else {
                    getManga(manga.url, manga.source)
                        ?: throw IllegalStateException(
                            "Failed to insert manga for source=${manga.source}, url=${manga.url}",
                        )
                }
            }
            !localManga.favorite -> {
                // if the manga isn't a favorite, set its display title from source
                // if it later becomes a favorite, updated title will go to db
                localManga.copy(title = manga.title)
            }
            else -> {
                localManga
            }
        }
    }

    private suspend fun getManga(url: String, sourceId: Long): Manga? {
        return mangaRepository.getMangaByUrlAndSourceId(url, sourceId)
    }

    private suspend fun insertManga(manga: Manga): Long? {
        return mangaRepository.insertManga(manga)
    }
}
