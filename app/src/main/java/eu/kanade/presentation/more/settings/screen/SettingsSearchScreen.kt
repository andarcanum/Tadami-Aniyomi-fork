package eu.kanade.presentation.more.settings.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.UpIcon
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_HORIZONTAL_INSET
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_SHAPE
import eu.kanade.presentation.more.settings.LocalSettingsUiStyle
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.SettingsAuroraBackground
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsAdvancedScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsAudioScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsDecoderScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsGesturesScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsPlayerScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsSubtitleScreen
import eu.kanade.presentation.more.settings.settingsCardContainerColor
import eu.kanade.presentation.more.settings.settingsSubtitleColor
import eu.kanade.presentation.more.settings.settingsTitleColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.runOnEnterKeyPressed
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

class SettingsSearchScreen(
    val isPlayer: Boolean = false,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uiStyle = rememberResolvedSettingsUiStyle()
        val isAurora = uiStyle == SettingsUiStyle.Aurora
        val auroraColors = AuroraTheme.colors
        val softKeyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val focusRequester = remember { FocusRequester() }
        val listState = rememberLazyListState()

        // Hide keyboard on change screen
        DisposableEffect(Unit) {
            onDispose {
                softKeyboardController?.hide()
            }
        }

        // Hide keyboard on outside text field is touched
        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) {
                focusManager.clearFocus()
            }
        }

        // Request text field focus on launch
        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }

        val textFieldState = rememberTextFieldState()
        CompositionLocalProvider(LocalSettingsUiStyle provides uiStyle) {
            Scaffold(
                containerColor = if (isAurora) Color.Transparent else MaterialTheme.colorScheme.background,
                topBar = {
                    Column {
                        TopAppBar(
                            colors = if (isAurora) {
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                )
                            } else {
                                TopAppBarDefaults.topAppBarColors()
                            },
                            navigationIcon = {
                                val canPop = remember { navigator.canPop }
                                if (canPop) {
                                    IconButton(onClick = navigator::pop) {
                                        if (isAurora) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                                contentDescription = stringResource(
                                                    MR.strings.action_bar_up_description,
                                                ),
                                                tint = auroraColors.accent,
                                            )
                                        } else {
                                            UpIcon()
                                        }
                                    }
                                }
                            },
                            title = {
                                BasicTextField(
                                    state = textFieldState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(if (isAurora) AURORA_SETTINGS_CARD_SHAPE else RoundedCornerShape(0.dp))
                                        .then(
                                            if (isAurora) {
                                                Modifier
                                                    .background(settingsCardContainerColor())
                                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .focusRequester(focusRequester)
                                        .runOnEnterKeyPressed(action = focusManager::clearFocus),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = if (isAurora) {
                                            auroraColors.textPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    ),
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    onKeyboardAction = { focusManager.clearFocus() },
                                    cursorBrush = SolidColor(
                                        if (isAurora) auroraColors.accent else MaterialTheme.colorScheme.primary,
                                    ),
                                    decorator = {
                                        if (textFieldState.text.isEmpty()) {
                                            Text(
                                                text = stringResource(
                                                    resource = if (isPlayer) {
                                                        AYMR.strings.action_search_player_settings
                                                    } else {
                                                        MR.strings.action_search_settings
                                                    },
                                                ),
                                                color = if (isAurora) {
                                                    auroraColors.textSecondary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        }
                                        it()
                                    },
                                )
                            },
                            actions = {
                                if (textFieldState.text.isNotEmpty()) {
                                    IconButton(onClick = { textFieldState.clearText() }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = null,
                                            tint = if (isAurora) {
                                                auroraColors.accent
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                            },
                        )
                        HorizontalDivider(
                            color = if (isAurora) {
                                auroraColors.textSecondary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        )
                    }
                },
            ) { contentPadding ->
                val contentBody: @Composable () -> Unit = {
                    SearchResult(
                        searchKey = textFieldState.text.toString(),
                        isPlayer = isPlayer,
                        listState = listState,
                        contentPadding = contentPadding,
                    ) { result ->
                        SearchableSettings.highlightKey = result.highlightKey
                        navigator.replace(result.route)
                    }
                }
                if (isAurora) {
                    SettingsAuroraBackground(modifier = Modifier.fillMaxSize()) {
                        contentBody()
                    }
                } else {
                    contentBody()
                }
            }
        }
    }
}

@Composable
private fun SearchResult(
    searchKey: String,
    isPlayer: Boolean,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    onItemClick: (SearchResultItem) -> Unit,
) {
    if (searchKey.isEmpty()) return

    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val context = LocalContext.current

    val index = if (isPlayer) getPlayerIndex() else getIndex() + getPlayerIndex()
    val result by produceState<List<SearchResultItem>?>(initialValue = null, searchKey) {
        value = index.asSequence()
            .flatMap { settingsData ->
                settingsData.contents.asSequence()
                    // Only search from enabled prefs and one with valid title
                    .filter { it.enabled && it.title.isNotBlank() }
                    // Flatten items contained inside *enabled* PreferenceGroup
                    .flatMap { p ->
                        when (p) {
                            is Preference.PreferenceGroup -> {
                                if (p.enabled) {
                                    p.preferenceItems.asSequence()
                                        .filter { it.enabled && it.title.isNotBlank() }
                                        .map { p.title to it }
                                } else {
                                    emptySequence()
                                }
                            }
                            is Preference.PreferenceItem<*> -> sequenceOf(null to p)
                        }
                    }
                    // Don't show info preference
                    .filterNot { it.second is Preference.PreferenceItem.InfoPreference }
                    // Filter by search query
                    .filter { (_, p) ->
                        val inTitle = p.title.contains(searchKey, true)
                        val inSummary = p.subtitle?.contains(searchKey, true) ?: false
                        inTitle || inSummary
                    }
                    // Map result data
                    .map { (categoryTitle, p) ->
                        SearchResultItem(
                            route = settingsData.route,
                            title = p.title,
                            breadcrumbs = getLocalizedBreadcrumb(
                                nodes = buildList {
                                    if (!isPlayer && settingsData.playerSettings) {
                                        add(AYMR.strings.label_player_settings.getString(context))
                                    }
                                    add(settingsData.title)
                                    if (categoryTitle != null) add(categoryTitle)
                                },
                                isLtr = isLtr,
                            ),
                            highlightKey = p.title,
                        )
                    }
            }
            .take(10) // Just take top 10 result for quicker result
            .toList()
    }

    Crossfade(
        targetState = result,
        label = "results",
    ) {
        when {
            it == null -> {}
            it.isEmpty() -> {
                EmptyScreen(stringResource(MR.strings.no_results_found))
            }
            else -> {
                val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(
                        items = it,
                        key = { i -> i.hashCode() },
                    ) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isAurora) {
                                        Modifier
                                            .padding(
                                                horizontal = AURORA_SETTINGS_CARD_HORIZONTAL_INSET,
                                                vertical = 6.dp,
                                            )
                                            .clip(AURORA_SETTINGS_CARD_SHAPE)
                                            .background(settingsCardContainerColor())
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { onItemClick(item) }
                                .padding(
                                    horizontal = if (isAurora) 16.dp else 24.dp,
                                    vertical = 14.dp,
                                ),
                        ) {
                            Text(
                                text = item.title,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                fontWeight = FontWeight.Normal,
                                color = settingsTitleColor(),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = item.breadcrumbs,
                                modifier = Modifier.paddingFromBaseline(top = 16.dp),
                                maxLines = 1,
                                color = settingsSubtitleColor(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@NonRestartableComposable
private fun getIndex() = settingScreens
    .map { screen ->
        SettingsData(
            title = stringResource(screen.getTitleRes()),
            route = screen,
            contents = screen.getPreferences(),
        )
    }

@Composable
@NonRestartableComposable
private fun getPlayerIndex() = playerSettingScreens
    .map { screen ->
        SettingsData(
            title = stringResource(screen.getTitleRes()),
            route = screen,
            contents = screen.getPreferences(),
            playerSettings = true,
        )
    }

private fun getLocalizedBreadcrumb(nodes: List<String>, isLtr: Boolean): String {
    return if (isLtr) {
        // This locale reads left to right.
        nodes.joinToString(" > ")
    } else {
        // This locale reads right to left.
        nodes.reversed().joinToString(" < ")
    }
}

private val playerSettingScreens = listOf(
    PlayerSettingsPlayerScreen,
    PlayerSettingsGesturesScreen,
    PlayerSettingsDecoderScreen,
    PlayerSettingsSubtitleScreen,
    PlayerSettingsAudioScreen,
    PlayerSettingsAdvancedScreen,
)

private val settingScreens = listOf(
    SettingsAppearanceScreen,
    SettingsLibraryScreen,
    SettingsReaderScreen,
    SettingsNovelReaderScreen,
    SettingsDownloadScreen,
    SettingsTrackingScreen,
    SettingsBrowseScreen,
    SettingsDataScreen,
    SettingsSecurityScreen,
    SettingsAdvancedScreen,
)

internal fun settingsSearchRouteScreens(includePlayerSettings: Boolean): List<VoyagerScreen> {
    return if (includePlayerSettings) {
        settingScreens + playerSettingScreens
    } else {
        settingScreens
    }
}

private data class SettingsData(
    val title: String,
    val route: VoyagerScreen,
    val contents: List<Preference>,
    val playerSettings: Boolean = false,
)

private data class SearchResultItem(
    val route: VoyagerScreen,
    val title: String,
    val breadcrumbs: String,
    val highlightKey: String,
)
