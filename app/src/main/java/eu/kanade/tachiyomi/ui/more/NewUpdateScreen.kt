package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.NewUpdateScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.util.system.openInBrowser
import tachiyomi.domain.release.service.AppUpdatePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NewUpdateScreen(
    private val versionName: String,
    private val releaseDate: String,
    private val changelogInfo: String,
    private val releaseLink: String,
    private val downloadLink: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val appUpdatePreferences = remember { Injekt.get<AppUpdatePreferences>() }
        val changelogInfoNoChecksum = remember {
            changelogInfo.replace("""---(\R|.)*Checksums(\R|.)*""".toRegex(), "")
        }
        var ignoreThisVersion by rememberSaveable { mutableStateOf(false) }

        fun closeDialog() {
            appUpdatePreferences.ignoredAppUpdateVersion().set(
                if (ignoreThisVersion) versionName else "",
            )
            navigator.pop()
        }

        NewUpdateScreen(
            versionName = versionName,
            releaseDate = releaseDate,
            changelogInfo = changelogInfoNoChecksum,
            ignoreThisVersion = ignoreThisVersion,
            onToggleIgnoreVersion = { ignoreThisVersion = it },
            onOpenInBrowser = { context.openInBrowser(releaseLink) },
            onRejectUpdate = ::closeDialog,
            onAcceptUpdate = {
                appUpdatePreferences.ignoredAppUpdateVersion().set("")
                AppUpdateDownloadJob.start(
                    context = context,
                    url = downloadLink,
                    title = versionName,
                )
                navigator.pop()
            },
        )
    }
}
