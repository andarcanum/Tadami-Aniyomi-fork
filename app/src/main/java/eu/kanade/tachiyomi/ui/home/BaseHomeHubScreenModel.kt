package eu.kanade.tachiyomi.ui.home

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.UserProfilePreferences
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO

internal abstract class BaseHomeHubScreenModel(
    protected val context: android.content.Context,
    initialState: HomeHubUiState,
    protected val userProfilePreferences: UserProfilePreferences,
) : StateScreenModel<HomeHubUiState>(initialState) {

    protected abstract val avatarFileName: String

    protected abstract fun updateCacheUserName(name: String)
    protected abstract fun updateCacheUserAvatar(path: String)

    protected open suspend fun loadGreetingStats(): HomeGreetingStats = HomeGreetingStats()

    suspend fun resolveAndSetGreeting() {
        val stats = loadGreetingStats()
        val greetingSelection = HomeGreetingSession.resolveGreeting(
            userProfilePreferences = userProfilePreferences,
            stats = stats,
        )
        mutableState.update {
            it.copy(
                greeting = greetingSelection.greeting,
                greetingReady = true,
            )
        }
    }

    protected fun initializeGreeting() {
        screenModelScope.launchIO {
            resolveAndSetGreeting()
        }
    }

    fun updateUserName(name: String) {
        val previousName = userProfilePreferences.name().get()
        userProfilePreferences.name().set(name)
        if (name != previousName) {
            userProfilePreferences.nameEdited().set(true)
        }
        updateCacheUserName(name)
        mutableState.update { it.copy(userName = name) }
    }

    fun updateUserAvatar(uriString: String) {
        try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val file = java.io.File(context.filesDir, avatarFileName)
            file.outputStream().use { output ->
                inputStream.use { input -> input.copyTo(output) }
            }
            val path = file.absolutePath
            userProfilePreferences.avatarUrl().set(path)
            updateCacheUserAvatar(path)
            mutableState.update { it.copy(userAvatar = path) }
        } catch (_: Exception) {
        }
    }
}
