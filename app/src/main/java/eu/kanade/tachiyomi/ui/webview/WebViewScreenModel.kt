package eu.kanade.tachiyomi.ui.webview

import android.content.Context
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class WebViewScreenModel(
    val sourceId: Long?,
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
) : StateScreenModel<StatsScreenState>(StatsScreenState.Loading) {

    var headers = emptyMap<String, String>()

    init {
        sourceId?.let { mangaSourceManager.get(it) as? HttpSource }?.let { mangaSource ->
            try {
                headers = mangaSource.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to build headers" }
            }
        }
        sourceId?.let { animeSourceManager.get(it) as? AnimeHttpSource }?.let { animeSource ->
            try {
                headers = animeSource.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to build headers" }
            }
        }

        if (headers.isEmpty()) {
            sourceId?.let { novelSourceManager.get(it) }?.let { novelSource ->
                headers = extractHeadersViaReflection(novelSource)
            }
        }

        if (headers.none { (name, _) -> name.equals("User-Agent", ignoreCase = true) }) {
            headers = headers + ("User-Agent" to network.defaultUserAgentProvider())
        }
    }

    fun shareWebpage(context: Context, url: String) {
        try {
            context.startActivity(url.toUri().toShareIntent(context, type = "text/plain"))
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    fun openInBrowser(context: Context, url: String) {
        context.openInBrowser(url, forceDefaultBrowser = true)
    }

    fun clearCookies(url: String) {
        url.toHttpUrlOrNull()?.let {
            val cleared = network.cookieJar.remove(it)
            logcat { "Cleared $cleared cookies for: $url" }
        }
    }

    private fun extractHeadersViaReflection(source: Any): Map<String, String> {
        return runCatching {
            source.javaClass.methods
                .firstOrNull { it.name == "getHeaders" && it.parameterCount == 0 }
                ?.invoke(source)
        }.getOrNull()
            ?.let { raw ->
                when (raw) {
                    is okhttp3.Headers -> raw.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
                    is Map<*, *> ->
                        raw.entries
                            .mapNotNull { (name, value) ->
                                val key = name?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                key to (value?.toString() ?: "")
                            }
                            .toMap()
                    else -> emptyMap()
                }
            }
            .orEmpty()
    }
}
