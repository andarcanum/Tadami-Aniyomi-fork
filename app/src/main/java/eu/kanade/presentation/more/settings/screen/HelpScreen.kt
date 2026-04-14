package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_HORIZONTAL_INSET
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Github

object HelpScreen : Screen() {
    @Composable
    override fun Content() {
        val uriHandler = LocalUriHandler.current
        val handleBack = LocalBackPress.current
        val uiStyle = rememberResolvedSettingsUiStyle()
        val state = rememberLazyListState()

        val itemModifier = if (uiStyle == SettingsUiStyle.Aurora) {
            Modifier.padding(horizontal = AURORA_SETTINGS_CARD_HORIZONTAL_INSET)
        } else {
            Modifier
        }

        Box(modifier = Modifier.fillMaxSize()) {
            SettingsScaffold(
                title = stringResource(AYMR.strings.aurora_help),
                uiStyle = uiStyle,
                onBackPressed = if (handleBack != null) handleBack::invoke else null,
                showTopBar = true,
                topBarCanScroll = { state.canScroll() },
            ) { contentPadding ->
                ScrollbarLazyColumn(
                    state = state,
                    contentPadding = contentPadding,
                ) {
                    item {
                        TextPreferenceWidget(
                            modifier = itemModifier,
                            title = "Telegram Channel",
                            subtitle = "https://t.me/TadamiApp",
                            icon = Icons.AutoMirrored.Outlined.Send,
                            onPreferenceClick = { uriHandler.openUri("https://t.me/TadamiApp") },
                        )
                    }

                    item {
                        TextPreferenceWidget(
                            modifier = itemModifier,
                            title = "Telegram Group",
                            subtitle = "https://t.me/TadamiSupport",
                            icon = Icons.AutoMirrored.Outlined.Chat,
                            onPreferenceClick = { uriHandler.openUri("https://t.me/TadamiSupport") },
                        )
                    }

                    item {
                        TextPreferenceWidget(
                            modifier = itemModifier,
                            title = "GitHub Issues",
                            subtitle = "https://github.com/andarcanum/Tadami-Aniyomi-fork/issues",
                            icon = CustomIcons.Github,
                            onPreferenceClick = {
                                uriHandler.openUri("https://github.com/andarcanum/Tadami-Aniyomi-fork/issues")
                            },
                        )
                    }
                }
            }
        }
    }
}
