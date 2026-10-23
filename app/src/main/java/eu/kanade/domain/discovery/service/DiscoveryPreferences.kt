package eu.kanade.domain.discovery.service

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

    /** Legacy migration input; new refreshes use [manualRefreshAt]. */
    fun feedManualRefreshAt(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong("discovery_feed_manual_refresh_at_" + mediaType.key, 0L)

    fun manualRefreshWorkId(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString("discovery_manual_refresh_work_id_" + mediaType.key, "")

    fun claimManualRefreshFailure(mediaType: DiscoveryMediaType, requestId: String): Boolean =
        synchronized(manualRefreshLock) {
            if (requestId.isBlank() || manualRefreshWorkId(mediaType).get() != requestId) return@synchronized false
            val shown = preferenceStore.getString("discovery_manual_refresh_failure_shown_" + mediaType.key, "")
            if (shown.get() == requestId) return@synchronized false
            shown.set(requestId)
            true
        }

    /** Legacy migration input; new refreshes use [manualRefreshAt]. */
    fun homeManualRefreshAt(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong("discovery_home_manual_refresh_at_" + mediaType.key, 0L)

    /** Число ручных обновлений per-media для динамической ротации сидов и страниц источников. */
    fun manualRefreshCount(mediaType: DiscoveryMediaType): Preference<Int> =
        preferenceStore.getInt("discovery_manual_refresh_count_" + mediaType.key, 0)

    /** Число фоновых циклов обновления per-media — ротация страниц провайдеров для авто-обновлений. */
    fun backgroundCycleCount(mediaType: DiscoveryMediaType): Preference<Int> =
        preferenceStore.getInt("discovery_background_cycle_count_" + mediaType.key, 0)

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
        preferenceStore.getLong("discovery_bootstrap_attempt_at_" + mediaType.key, 0L)

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
    fun priorityGenres(): Preference<String> =
        preferenceStore.getString("discovery_priority_genres", "")

    /**
     * V3: CSV канонических ключей жанров из [GenreOntology] — «обязательные»:
     * только тайтлы с этими жанрами (best-effort: провайдер без жанров → обычная выдача).
     * Формат: canonical|canonical|raw:«строка» — raw-жанры с префиксом.
     */
    fun requiredGenres(): Preference<String> =
        preferenceStore.getString("discovery_required_genres", "")

    /**
     * V3: CSV игнорируемых жанров подборки (глобально, поверх per-media блэклиста тегов):
     * тайтлы с этими жанрами не проходят в ряды. Формат как у priority/required.
     */
    fun ignoredGenres(): Preference<String> =
        preferenceStore.getString("discovery_ignored_genres", "")

    /** CSV ключей рядов, упавших при последней генерации (для баннера «показан кэш»). */
    fun lastFailedRows(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString("discovery_failed_rows_" + mediaType.key, "")

    /** Статус последней генерации per-media: "SUCCESS", "PARTIAL", "FAILED", "SKIPPED", "" (не запускалось). */
    fun lastGenerationStatus(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString("discovery_last_gen_status_" + mediaType.key, "")

    /** Время завершения последней попытки генерации per-media. */
    fun lastGenerationAt(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong("discovery_last_gen_at_" + mediaType.key, 0L)

    /** Режим участия плагинов в подборках: "auto" (топ-3 по весу библиотеки) | "manual" (свой набор). */
    fun discoverySourceMode(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString("discovery_source_mode_" + mediaType.key, "auto")

    /**
     * CSV исключённых ключей плагинов (pkgName расширений / id novel-плагинов):
     * хранятся ВЫКЛЮЧЕННЫЕ — новый плагин участвует автоматически.
     * Ключ объединяет все языковые варианты расширения. Учитывается в обоих режимах.
     */
    fun discoverySourceExcluded(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString("discovery_source_excluded_" + mediaType.key, "")

    /**
     * Version code, при котором уже выполнен backfill-чек привязок подборок:
     * после апгрейда Home разово проверяет кэш на SOURCE-строки без source_id
     * и при наличии запрашивает тихую перегенерацию ленты.
     */
    fun bindingBackfillVersion(): Preference<Int> =
        preferenceStore.getInt("discovery_binding_backfill_version", 0)

    fun teaserCount(): Preference<Int> = preferenceStore.getInt("discovery_teaser_count", 16)
    fun showReasons(): Preference<Boolean> = preferenceStore.getBoolean("discovery_show_reasons", true)

    fun collageRotationIntervalHours(): Preference<Int> = preferenceStore.getInt("collage_rotation_interval_hours", 2)
    fun collageAnimationSpeed(): Preference<String> = preferenceStore.getString("collage_animation_speed", "normal")
    fun collageLastRotationTime(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong("collage_last_rotation_time_" + mediaType.key, 0L)
    fun collageOffset(mediaType: DiscoveryMediaType): Preference<Int> =
        preferenceStore.getInt("collage_offset_" + mediaType.key, 0)

    fun stageRotationIntervalHours(): Preference<Int> = preferenceStore.getInt("stage_rotation_interval_hours", 2)
    fun stageAnimationSpeed(): Preference<String> = preferenceStore.getString("stage_animation_speed", "normal")
    fun stageLastRotationTime(mediaType: DiscoveryMediaType): Preference<Long> =
        preferenceStore.getLong("stage_last_rotation_time_" + mediaType.key, 0L)
    fun stageOffset(mediaType: DiscoveryMediaType): Preference<Int> =
        preferenceStore.getInt("stage_offset_" + mediaType.key, 0)

    companion object {
        private val manualRefreshLock = Any()

        const val MANUAL_REFRESH_COOLDOWN_MS = 5 * 60_000L
    }
}
