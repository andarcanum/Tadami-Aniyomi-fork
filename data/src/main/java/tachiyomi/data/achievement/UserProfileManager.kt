package tachiyomi.data.achievement

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.achievement.model.Reward
import tachiyomi.domain.achievement.model.RewardType
import tachiyomi.domain.achievement.model.UserProfile
import tachiyomi.domain.achievement.repository.UserProfileRepository

class UserProfileManager(
    private val repository: UserProfileRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutationMutex = Mutex()

    private val userId = "default"

    val profile: Flow<UserProfile?> = repository.getProfile(userId)

    /**
     * Получить текущий профиль
     */
    suspend fun getCurrentProfile(): UserProfile {
        return repository.getProfileSync(userId) ?: UserProfile.createDefault().also {
            // Инициализация профиля при первом запуске
            repository.saveProfile(it)
        }
    }

    /**
     * Добавить XP профилю
     * @return true если уровень повысился
     */
    suspend fun addXP(amount: Int): Boolean {
        if (amount <= 0) return false
        return repository.addXP(userId, amount)
    }

    suspend fun addTitle(title: String) {
        mutationMutex.withLock {
            val currentProfile = getCurrentProfile()
            if (currentProfile.titles.contains(title)) return@withLock

            repository.addTitle(userId, title)
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Title unlocked: $title" }
        }
    }

    suspend fun addBadge(badge: String) {
        mutationMutex.withLock {
            val currentProfile = getCurrentProfile()
            if (currentProfile.badges.contains(badge)) return@withLock

            repository.addBadge(userId, badge)
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Badge unlocked: $badge" }
        }
    }

    suspend fun removeBadge(badge: String) {
        mutationMutex.withLock {
            val currentProfile = getCurrentProfile()
            if (!currentProfile.badges.contains(badge)) return@withLock

            repository.removeBadge(userId, badge)
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Badge removed: $badge" }
        }
    }

    suspend fun unlockTheme(themeId: String) {
        mutationMutex.withLock {
            val currentProfile = getCurrentProfile()
            if (currentProfile.unlockedThemes.contains(themeId)) return@withLock

            repository.addTheme(userId, themeId)
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Theme unlocked: $themeId" }
        }
    }

    suspend fun removeTheme(themeId: String) {
        mutationMutex.withLock {
            val currentProfile = getCurrentProfile()
            if (!currentProfile.unlockedThemes.contains(themeId)) return@withLock

            repository.removeTheme(userId, themeId)
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Theme removed: $themeId" }
        }
    }

    /**
     * Получить список ID разблокированных тем
     */
    suspend fun getUnlockedThemes(): List<String> {
        return getCurrentProfile().unlockedThemes
    }

    /**
     * Выдать награды за достижение
     */
    suspend fun grantRewards(rewards: List<Reward>) {
        rewards.forEach { reward ->
            when (reward.type) {
                RewardType.EXPERIENCE -> {
                    addXP(reward.value)
                }
                RewardType.TITLE -> {
                    addTitle(reward.title)
                }
                RewardType.BADGE -> {
                    addBadge(reward.title)
                }
                RewardType.THEME -> {
                    // Используем reward.id как ID темы
                    unlockTheme(reward.id.removePrefix("theme_"))
                }
                RewardType.AURA -> {
                    // Auras are handled by UnlockableManager/Preferences
                    logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Aura unlocked: ${reward.title}" }
                }
                RewardType.SPECIAL -> {
                    // Специальные награды обрабатываются отдельно
                    logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Special reward: ${reward.title}" }
                }
            }
        }
    }

    /**
     * Обновить количество разблокированных достижений
     */
    suspend fun updateAchievementsCount(unlocked: Int, total: Int) {
        repository.updateAchievementCounts(userId, unlocked, total)
    }

    /**
     * Загрузить профиль из базы данных
     * Если профиль не существует, создается дефолтный
     */
    suspend fun loadProfile() {
        val existingProfile = repository.getProfileSync(userId)
        if (existingProfile == null) {
            val defaultProfile = UserProfile.createDefault()
            repository.saveProfile(defaultProfile)
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Created default profile" }
        } else {
            logcat(LogPriority.INFO) {
                "[ACHIEVEMENTS] Loaded profile: level ${existingProfile.level}, XP ${existingProfile.totalXP}"
            }
        }
    }

    /**
     * Восстановить профиль из бэкапа
     * Используется при восстановлении данных
     */
    suspend fun restoreProfile(profile: UserProfile) {
        repository.saveProfile(profile)
        logcat(LogPriority.INFO) {
            "[ACHIEVEMENTS] Profile restored: level ${profile.level}, XP ${profile.totalXP}"
        }
    }

    /**
     * Удалить профиль пользователя
     * Используется для сброса данных
     */
    suspend fun deleteProfile() {
        repository.deleteProfile(userId)
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Profile deleted" }
    }
}
