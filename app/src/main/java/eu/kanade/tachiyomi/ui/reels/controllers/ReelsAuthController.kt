package eu.kanade.tachiyomi.ui.reels.controllers

import android.net.Uri
import android.webkit.CookieManager
import eu.kanade.tachiyomi.animesource.AnimeBlockedTagsSource
import eu.kanade.tachiyomi.animesource.AnimeContentPreferencesSource
import eu.kanade.tachiyomi.animesource.AnimeFeedLoginInstrumentationSource
import eu.kanade.tachiyomi.animesource.AnimeFeedLoginSource
import eu.kanade.tachiyomi.animesource.AnimeFeedOriginScopedSessionSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedWebLoginSource
import eu.kanade.tachiyomi.animesource.model.WebLoginCookie
import eu.kanade.tachiyomi.ui.reels.ReelsFeedScreenModel
import eu.kanade.tachiyomi.ui.reels.components.legacyCookieMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Owns Reels authentication (password login, hosted WebLogin + stage-2 PKCE upgrade, Cloudflare
 * bootstrap), logout + cookie purge, content preferences, and blocked-tags management.
 */
internal class ReelsAuthController(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val mutableState: MutableStateFlow<ReelsFeedScreenModel.State>,
    private val currentSource: () -> AnimeFeedSource?,
    private val onAccountScopeInvalidated: () -> Unit,
    private val onStopRoundRobin: () -> Unit,
    private val onReloadFeed: () -> Unit,
    private val onBlockedTagsUpdated: (List<String>) -> Unit,
) {
    fun toggleLoginDialog(open: Boolean) {
        mutableState.update {
            it.copy(
                isLoginDialogOpen = open,
                loginError = if (open) null else it.loginError,
                loginRejected = if (open) false else it.loginRejected,
            )
        }
    }

    fun toggleWebLoginDialog(open: Boolean) {
        mutableState.update {
            it.copy(
                isWebLoginDialogOpen = open,
                webLoginHint = if (open) false else it.webLoginHint,
                webLoginStage2Attempt = if (open) 0 else it.webLoginStage2Attempt,
            )
        }
    }

    fun openLoginFlow() {
        if (mutableState.value.isWebLoginCapable) toggleWebLoginDialog(true) else toggleLoginDialog(true)
    }

    fun webLoginUrl(): String? = (currentSource() as? AnimeFeedWebLoginSource)?.webLoginUrl()

    fun sessionInstrumentationJs(): String? =
        (currentSource() as? AnimeFeedLoginInstrumentationSource)?.sessionInstrumentationJs()

    fun extraSessionCookieOrigins(): List<String> =
        (currentSource() as? AnimeFeedLoginInstrumentationSource)?.extraSessionCookieOrigins().orEmpty()

    fun ownAuthorizeUrl(): String? = (currentSource() as? AnimeFeedWebLoginSource)?.ownAuthorizeUrl()

    fun isOwnWebLoginRedirect(url: String): Boolean =
        (currentSource() as? AnimeFeedWebLoginSource)?.isOwnLoginRedirect(url) == true

    fun tryImportWebSession(cookies: List<WebLoginCookie>, localStorage: Map<String, String>) {
        val webSource = currentSource() as? AnimeFeedWebLoginSource ?: return
        val scopedSource = webSource as? AnimeFeedOriginScopedSessionSource
        scope.launch(NonCancellable + ioDispatcher) {
            val imported = runCatching {
                if (scopedSource != null) {
                    scopedSource.importWebSessionScoped(cookies, localStorage)
                } else {
                    webSource.importWebSession(legacyCookieMap(cookies), localStorage)
                }
            }.getOrDefault(false)
            if (imported) onWebSessionImported() else mutableState.update { it.copy(webLoginHint = true) }
        }
    }

    fun tryImportWebRedirect(url: String, cookies: List<WebLoginCookie>) {
        val webSource = currentSource() as? AnimeFeedWebLoginSource ?: return
        val scopedSource = webSource as? AnimeFeedOriginScopedSessionSource
        scope.launch(NonCancellable + ioDispatcher) {
            val imported = runCatching {
                if (scopedSource != null) {
                    scopedSource.importWebRedirectScoped(url, cookies)
                } else {
                    webSource.importWebRedirect(url, legacyCookieMap(cookies))
                }
            }.getOrDefault(false)
            if (imported) applyWebLoginSuccess() else mutableState.update { it.copy(webLoginHint = true) }
        }
    }

    fun tryImportWebSessionStage2(cookies: List<WebLoginCookie>, localStorage: Map<String, String>) {
        val webSource = currentSource() as? AnimeFeedWebLoginSource ?: return
        val scopedSource = webSource as? AnimeFeedOriginScopedSessionSource
        scope.launch(NonCancellable + ioDispatcher) {
            val imported = runCatching {
                if (scopedSource != null) {
                    scopedSource.importWebSessionScoped(cookies, localStorage)
                } else {
                    webSource.importWebSession(legacyCookieMap(cookies), localStorage)
                }
            }.getOrDefault(false)
            if (imported) {
                onWebSessionImported()
            } else {
                mutableState.update { current ->
                    current.copy(webLoginHint = true, webLoginStage2Attempt = current.webLoginStage2Attempt + 1)
                }
            }
        }
    }

    private fun onWebSessionImported() {
        if (!mutableState.value.webLoginPendingClose) {
            mutableState.update { current ->
                current.copy(
                    webLoginPendingClose = true,
                    webLoginHint = false,
                    webLoginStage2Attempt = current.webLoginStage2Attempt + 1,
                )
            }
        } else {
            applyWebLoginSuccess()
        }
    }

    private fun applyWebLoginSuccess() {
        val loginSource = currentSource() as? AnimeFeedLoginSource
        mutableState.update { current ->
            current.copy(
                isWebLoginDialogOpen = false,
                webLoginHint = false,
                webLoginPendingClose = false,
                cfBootstrapAttempt = 0,
                cfBootstrapExhausted = false,
                loggedInAccount = loginSource?.takeIf { it.isLoggedIn() }?.loggedInAccount(),
            )
        }
        onAccountScopeInvalidated()
        if (mutableState.value.isContentPreferencesOpen) loadContentPreferences()
        if (mutableState.value.isBlockedTagsOpen) loadBlockedTags()
        scope.launch(Dispatchers.Main.immediate) {
            onStopRoundRobin()
            onReloadFeed()
        }
    }

    fun login(email: String, password: String) {
        val loginSource = currentSource() as? AnimeFeedLoginSource ?: return
        if (mutableState.value.isLoggingIn) return
        mutableState.update { it.copy(isLoggingIn = true, loginError = null, loginRejected = false) }
        scope.launch(NonCancellable + ioDispatcher) {
            val ok = runCatching { loginSource.login(email, password) }.fold(
                onSuccess = { it },
                onFailure = { t ->
                    logcat(LogPriority.ERROR, t) { "Login failed for source ${mutableState.value.currentSourceId}" }
                    mutableState.update {
                        it.copy(isLoggingIn = false, loginError = t.localizedMessage.orEmpty())
                    }
                    return@launch
                },
            )
            mutableState.update { current ->
                current.copy(
                    isLoggingIn = false,
                    loggedInAccount = if (ok) loginSource.loggedInAccount() else null,
                    loginError = null,
                    loginRejected = !ok,
                )
            }
            onAccountScopeInvalidated()
            if (ok) {
                withContext(Dispatchers.Main.immediate) {
                    onStopRoundRobin()
                    onReloadFeed()
                }
            }
        }
    }

    fun logout() {
        val src = currentSource()
        val loginSource = src as? AnimeFeedLoginSource ?: return
        val webLoginSource = src as? AnimeFeedWebLoginSource
        onStopRoundRobin()
        scope.launch(NonCancellable + ioDispatcher) {
            runCatching { loginSource.logout() }
            if (webLoginSource != null) {
                val purgeOrigins = (src as? AnimeFeedLoginInstrumentationSource)
                    ?.logoutCookieOrigins()
                    .orEmpty()
                runCatching { clearWebLoginCookies(webLoginSource.webLoginUrl(), purgeOrigins) }
            }
            mutableState.update { it.copy(loggedInAccount = null, loginError = null, loginRejected = false) }
            onAccountScopeInvalidated()
            withContext(Dispatchers.Main.immediate) {
                onReloadFeed()
            }
        }
    }

    fun maybeBumpBootstrap(shouldTrigger: Boolean) {
        if (shouldTrigger && currentSource() is AnimeFeedWebLoginSource) {
            mutableState.update { current ->
                if (current.cfBootstrapExhausted) return@update current
                if (current.cfBootstrapAttempt >= CF_BOOTSTRAP_MAX_ATTEMPTS) {
                    return@update current.copy(cfBootstrapAttempt = 0, cfBootstrapExhausted = true)
                }
                current.copy(cfBootstrapAttempt = current.cfBootstrapAttempt + 1)
            }
        }
    }

    fun toggleContentPreferences(open: Boolean) {
        mutableState.update {
            it.copy(
                isContentPreferencesOpen = open,
                contentPreferencesError = if (open) null else it.contentPreferencesError,
            )
        }
        if (open) loadContentPreferences()
    }

    fun loadContentPreferences() {
        val src = currentSource() as? AnimeContentPreferencesSource ?: return
        val sourceId = mutableState.value.currentSourceId
        mutableState.update {
            it.copy(
                isContentPreferencesLoading = true,
                contentPreferencesError = null,
                contentPreferencesSaveFailed = false,
            )
        }
        scope.launch(NonCancellable + ioDispatcher) {
            val result = runCatching { src.getContentPreferences() }
            mutableState.update { current ->
                if (current.currentSourceId != sourceId) {
                    return@update current.copy(isContentPreferencesLoading = false)
                }
                current.copy(
                    isContentPreferencesLoading = false,
                    contentPreferences = result.getOrNull()?.toImmutableList(),
                    contentPreferencesError = result.exceptionOrNull()?.localizedMessage,
                    contentPreferencesSaveFailed = false,
                )
            }
            maybeBumpBootstrap(
                result.isFailure &&
                    HTTP_SESSION_ERROR_REGEX.containsMatchIn(result.exceptionOrNull()?.localizedMessage.orEmpty()),
            )
        }
    }

    fun saveContentPreferences(enabledIds: List<String>) {
        val src = currentSource() as? AnimeContentPreferencesSource ?: return
        scope.launch(NonCancellable + ioDispatcher) {
            val ok = runCatching { src.setContentPreferences(enabledIds) }.getOrDefault(false)
            if (ok) {
                loadContentPreferences()
            } else {
                mutableState.update { it.copy(contentPreferencesError = null, contentPreferencesSaveFailed = true) }
            }
        }
    }

    fun toggleBlockedTags(open: Boolean) {
        mutableState.update {
            it.copy(
                isBlockedTagsOpen = open,
                blockedTagsError = if (open) null else it.blockedTagsError,
            )
        }
        if (open) loadBlockedTags()
    }

    fun loadBlockedTags() {
        val src = currentSource() as? AnimeBlockedTagsSource ?: return
        val sourceId = mutableState.value.currentSourceId
        mutableState.update {
            it.copy(isBlockedTagsLoading = true, blockedTagsError = null, blockedTagsSaveFailed = false)
        }
        scope.launch(ioDispatcher) {
            val result = try {
                Result.success(src.getBlockedTags())
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
            val loadedTags = result.getOrNull()
            if (loadedTags != null && mutableState.value.currentSourceId == sourceId) {
                onBlockedTagsUpdated(loadedTags)
            }
            mutableState.update { current ->
                if (current.currentSourceId != sourceId) {
                    return@update current.copy(isBlockedTagsLoading = false)
                }
                current.copy(
                    isBlockedTagsLoading = false,
                    blockedTags = loadedTags?.toImmutableList(),
                    blockedTagsError = result.exceptionOrNull()?.localizedMessage,
                    blockedTagsSaveFailed = false,
                )
            }
        }
    }

    fun saveBlockedTags(tags: List<String>) {
        val src = currentSource() as? AnimeBlockedTagsSource ?: return
        scope.launch(NonCancellable + ioDispatcher) {
            val ok = runCatching { src.setBlockedTags(tags) }.getOrDefault(false)
            if (ok) {
                onBlockedTagsUpdated(tags)
                loadBlockedTags()
            } else {
                mutableState.update { it.copy(blockedTagsError = null, blockedTagsSaveFailed = true) }
            }
        }
    }

    private fun clearWebLoginCookies(startUrl: String, purgeOrigins: List<String>) {
        val origins = mutableSetOf<String>()
        if (purgeOrigins.isNotEmpty()) {
            purgeOrigins.forEach { if (it.isNotBlank()) origins += it }
        } else {
            val uri = runCatching { Uri.parse(startUrl) }.getOrNull() ?: return
            val host = uri.host ?: return
            val scheme = uri.scheme ?: "https"
            origins += "$scheme://$host"
            val labels = host.split('.')
            val root = if (labels.size >= 2) labels.takeLast(2).joinToString(".") else host
            if (labels.size >= 2) {
                origins += "$scheme://$root"
                origins += "$scheme://auth2.$root"
                origins += "$scheme://api.$root"
            }
        }
        val cm = CookieManager.getInstance()
        val fallbackRoot = runCatching { Uri.parse(startUrl).host }.getOrNull().orEmpty()
        origins.forEach { origin ->
            val cookieStr = cm.getCookie(origin) ?: return@forEach
            cookieStr.split(';').forEach { pair ->
                val key = pair.substringBefore('=').trim()
                if (key.isNotEmpty()) {
                    cm.setCookie(origin, "$key=; Max-Age=0; Path=/")
                    if (fallbackRoot.isNotEmpty()) {
                        cm.setCookie(origin, "$key=; Domain=.$fallbackRoot; Max-Age=0; Path=/")
                    }
                    runCatching { Uri.parse(origin).host }.getOrNull()?.let { originHost ->
                        cm.setCookie(origin, "$key=; Domain=$originHost; Max-Age=0; Path=/")
                    }
                }
            }
        }
        cm.flush()
    }

    companion object {
        private const val CF_BOOTSTRAP_MAX_ATTEMPTS = 2
        private val HTTP_SESSION_ERROR_REGEX = Regex("""\b(401|403)\b""")
    }
}
