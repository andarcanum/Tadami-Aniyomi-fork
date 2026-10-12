package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.CoverRecoveryInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.CompressionInterceptor
import okhttp3.Gzip
import okhttp3.OkHttpClient
import okhttp3.brotli.Brotli
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.zstd.Zstd
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
) {

    val cookieJar = AndroidCookieJar()

    private val isDebug: Boolean by lazy {
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private val clientBuilder: OkHttpClient.Builder = run {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 64
                    // Cover grids hammer a single CDN host; 8 serialized
                    // http/1.1 connections per host was the throughput ceiling.
                    maxRequestsPerHost = 16
                },
            )
            .connectionPool(
                okhttp3.ConnectionPool(
                    maxIdleConnections = 15,
                    keepAliveDuration = 5,
                    timeUnit = TimeUnit.MINUTES,
                ),
            )
            .cache(
                Cache(
                    directory = File(context.cacheDir, "network_cache"),
                    maxSize = preferences.networkCacheSize().get().toLong() * 1024 * 1024,
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addInterceptor(CoverRecoveryInterceptor())
            // OkHttp 5 + KeiSource (extensions-lib 1.6): compression is an *application*
            // interceptor. Network-level IgnoreGzipInterceptor/BrotliInterceptor are rejected
            // by KeiSource with "must not be present in default client" (e.g. 3Hentai).
            .addInterceptor(CompressionInterceptor(Zstd, Brotli, Gzip))

        if (isDebug && preferences.verboseLogging().get()) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(httpLoggingInterceptor)
        }

        when (preferences.dohProvider().get()) {
            PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
            PREF_DOH_GOOGLE -> builder.dohGoogle()
            PREF_DOH_ADGUARD -> builder.dohAdGuard()
            PREF_DOH_QUAD9 -> builder.dohQuad9()
            PREF_DOH_ALIDNS -> builder.dohAliDNS()
            PREF_DOH_DNSPOD -> builder.dohDNSPod()
            PREF_DOH_360 -> builder.doh360()
            PREF_DOH_QUAD101 -> builder.dohQuad101()
            PREF_DOH_MULLVAD -> builder.dohMullvad()
            PREF_DOH_CONTROLD -> builder.dohControlD()
            PREF_DOH_NJALLA -> builder.dohNajalla()
            PREF_DOH_SHECAN -> builder.dohShecan()
            PREF_DOH_LIBREDNS -> builder.dohLibreDNS()
            else -> builder
        }
    }

    val client = clientBuilder
        .addInterceptor(
            CloudflareInterceptor(
                context = context,
                cookieManager = cookieJar,
                defaultUserAgentProvider = ::defaultUserAgentProvider,
            ),
        )
        .build()

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider(): String {
        val configured = preferences.defaultUserAgent().get().trim()
        if (preferences.defaultUserAgent().isSet() && configured.isNotBlank()) {
            return configured
        }
        // Unset preference: mirror the device WebView fingerprint instead of the hardcoded
        // fallback. Sessions and cf_clearance cookies are bound to the agent that earned them,
        // so every client (plugin fetches, covers, hidden solves, manual WebView) must share
        // one. NovelUpdates kills the login session when the same cookies arrive from two
        // different agents within minutes (plugin fetches used the device agent while covers
        // used the hardcoded one) - LnReader uses a single agent everywhere and holds login.
        return deviceWebViewUserAgent.ifBlank { configured }
    }

    /**
     * User agent for the JS plugin runtime fetches.
     *
     * Same value as [defaultUserAgentProvider]: one agent everywhere keeps cf_clearance cookies
     * (bound to the agent that earned them) valid across WebViews and OkHttp and keeps login
     * sessions alive on hosts that bind them to the agent (NovelUpdates).
     */
    fun pluginUserAgentProvider(): String = defaultUserAgentProvider()

    private val deviceWebViewUserAgent: String by lazy {
        // Genuine device WebView user agent: matches the system WebView and Android Conscrypt TLS stack.
        // Stripping the Version marker to pretend to be standalone desktop Chrome creates a TLS/UA mismatch
        // that triggers bot detection (Cloudflare) and invalidates agent-bound sessions.
        runCatching { android.webkit.WebSettings.getDefaultUserAgent(context) }
            .getOrDefault("")
            .trim()
    }
}
