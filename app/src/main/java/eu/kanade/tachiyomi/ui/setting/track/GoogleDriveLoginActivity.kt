package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Activity for handling Google Drive OAuth login.
 * Receives the OAuth callback and processes the authorization code.
 */
class GoogleDriveLoginActivity : BaseOAuthLoginActivity() {

    private val googleDriveService: GoogleDriveService = Injekt.get()

    override fun handleResult(uri: Uri?) {
        if (uri == null) {
            returnToSettings()
            return
        }

        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        when {
            code != null -> {
                // Authorization successful, exchange code for tokens
                lifecycleScope.launchIO {
                    googleDriveService.handleAuthorizationCode(
                        authorizationCode = code,
                        activity = this@GoogleDriveLoginActivity,
                        onSuccess = {
                            Toast.makeText(
                                this@GoogleDriveLoginActivity,
                                this@GoogleDriveLoginActivity.stringResource(AYMR.strings.google_drive_login_success),
                                Toast.LENGTH_LONG,
                            ).show()
                            returnToSettings()
                        },
                        onFailure = { errorMessage ->
                            Toast.makeText(
                                this@GoogleDriveLoginActivity,
                                this@GoogleDriveLoginActivity.stringResource(
                                    AYMR.strings.google_drive_login_failed,
                                    errorMessage,
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                            returnToSettings()
                        },
                    )
                }
            }
            error != null -> {
                // Authorization failed
                Toast.makeText(
                    this@GoogleDriveLoginActivity,
                    this@GoogleDriveLoginActivity.stringResource(AYMR.strings.google_drive_login_failed, error),
                    Toast.LENGTH_LONG,
                ).show()
                returnToSettings()
            }
            else -> {
                // No code or error in URI
                returnToSettings()
            }
        }
    }
}
