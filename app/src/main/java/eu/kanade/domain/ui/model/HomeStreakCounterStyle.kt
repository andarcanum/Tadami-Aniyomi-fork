package eu.kanade.domain.ui.model

enum class HomeStreakCounterStyle(val key: String) {
    ClassicBadge("classic_badge"),
    NumberBadgeOnly("number_badge_only"),
    NoBadge("no_badge"),
    ;

    companion object {
        fun fromKey(key: String): HomeStreakCounterStyle {
            return entries.firstOrNull { it.key == key } ?: NoBadge
        }
    }
}
