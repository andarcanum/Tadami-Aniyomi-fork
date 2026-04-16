@file:Suppress("DEPRECATION")

package eu.kanade.tachiyomi.data.sync.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import eu.kanade.domain.sync.SyncPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

/**
 * Service class for handling Google Drive authentication and API interactions.
 */
class GoogleDriveService(private val context: Context) {

    private val syncPreferences: SyncPreferences = Injekt.get()

    var driveService: Drive? = null
        private set

    companion object {
        private const val TAG = "GoogleDriveService"
        private const val LOCAL_CLIENT_SECRETS_FILE = "client_secrets.local.json"
        private const val DEFAULT_CLIENT_SECRETS_FILE = "client_secrets.json"
    }

    init {
        initGoogleDriveService()
    }

    /**
     * Initializes the Google Drive service using stored access and refresh tokens.
     */
    private fun initGoogleDriveService() {
        val accessToken = syncPreferences.googleDriveAccessToken().get()
        val refreshToken = syncPreferences.googleDriveRefreshToken().get()

        if (accessToken.isEmpty() || refreshToken.isEmpty()) {
            driveService = null
            logcat { "Google Drive not signed in" }
            return
        }

        try {
            setupGoogleDriveService(accessToken)
        } catch (e: Exception) {
            driveService = null
            this.logcat(LogPriority.ERROR, e) {
                "Google Drive OAuth credentials are not configured"
            }
        }
    }

    /**
     * Creates an Intent to open the browser for Google Drive sign-in.
     */
    fun getSignInIntent(): Intent {
        val authorizationUrl = generateAuthorizationUrl()

        logcat { "Opening browser for OAuth" }

        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(authorizationUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Generates the OAuth authorization URL.
     */
    private fun generateAuthorizationUrl(): String {
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
        val secrets = loadGoogleClientSecrets(jsonFactory)

        val flow = GoogleAuthorizationCodeFlow.Builder(
            NetHttpTransport(),
            jsonFactory,
            secrets,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA),
        ).setAccessType("offline").build()

        return flow.newAuthorizationUrl()
            .setRedirectUri(buildRedirectUri(secrets.installed.clientId.orEmpty()))
            .setApprovalPrompt("force")
            .build()
    }

    /**
     * Refreshes the access token using the stored refresh token.
     */
    suspend fun refreshToken() = withContext(Dispatchers.IO) {
        val refreshToken = syncPreferences.googleDriveRefreshToken().get()

        if (refreshToken.isEmpty()) {
            throw Exception("Not signed in to Google Drive")
        }

        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
        val secrets = loadGoogleClientSecrets(jsonFactory)

        try {
            val tokenResponse = GoogleRefreshTokenRequest(
                NetHttpTransport(),
                jsonFactory,
                refreshToken,
                secrets.installed.clientId,
                secrets.installed.clientSecret.orEmpty(),
            ).execute()

            val newAccessToken = tokenResponse.accessToken
                ?: throw IllegalStateException("Google Drive refresh did not return an access token")
            val newRefreshToken = tokenResponse.refreshToken ?: refreshToken

            syncPreferences.googleDriveAccessToken().set(newAccessToken)
            syncPreferences.googleDriveRefreshToken().set(newRefreshToken)
            setupGoogleDriveService(newAccessToken)

            logcat { "Token refreshed successfully" }
        } catch (e: TokenResponseException) {
            if (e.details?.error == "invalid_grant") {
                this.logcat(LogPriority.ERROR, e) { "Refresh token invalid" }
                throw Exception("Refresh token invalid. Please sign in again.", e)
            } else {
                this.logcat(LogPriority.ERROR, e) { "Failed to refresh access token" }
                throw Exception("Failed to refresh token: ${e.message}", e)
            }
        } catch (e: IOException) {
            this.logcat(LogPriority.ERROR, e) { "Network error during token refresh" }
            throw Exception("Network error: ${e.message}", e)
        }
    }

    /**
     * Sets up the Google Drive service with the provided tokens.
     */
    private fun setupGoogleDriveService(accessToken: String) {
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
        loadGoogleClientSecrets(jsonFactory)

        val requestInitializer = HttpRequestInitializer { request: HttpRequest ->
            request.headers.authorization = "Bearer $accessToken"
        }

        driveService = Drive.Builder(
            NetHttpTransport(),
            jsonFactory,
            requestInitializer,
        ).setApplicationName(context.packageName)
            .build()

        logcat { "Google Drive service initialized" }
    }

    /**
     * Handles the OAuth authorization code returned from the browser.
     */
    fun handleAuthorizationCode(
        authorizationCode: String,
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        launchIO {
            try {
                val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
                val secrets = loadGoogleClientSecrets(jsonFactory)

                val tokenResponse: GoogleTokenResponse = GoogleAuthorizationCodeTokenRequest(
                    NetHttpTransport(),
                    jsonFactory,
                    secrets.installed.clientId,
                    secrets.installed.clientSecret.orEmpty(),
                    authorizationCode,
                    buildRedirectUri(secrets.installed.clientId.orEmpty()),
                ).setGrantType("authorization_code").execute()

                // Save the access token and refresh token
                val accessToken = tokenResponse.accessToken
                val newRefreshToken = tokenResponse.refreshToken ?: syncPreferences.googleDriveRefreshToken().get()

                syncPreferences.googleDriveAccessToken().set(accessToken)
                syncPreferences.googleDriveRefreshToken().set(newRefreshToken)

                setupGoogleDriveService(accessToken)

                logcat { "Authorization successful" }

                activity.runOnUiThread {
                    onSuccess()
                }
            } catch (e: Exception) {
                this@GoogleDriveService.logcat(LogPriority.ERROR, e) { "Authorization failed" }
                activity.runOnUiThread {
                    onFailure(e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    private fun loadGoogleClientSecrets(jsonFactory: JsonFactory): GoogleClientSecrets {
        val secretsReader = try {
            context.assets.open(LOCAL_CLIENT_SECRETS_FILE).reader()
        } catch (_: IOException) {
            context.assets.open(DEFAULT_CLIENT_SECRETS_FILE).reader()
        }

        return secretsReader.use { reader ->
            val secrets = GoogleClientSecrets.load(jsonFactory, reader)
            val installed = secrets.installed
                ?: throw IllegalStateException("Google Drive OAuth client is missing installed credentials.")

            val clientId = installed.clientId.orEmpty()

            if (clientId.isBlank() || clientId.startsWith("YOUR_")) {
                throw IllegalStateException(
                    "Google Drive OAuth credentials are not configured.",
                )
            }

            secrets
        }
    }

    private fun buildRedirectUri(clientId: String): String {
        val clientPrefix = clientId.removeSuffix(".apps.googleusercontent.com")
        return "com.googleusercontent.apps.$clientPrefix:/oauth2redirect"
    }

    /**
     * Checks if the user is signed in to Google Drive.
     */
    fun isSignedIn(): Boolean {
        return driveService != null
    }

    /**
     * Signs out from Google Drive by clearing tokens.
     */
    fun signOut() {
        syncPreferences.clearGoogleDriveTokens()
        driveService = null
        logcat { "Signed out from Google Drive" }
    }
}
