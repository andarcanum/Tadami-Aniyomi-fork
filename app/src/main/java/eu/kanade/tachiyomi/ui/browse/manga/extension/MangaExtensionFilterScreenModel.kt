package eu.kanade.tachiyomi.ui.browse.manga.extension

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionLanguages
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

class MangaExtensionFilterScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    private val getExtensionLanguages: GetMangaExtensionLanguages = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
) : ScreenModel {

    // BEXT-13: BUFFERED - send(FailedFetchingLanguages) ran inside catch{} while the screen's
    // collector sits behind the Loading gate; a rendezvous send hung and stalled stateIn.
    private val _events: Channel<MangaExtensionFilterEvent> = Channel(Channel.BUFFERED)
    val events: Flow<MangaExtensionFilterEvent> = _events.receiveAsFlow()

    val state: StateFlow<MangaExtensionFilterState> = combine(
        getExtensionLanguages.subscribe(),
        preferences.enabledLanguages().changes(),
    ) { a, b -> a to b }
        .map { (extensionLanguages, enabledLanguages) ->
            MangaExtensionFilterState.Success(
                languages = extensionLanguages.toImmutableList(),
                enabledLanguages = enabledLanguages.toImmutableSet(),
            )
        }
        .catch { throwable ->
            logcat(LogPriority.ERROR, throwable)
            _events.send(MangaExtensionFilterEvent.FailedFetchingLanguages)
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), MangaExtensionFilterState.Loading)

    fun toggle(language: String) {
        toggleLanguage.await(language)
    }
}

sealed interface MangaExtensionFilterEvent {
    data object FailedFetchingLanguages : MangaExtensionFilterEvent
}

sealed interface MangaExtensionFilterState {

    @Immutable
    data object Loading : MangaExtensionFilterState

    @Immutable
    data class Success(
        val languages: ImmutableList<String>,
        val enabledLanguages: ImmutableSet<String> = persistentSetOf(),
    ) : MangaExtensionFilterState {

        val isEmpty: Boolean
            get() = languages.isEmpty()
    }
}
