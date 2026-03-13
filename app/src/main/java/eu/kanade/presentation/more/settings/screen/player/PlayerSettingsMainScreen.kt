package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.screen.SettingsSearchScreen
import eu.kanade.presentation.more.settings.screen.playerSettingsNavigationItems
import eu.kanade.presentation.more.settings.screen.subtitleText
import eu.kanade.presentation.more.settings.settingsSelectionBackgroundColor
import eu.kanade.presentation.more.settings.settingsSelectionBorderColor
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

class PlayerSettingsMainScreen(private val mainSettings: Boolean) : Screen() {
    @Composable
    override fun Content() {
        Content(twoPane = false)
    }

    @Composable
    fun Content(twoPane: Boolean) {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val uiStyle = rememberResolvedSettingsUiStyle()
        val items = remember { playerSettingsNavigationItems() }

        SettingsScaffold(
            title = stringResource(
                if (mainSettings) {
                    AYMR.strings.label_player
                } else {
                    AYMR.strings.label_player_settings
                },
            ),
            uiStyle = uiStyle,
            onBackPressed = backPress::invoke,
            actions = {
                AppBarActions(
                    persistentListOf(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_search),
                            icon = Icons.Outlined.Search,
                            onClick = { navigator.navigate(SettingsSearchScreen(true), twoPane) },
                        ),
                    ),
                )
            },
        ) { contentPadding ->
            val state = rememberLazyListState()
            val indexSelected = if (twoPane) {
                items.indexOfFirst { it.screen::class == navigator.items.first()::class }
                    .also {
                        LaunchedEffect(Unit) {
                            state.animateScrollToItem(it)
                        }
                    }
            } else {
                null
            }

            LazyColumn(
                state = state,
                contentPadding = contentPadding,
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.hashCode() },
                ) { index, item ->
                    val selected = indexSelected == index
                    var modifier: Modifier = Modifier
                    var contentColor = LocalContentColor.current
                    if (twoPane) {
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .then(
                                if (selected) {
                                    Modifier
                                        .background(settingsSelectionBackgroundColor())
                                        .border(
                                            width = 1.dp,
                                            color = settingsSelectionBorderColor(),
                                            shape = RoundedCornerShape(24.dp),
                                        )
                                } else {
                                    Modifier
                                },
                            )
                        if (selected) {
                            contentColor = if (uiStyle == SettingsUiStyle.Aurora) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }
                    }
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        TextPreferenceWidget(
                            modifier = modifier,
                            title = stringResource(item.titleRes),
                            subtitle = item.subtitleText(),
                            icon = item.icon,
                            onPreferenceClick = { navigator.navigate(item.screen, twoPane) },
                        )
                    }
                }
            }
        }
    }

    private fun Navigator.navigate(screen: VoyagerScreen, twoPane: Boolean) {
        if (twoPane) replaceAll(screen) else push(screen)
    }
}
