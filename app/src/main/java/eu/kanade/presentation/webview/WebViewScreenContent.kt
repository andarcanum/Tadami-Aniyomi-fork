package eu.kanade.presentation.webview

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Message
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.stack.mutableStateStackOf
import com.kevinnzou.web.AccompanistWebChromeClient
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.WebViewNavigator
import com.kevinnzou.web.WebViewState
import com.tadami.aurora.BuildConfig
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginAssetBindings
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginIdentitySource
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginWebViewCoordinator
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import logcat.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class WebViewWindow(webContent: WebContent, val navigator: WebViewNavigator) {
    var state by mutableStateOf(WebViewState(webContent))
    var popupMessage: Message? = null
        private set
    var webView: WebView? = null

    constructor(popupMessage: Message, navigator: WebViewNavigator) : this(WebContent.NavigatorOnly, navigator) {
        this.popupMessage = popupMessage
    }
}

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
    val coroutineScope = rememberCoroutineScope()
    val windowStack = remember {
        mutableStateStackOf(
            WebViewWindow(
                WebContent.Url(url = url, additionalHttpHeaders = headers),
                WebViewNavigator(coroutineScope),
            ),
        )
    }
    val currentWindow = windowStack.lastItemOrNull!!
    val navigator = currentWindow.navigator
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
    var isActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { isActive = false }
    }

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
                showCloudflareHelp = isCloudflareChallengePage(
                    url = url,
                    title = view.title,
                )
                if (!showCloudflareHelp) {
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
                val url = request?.url?.toString() ?: return false

                // Ignore intents urls
                if (url.startsWith("intent://")) return true

                // Only manually reload changed main-frame GET navigations so we preserve
                // source headers without hijacking iframe/subframe challenge requests.
                if (shouldReloadMainFrameNavigation(
                        requestUrl = url,
                        currentUrl = view?.url,
                        isForMainFrame = request.isForMainFrame,
                        method = request.method,
                    )
                ) {
                    view?.loadUrl(url, headers)
                    return true
                }

                return false
            }
        }
    }

    val webChromeClient = remember {
        object : AccompanistWebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                if (isUserGesture) {
                    windowStack.push(WebViewWindow(resultMsg, WebViewNavigator(coroutineScope)))
                    return true
                }
                return false
            }

            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                if (!isActive) {
                    result.confirm()
                    return true
                }
                return super.onJsAlert(view, url, message, result)
            }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                if (!isActive) {
                    result.cancel()
                    return true
                }
                return super.onJsConfirm(view, url, message, result)
            }

            override fun onJsPrompt(
                view: WebView,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult,
            ): Boolean {
                if (!isActive) {
                    result.cancel()
                    return true
                }
                return super.onJsPrompt(view, url, message, defaultValue, result)
            }
        }
    }

    fun initializePopup(webView: WebView, message: Message): WebView {
        val transport = message.obj as WebView.WebViewTransport
        transport.webView = webView
        message.sendToTarget()
        return webView
    }

    val popState = remember<() -> Unit> {
        {
            if (windowStack.size == 1) {
                onNavigateUp()
            } else {
                windowStack.pop()
            }
        }
    }

    BackHandler(windowStack.size > 1, popState)

    Scaffold(
        topBar = {
            Box {
                Column {
                    AppBar(
                        title = currentWindow.state.pageTitle ?: initialTitle,
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
                                ).builder().apply {
                                    if (windowStack.size > 1) {
                                        add(
                                            0,
                                            AppBar.Action(
                                                title = stringResource(MR.strings.action_close),
                                                icon = Icons.Outlined.Close,
                                                onClick = popState,
                                            ),
                                        )
                                    }
                                }.build(),
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
                when (val loadingState = currentWindow.state.loadingState) {
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
        key(currentWindow) {
            WebView(
                state = currentWindow.state,
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

                    headers["user-agent"]?.let {
                        webView.settings.userAgentString = it
                    }
                },
                onDispose = { webView ->
                    val window = windowStack.items.find { it.webView == webView }
                    if (window == null) {
                        webView.destroy()
                    } else {
                        window.state.content = WebContent.NavigatorOnly
                    }
                },
                client = webClient,
                chromeClient = webChromeClient,
                factory = { context ->
                    currentWindow.webView
                        ?: WebView(context).also { webView ->
                            currentWindow.webView = webView
                            currentWindow.popupMessage?.let {
                                initializePopup(webView, it)
                            }
                        }
                },
            )
        }
    }
}

private suspend fun applyNovelPluginWebViewBindings(
    view: WebView,
    pluginId: String,
    scope: CoroutineScope,
    assetBindings: NovelPluginAssetBindings,
    webViewCoordinator: NovelPluginWebViewCoordinator,
) {
    view.evaluateJavascript(buildWebStorageSnapshotScript()) { result ->
        val snapshotData = decodeWebStorageSnapshot(result)
        val localKeys = snapshotData?.first?.keys?.joinToString() ?: "null"
        val hasAuth = snapshotData?.first?.containsKey("auth") == true
        logcat(priority = LogPriority.DEBUG, tag = "WebViewBindings") {
            "extract plugin=$pluginId hasAuth=$hasAuth localKeys=[$localKeys]"
        }
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

    val assetInjectionScript = assetBindings.generateAssetInjectionScript(pluginId)
    if (assetInjectionScript.isNotBlank()) {
        view.evaluateJavascript(assetInjectionScript, null)
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
