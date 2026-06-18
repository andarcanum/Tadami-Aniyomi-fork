package eu.kanade.domain.entries.novel.interactor

import eu.kanade.domain.entries.novel.model.hasCustomCover
import eu.kanade.domain.entries.novel.model.normalizeNovelDescription
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.novelsource.model.SNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

class UpdateNovel(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(novelUpdate: NovelUpdate): Boolean {
        return novelRepository.updateNovel(novelUpdate)
    }

    suspend fun awaitUpdateMetadata(
        novelId: Long,
        customTitle: String?,
        customAuthor: String?,
        customDescription: String?,
        customGenre: List<String>?,
        customStatus: Long?,
    ): Boolean {
        return novelRepository.updateNovelMetadata(
            novelId = novelId,
            customTitle = customTitle,
            customAuthor = customAuthor,
            customDescription = customDescription,
            customGenre = customGenre,
            customStatus = customStatus,
        )
    }

    suspend fun awaitAll(novelUpdates: List<NovelUpdate>): Boolean {
        return novelRepository.updateAllNovel(novelUpdates)
    }

    suspend fun awaitUpdateCoverLastModified(novelId: Long): Boolean {
        return await(
            NovelUpdate(
                id = novelId,
                coverLastModified = Instant.now().toEpochMilli(),
            ),
        )
    }

    suspend fun awaitUpdateFromSource(
        localNovel: Novel,
        remoteNovel: SNovel,
        manualFetch: Boolean,
        coverCache: NovelCoverCache? = null,
    ): Boolean {
        val remoteTitle = try {
            remoteNovel.title
        } catch (_: UninitializedPropertyAccessException) {
            ""
        }

        val title = if (remoteTitle.isEmpty() || localNovel.favorite) null else remoteTitle
        val shouldUpdateCover = manualFetch || localNovel.thumbnailUrl.isNullOrEmpty() || !localNovel.initialized
        val remoteThumbnailUrl = remoteNovel.thumbnail_url?.takeIf { it.isNotEmpty() }
        val hasCustomCover = if (remoteThumbnailUrl != null && shouldUpdateCover) {
            localNovel.hasCustomCover(coverCache ?: Injekt.get())
        } else {
            false
        }

        val coverLastModified =
            when {
                remoteThumbnailUrl == null -> null
                !shouldUpdateCover -> null
                hasCustomCover -> null
                else -> Instant.now().toEpochMilli()
            }

        val thumbnailUrl = if (shouldUpdateCover && !hasCustomCover) {
            remoteThumbnailUrl
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
