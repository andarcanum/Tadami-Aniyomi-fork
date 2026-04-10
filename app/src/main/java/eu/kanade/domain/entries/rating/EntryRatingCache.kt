package eu.kanade.domain.entries.rating

import android.app.Application
import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uy.kohesive.injekt.injectLazy
import java.util.Locale

class EntryRatingCache {
    private val app: Application by injectLazy()
    private val preferences by lazy {
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val mutex = Mutex()

    suspend fun resolve(
        contentType: String,
        sourceName: String,
        url: String,
        forceRefresh: Boolean = false,
        loader: suspend () -> Float?,
    ): Float? {
        val key = buildKey(contentType, sourceName, url)
        val cached = read(key)
        if (cached != null && !forceRefresh) {
            return cached.rating
        }

        val fresh = loader()

        mutex.withLock {
            when {
                fresh != null -> write(key, fresh)
                cached == null -> write(key, null)
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
        val updatedAtMillis = System.currentTimeMillis()
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
    )

    private companion object {
        private const val PREFS_NAME = "entry_rating_cache"
    }
}
