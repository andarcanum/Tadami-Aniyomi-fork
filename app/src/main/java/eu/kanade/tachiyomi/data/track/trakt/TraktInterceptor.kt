/*
 * Adapted from Animetail (Animetailapp/Animetail) — Apache-2.0 licensed.
 * Original source:
 *   app/src/main/java/eu/kanade/tachiyomi/data/track/trakt/TraktInterceptor.kt
 */
package eu.kanade.tachiyomi.data.track.trakt

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Interceptor that injects Trakt auth headers and refreshes the access token before expiry.
 */
class TraktInterceptor(
    private val trakt: Trakt,
    accessToken: String? = null,
    private val clientId: String = Trakt.CLIENT_ID,
) : Interceptor {

    private val tokenRef = AtomicReference<String?>(accessToken)

    fun setAuth(token: String?) {
        tokenRef.set(token)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (tokenRef.get().isNullOrEmpty()) {
            trakt.restoreToken()?.let { saved ->
                tokenRef.set(saved.access_token)
            }
        }

        if (tokenRef.get().isNullOrEmpty()) {
            throw IOException("Not authenticated with Trakt")
        }

        try {
            val saved = trakt.restoreToken()
            if (saved != null) {
                val expiresAt = (saved.created_at * 1000L) + (saved.expires_in * 1000L)
                // Refresh 60 seconds before expiry.
                if (System.currentTimeMillis() > (expiresAt - 60_000L)) {
                    val refreshed = trakt.refreshAuthBlocking()
                    if (!refreshed) {
                        trakt.logout()
                        throw IOException("Token expired")
                    }
                }
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            trakt.logout()
            throw IOException("Failed to refresh Trakt token", e)
        }

        val requestBuilder = originalRequest.newBuilder()
            .header("Content-Type", "application/json")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", clientId)

        tokenRef.get()?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        return chain.proceed(requestBuilder.build())
    }
}
