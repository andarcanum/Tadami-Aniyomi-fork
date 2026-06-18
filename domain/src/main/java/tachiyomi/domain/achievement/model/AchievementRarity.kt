package tachiyomi.domain.achievement.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Rarity tier of an achievement. Drives banner styling (fireworks/glow),
 * grid badges and sorting. Replaces the old "points >= 50 means rare" heuristic.
 */
@Immutable
@Serializable
enum class AchievementRarity {
    @SerialName("common")
    COMMON,

    @SerialName("uncommon")
    UNCOMMON,

    @SerialName("rare")
    RARE,

    @SerialName("epic")
    EPIC,

    @SerialName("legendary")
    LEGENDARY,

    @SerialName("mythic")
    MYTHIC,
    ;

    /** Whether this rarity should get the celebratory "rare" banner treatment. */
    val isCelebrated: Boolean
        get() = this >= RARE

    companion object {
        fun fromString(value: String?): AchievementRarity {
            if (value.isNullOrBlank()) return COMMON
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: COMMON
        }
    }
}
