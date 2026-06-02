package tachiyomi.domain.library.model

enum class LibraryTrackStatus(val int: Int) {
    READING(1),
    REPEATING(2),
    COMPLETED(3),
    ON_HOLD(4),
    DROPPED(5),
    PLAN_TO_READ(6),
    OTHER(7),
}
