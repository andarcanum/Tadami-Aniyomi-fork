package eu.kanade.domain.entries.rating

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class EntryRatingCache(
    private val sharedPreferences: SharedPreferences? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val preferences by lazy {
        sharedPreferences ?: Injekt.get<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val mutex = Mutex()
    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = mutableMapOf<String, Deferred<Float?>>()

    suspend fun resolve(
        contentType: String,
        sourceName: String,
        url: String,
        forceRefresh: Boolean = false,
        loader: suspend () -> Float?,
    ): Float? {
        val key = buildKey(contentType, sourceName, url)
        val cached = read(key)
        if (cached != null && !forceRefresh && cached.isFresh(nowMillis())) {
            return cached.rating
        }

        val fresh = loadOnce(key, loader)

        mutex.withLock {
            when {
                fresh != null -> write(key, fresh)
                cached == null || cached.isEmptyRatingStale(nowMillis()) -> write(key, fresh)
            }
        }

        return fresh ?: cached?.rating
    }

    fun peek(
        contentType: String,
        sourceName: String,
        url: String,
    ): Float? {
        return read(buildKey(contentType, sourceName, url))?.rating
    }

    suspend fun put(
        contentType: String,
        sourceName: String,
        url: String,
        rating: Float?,
    ) {
        mutex.withLock {
            write(buildKey(contentType, sourceName, url), rating)
        }
    }

    private suspend fun loadOnce(
        key: String,
        loader: suspend () -> Float?,
    ): Float? {
        val deferred = mutex.withLock {
            inFlight[key] ?: loaderScope.async { loader() }.also { newDeferred ->
                inFlight[key] = newDeferred
                newDeferred.invokeOnCompletion {
                    // ponytail: app-scope load survives caller cancellation; completion cleanup is enough.
                    loaderScope.launch {
                        mutex.withLock {
                            if (inFlight[key] === newDeferred) {
                                inFlight.remove(key)
                            }
                        }
                    }
                }
            }
        }
        return deferred.await()
    }

    private fun read(key: String): CachedRating? {
        return preferences.getString(key, null)?.let(::decode)
    }

    private fun write(key: String, rating: Float?) {
        preferences.edit()
            .putString(key, encode(rating))
            .apply()
    }

    private fun buildKey(contentType: String, sourceName: String, url: String): String {
        return buildString {
            append(contentType.trim().lowercase(Locale.ROOT))
            append('|')
            append(sourceName.trim().lowercase(Locale.ROOT))
            append('|')
            append(normalizeUrl(url))
        }
    }

    private fun normalizeUrl(url: String): String {
        return url.trim()
    }

    private fun encode(rating: Float?): String {
        val updatedAtMillis = nowMillis()
        return if (rating == null) {
            "n|$updatedAtMillis"
        } else {
            "r|${rating.coerceAtLeast(0f)}|$updatedAtMillis"
        }
    }

    private fun decode(encoded: String): CachedRating? {
        val parts = encoded.split('|', limit = 3)
        return when (parts.firstOrNull()) {
            "n" -> CachedRating(
                rating = null,
                updatedAtMillis = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
            )

            "r" -> CachedRating(
                rating = parts.getOrNull(1)?.toFloatOrNull(),
                updatedAtMillis = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
            )

            else -> null
        }
    }

    private data class CachedRating(
        val rating: Float?,
        val updatedAtMillis: Long,
    ) {
        fun isFresh(nowMillis: Long): Boolean {
            val age = nowMillis - updatedAtMillis
            val ttl = if (rating == null) EMPTY_TTL_MS else RATING_TTL_MS
            return age in 0..ttl
        }

        fun isEmptyRatingStale(nowMillis: Long): Boolean {
            return rating == null && !isFresh(nowMillis)
        }
    }

    private companion object {
        private const val PREFS_NAME = "entry_rating_cache"
        private val RATING_TTL_MS = 7.days.inWholeMilliseconds
        private val EMPTY_TTL_MS = 6.hours.inWholeMilliseconds
    }
}
