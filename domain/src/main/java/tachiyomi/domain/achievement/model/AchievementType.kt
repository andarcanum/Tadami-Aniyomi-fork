package tachiyomi.domain.achievement.model

enum class AchievementType {
    QUANTITY,
    EVENT,
    DIVERSITY,
    STREAK,
    LIBRARY,
    META,
    BALANCED,
    SECRET,
    TIME_BASED, // Достижения по времени (ночь, утро, длительность сессии)
    FEATURE_BASED, // Достижения за использование функций (поиск, фильтры, скачивание)
}
