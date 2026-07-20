package eu.kanade.tachiyomi.ui.home

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

internal class HomeHubFastCache(context: Context, section: HomeHubSection) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        when (section) {
            HomeHubSection.Anime -> "home_hub_cache"
            HomeHubSection.Manga -> "manga_home_hub_cache"
            HomeHubSection.Novel -> "novel_home_hub_cache"
        },
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var memoryCache: CachedHomeState? = null

    fun load(): CachedHomeState {
        memoryCache?.let { return it }

        val heroJson = prefs.getString(KEY_HERO, null)
        val historyJson = prefs.getString(KEY_HISTORY, null)
        val recommendationsJson = prefs.getString(KEY_RECOMMENDATIONS, null)
        val userName = prefs.getString(KEY_USER_NAME, "") ?: ""
        val userAvatar = prefs.getString(KEY_USER_AVATAR, "") ?: ""
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)

        val state = CachedHomeState(
            hero = heroJson?.let { runCatching { json.decodeFromString<CachedHeroItem>(it) }.getOrNull() },
            history = historyJson?.let {
                runCatching { json.decodeFromString<List<CachedHistoryItem>>(it) }.getOrNull()
            } ?: emptyList(),
            recommendations = recommendationsJson?.let {
                runCatching { json.decodeFromString<List<CachedRecommendationItem>>(it) }.getOrNull()
            } ?: emptyList(),
            userName = userName,
            userAvatar = userAvatar,
            isInitialized = initialized,
        )

        memoryCache = state
        return state
    }

    fun save(state: CachedHomeState) {
        if (memoryCache == state) return

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

    fun loadCachedState(): CachedHomeState = load()

    companion object {
        private const val KEY_HERO = "hero"
        private const val KEY_HISTORY = "history"
        private const val KEY_RECOMMENDATIONS = "recommendations"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val KEY_INITIALIZED = "initialized"
    }
}

@Serializable
data class CachedHomeState(
    val hero: CachedHeroItem? = null,
    val history: List<CachedHistoryItem> = emptyList(),
    val recommendations: List<CachedRecommendationItem> = emptyList(),
    val userName: String = "",
    val userAvatar: String = "",
    val isInitialized: Boolean = false,
) {
    val isEmpty: Boolean
        get() = hero == null && history.isEmpty() && recommendations.isEmpty()
}

@Serializable
data class CachedHeroItem(
    @JsonNames("animeId", "mangaId", "novelId")
    val entryId: Long,
    val title: String,
    @JsonNames("episodeNumber", "chapterNumber")
    val progressNumber: Double,
    val coverUrl: String?,
    val coverLastModified: Long,
    @JsonNames("episodeId", "chapterId")
    val subId: Long,
    val sourceId: Long = -1L,
    val favorite: Boolean = false,
)

@Serializable
data class CachedHistoryItem(
    @JsonNames("animeId", "mangaId", "novelId")
    val entryId: Long,
    val title: String,
    @JsonNames("episodeNumber", "chapterNumber")
    val progressNumber: Double,
    val coverUrl: String?,
    val coverLastModified: Long,
    val sourceId: Long = -1L,
    val favorite: Boolean = false,
)

@Serializable
data class CachedRecommendationItem(
    @JsonNames("animeId", "mangaId", "novelId")
    val entryId: Long,
    val title: String,
    val coverUrl: String?,
    val coverLastModified: Long,
    val totalCount: Long = 0,
    @JsonNames("seenCount", "readCount")
    val progressCount: Long = 0,
    val unreadCount: Long? = null,
    val sourceId: Long = -1L,
    val favorite: Boolean = false,
) {
    val progressNumerator: Long
        get() = unreadCount?.let { totalCount - it } ?: progressCount
}
