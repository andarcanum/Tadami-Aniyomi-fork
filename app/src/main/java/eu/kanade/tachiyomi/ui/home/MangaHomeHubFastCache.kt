package eu.kanade.tachiyomi.ui.home

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MangaHomeHubFastCache(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var memoryCache: CachedMangaHomeState? = null

    fun load(): CachedMangaHomeState {
        memoryCache?.let { return it }

        val heroJson = prefs.getString(KEY_HERO, null)
        val historyJson = prefs.getString(KEY_HISTORY, null)
        val recommendationsJson = prefs.getString(KEY_RECOMMENDATIONS, null)
        val userName = prefs.getString(KEY_USER_NAME, "Читатель") ?: "Читатель"
        val userAvatar = prefs.getString(KEY_USER_AVATAR, "") ?: ""
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)

        val state = CachedMangaHomeState(
            hero = heroJson?.let { runCatching { json.decodeFromString<CachedMangaHeroItem>(it) }.getOrNull() },
            history = historyJson?.let {
                runCatching { json.decodeFromString<List<CachedMangaHistoryItem>>(it) }.getOrNull()
            } ?: emptyList(),
            recommendations = recommendationsJson?.let {
                runCatching { json.decodeFromString<List<CachedMangaRecommendationItem>>(it) }.getOrNull()
            } ?: emptyList(),
            userName = userName,
            userAvatar = userAvatar,
            isInitialized = initialized,
        )

        memoryCache = state
        return state
    }

    fun save(state: CachedMangaHomeState) {
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
        private const val PREFS_NAME = "manga_home_hub_cache"
        private const val KEY_HERO = "hero"
        private const val KEY_HISTORY = "history"
        private const val KEY_RECOMMENDATIONS = "recommendations"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val KEY_INITIALIZED = "initialized"
    }
}

@Serializable
data class CachedMangaHomeState(
    val hero: CachedMangaHeroItem? = null,
    val history: List<CachedMangaHistoryItem> = emptyList(),
    val recommendations: List<CachedMangaRecommendationItem> = emptyList(),
    val userName: String = "Читатель",
    val userAvatar: String = "",
    val isInitialized: Boolean = false,
) {
    val isEmpty: Boolean
        get() = hero == null && history.isEmpty() && recommendations.isEmpty()
}

@Serializable
data class CachedMangaHeroItem(
    val mangaId: Long,
    val title: String,
    val chapterNumber: Double,
    val coverUrl: String?,
    val coverLastModified: Long,
    val chapterId: Long,
)

@Serializable
data class CachedMangaHistoryItem(
    val mangaId: Long,
    val title: String,
    val chapterNumber: Double,
    val coverUrl: String?,
    val coverLastModified: Long,
)

@Serializable
data class CachedMangaRecommendationItem(
    val mangaId: Long,
    val title: String,
    val coverUrl: String?,
    val coverLastModified: Long,
    val totalCount: Long = 0,
    val unreadCount: Long = 0,
)
