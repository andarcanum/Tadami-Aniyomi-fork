package eu.kanade.domain.discovery.service

import eu.kanade.tachiyomi.data.discovery.GenreFilterSpec
import eu.kanade.tachiyomi.data.discovery.RequiredEnforcement
import eu.kanade.tachiyomi.data.discovery.RequiredMatchMode
import eu.kanade.tachiyomi.data.discovery.effective
import eu.kanade.tachiyomi.data.discovery.parseGenreFilterCsv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class DiscoveryPreferences(private val preferenceStore: PreferenceStore) {

    fun manualRefreshAt(mediaType: DiscoveryMediaType): Preference<Long> = synchronized(manualRefreshLock) {
        val shared = preferenceStore.getLong("discovery_manual_refresh_at_" + mediaType.key, 0L)
        val migrated = preferenceStore.getBoolean("discovery_manual_refresh_migrated_" + mediaType.key, false)
        if (!migrated.get()) {
            val home = homeManualRefreshAt(mediaType)
            val feed = feedManualRefreshAt(mediaType)
            shared.set(maxOf(shared.get(), home.get(), feed.get()))
            home.delete()
            feed.delete()
            migrated.set(true)
        }
        shared
    }

    fun clearManualRefreshAt(mediaType: DiscoveryMediaType) = synchronized(manualRefreshLock) {
        manualRefreshAt(mediaType).set(0L)
    }

    fun tryStartManualRefresh(mediaType: DiscoveryMediaType, now: Long): Boolean = synchronized(manualRefreshLock) {
        val timestamp = manualRefreshAt(mediaType)
        val previous = timestamp.get()
        if (previous > 0L && now - previous in 0 until MANUAL_REFRESH_COOLDOWN_MS) return@synchronized false
        timestamp.set(now)
        true
    }

    fun discoveryEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_enabled", true)
    fun homeHeroMode(): Preference<String> = preferenceStore.getString("home_hero_mode", "auto")

    fun rowLikeEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_like", true)
    fun rowTasteEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_taste", true)
    fun rowTrendEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_trend", true)
    fun rowSourceEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_source", true)

    fun seedCount(): Preference<Int> = preferenceStore.getInt("discovery_seed_count", 3)
    fun seedCompleted(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_completed", true)
    fun seedActive14(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_active14", true)
    fun seedAdded(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_added", false)
    fun seedCompletedDays(): Preference<Int> = preferenceStore.getInt("discovery_seed_completed_days", 30)
    fun seedActiveDays(): Preference<Int> = preferenceStore.getInt("discovery_seed_active_days", 14)

    /** Slice 2: требует join с треками; до этого в настройках не показывать. */
    fun seedRated(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_rated", false)

    fun trendSeason(): Preference<String> = preferenceStore.getString("discovery_trend_season", "current")
    fun trendSort(): Preference<String> = preferenceStore.getString("discovery_trend_sort", "popularity")

    fun refreshIntervalHours(): Preference<Int> = preferenceStore.getInt("discovery_refresh_interval", 12)
    fun refreshAfterLibrary(): Preference<Boolean> = preferenceStore.getBoolean("discovery_refresh_after_library", true)
    fun refreshWifiOnly(): Preference<Boolean> = preferenceStore.getBoolean("discovery_refresh_wifi_only", false)

    /**
     * When true, any consumed title triggers background top-up back to [DISCOVERY_POOL_TARGET] (60),
     * keeping the feed continuously full instead of waiting for low-water (48).
     */
    fun eagerRefill(): Preference<Boolean> = preferenceStore.getBoolean("discovery_eager_refill", true)

    /** Legacy migration input; new refreshes use [manualRefreshAt]. */
    fun feedManualRefreshAt(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong("discovery_feed_manual_refresh_at_" + mediaType.key, 0L)

    fun manualRefreshWorkId(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString(Preference.appStateKey("discovery_manual_refresh_work_id_" + mediaType.key), "")

    fun claimManualRefreshFailure(mediaType: DiscoveryMediaType, requestId: String): Boolean =
        synchronized(manualRefreshLock) {
            if (requestId.isBlank() || manualRefreshWorkId(mediaType).get() != requestId) return@synchronized false
            val shown = preferenceStore.getString(
                Preference.appStateKey("discovery_manual_refresh_failure_shown_" + mediaType.key),
                "",
            )
            if (shown.get() == requestId) return@synchronized false
            shown.set(requestId)
            true
        }

    /** Legacy migration input; new refreshes use [manualRefreshAt]. */
    fun homeManualRefreshAt(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong("discovery_home_manual_refresh_at_" + mediaType.key, 0L)

    /** Число ручных обновлений per-media для динамической ротации сидов и страниц источников. */
    fun manualRefreshCount(mediaType: DiscoveryMediaType): Preference<Int> =
        preferenceStore.getInt(Preference.appStateKey("discovery_manual_refresh_count_" + mediaType.key), 0)

    /** Число фоновых циклов обновления per-media — ротация страниц провайдеров для авто-обновлений. */
    fun backgroundCycleCount(mediaType: DiscoveryMediaType): Preference<Int> =
        preferenceStore.getInt(Preference.appStateKey("discovery_background_cycle_count_" + mediaType.key), 0)

    /**
     * Внешние сервисы рекомендаций (AniList, Shikimori, MAL, Jikan, MangaDex, MU/NU):
     * false — подборки строятся только из каталогов установленных плагинов;
     * ряды «Похоже» и «Тренды» (чисто внешние) не генерируются и чистятся из кэша.
     */
    fun externalProvidersEnabled(): Preference<Boolean> =
        preferenceStore.getBoolean("discovery_external_providers", true)

    /**
     * Момент последней попытки bootstrap-генерации ленты медиатипа (анти-шторм:
     * при стойких неудачах генерации вход на вкладку не должен перезапускать её).
     */
    fun bootstrapAttemptAt(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong(Preference.appStateKey("discovery_bootstrap_attempt_at_" + mediaType.key), 0L)

    /**
     * Независимый фильтр контента 18+ во внешних провайдерах подборок.
     * Игнорирует общесистемную NSFW-настройку приложения: включён — фильтрует всегда.
     * Каталоги плагинов (ряд SOURCE) регулируются общими настройками приложения.
     */
    fun filterNsfw(): Preference<Boolean> = preferenceStore.getBoolean("discovery_filter_nsfw", true)

    /**
     * V1: CSV ключей статусов выпуска для трендов (DiscoveryReleaseStatus.key),
     * пусто = все статусы (фильтр выключен). Действует серверно у AniList/Shikimori
     * при следующем обновлении ленты; ряды «похоже»/источников статусов не знают.
     */
    fun releaseStatusFilter(): Preference<String> =
        preferenceStore.getString("discovery_release_status_filter", "")

    /**
     * V3: CSV канонических ключей жанров из [GenreOntology] — «приоритетные»:
     * тайтлы с этими жанрами поднимаются выше в миксе. Не отсекают остальное.
     * Формат: canonical|canonical|raw:«строка» — raw-жанры с префиксом.
     */
    fun priorityGenres(mediaType: DiscoveryMediaType): Preference<String> =
        mediaGenrePreference(PRIORITY_GENRES_KEY, mediaType)

    /**
     * V3: CSV канонических ключей жанров из [GenreOntology] — «обязательные»:
     * только тайтлы с этими жанрами (best-effort: провайдер без жанров → обычная выдача).
     * Формат: canonical|canonical|raw:«строка» — raw-жанры с префиксом.
     */
    fun requiredGenres(mediaType: DiscoveryMediaType): Preference<String> =
        mediaGenrePreference(REQUIRED_GENRES_KEY, mediaType)

    /**
     * V3: CSV игнорируемых жанров подборки (поверх per-media блэклиста тегов):
     * тайтлы с этими жанрами не проходят в ряды. Формат как у priority/required.
     */
    fun ignoredGenres(mediaType: DiscoveryMediaType): Preference<String> =
        mediaGenrePreference(IGNORED_GENRES_KEY, mediaType)

    /** G01: Режим сопоставления обязательных жанров ("ALL" | "ANY"). По умолчанию "ALL". */
    fun requiredMatchMode(): Preference<String> =
        preferenceStore.getString("discovery_required_match_mode", "ALL")

    /** G01: Режим строгости обязательных жанров ("STRICT" | "BEST_EFFORT"). По умолчанию "STRICT". */
    fun requiredEnforcement(): Preference<String> =
        preferenceStore.getString("discovery_required_enforcement", "STRICT")

    fun genreFilterSpec(mediaType: DiscoveryMediaType): GenreFilterSpec = createGenreFilterSpec(
        ignoredGenres(mediaType).get(),
        requiredGenres(mediaType).get(),
        priorityGenres(mediaType).get(),
        requiredMatchMode().get(),
        requiredEnforcement().get(),
    )

    fun observeGenreFilterSpec(mediaType: DiscoveryMediaType): Flow<GenreFilterSpec> = combine(
        ignoredGenres(mediaType).changes(),
        requiredGenres(mediaType).changes(),
        priorityGenres(mediaType).changes(),
        requiredMatchMode().changes(),
        requiredEnforcement().changes(),
        ::createGenreFilterSpec,
    )

    private fun mediaGenrePreference(
        legacyKey: String,
        mediaType: DiscoveryMediaType,
    ): Preference<String> {
        migrateGenrePreferencesIfNeeded()
        return preferenceStore.getString("${legacyKey}_${mediaType.key}", "")
    }

    @Volatile
    private var genrePreferencesMigrated = false

    private fun migrateGenrePreferencesIfNeeded() {
        if (genrePreferencesMigrated) return
        synchronized(genreFilterMigrationLock) {
            if (genrePreferencesMigrated) return
            val migrated = preferenceStore.getBoolean(
                Preference.appStateKey(GENRE_FILTER_MIGRATION_MARKER),
                false,
            )
            if (!migrated.get()) {
                migrateGenrePreference(PRIORITY_GENRES_KEY)
                migrateGenrePreference(REQUIRED_GENRES_KEY)
                migrateGenrePreference(IGNORED_GENRES_KEY)
                migrated.set(true)
            }
            genrePreferencesMigrated = true
        }
    }

    private fun migrateGenrePreference(legacyKey: String) {
        val legacyValue = preferenceStore.getString(legacyKey, "").get()
        DiscoveryMediaType.entries.forEach { mediaType ->
            val target = preferenceStore.getString("${legacyKey}_${mediaType.key}", "")
            if (!target.isSet()) {
                target.set(if (mediaType == DiscoveryMediaType.MANGA) legacyValue else "")
            }
        }
    }

    private fun createGenreFilterSpec(
        ignoredCsv: String,
        requiredCsv: String,
        priorityCsv: String,
        matchModeValue: String,
        enforcementValue: String,
    ): GenreFilterSpec {
        val (ignCanon, ignRaw) = parseGenreFilterCsv(ignoredCsv)
        val (reqCanon, reqRaw) = parseGenreFilterCsv(requiredCsv)
        val (priCanon, priRaw) = parseGenreFilterCsv(priorityCsv)
        val matchMode = runCatching {
            RequiredMatchMode.valueOf(matchModeValue)
        }.getOrDefault(RequiredMatchMode.ALL)
        val enforcement = runCatching {
            RequiredEnforcement.valueOf(enforcementValue)
        }.getOrDefault(RequiredEnforcement.STRICT)
        return GenreFilterSpec(
            ignoredGenres = ignCanon + ignRaw.toSet(),
            requiredGenres = reqCanon + reqRaw.toSet(),
            priorityGenres = priCanon + priRaw.toSet(),
            requiredMatchMode = matchMode,
            requiredEnforcement = enforcement,
        ).effective()
    }

    /** CSV ключей рядов, упавших при последней генерации (для баннера «показан кэш»). */
    fun lastFailedRows(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString(Preference.appStateKey("discovery_failed_rows_" + mediaType.key), "")

    /** Число source-level сбоев последней генерации; app-state, не входит в backup. */
    fun lastSourceFailureCount(mediaType: DiscoveryMediaType): Preference<Int> =
        preferenceStore.getInt(Preference.appStateKey("discovery_source_failures_" + mediaType.key), 0)

    /** История сбоев источников (sourceId:timestamp:count) per-media; app-state, не входит в backup. */
    fun sourceFailureHistory(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString(Preference.appStateKey("discovery_source_fail_history_" + mediaType.key), "")

    /** Статус последней генерации per-media: "SUCCESS", "PARTIAL", "FAILED", "SKIPPED", "" (не запускалось). */
    fun lastGenerationStatus(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString(Preference.appStateKey("discovery_last_gen_status_" + mediaType.key), "")

    /** Время завершения последней попытки генерации per-media. */
    fun lastGenerationAt(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong(Preference.appStateKey("discovery_last_gen_at_" + mediaType.key), 0L)

    fun lastTopUpAt(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong(Preference.appStateKey("discovery_last_top_up_at_" + mediaType.key), 0L)

    fun lastTopUpAdded(mediaType: DiscoveryMediaType): Preference<Int> =
        preferenceStore.getInt(Preference.appStateKey("discovery_last_top_up_added_" + mediaType.key), -1)

    /** Keeps synchronous source preference mutations atomic across consumers and preference instances. */
    internal fun <T> withSourcePreferencesLock(mediaType: DiscoveryMediaType, block: () -> T): T =
        synchronized(sourcePreferenceLocks.getValue(mediaType), block)

    /** Режим участия плагинов в подборках: "auto" (топ-3 по весу библиотеки) | "manual" (свой набор). */
    fun discoverySourceMode(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString("discovery_source_mode_" + mediaType.key, "auto")

    /**
     * CSV исключённых ключей плагинов (pkgName расширений / id novel-плагинов):
     * хранятся выключенные ключи. В ручном режиме (`manual`) вновь установленный плагин,
     * отсутствующий в [discoverySourceKnown], автоматически попадает сюда и не добавляется в подборку.
     * Ключ объединяет все языковые варианты расширения. Учитывается в обоих режимах.
     */
    fun discoverySourceExcluded(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString("discovery_source_excluded_" + mediaType.key, "")

    /**
     * CSV ключей плагинов, уже зафиксированных для ручной выборки на [mediaType].
     * Пустая строка означает, что снимок ещё не зафиксирован; после фиксации новые плагины
     * автоматически добавляются в [discoverySourceExcluded].
     */
    fun discoverySourceKnown(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString("discovery_source_known_" + mediaType.key, "")

    /** Последний выбранный раздел на экране источников подборок (app-state, не входит в backup). */
    fun lastSourcesMediaType(): Preference<String> = preferenceStore.getString(
        Preference.appStateKey("discovery_sources_media_type"),
        DiscoveryMediaType.ANIME.key,
    )

    /**
     * Version code, при котором уже выполнен backfill-чек привязок подборок:
     * после апгрейда Home разово проверяет кэш на SOURCE-строки без source_id
     * и при наличии запрашивает тихую перегенерацию ленты.
     */
    fun bindingBackfillVersion(): Preference<Int> =
        preferenceStore.getInt(Preference.appStateKey("discovery_binding_backfill_version"), 0)

    fun retirementResetVersion(): Preference<Int> =
        preferenceStore.getInt(Preference.appStateKey("discovery_retirement_reset_version"), 0)

    /** Последний выбранный раздел в диалоге жанровых фильтров (app-state, не входит в backup). */
    fun lastGenreDialogMediaType(): Preference<String> = preferenceStore.getString(
        Preference.appStateKey("discovery_genre_dialog_media_type"),
        DiscoveryMediaType.ANIME.key,
    )

    fun teaserCount(): Preference<Int> = preferenceStore.getInt("discovery_teaser_count", 16)
    fun showReasons(): Preference<Boolean> = preferenceStore.getBoolean("discovery_show_reasons", true)

    fun collageRotationIntervalHours(): Preference<Int> = preferenceStore.getInt("collage_rotation_interval_hours", 2)
    fun collageAnimationSpeed(): Preference<String> = preferenceStore.getString("collage_animation_speed", "normal")
    fun collageLastRotationTime(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong(Preference.appStateKey("collage_last_rotation_time_" + mediaType.key), 0L)
    fun collageOffset(mediaType: DiscoveryMediaType): Preference<Int> =
        preferenceStore.getInt(Preference.appStateKey("collage_offset_" + mediaType.key), 0)

    fun stageRotationIntervalHours(): Preference<Int> = preferenceStore.getInt("stage_rotation_interval_hours", 2)
    fun stageAnimationSpeed(): Preference<String> = preferenceStore.getString("stage_animation_speed", "normal")
    fun stageLastRotationTime(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong(Preference.appStateKey("stage_last_rotation_time_" + mediaType.key), 0L)
    fun stageOffset(mediaType: DiscoveryMediaType): Preference<Int> =
        preferenceStore.getInt(Preference.appStateKey("stage_offset_" + mediaType.key), 0)

    companion object {
        private const val PRIORITY_GENRES_KEY = "discovery_priority_genres"
        private const val REQUIRED_GENRES_KEY = "discovery_required_genres"
        private const val IGNORED_GENRES_KEY = "discovery_ignored_genres"
        private const val GENRE_FILTER_MIGRATION_MARKER = "discovery_genre_filters_per_media_migrated"

        private val manualRefreshLock = Any()
        private val genreFilterMigrationLock = Any()
        private val sourcePreferenceLocks = DiscoveryMediaType.entries.associateWith { Any() }

        const val MANUAL_REFRESH_COOLDOWN_MS = 5 * 60_000L
        const val CURRENT_RETIREMENT_RESET_VERSION = 1

        private val runtimeStatePrefixes = listOf(
            "discovery_manual_refresh_",
            "discovery_feed_manual_refresh_at_",
            "discovery_home_manual_refresh_at_",
            "discovery_background_cycle_count_",
            "discovery_bootstrap_attempt_at_",
            "discovery_failed_rows_",
            "discovery_source_failures_",
            "discovery_source_fail_history_",
            "discovery_last_gen_",
            "discovery_last_top_up_",
            "discovery_binding_backfill_version",
            "discovery_retirement_reset_version",
            "discovery_sources_media_type",
            "discovery_genre_dialog_media_type",
            "discovery_genre_filters_per_media_migrated",
            "collage_last_rotation_time_",
            "collage_offset_",
            "stage_last_rotation_time_",
            "stage_offset_",
        )

        private val globalGenerationConfigPrefixes = listOf(
            "discovery_enabled",
            "discovery_external_providers",
            "discovery_filter_nsfw",
            "discovery_release_status_filter",
            "discovery_required_match_mode",
            "discovery_required_enforcement",
            "discovery_row_",
            "discovery_seed_",
            "discovery_trend_",
        )

        fun isRuntimeStateKey(key: String): Boolean {
            if (Preference.isAppState(key)) return true
            return runtimeStatePrefixes.any { key.startsWith(it) }
        }

        fun isGenerationConfigKey(key: String): Boolean =
            globalGenerationConfigPrefixes.any { key.startsWith(it) }
    }
}
