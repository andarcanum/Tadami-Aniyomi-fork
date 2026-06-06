package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.tadami.aurora.BuildConfig
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.auroraPrimaryMenuTitleTextStyle
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_HORIZONTAL_INSET
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_SHAPE
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_VERTICAL_PADDING
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.auroraCardStyle
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.settingsSelectionBackgroundColor
import eu.kanade.presentation.more.settings.settingsSelectionBorderColor
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalIsDefaultAppUiFont
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

object SettingsMainScreen : Screen() {
    @Composable
    override fun Content() {
        Content(twoPane = false)
    }

    @Composable
    fun Content(twoPane: Boolean) {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val uiStyle = rememberResolvedSettingsUiStyle()
        val unlockableManager = remember { Injekt.get<tachiyomi.data.achievement.UnlockableManager>() }
        val items = remember {
            mainSettingsNavigationItems().filter { item ->
                if (item.key == "treasury") {
                    shouldShowTreasury(
                        isDebugBuild = BuildConfig.DEBUG,
                        unlockedUnlockables = unlockableManager.getUnlockedUnlockables(),
                    )
                } else {
                    true
                }
            }
        }

        val state = rememberLazyListState()

        // Track settings visit for achievement
        val achievementHandler = Injekt.get<AchievementHandler>()
        LaunchedEffect(Unit) {
            achievementHandler.trackFeatureUsed(AchievementEvent.Feature.SETTINGS)
        }

        SettingsScaffold(
            title = stringResource(MR.strings.label_settings),
            uiStyle = uiStyle,
            onBackPressed = backPress::invoke,
            topBarCanScroll = { state.canScroll() },
            actions = {
                if (uiStyle == SettingsUiStyle.Aurora) {
                    AuroraTopBarIconButton(
                        onClick = { navigator.navigate(SettingsSearchScreen(), twoPane) },
                        icon = Icons.Outlined.Search,
                        contentDescription = stringResource(MR.strings.action_search),
                    )
                } else {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_search),
                                icon = Icons.Outlined.Search,
                                onClick = { navigator.navigate(SettingsSearchScreen(), twoPane) },
                            ),
                        ),
                    )
                }
            },
        ) { contentPadding ->
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
                    var modifier: Modifier =
                        if (uiStyle == SettingsUiStyle.Aurora && !twoPane) {
                            Modifier.padding(horizontal = AURORA_SETTINGS_CARD_HORIZONTAL_INSET)
                        } else {
                            Modifier
                        }
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
                        if (uiStyle == SettingsUiStyle.Aurora && !twoPane) {
                            AuroraMainSettingsItem(
                                modifier = modifier,
                                title = stringResource(item.titleRes),
                                subtitle = item.subtitleText(),
                                icon = item.icon,
                                onClick = { navigator.navigate(item.screen, twoPane) },
                            )
                        } else {
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
    }

    private fun Navigator.navigate(screen: VoyagerScreen, twoPane: Boolean) {
        if (twoPane) replaceAll(screen) else push(screen)
    }
}

@Composable
private fun AuroraMainSettingsItem(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val useMediumWeight = LocalIsDefaultAppUiFont.current
    val appHaptics = LocalAppHaptics.current

    Card(
        onClick = {
            appHaptics.tap()
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AURORA_SETTINGS_CARD_VERTICAL_PADDING)
            .auroraCardStyle(colors, AURORA_SETTINGS_CARD_SHAPE),
        shape = AURORA_SETTINGS_CARD_SHAPE,
        colors = CardDefaults.cardColors(
            containerColor = if (!colors.isDark && !colors.isEInk) {
                Color.Transparent
            } else {
                resolveAuroraMoreCardContainerColor(colors)
            },
        ),
        border = if (colors.isEInk) {
            BorderStroke(
                width = 1.dp,
                color = resolveAuroraMoreCardBorderColor(colors),
            )
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = auroraPrimaryMenuTitleTextStyle(
                        baseStyle = MaterialTheme.typography.bodyLarge,
                        useMediumWeight = useMediumWeight,
                    ),
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}
