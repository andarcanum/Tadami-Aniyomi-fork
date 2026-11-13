package eu.kanade.presentation.more.settings.screen.appearance

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.tadami.aurora.R
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_HORIZONTAL_INSET
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.settingsAccentColor
import eu.kanade.presentation.more.settings.settingsSubtitleColor
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.xmlpull.v1.XmlPullParser
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class AppLanguageScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val uiStyle = rememberResolvedSettingsUiStyle()
        val state = rememberLazyListState()

        val langs = remember { getLangs(context) }
        var currentLanguage by remember {
            mutableStateOf(AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: "")
        }

        LaunchedEffect(currentLanguage) {
            val locale = if (currentLanguage.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(currentLanguage)
            }
            AppCompatDelegate.setApplicationLocales(locale)
        }

        SettingsScaffold(
            title = stringResource(MR.strings.pref_app_language),
            uiStyle = uiStyle,
            onBackPressed = navigator::pop,
            topBarCanScroll = { state.canScroll() },
        ) { contentPadding ->
            val itemHorizontalPadding = if (uiStyle ==
                SettingsUiStyle.Aurora
            ) {
                AURORA_SETTINGS_CARD_HORIZONTAL_INSET
            } else {
                0.dp
            }
            LazyColumn(
                state = state,
                contentPadding = contentPadding,
            ) {
                items(langs) {
                    BasePreferenceWidget(
                        modifier = Modifier.padding(horizontal = itemHorizontalPadding),
                        title = it.displayName,
                        subcomponent = if (!it.localizedDisplayName.isNullOrBlank()) {
                            {
                                Text(
                                    text = it.localizedDisplayName,
                                    modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = settingsSubtitleColor(),
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            currentLanguage = it.langTag
                        },
                        widget = {
                            if (currentLanguage == it.langTag) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = settingsAccentColor(),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    private fun getLangs(context: Context): ImmutableList<Language> {
        val langs = mutableListOf<Language>()
        val parser = context.resources.getXml(R.xml.locales_config)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                for (i in 0..<parser.attributeCount) {
                    if (parser.getAttributeName(i) == "name") {
                        val langTag = parser.getAttributeValue(i)
                        val displayName = LocaleHelper.getLocalizedDisplayName(langTag)
                        if (displayName.isNotEmpty()) {
                            langs.add(Language(langTag, displayName, LocaleHelper.getDisplayName(langTag)))
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val uniqueLangs = langs.distinctBy { it.langTag }.sortedBy { it.displayName }
        return uniqueLangs.toMutableList().apply {
            add(0, Language("", context.stringResource(MR.strings.label_default), null))
        }.toImmutableList()
    }

    private data class Language(
        val langTag: String,
        val displayName: String,
        val localizedDisplayName: String?,
    )
}
