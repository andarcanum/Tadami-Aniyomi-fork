package tachiyomi.data.achievement

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.achievement.model.Reward
import tachiyomi.domain.achievement.model.RewardType
import tachiyomi.domain.achievement.model.UserProfile
import tachiyomi.domain.achievement.repository.UserProfileRepository

/**
 * Менеджер профиля пользователя
 * Управляет XP, уровнями, званиями и наградами
 * Хранит профиль в базе данных через UserProfileRepository
 */
class UserProfileManager(
    private val repository: UserProfileRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        val currentProfile = getCurrentProfile()
        val newTotalXP = currentProfile.totalXP + amount
        val newLevel = UserProfile.getLevelFromXP(newTotalXP)

        val oldLevel = currentProfile.level
        val levelUp = newLevel > oldLevel

        // Обновляем только XP-related поля через специальный метод
        repository.updateXP(
            userId = userId,
            totalXP = newTotalXP,
            currentXP = calculateCurrentLevelXP(newTotalXP, newLevel),
            level = newLevel,
            xpToNextLevel = UserProfile.getXPForLevel(newLevel + 1),
        )

        if (levelUp) {
            logcat(LogPriority.INFO) {
                "[ACHIEVEMENTS] LEVEL UP! $oldLevel -> $newLevel (Total XP: $newTotalXP)"
            }
        }

        return levelUp
    }

    /**
     * Добавить звание профилю
     */
    suspend fun addTitle(title: String) {
        val currentProfile = getCurrentProfile()
        if (currentProfile.titles.contains(title)) return

        repository.addTitle(userId, title)
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Title unlocked: $title" }
    }

    /**
     * Добавить бейдж профилю
     */
    suspend fun addBadge(badge: String) {
        val currentProfile = getCurrentProfile()
        if (currentProfile.badges.contains(badge)) return

        repository.addBadge(userId, badge)
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Badge unlocked: $badge" }
    }

    /**
     * Разблокировать тему
     * @param themeId ID темы для разблокировки
     */
    suspend fun unlockTheme(themeId: String) {
        val currentProfile = getCurrentProfile()
        if (currentProfile.unlockedThemes.contains(themeId)) return

        repository.addTheme(userId, themeId)
        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Theme unlocked: $themeId" }
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
     * Рассчитать XP для текущего уровня
     */
    private fun calculateCurrentLevelXP(totalXP: Int, level: Int): Int {
        var xpNeeded = 0
        for (l in 1 until level) {
            xpNeeded += UserProfile.getXPForLevel(l)
        }
        return totalXP - xpNeeded
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
