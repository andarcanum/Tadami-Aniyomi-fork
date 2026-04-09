package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.NewUpdateScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.util.system.openInBrowser

class DebugAppUpdatePreviewScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        var ignoreThisVersion by rememberSaveable { mutableStateOf(false) }

        NewUpdateScreen(
            versionName = "v99.0.0-debug",
            releaseDate = "2026-04-09",
            changelogInfo = """
                ## Debug preview
                - This is a fake update screen for UI testing.
                - It does not download or install anything.
                - You can use it to verify layout, spacing, and translations.
            """.trimIndent(),
            ignoreThisVersion = ignoreThisVersion,
            onToggleIgnoreVersion = { ignoreThisVersion = it },
            onOpenInBrowser = { context.openInBrowser(RELEASE_URL) },
            onRejectUpdate = { navigator.pop() },
            onAcceptUpdate = { navigator.pop() },
        )
    }
}
