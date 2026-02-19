package eu.kanade.domain.entries.novel.interactor

import eu.kanade.domain.entries.novel.model.normalizeNovelDescription
import eu.kanade.tachiyomi.novelsource.model.SNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import java.time.Instant

class UpdateNovel(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(novelUpdate: NovelUpdate): Boolean {
        return novelRepository.updateNovel(novelUpdate)
    }

    suspend fun awaitAll(novelUpdates: List<NovelUpdate>): Boolean {
        return novelRepository.updateAllNovel(novelUpdates)
    }

    suspend fun awaitUpdateFromSource(
        localNovel: Novel,
        remoteNovel: SNovel,
        manualFetch: Boolean,
    ): Boolean {
        val remoteTitle = try {
            remoteNovel.title
        } catch (_: UninitializedPropertyAccessException) {
            ""
        }

        val title = if (remoteTitle.isEmpty() || localNovel.favorite) null else remoteTitle
        val shouldUpdateCover = manualFetch || localNovel.thumbnailUrl.isNullOrEmpty() || !localNovel.initialized

        val coverLastModified =
            when {
                remoteNovel.thumbnail_url.isNullOrEmpty() -> null
                !shouldUpdateCover -> null
                else -> Instant.now().toEpochMilli()
            }

        val thumbnailUrl = if (shouldUpdateCover) {
            remoteNovel.thumbnail_url?.takeIf { it.isNotEmpty() }
        } else {
            null
        }

        return novelRepository.updateNovel(
            NovelUpdate(
                id = localNovel.id,
                title = title,
                coverLastModified = coverLastModified,
                author = remoteNovel.author,
                description = normalizeNovelDescription(remoteNovel.description),
                genre = remoteNovel.getGenres(),
                thumbnailUrl = thumbnailUrl,
                status = remoteNovel.status.toLong(),
                updateStrategy = remoteNovel.update_strategy,
                initialized = true,
            ),
        )
    }
}
