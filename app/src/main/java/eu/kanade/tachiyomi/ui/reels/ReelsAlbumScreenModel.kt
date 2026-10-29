package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.reels.anime.repository.ReelsWatchRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * One saved album's content (device feature, sign-off prototype): paged items of the source's
 * category feed rendered as a preview grid; progress bars and watched badges come from the
 * watch history (positionMs/durationSec) plus the manual watched marks; the search field is a
 * client-side filter over the loaded pages (the site offers no server-side album search).
 * Tapping an item opens the regular reels feed in niche mode at that clip.
 */
class ReelsAlbumScreenModel(
    val sourceId: Long,
    val albumId: String,
    private val albumName: String = "",
    private val albumRepository: ReelsAlbumRepository = Injekt.get(),
    private val watchRepository: ReelsWatchRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<ReelsAlbumScreenModel.State>(State()) {

    @Immutable
    data class State(
        val items: ImmutableList<ShortVideoItem> = persistentListOf(),
        val isLoading: Boolean = false,
        val canLoadMore: Boolean = true,
        // First-load failure: nothing is rendered yet, retry reloads the SAME page/cursor.
        val error: String? = null,
        // Append failure: the loaded grid stays usable and the error is transient.
        val pageError: String? = null,
        // The source lost the browse capability (or was uninstalled): not an empty album.
        val unsupported: Boolean = false,
        val nextPage: Int = 1,
        val nextCursor: String? = null,
        val cursorMode: Boolean = false,
        val watched: Set<String> = emptySet(),
        val progress: Map<String, Float> = emptyMap(),
        // Report fix: the AppBar save toggle observes the collection reactively.
        val isAlbumSaved: Boolean = false,
    )

    private val seenIds = mutableSetOf<String>()

    init {
        screenModelScope.launch {
            albumRepository.subscribeBySource(sourceId).collectLatest { albums ->
                mutableState.update { it.copy(isAlbumSaved = albums.any { a -> a.albumId == albumId }) }
            }
        }
        screenModelScope.launch {
            albumRepository.subscribeWatched(sourceId).collectLatest { watched ->
                mutableState.update { it.copy(watched = watched) }
            }
        }
        screenModelScope.launch {
            watchRepository.subscribeAll().collectLatest { entries ->
                val progress = entries
                    .filter { it.sourceId == sourceId }
                    .mapNotNull { entry ->
                        val durationMs = (entry.durationSec ?: 0.0) * 1000.0
                        if (durationMs <= 0.0) {
                            null
                        } else {
                            entry.videoId to (entry.positionMs / durationMs).toFloat().coerceIn(0f, 1f)
                        }
                    }
                    .toMap()
                mutableState.update { it.copy(progress = progress) }
            }
        }
        loadMore()
    }

    /** Appends the next category-feed page; sticky-cursor protocol per contract v17. */
    fun loadMore() {
        val snapshot = state.value
        if (snapshot.isLoading || !snapshot.canLoadMore || snapshot.unsupported) return
        val browse = sourceManager.get(sourceId) as? AnimeFeedBrowseSource
        if (browse == null) {
            mutableState.update {
                it.copy(isLoading = false, canLoadMore = false, error = null, pageError = null, unsupported = true)
            }
            return
        }
        mutableState.update { it.copy(isLoading = true, error = null) }
        screenModelScope.launch(ioDispatcher) {
            try {
                // Contract v17: the token is authoritative only once cursor mode is locked;
                // before that the source always receives a null cursor.
                val cursor = if (snapshot.cursorMode) snapshot.nextCursor else null
                val page = browse.getCategoryFeed(albumId, snapshot.nextPage, cursor)
                val fresh = page.videos.distinctBy { it.id }.filter { seenIds.add(it.id) }
                mutableState.update { current ->
                    val step = ReelsPagination(
                        nextPage = current.nextPage,
                        nextCursor = current.nextCursor,
                        cursorMode = current.cursorMode,
                        canLoadMore = current.canLoadMore,
                    ).advance(page.hasNextPage, page.nextCursor)
                    if (step.cursorLost) {
                        logcat(LogPriority.WARN) {
                            "Album $albumId dropped its cursor mid-feed; stopping pagination (contract v17)."
                        }
                    }
                    current.copy(
                        items = (current.items + fresh).toImmutableList(),
                        isLoading = false,
                        error = null,
                        pageError = null,
                        canLoadMore = step.pagination.canLoadMore,
                        nextPage = step.pagination.nextPage,
                        nextCursor = step.pagination.nextCursor,
                        cursorMode = step.pagination.cursorMode,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logcat(LogPriority.ERROR, throwable = t) { "Failed to load album $albumId page ${snapshot.nextPage}" }
                mutableState.update { current ->
                    // Pagination inputs were not advanced, so a retry repeats this page/cursor.
                    if (current.items.isEmpty()) {
                        current.copy(isLoading = false, error = t.localizedMessage.orEmpty())
                    } else {
                        current.copy(isLoading = false, pageError = t.localizedMessage.orEmpty())
                    }
                }
            }
        }
    }

    fun onPageErrorShown() {
        mutableState.update { it.copy(pageError = null) }
    }

    fun markWatched(videoId: String) {
        screenModelScope.launch(ioDispatcher) { albumRepository.markWatched(sourceId, videoId) }
    }

    fun unmarkWatched(videoId: String) {
        screenModelScope.launch(ioDispatcher) { albumRepository.unmarkWatched(sourceId, videoId) }
    }

    /** AppBar save/unsave of this album (report fix); cover = first reel's poster. */
    fun toggleAlbumSaved() {
        screenModelScope.launch(ioDispatcher) {
            if (state.value.isAlbumSaved) {
                albumRepository.delete(sourceId, albumId)
            } else {
                albumRepository.insert(
                    ReelsAlbum(
                        sourceId = sourceId,
                        albumId = albumId,
                        name = albumName,
                        coverUrl = state.value.items.firstOrNull()?.posterUrl,
                        addedAt = Date(),
                    ),
                )
            }
        }
    }
}
