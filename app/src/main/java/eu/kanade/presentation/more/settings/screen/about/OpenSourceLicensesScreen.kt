package eu.kanade.presentation.more.settings.screen.about

import android.text.TextUtils
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Developer
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.entity.Scm
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.tadami.aurora.R
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class OpenSourceLicensesScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val uiStyle = rememberResolvedSettingsUiStyle()
        val loadedLicenses by produceState<LoadedLicenses?>(initialValue = null, context) {
            value = withContext(Dispatchers.IO) {
                val aboutLibrariesJson = context.resources.openRawResource(R.raw.aboutlibraries)
                    .use { it.readBytes().decodeToString() }
                val built = Libs.Builder()
                    .withJson(aboutLibrariesJson)
                    .build()
                val ssivLicenseText = runCatching {
                    context.assets.open("licenses/mihon-ssiv-LICENSE.txt")
                        .bufferedReader()
                        .use { it.readText() }
                }.getOrNull()
                val imageDecoderNotice = runCatching {
                    context.assets.open("licenses/image-reader-NOTICE.txt")
                        .bufferedReader()
                        .use { it.readText() }
                }.getOrNull()
                LoadedLicenses(
                    libs = appendSubsamplingScaleImageViewLibrary(built, ssivLicenseText),
                    imageDecoderNotice = imageDecoderNotice,
                )
            }
        }
        SettingsScaffold(
            title = stringResource(MR.strings.licenses),
            uiStyle = uiStyle,
            onBackPressed = navigator::pop,
        ) { contentPadding ->
            when (val loaded = loadedLicenses) {
                null -> LoadingScreen(modifier = Modifier.padding(contentPadding))
                else -> LibrariesContainer(
                    libraries = loaded.libs,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = contentPadding,
                    onLibraryClick = {
                        val licenseHtml = when {
                            it.uniqueId == "ca.mpreg:imagedecoder" && !loaded.imageDecoderNotice.isNullOrEmpty() -> {
                                TextUtils.htmlEncode(loaded.imageDecoderNotice)
                                    .replace("\r\n", "\n")
                                    .replace("\n", "<br />")
                            }
                            it.uniqueId == SSIV_UNIQUE_ID -> {
                                val raw = it.licenses.firstOrNull()?.licenseContent.orEmpty()
                                TextUtils.htmlEncode(raw)
                                    .replace("\r\n", "\n")
                                    .replace("\n", "<br />")
                            }
                            else -> {
                                it.licenses.firstOrNull()?.licenseContent?.replace("\n", "<br />").orEmpty()
                            }
                        }
                        navigator.push(
                            OpenSourceLibraryLicenseScreen(
                                name = it.name,
                                website = it.website,
                                license = licenseHtml,
                            ),
                        )
                        true
                    },
                )
            }
        }
    }
}

private data class LoadedLicenses(
    val libs: Libs,
    val imageDecoderNotice: String?,
)

private fun appendSubsamplingScaleImageViewLibrary(built: Libs, licenseText: String?): Libs {
    if (built.libraries.any { it.uniqueId == SSIV_UNIQUE_ID }) {
        return built
    }
    val ssivLicense = License(
        name = "Apache License 2.0",
        url = "https://www.apache.org/licenses/LICENSE-2.0.txt",
        year = null,
        spdxId = "Apache-2.0",
        licenseContent = licenseText,
        hash = "mihon-ssiv-apache-2.0",
    )
    val ssivLibrary = Library(
        uniqueId = SSIV_UNIQUE_ID,
        artifactVersion = null,
        name = "Subsampling Scale Image View",
        description = "Custom image view for displaying large images, with pinch to zoom and quick subsampling.",
        website = SSIV_WEBSITE,
        developers = listOf(Developer(name = "Dave Morrissey", organisationUrl = null)),
        organization = null,
        scm = Scm(
            connection = null,
            developerConnection = null,
            url = SSIV_WEBSITE,
        ),
        licenses = setOf(ssivLicense),
        funding = emptySet(),
        tag = null,
        targets = emptySet(),
    )
    return Libs(
        libraries = (built.libraries + ssivLibrary).sortedBy { it.name.lowercase() },
        licenses = built.licenses + ssivLicense,
    )
}

private const val SSIV_UNIQUE_ID = "com.davemorrissey.labs:subsampling-scale-image-view"
private const val SSIV_WEBSITE = "https://github.com/davemorrissey/subsampling-scale-image-view"
