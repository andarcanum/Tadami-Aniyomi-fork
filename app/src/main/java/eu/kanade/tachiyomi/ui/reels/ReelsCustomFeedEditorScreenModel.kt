package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
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
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private const val SAVE_TIMEOUT_MS = 60_000L

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
    State(
        name = initialName.orEmpty(),
        // Create mode has no detail to load; edit mode is only editable once it arrives.
        isDetailLoaded = feedId == null,
    ),
) {

    private val source: AnimeCustomFeedSource?
        get() = sourceManager.get(sourceId) as? AnimeCustomFeedSource

    init {
        load()
    }

    private fun load() {
        mutableState.update { it.copy(isLoading = true, error = null, errorRes = null) }
        val src = source
        if (src == null) {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isDetailLoaded = false,
                    error = null,
                    errorRes = MR.strings.reels_source_missing_custom_feeds,
                )
            }
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
                    // An edit form without its detail does not know the existing tags; it
                    // must not be saved as if the incomplete state were the user's intent.
                    isDetailLoaded = if (detailFailed) false else detail != null || feedId == null,
                    allTags = tags.orEmpty().toImmutableList(),
                    selectedTags = detail?.tags?.toImmutableSet() ?: current.selectedTags,
                    name = detail?.name ?: current.name,
                    error = null,
                    errorRes = when {
                        tags == null && detailFailed -> MR.strings.reels_editor_load_failed
                        tags == null -> MR.strings.reels_editor_tags_failed
                        detailFailed -> MR.strings.reels_feed_load_failed
                        else -> null
                    },
                )
            }
        }
    }

    fun retryLoad() {
        if (state.value.isLoading) return
        load()
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
        if (state.value.isSaving || !state.value.isDetailLoaded || name.isEmpty()) return
        val tags = state.value.selectedTags.toList()
        mutableState.update { it.copy(isSaving = true, error = null, errorRes = null) }
        screenModelScope.launch(NonCancellable + ioDispatcher) {
            // A source call that never returns must not pin the editor in isSaving forever.
            val ok = withTimeoutOrNull(SAVE_TIMEOUT_MS) {
                if (feedId != null) {
                    runCatching { src.updateCustomFeed(feedId, name, tags) }.getOrDefault(false)
                } else {
                    runCatching { src.createCustomFeed(name, tags) }.isSuccess
                }
            } ?: false
            mutableState.update { current ->
                if (ok) {
                    current.copy(isSaving = false, isSaved = true)
                } else {
                    current.copy(isSaving = false, error = null, errorRes = MR.strings.reels_save_failed)
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
        // Edit mode: false until getCustomFeedDetail succeeds. Saving an incomplete form
        // would send an empty tag list as if it were the user's chosen replacement.
        val isDetailLoaded: Boolean = false,
        // Raw transport failure text (if any); blank values render nothing.
        val error: String? = null,
        // Static, localizable failure chosen by the model.
        val errorRes: StringResource? = null,
    )
}
