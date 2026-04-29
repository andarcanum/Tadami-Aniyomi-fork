package tachiyomi.domain.source.model

enum class SourceType(val id: Long) {
    MANGA(0L),
    ANIME(1L),
    NOVEL(2L),
    ;

    companion object {
        fun fromId(id: Long): SourceType {
            return entries.firstOrNull { it.id == id } ?: MANGA
        }
    }
}
