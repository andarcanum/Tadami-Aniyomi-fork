package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import kotlinx.coroutines.delay
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Минимальный rate-governor для внешних API рекомендаций/трендов без собственного
 * guard (паттерн [eu.kanade.tachiyomi.data.suggestions.sources.AniListRequestGuard]).
 *
 * Официальные лимиты: Shikimori 5 rps / 90 rpm; Jikan 3 rps / 60 rpm;
 * MangaUpdates лимиты не публиковала — консервативно. AniList троттлится
 * собственным guard'ом и здесь не дублируется.
 */
object ExternalApiThrottle {

    enum class Api(val minIntervalMs: Long) {
        SHIKIMORI(700L),
        JIKAN(1_100L),
        MANGAUPDATES(400L),
    }

    private val lastCallAt = Api.entries.associateWith { 0L }.toMutableMap()

    internal fun nextWaitMs(lastCallAt: Long, nowMs: Long, minIntervalMs: Long): Long {
        val wait = lastCallAt + minIntervalMs - nowMs
        return if (wait > 0L) wait else 0L
    }

    /** Выдерживает минимальный интервал между вызовами; безопасен при параллельных заходах. */
    suspend fun acquire(api: Api) {
        val waitMs = synchronized(this) {
            val now = System.currentTimeMillis()
            val last = lastCallAt.getValue(api)
            val wait = nextWaitMs(last, now, api.minIntervalMs)
            lastCallAt[api] = now + wait
            wait
        }
        if (waitMs > 0L) delay(waitMs)
    }
}

/**
 * Значение независимого NSFW-фильтра подборок для дефолтов внешних источников.
 * true = фильтровать контент 18+. Без Injekt-реестра (юнит-тесты) — true (fail-safe).
 */
internal fun discoveryNsfwFilterEnabled(): Boolean = runCatching {
    Injekt.get<DiscoveryPreferences>().filterNsfw().get()
}.getOrDefault(true)
