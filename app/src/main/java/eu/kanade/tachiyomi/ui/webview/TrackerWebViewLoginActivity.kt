package eu.kanade.tachiyomi.ui.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.tadami.aurora.R
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy

class TrackerWebViewLoginActivity : BaseActivity() {

    private val trackerManager: TrackerManager by injectLazy()

    init {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }
        super.onCreate(savedInstanceState)

        if (!WebViewUtil.supportsWebView(this)) {
            toast(MR.strings.information_webview_required, Toast.LENGTH_LONG)
            finish()
            return
        }

        val trackerId = intent.extras?.getLong(TRACKER_ID_KEY, -1L) ?: -1L
        val trackerName = intent.extras?.getString(TRACKER_NAME_KEY).orEmpty()
        val loginUrl = intent.extras?.getString(LOGIN_URL_KEY).orEmpty()
        if (trackerId <= 0L || trackerName.isBlank() || loginUrl.isBlank()) {
            finish()
            return
        }

        setContent {
            TachiyomiTheme {
                TrackerWebViewLoginScreen(
                    trackerName = trackerName,
                    loginUrl = loginUrl,
                    onLoginComplete = {
                        val tracker = trackerManager.get(trackerId)
                        if (tracker == null) {
                            toast("Unknown tracker")
                        } else {
                            CookieManager.getInstance().flush()
                            val cookieUrl = when (trackerId) {
                                11L -> "https://www.novellist.co"
                                else -> loginUrl
                            }
                            val cookies = CookieManager.getInstance().getCookie(cookieUrl)
                            val token = when (trackerId) {
                                10L -> extractNovelUpdatesCookie(cookies)
                                11L -> extractNovelListToken(cookies)
                                else -> null
                            }

                            if (token.isNullOrBlank()) {
                                toast("Unable to extract tracker login data")
                            } else {
                                tracker.logout()
                                lifecycleScope.launch {
                                    try {
                                        tracker.login("cookie_auth", token)
                                        toast("Login successful")
                                        setResult(RESULT_OK)
                                        finish()
                                    } catch (e: Throwable) {
                                        Log.e("TrackerWebViewLogin", "Failed to complete tracker login", e)
                                        toast(e.message ?: "Login failed")
                                    }
                                }
                            }
                        }
                    },
                    onNavigateUp = { finish() },
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    companion object {
        private const val TRACKER_ID_KEY = "tracker_id_key"
        private const val TRACKER_NAME_KEY = "tracker_name_key"
        private const val LOGIN_URL_KEY = "login_url_key"

        fun newIntent(context: Context, trackerId: Long, trackerName: String, loginUrl: String): Intent {
            return Intent(context, TrackerWebViewLoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(TRACKER_ID_KEY, trackerId)
                putExtra(TRACKER_NAME_KEY, trackerName)
                putExtra(LOGIN_URL_KEY, loginUrl)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerWebViewLoginScreen(
    trackerName: String,
    loginUrl: String,
    onLoginComplete: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login to $trackerName") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                    IconButton(onClick = { scope.launch { onLoginComplete() } }) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Complete Login",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }
                        }

                        loadUrl(loginUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Text(
                    text = "Login in the webview, then tap the check button to finish.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

internal fun extractNovelUpdatesCookie(cookieHeader: String?): String? {
    val cookies = cookieHeader?.trim().orEmpty()
    return if (cookies.contains("wordpress_logged_in")) cookies else null
}

internal fun extractNovelListToken(cookieHeader: String?): String? {
    val cookies = cookieHeader?.trim().orEmpty()
    val novellistCookie = Regex("novellist=([^;]+)").find(cookies)?.groupValues?.get(1) ?: return null

    val decoded = try {
        if (novellistCookie.startsWith("base64-")) {
            val base64Part = novellistCookie.removePrefix("base64-")
            val decodedBytes = java.util.Base64.getDecoder().decode(base64Part)
            String(decodedBytes, Charsets.UTF_8)
        } else {
            novellistCookie
        }
    } catch (e: Exception) {
        Log.e("TrackerWebViewLogin", "Failed to decode NovelList cookie", e)
        novellistCookie
    }

    val tokenMatch = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(decoded)
    return tokenMatch?.groupValues?.get(1) ?: decoded.takeIf { it.isNotBlank() }
}
