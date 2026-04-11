package eu.kanade.tachiyomi.ui.home

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NovelHomeHubFastCache(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var memoryCache: CachedNovelHomeState? = null

    fun load(): CachedNovelHomeState {
        memoryCache?.let { return it }

        val heroJson = prefs.getString(KEY_HERO, null)
        val historyJson = prefs.getString(KEY_HISTORY, null)
        val recommendationsJson = prefs.getString(KEY_RECOMMENDATIONS, null)
        val userName = prefs.getString(KEY_USER_NAME, "") ?: ""
        val userAvatar = prefs.getString(KEY_USER_AVATAR, "") ?: ""
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)

        val state = CachedNovelHomeState(
            hero = heroJson?.let { runCatching { json.decodeFromString<CachedNovelHeroItem>(it) }.getOrNull() },
            history = historyJson?.let {
                runCatching { json.decodeFromString<List<CachedNovelHistoryItem>>(it) }.getOrNull()
            } ?: emptyList(),
            recommendations = recommendationsJson?.let {
                runCatching { json.decodeFromString<List<CachedNovelRecommendationItem>>(it) }.getOrNull()
            } ?: emptyList(),
            userName = userName,
            userAvatar = userAvatar,
            isInitialized = initialized,
        )

        memoryCache = state
        return state
    }

    fun save(state: CachedNovelHomeState) {
        memoryCache = state

        prefs.edit().apply {
            putString(KEY_HERO, state.hero?.let { json.encodeToString(it) })
            putString(KEY_HISTORY, json.encodeToString(state.history))
            putString(KEY_RECOMMENDATIONS, json.encodeToString(state.recommendations))
            putString(KEY_USER_NAME, state.userName)
            putString(KEY_USER_AVATAR, state.userAvatar)
            putBoolean(KEY_INITIALIZED, state.isInitialized)
            apply()
        }
    }

    fun markInitialized() {
        prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
        memoryCache = memoryCache?.copy(isInitialized = true)
    }

    fun updateUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
        memoryCache = memoryCache?.copy(userName = name)
    }

    fun updateUserAvatar(path: String) {
        prefs.edit().putString(KEY_USER_AVATAR, path).apply()
        memoryCache = memoryCache?.copy(userAvatar = path)
    }

    companion object {
        private const val PREFS_NAME = "novel_home_hub_cache"
        private const val KEY_HERO = "hero"
        private const val KEY_HISTORY = "history"
        private const val KEY_RECOMMENDATIONS = "recommendations"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val KEY_INITIALIZED = "initialized"
    }
}

@Serializable
data class CachedNovelHomeState(
    val hero: CachedNovelHeroItem? = null,
    val history: List<CachedNovelHistoryItem> = emptyList(),
    val recommendations: List<CachedNovelRecommendationItem> = emptyList(),
    val userName: String = "",
    val userAvatar: String = "",
    val isInitialized: Boolean = false,
) {
    val isEmpty: Boolean
        get() = hero == null && history.isEmpty() && recommendations.isEmpty()
}

@Serializable
data class CachedNovelHeroItem(
    val novelId: Long,
    val title: String,
    val chapterNumber: Double,
    val coverUrl: String?,
    val coverLastModified: Long,
    val chapterId: Long,
)

@Serializable
data class CachedNovelHistoryItem(
    val novelId: Long,
    val title: String,
    val chapterNumber: Double,
    val coverUrl: String?,
    val coverLastModified: Long,
)

@Serializable
data class CachedNovelRecommendationItem(
    val novelId: Long,
    val title: String,
    val coverUrl: String?,
    val coverLastModified: Long,
    val totalCount: Long = 0,
    val readCount: Long = 0,
)
