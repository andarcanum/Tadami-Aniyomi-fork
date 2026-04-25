package eu.kanade.presentation.webview

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import com.tadami.aurora.BuildConfig
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginAssetBindings
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginIdentitySource
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginWebViewCoordinator
import eu.kanade.tachiyomi.util.system.getHtml
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun WebViewScreenContent(
    onNavigateUp: () -> Unit,
    initialTitle: String?,
    url: String,
    onShare: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onClearCookies: (String) -> Unit,
    headers: Map<String, String> = emptyMap(),
    onUrlChange: (String) -> Unit = {},
    novelSourceId: Long? = null,
) {
    val state = rememberWebViewState(url = url, additionalHttpHeaders = headers)
    val navigator = rememberWebViewNavigator()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appHaptics = LocalAppHaptics.current
    val scope = rememberCoroutineScope()
    val novelSourceManager = remember { Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>() }
    val pluginAssetBindings = remember { Injekt.get<NovelPluginAssetBindings>() }
    val webViewCoordinator = remember { Injekt.get<NovelPluginWebViewCoordinator>() }
    val novelPluginId = remember(novelSourceId) {
        novelSourceId?.let { novelSourceManager.get(it) as? NovelPluginIdentitySource }?.pluginId
    }

    var currentUrl by remember { mutableStateOf(url) }
    var showCloudflareHelp by remember { mutableStateOf(false) }

    val webClient = remember {
        object : AccompanistWebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    currentUrl = it
                    onUrlChange(it)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                scope.launch {
                    val html = view.getHtml()
                    showCloudflareHelp = "window._cf_chl_opt" in html || "Ray ID is" in html
                }
                novelPluginId?.let { pluginId ->
                    scope.launch {
                        applyNovelPluginWebViewBindings(
                            view = view,
                            pluginId = pluginId,
                            scope = scope,
                            assetBindings = pluginAssetBindings,
                            webViewCoordinator = webViewCoordinator,
                        )
                    }
                }
            }

            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String?,
                isReload: Boolean,
            ) {
                super.doUpdateVisitedHistory(view, url, isReload)
                url?.let {
                    currentUrl = it
                    onUrlChange(it)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                request?.let {
                    // Don't attempt to open blobs as webpages
                    if (it.url.toString().startsWith("blob:http")) {
                        return false
                    }

                    // Ignore intents urls
                    if (it.url.toString().startsWith("intent://")) {
                        return true
                    }

                    view?.loadUrl(it.url.toString(), headers)
                    return true
                }
                return false
            }
        }
    }

    Scaffold(
        topBar = {
            Box {
                Column {
                    AppBar(
                        title = state.pageTitle ?: initialTitle,
                        subtitle = currentUrl,
                        navigateUp = onNavigateUp,
                        navigationIcon = Icons.Outlined.Close,
                        actions = {
                            AppBarActions(
                                persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_webview_back),
                                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                        onClick = {
                                            if (navigator.canGoBack) {
                                                navigator.navigateBack()
                                            }
                                        },
                                        enabled = navigator.canGoBack,
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_webview_forward),
                                        icon = Icons.AutoMirrored.Outlined.ArrowForward,
                                        onClick = {
                                            if (navigator.canGoForward) {
                                                navigator.navigateForward()
                                            }
                                        },
                                        enabled = navigator.canGoForward,
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_webview_refresh),
                                        onClick = { navigator.reload() },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_share),
                                        onClick = { onShare(currentUrl) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_open_in_browser),
                                        onClick = { onOpenInBrowser(currentUrl) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.pref_clear_cookies),
                                        onClick = { onClearCookies(currentUrl) },
                                    ),
                                ),
                            )
                        },
                    )

                    if (showCloudflareHelp) {
                        Surface(
                            modifier = Modifier.padding(8.dp),
                        ) {
                            WarningBanner(
                                textRes = MR.strings.information_cloudflare_help,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        appHaptics.tap()
                                        uriHandler.openUri(
                                            "https://aniyomi.org/docs/guides/troubleshooting/#cloudflare",
                                        )
                                    },
                            )
                        }
                    }
                }
                when (val loadingState = state.loadingState) {
                    is LoadingState.Initializing -> LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    is LoadingState.Loading -> LinearProgressIndicator(
                        progress = { loadingState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    else -> {}
                }
            }
        },
    ) { contentPadding ->
        WebView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            navigator = navigator,
            onCreated = { webView ->
                webView.setDefaultSettings()

                // Debug mode (chrome://inspect/#devices)
                if (BuildConfig.DEBUG &&
                    0 != webView.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
                ) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }

                headers.entries.firstOrNull { (name, _) ->
                    name.equals("User-Agent", ignoreCase = true)
                }?.value?.let {
                    webView.settings.userAgentString = it
                }
            },
            client = webClient,
        )
    }
}

private suspend fun applyNovelPluginWebViewBindings(
    view: WebView,
    pluginId: String,
    scope: CoroutineScope,
    assetBindings: NovelPluginAssetBindings,
    webViewCoordinator: NovelPluginWebViewCoordinator,
) {
    val snapshot = webViewCoordinator.syncBeforeParsing(pluginId)

    view.evaluateJavascript(buildWebStorageSnapshotScript()) { result ->
        val snapshotData = decodeWebStorageSnapshot(result)
        if (snapshotData != null) {
            val (localStorage, sessionStorage) = snapshotData
            if (sessionStorage.isNotEmpty()) {
                scope.launch {
                    webViewCoordinator.syncAfterPageMutation(
                        pluginId = pluginId,
                        localStorageData = localStorage,
                        sessionStorageData = sessionStorage,
                    )
                }
            } else {
                scope.launch {
                    webViewCoordinator.syncAfterAuthFlow(
                        pluginId = pluginId,
                        webStorageData = localStorage,
                    )
                }
            }
        }
    }

    val storageInjectionScript = webViewCoordinator.generateStorageInjectionScript(snapshot)
    val assetInjectionScript = assetBindings.generateAssetInjectionScript(pluginId)
    val combinedScript = listOf(storageInjectionScript, assetInjectionScript)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")

    if (combinedScript.isNotBlank()) {
        view.evaluateJavascript(combinedScript, null)
    }
}

private fun buildWebStorageSnapshotScript(): String {
    return """
        (function() {
          function collect(storage) {
            var result = {};
            if (!storage) return result;
            try {
              for (var index = 0; index < storage.length; index++) {
                var key = storage.key(index);
                if (!key) continue;
                var value = storage.getItem(key);
                if (value == null) continue;
                result[key] = String(value);
              }
            } catch (e) {}
            return result;
          }
          return JSON.stringify({
            localStorage: collect(window.localStorage),
            sessionStorage: collect(window.sessionStorage)
          });
        })();
    """.trimIndent()
}

private fun decodeWebStorageSnapshot(result: String?): Pair<Map<String, String>, Map<String, String>>? {
    if (result.isNullOrBlank() || result == "null") return null
    val rawJson = runCatching { Json.decodeFromString<String>(result) }.getOrNull() ?: return null
    val element = runCatching { Json.parseToJsonElement(rawJson) }.getOrNull() ?: return null
    val objectValue = element as? JsonObject ?: return null
    val localStorage = objectValue["localStorage"].decodeStorageMap()
    val sessionStorage = objectValue["sessionStorage"].decodeStorageMap()
    return localStorage to sessionStorage
}

private fun kotlinx.serialization.json.JsonElement?.decodeStorageMap(): Map<String, String> {
    val value = this as? JsonObject ?: return emptyMap()
    return value.mapNotNull { (key, element) ->
        val stringValue = element.jsonPrimitive.contentOrNull ?: return@mapNotNull null
        key to stringValue
    }.toMap()
}
