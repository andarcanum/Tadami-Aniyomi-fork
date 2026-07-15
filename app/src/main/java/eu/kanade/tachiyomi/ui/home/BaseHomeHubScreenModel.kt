package eu.kanade.tachiyomi.ui.home

import android.os.Handler
import android.os.Looper
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

        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentTimeOfDay = when (hour) {
            in 5..11 -> 0
            in 12..16 -> 1
            in 17..21 -> 2
            else -> 3
        }
        userProfilePreferences.lastGreetingId().set(greetingSelection.greetingId)
        userProfilePreferences.lastGreetingTimeOfDay().set(currentTimeOfDay)

        val wasReady = state.value.greetingReady
        if (!wasReady) {
            mutableState.update {
                it.copy(
                    greeting = greetingSelection.greeting,
                    greetingReady = true,
                )
            }
        }
    }

    protected fun initializeGreeting() {
        screenModelScope.launchIO {
            resolveAndSetGreeting()
        }
    }

    /**
     * PERF: Defer greeting + heavy stats loading until after the first frame.
     * This is critical for fast Home screen appearance on cold start.
     */
    protected fun initializeGreetingDeferred() {
        Handler(Looper.getMainLooper()).post {
            screenModelScope.launchIO {
                resolveAndSetGreeting()
            }
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
