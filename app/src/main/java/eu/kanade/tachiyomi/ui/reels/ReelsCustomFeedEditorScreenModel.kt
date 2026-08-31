package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeCustomFeedSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * State for the custom-feed editor (contract v19). Create mode when [feedId] is null; edit
 * mode otherwise (the feed's name/tags are loaded from [AnimeCustomFeedSource.getCustomFeedDetail]).
 */
class ReelsCustomFeedEditorScreenModel(
    val sourceId: Long,
    private val feedId: String?,
    initialName: String?,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<ReelsCustomFeedEditorScreenModel.State>(
    State(name = initialName.orEmpty()),
) {

    private val source: AnimeCustomFeedSource?
        get() = sourceManager.get(sourceId) as? AnimeCustomFeedSource

    init {
        load()
    }

    private fun load() {
        val src = source
        if (src == null) {
            mutableState.update { it.copy(isLoading = false, error = "Source does not support custom feeds") }
            return
        }
        screenModelScope.launch(ioDispatcher) {
            val tagsResult = runCatching { src.getCustomFeedTags() }
            val detailResult = if (feedId != null) runCatching { src.getCustomFeedDetail(feedId) } else null
            val tags = tagsResult.getOrNull()
            val detail = detailResult?.getOrNull()
            val detailFailed = feedId != null && detail == null
            mutableState.update { current ->
                current.copy(
                    isLoading = false,
                    allTags = tags.orEmpty().toImmutableList(),
                    selectedTags = detail?.tags?.toImmutableSet() ?: current.selectedTags,
                    name = detail?.name ?: current.name,
                    error = when {
                        tags == null && detailFailed -> "Failed to load custom feed data"
                        tags == null -> "Failed to load tags"
                        detailFailed -> "Failed to load feed"
                        else -> null
                    },
                )
            }
        }
    }

    fun setName(value: String) = mutableState.update { it.copy(name = value) }

    fun toggleTag(tag: String) = mutableState.update { current ->
        val next = (
            if (tag in current.selectedTags) current.selectedTags - tag else current.selectedTags + tag
            ).toImmutableSet()
        current.copy(selectedTags = next)
    }

    fun save() {
        val src = source ?: return
        val name = state.value.name.trim()
        if (state.value.isSaving || name.isEmpty()) return
        val tags = state.value.selectedTags.toList()
        mutableState.update { it.copy(isSaving = true, error = null) }
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            val ok = if (feedId != null) {
                runCatching { src.updateCustomFeed(feedId, name, tags) }.getOrDefault(false)
            } else {
                runCatching { src.createCustomFeed(name, tags) }.isSuccess
            }
            mutableState.update { current ->
                if (ok) {
                    current.copy(isSaving = false, isSaved = true)
                } else {
                    current.copy(isSaving = false, error = "Save failed")
                }
            }
        }
    }

    @Immutable
    data class State(
        val name: String = "",
        val allTags: ImmutableList<String> = persistentListOf(),
        val selectedTags: ImmutableSet<String> = persistentSetOf(),
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val isSaved: Boolean = false,
        val error: String? = null,
    )
}
