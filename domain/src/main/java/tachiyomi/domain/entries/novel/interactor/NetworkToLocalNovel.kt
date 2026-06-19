package tachiyomi.domain.entries.novel.interactor

import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository

class NetworkToLocalNovel(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(novels: List<Novel>, autoFavorite: Boolean = false): List<Novel> {
        return novelRepository.insertNetworkNovels(novels, autoFavorite)
    }

    suspend fun await(novel: Novel, autoFavorite: Boolean = false): Novel {
        val localNovel = getNovel(novel.url, novel.source)
        return when {
            localNovel == null -> {
                val novelToInsert = if (autoFavorite) {
                    novel.copy(favorite = true, dateAdded = System.currentTimeMillis())
                } else {
                    novel
                }
                val insertedId = insertNovel(novelToInsert)
                if (insertedId != null) {
                    novelToInsert.copy(id = insertedId)
                } else {
                    getNovel(novel.url, novel.source)
                        ?: throw IllegalStateException(
                            "Failed to insert novel for source=${novel.source}, url=${novel.url}",
                        )
                }
            }
            !localNovel.favorite -> {
                if (autoFavorite) {
                    localNovel.copy(favorite = true, dateAdded = System.currentTimeMillis())
                } else {
                    localNovel.copy(
                        title = novel.title,
                        thumbnailUrl = if (localNovel.thumbnailUrl.isNullOrBlank()) {
                            novel.thumbnailUrl
                        } else {
                            localNovel.thumbnailUrl
                        },
                    )
                }
            }
            else -> {
                localNovel
            }
        }
    }

    private suspend fun getNovel(url: String, sourceId: Long): Novel? {
        return novelRepository.getNovelByUrlAndSourceId(url, sourceId)
    }

    private suspend fun insertNovel(novel: Novel): Long? {
        return novelRepository.insertNovel(novel)
    }
}
