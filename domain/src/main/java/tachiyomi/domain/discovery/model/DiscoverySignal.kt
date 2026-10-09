package tachiyomi.domain.discovery.model

/**
 * Taste Learning Engine: вид сигнала взаимодействия с карточкой подборки.
 * Порядок enum = старшинство: более сильный сигнал перезаписывает слабый
 * при повторной записи на тот же тайтл (hide побеждает add и т.д.).
 *
 * CONSUMED — «просмотрено/прочитано»: нейтральное исключение из ленты
 * (тайтл больше не показывается, инкогнито в т.ч.), вес 0 — вкусовой
 * профиль не трогает. Старшинство между like и add: перекрывает клик/лайк,
 * но не добавление в библиотеку и не скрытие.
 *
 * SHOWN_IGNORED — неявный негатив «показано ≥3 раз за 48ч, ни разу не
 * кликнуто»: самый младший (ordinal 0), любой явный сигнал перекрывает.
 * Не пишется в БД — синтезируется в Runner из счётчика показов.
 */
enum class DiscoverySignalType(val key: String, val weight: Double) {
    SHOWN_IGNORED("shown_ignored", -0.15),
    CLICK("click", 0.4),
    CONSUMED("consumed", 0.0),
    LIKE("like", 0.8),
    CONSUMED_LIKED("consumed_liked", 0.8),
    ADD("add", 1.0),
    CONSUMED_ADDED("consumed_added", 1.0),
    HIDE("hide", -1.0),
    ;

    /** «Просмотрено/прочитано»: сигнал исключает тайтл из лент (read-time и генерация). */
    val excludesFromFeed: Boolean
        get() = this == CONSUMED || this == CONSUMED_LIKED || this == CONSUMED_ADDED

    companion object {
        fun fromKey(key: String?): DiscoverySignalType? = entries.firstOrNull { it.key == key }

        fun overrides(previous: DiscoverySignalType?, next: DiscoverySignalType): Boolean =
            previous == null || next.ordinal >= previous.ordinal

        /**
         * Лайк, потребление и добавление ортогональны: пересечения схлопываются в
         * комбинированные consumed_*-состояния (вкус сохраняется, исключение из
         * ленты — тоже): CONSUMED_LIKED (прочитано + лайк, +0.8), CONSUMED_ADDED
         * (прочитано/добавлено в библиотеку, +1.0). Слабые сигналы комбинированные
         * состояния не понижают; HIDE остаётся сильнейшим.
         */
        fun resolveEffectiveSignal(previous: DiscoverySignalType?, next: DiscoverySignalType): DiscoverySignalType =
            when {
                previous == CONSUMED && next == LIKE -> CONSUMED_LIKED
                previous == LIKE && next == CONSUMED -> CONSUMED_LIKED
                previous == CONSUMED && next == ADD -> CONSUMED_ADDED
                previous == ADD && next == CONSUMED -> CONSUMED_ADDED
                previous == CONSUMED_LIKED && next == ADD -> CONSUMED_ADDED
                previous == CONSUMED_ADDED && next.ordinal <= ADD.ordinal -> CONSUMED_ADDED
                previous == CONSUMED_LIKED && next.ordinal <= CONSUMED_LIKED.ordinal -> CONSUMED_LIKED
                else -> next
            }
    }
}

/** Строка `discovery_signals`: снимок взаимодействия с тайтлом подборки. */
data class DiscoverySignal(
    val mediaType: DiscoveryMediaType,
    val cleanTitle: String,
    val title: String,
    val signalType: DiscoverySignalType,
    val genres: List<String>,
    val provider: String?,
    val sourceKey: String?,
    val createdAt: Long,
)
