package tachiyomi.domain.achievement.model

import androidx.compose.runtime.Immutable

/**
 * Профиль пользователя с информацией о прогрессе и наградах
 *
 * @property userId ID пользователя
 * @property username Имя пользователя
 * @property level Уровень профиля
 * @property currentXP Текущий опыт
 * @property xpToNextLevel Опыт до следующего уровня
 * @property totalXP Всего получено опыта
 * @property titles Список полученных званий
 * @property badges Список полученных бейджей
 * @property unlockedThemes Список разблокированных тем
 * @property achievementsUnlocked Количество разблокированных достижений
 * @property totalAchievements Общее количество достижений
 * @property joinDate Дата регистрации
 */
@Immutable
data class UserProfile(
    val userId: String = "default",
    val username: String? = null,
    val level: Int = 1,
    val currentXP: Int = 0,
    val xpToNextLevel: Int = 100,
    val totalXP: Int = 0,
    val titles: List<String> = emptyList(),
    val badges: List<String> = emptyList(),
    val unlockedThemes: List<String> = emptyList(),
    val achievementsUnlocked: Int = 0,
    val totalAchievements: Int = 0,
    val joinDate: Long = System.currentTimeMillis(),
) {
    /**
     * Процент выполнения до следующего уровня (0.0 - 1.0)
     */
    val levelProgress: Float
        get() = if (xpToNextLevel > 0) {
            currentXP.toFloat() / xpToNextLevel.toFloat()
        } else {
            0f
        }

    /**
     * Текущее звание (активное)
     */
    val activeTitle: String?
        get() = titles.firstOrNull()

    /**
     * Проверить, имеет ли пользователь звание
     */
    fun hasTitle(title: String): Boolean {
        return titles.contains(title)
    }

    /**
     * Проверить, имеет ли пользователь бейдж
     */
    fun hasBadge(badge: String): Boolean {
        return badges.contains(badge)
    }

    /**
     * Проверить, разблокирована ли тема
     */
    fun hasTheme(theme: String): Boolean {
        return unlockedThemes.contains(theme)
    }

    /**
     * Получить название уровня
     */
    fun getLevelName(): String {
        return when {
            level >= 100 -> "Легенда"
            level >= 50 -> "Мастер"
            level >= 25 -> "Эксперт"
            level >= 10 -> "Ветеран"
            level >= 5 -> "Опытный"
            else -> "Новичок"
        }
    }

    companion object {
        /**
         * Расчет опыта для следующего уровня
         * Формула: XP для уровня N = 100 * N^1.5
         */
        fun getXPForLevel(level: Int): Int {
            return (100 * Math.pow(level.toDouble(), 1.5)).toInt()
        }

        /**
         * Расчет уровня по общему XP
         */
        fun getLevelFromXP(totalXP: Int): Int {
            var level = 1
            var xpNeeded = 0
            while (xpNeeded <= totalXP) {
                level++
                xpNeeded += getXPForLevel(level)
            }
            return (level - 1).coerceAtLeast(1)
        }

        /**
         * Создать профиль по умолчанию
         */
        fun createDefault(userId: String = "default"): UserProfile {
            return UserProfile(
                userId = userId,
                level = 1,
                currentXP = 0,
                xpToNextLevel = getXPForLevel(2),
                totalXP = 0,
            )
        }
    }
}

/**
 * Статистика профиля для отображения на экране
 */
@Immutable
data class ProfileStats(
    val totalReadTime: Long = 0, // Общее время чтения (мс)
    val totalChaptersRead: Int = 0, // Всего прочитано глав
    val totalEpisodesWatched: Int = 0, // Всего просмотрено серий
    val totalDownloads: Int = 0, // Всего скачиваний
    val totalSearches: Int = 0, // Всего поисков
    val longestStreak: Int = 0, // Самая длинная серия (дней)
    val currentStreak: Int = 0, // Текущая серия (дней)
    val mangaInLibrary: Int = 0, // Манги в библиотеке
    val animeInLibrary: Int = 0, // Аниме в библиотеке
    val favoriteGenre: String? = null, // Любимый жанр
    val rarestAchievements: List<String> = emptyList(), // Редчайшие достижения
) {
    /**
     * Общее количество тайтлов в библиотеке
     */
    val totalLibrarySize: Int
        get() = mangaInLibrary + animeInLibrary
}
