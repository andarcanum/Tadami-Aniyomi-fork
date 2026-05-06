package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import tachiyomi.core.common.util.lang.launchIO

class TrackLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(uri: Uri?) {
        when (uri?.host) {
            "anilist-auth" -> handleAnilist(uri)
            "bangumi-auth" -> handleBangumi(uri)
            "myanimelist-auth" -> handleMyAnimeList(uri)
            "shikimori-auth" -> handleShikimori(uri)
            "simkl-auth" -> handleSimkl(uri)
            "trakt-auth" -> handleTrakt(uri)
            "tmdb-auth" -> handleTmdb(uri)
        }
    }

    private fun handleAnilist(data: Uri) {
        val regex = "(?:access_token=)(.*?)(?:&)".toRegex()
        val matchResult = regex.find(data.fragment.toString())
        if (matchResult?.groups?.get(1) != null) {
            lifecycleScope.launchIO {
                trackerManager.aniList.login(matchResult.groups[1]!!.value)
                returnToSettings()
            }
        } else {
            trackerManager.aniList.logout()
            returnToSettings()
        }
    }

    private fun handleBangumi(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.bangumi.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.bangumi.logout()
            returnToSettings()
        }
    }

    private fun handleMyAnimeList(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.myAnimeList.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.myAnimeList.logout()
            returnToSettings()
        }
    }

    private fun handleShikimori(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.shikimori.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.shikimori.logout()
            returnToSettings()
        }
    }

    private fun handleSimkl(data: Uri?) {
        val code = data?.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.simkl.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.simkl.logout()
            returnToSettings()
        }
    }

    private fun handleTrakt(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.trakt.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.trakt.logout()
            returnToSettings()
        }
    }

    private fun handleTmdb(data: Uri) {
        // TMDB redirects with the request token as the `request_token` query parameter once the
        // user authorizes our app. We exchange it for a session id inside Tmdb.login.
        val requestToken = data.getQueryParameter("request_token")
        val approved = data.getQueryParameter("approved")?.equals("true", ignoreCase = true) ?: true
        if (!approved || requestToken.isNullOrBlank()) {
            trackerManager.tmdb.logout()
            returnToSettings()
            return
        }
        lifecycleScope.launchIO {
            trackerManager.tmdb.login(requestToken)
            returnToSettings()
        }
    }
}
