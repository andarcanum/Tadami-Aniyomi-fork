package tachiyomi.domain.series.model

enum class SeriesCoverMode(val value: Long) {
    AUTO(0),
    ENTRY(1),
    CUSTOM(2),
    ;

    companion object {
        fun from(value: Long): SeriesCoverMode {
            return entries.firstOrNull { it.value == value } ?: AUTO
        }
    }
}
