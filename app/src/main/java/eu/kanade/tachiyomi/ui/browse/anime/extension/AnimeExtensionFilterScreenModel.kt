package eu.kanade.tachiyomi.ui.browse.anime.extension

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionLanguages
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionFilterScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    private val getExtensionLanguages: GetAnimeExtensionLanguages = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
) : ScreenModel {

    // BEXT-13: BUFFERED - see the manga filter SM comment (send inside catch{} behind the gate).
    private val _events: Channel<AnimeExtensionFilterEvent> = Channel(Channel.BUFFERED)
    val events: Flow<AnimeExtensionFilterEvent> = _events.receiveAsFlow()

    val state: StateFlow<AnimeExtensionFilterState> = combine(
        getExtensionLanguages.subscribe(),
        preferences.enabledLanguages().changes(),
    ) { a, b -> a to b }
        .map { (extensionLanguages, enabledLanguages) ->
            AnimeExtensionFilterState.Success(
                languages = extensionLanguages.toImmutableList(),
                enabledLanguages = enabledLanguages.toImmutableSet(),
            )
        }
        .catch { throwable ->
            logcat(LogPriority.ERROR, throwable)
            _events.send(AnimeExtensionFilterEvent.FailedFetchingLanguages)
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), AnimeExtensionFilterState.Loading)

    fun toggle(language: String) {
        toggleLanguage.await(language)
    }
}

sealed interface AnimeExtensionFilterEvent {
    data object FailedFetchingLanguages : AnimeExtensionFilterEvent
}

sealed interface AnimeExtensionFilterState {

    @Immutable
    data object Loading : AnimeExtensionFilterState

    @Immutable
    data class Success(
        val languages: ImmutableList<String>,
        val enabledLanguages: ImmutableSet<String> = persistentSetOf(),
    ) : AnimeExtensionFilterState {

        val isEmpty: Boolean
            get() = languages.isEmpty()
    }
}
