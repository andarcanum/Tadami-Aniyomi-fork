package eu.kanade.tachiyomi.ui.reels.controllers

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeReelsFeedbackSource
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.reels.ReelsBoundedSet
import eu.kanade.tachiyomi.ui.reels.ReelsFeedScreenModel
import eu.kanade.tachiyomi.ui.reels.ReelsTasteEngine
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.util.Date

/**
 * Owns user engagement actions: likes/favorites, creator follows, album saving/preview, and
 * taste-engine positive feedback signals.
 *
 * Extracted from [ReelsFeedScreenModel] as part of the Stage 4 modularization.
 */
internal class ReelsEngagementController(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val mutableState: MutableStateFlow<ReelsFeedScreenModel.State>,
    private val sourceManager: AnimeSourceManager,
    private val reelsFavoriteRepository: ReelsFavoriteRepository,
    private val reelsFollowRepository: ReelsFollowRepository,
    private val reelsAlbumRepository: ReelsAlbumRepository,
    private val tasteEngine: ReelsTasteEngine,
    private val isIncognito: (Long) -> Boolean,
    private val currentSource: () -> AnimeFeedSource?,
    private val offlineSourceIds: () -> List<Long>,
) {
    val decidedIds = ReelsBoundedSet<String>()
    val decidedFollows = ReelsBoundedSet<String>()

    fun clearSessionDecisions() {
        decidedIds.clear()
        decidedFollows.clear()
    }

    fun toggleLike(item: ShortVideoItem, itemIndex: Int = -1) {
        setLike(item, liked = !mutableState.value.isLiked(item.id, itemIndex), itemIndex = itemIndex)
    }

    fun setLike(item: ShortVideoItem, liked: Boolean, itemIndex: Int = -1) {
        val videoId = item.id
        val current = mutableState.value
        val resolvedIndex = when {
            itemIndex >= 0 -> itemIndex
            current.isMixMode -> {
                if (current.items.getOrNull(current.activeIndex)?.id == videoId) {
                    current.activeIndex
                } else {
                    current.items.indexOfFirst { it.id == videoId }
                }
            }
            else -> -1
        }
        val likeKey = current.likeKey(videoId, resolvedIndex)
        if (liked == (likeKey in current.likedIds)) return
        val sourceId = if (current.isOffline || current.isMixMode) {
            current.itemSourceIds.getOrNull(resolvedIndex)
                ?: offlineSourceIds().getOrNull(resolvedIndex)
                ?: current.currentSourceId
        } else {
            current.currentSourceId
        }
        if (sourceId == SourcePreferences.REELS_ALL_SOURCES_ID) return
        val feedback = (
            if (current.isMixMode || current.isOffline) {
                sourceManager.get(sourceId) as? AnimeFeedSource
            } else {
                currentSource()
            }
            ) as? AnimeReelsFeedbackSource
        decidedIds += likeKey
        mutableState.update { state ->
            val newLikes = if (liked) state.likedIds + likeKey else state.likedIds - likeKey
            state.copy(likedIds = newLikes.toImmutableSet())
        }
        scope.launch(NonCancellable + ioDispatcher) {
            if (liked) {
                if (!isIncognito(sourceId)) {
                    tasteEngine.recordLike(item)
                    reelsFavoriteRepository.insert(item.toReelsFavorite(sourceId))
                }
            } else {
                reelsFavoriteRepository.delete(videoId, sourceId)
            }
            if (feedback != null && !isIncognito(sourceId)) {
                runCatching { feedback.onVideoLiked(videoId, liked) }
            }
        }
    }

    fun loadPersistedFavorites(sourceId: Long) {
        scope.launch(ioDispatcher) {
            val favoriteIds = reelsFavoriteRepository.getIdsBySource(sourceId)
            mutableState.update { current ->
                if (current.currentSourceId != sourceId) return@update current
                val persisted = favoriteIds.filterNot { it in decidedIds }
                current.copy(likedIds = (persisted.toSet() + current.likedIds).toImmutableSet())
            }
        }
    }

    fun toggleFollow(creator: String, sourceId: Long) {
        if (sourceId == SourcePreferences.REELS_ALL_SOURCES_ID) return
        val isMix = mutableState.value.isMixMode
        val willFollow = if (isMix) {
            creator !in mutableState.value.followingCreatorsBySource[sourceId].orEmpty()
        } else {
            creator !in mutableState.value.followingCreators
        }
        decidedFollows += if (isMix) "$sourceId:$creator" else creator
        mutableState.update { state ->
            if (state.isMixMode) {
                val existing = state.followingCreatorsBySource[sourceId].orEmpty()
                val updatedSet = (if (willFollow) existing + creator else existing - creator).toImmutableSet()
                val updatedMap = (state.followingCreatorsBySource + (sourceId to updatedSet)).toImmutableMap()
                state.copy(followingCreatorsBySource = updatedMap)
            } else {
                val newFollows = if (willFollow) {
                    state.followingCreators + creator
                } else {
                    state.followingCreators - creator
                }
                state.copy(followingCreators = newFollows.toImmutableSet())
            }
        }
        scope.launch(NonCancellable + ioDispatcher) {
            if (willFollow && !isIncognito(sourceId)) {
                tasteEngine.recordFollow(creator)
                reelsFollowRepository.insert(ReelsFollow(sourceId = sourceId, creator = creator, addedAt = Date()))
            } else if (!willFollow) {
                reelsFollowRepository.delete(sourceId, creator)
            }
        }
    }

    fun loadPersistedFollows(sourceId: Long) {
        scope.launch(ioDispatcher) {
            val follows = reelsFollowRepository.getBySource(sourceId)
            val persisted = follows.map { it.creator }
            mutableState.update { current ->
                if (current.currentSourceId != sourceId) return@update current
                val unTouched = persisted.filterNot { it in decidedFollows }
                current.copy(followingCreators = (unTouched.toSet() + current.followingCreators).toImmutableSet())
            }
        }
    }

    fun toggleAlbumSaved(category: FeedCategory) {
        val sourceId = mutableState.value.currentSourceId
        val activeItem = mutableState.value.items.getOrNull(mutableState.value.activeIndex)
        scope.launch(ioDispatcher) {
            if (category.id in mutableState.value.savedAlbumIds) {
                reelsAlbumRepository.delete(sourceId, category.id)
            } else {
                if (activeItem != null && !isIncognito(sourceId)) {
                    tasteEngine.recordSave(activeItem)
                }
                reelsAlbumRepository.insert(
                    ReelsAlbum(
                        sourceId = sourceId,
                        albumId = category.id,
                        name = category.name,
                        coverUrl = category.imageUrl,
                        addedAt = Date(),
                    ),
                )
            }
        }
    }

    fun loadAlbumPreview(albumId: String) {
        mutableState.update {
            it.copy(previewAlbumId = albumId, previewLoading = true, previewItems = persistentListOf())
        }
        scope.launch(ioDispatcher) {
            val browse = currentSource() as? AnimeFeedBrowseSource
            val items = runCatching { browse?.getCategoryFeed(albumId, 1, null)?.videos.orEmpty() }
                .getOrDefault(emptyList())
                .take(8)
            mutableState.update { current ->
                if (current.previewAlbumId == albumId) {
                    current.copy(previewItems = items.toImmutableList(), previewLoading = false)
                } else {
                    current
                }
            }
        }
    }

    fun closeAlbumPreview() {
        mutableState.update {
            it.copy(previewAlbumId = null, previewItems = persistentListOf(), previewLoading = false)
        }
    }

    private fun ShortVideoItem.toReelsFavorite(sourceId: Long) = ReelsFavorite(
        videoId = id,
        sourceId = sourceId,
        title = title,
        author = author,
        videoUrl = videoUrl,
        videoUrlHd = videoUrlHd,
        posterUrl = posterUrl,
        posterUrlVertical = posterUrlVertical,
        webUrl = webUrl,
        durationSec = durationSec?.toDouble(),
        hasAudio = hasAudio,
        addedAt = Date(),
    )
}
