package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.tadami.aurora.R
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class OpenSourceLicensesScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val uiStyle = rememberResolvedSettingsUiStyle()
        val aboutLibrariesJson = remember(context) {
            context.resources.openRawResource(R.raw.aboutlibraries).use { it.readBytes() }
        }
        val libraries = remember(aboutLibrariesJson) {
            Libs.Builder()
                .withJson(aboutLibrariesJson.decodeToString())
                .build()
        }
        SettingsScaffold(
            title = stringResource(MR.strings.licenses),
            uiStyle = uiStyle,
            onBackPressed = navigator::pop,
        ) { contentPadding ->
            LibrariesContainer(
                libraries = libraries,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = contentPadding,
                onLibraryClick = {
                    navigator.push(
                        OpenSourceLibraryLicenseScreen(
                            name = it.name,
                            website = it.website,
                            license = it.licenses.firstOrNull()?.licenseContent?.replace("\n", "<br />").orEmpty(),
                        ),
                    )
                },
            )
        }
    }
}
