package tachiyomi.domain.entries.novel.interactor

import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository

class NetworkToLocalNovel(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(novel: Novel): Novel {
        val localNovel = getNovel(novel.url, novel.source)
        return when {
            localNovel == null -> {
                val insertedId = insertNovel(novel)
                if (insertedId != null) {
                    novel.copy(id = insertedId)
                } else {
                    getNovel(novel.url, novel.source)
                        ?: throw IllegalStateException(
                            "Failed to insert novel for source=${novel.source}, url=${novel.url}",
                        )
                }
            }
            !localNovel.favorite -> {
                // if the novel isn't a favorite, set its display title from source
                // if it later becomes a favorite, updated title will go to db
                localNovel.copy(title = novel.title)
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
