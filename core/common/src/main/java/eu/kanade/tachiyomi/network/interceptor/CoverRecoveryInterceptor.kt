package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class CoverRecoveryInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!CoverRequestPolicy.isCoverRequest(request)) {
            return chain.proceed(request)
        }

        val host = request.url.host
        if (CoverRequestPolicy.isBlacklisted(host)) {
            throw IOException("Skipped blacklisted cover host: $host")
        }

        return try {
            val response = chain.proceed(request)
            if (response.isSuccessful) {
                CoverRequestPolicy.clear(host)
            } else if (CoverRequestPolicy.isRecoverableResponseCode(response.code)) {
                CoverRequestPolicy.recordFailure(host)
            }
            response
        } catch (e: IOException) {
            // Keep transient network failures from poisoning the host blacklist.
            throw e
        }
    }
}
