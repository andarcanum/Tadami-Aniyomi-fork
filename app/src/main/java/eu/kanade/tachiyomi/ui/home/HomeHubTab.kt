package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.presentation.components.AuroraCard
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.more.settings.screen.browse.AnimeExtensionReposScreen
import eu.kanade.presentation.more.settings.screen.browse.MangaExtensionReposScreen
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionReposScreen
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.injectLazy
import java.time.LocalDate
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

private enum class HomeHubSection {
    Anime,
    Manga,
    Novel,
}

internal data class HomeHubScrollSnapshot(
    val index: Int,
    val offset: Int,
)

internal enum class HomeHubScrollDirection {
    Up,
    Down,
    Idle,
}

internal fun resolveHomeHubScrollDirection(
    previous: HomeHubScrollSnapshot,
    current: HomeHubScrollSnapshot,
): HomeHubScrollDirection {
    return when {
        current.index > previous.index -> HomeHubScrollDirection.Down
        current.index < previous.index -> HomeHubScrollDirection.Up
        current.offset > previous.offset -> HomeHubScrollDirection.Down
        current.offset < previous.offset -> HomeHubScrollDirection.Up
        else -> HomeHubScrollDirection.Idle
    }
}

internal fun resolveHomeHubScrollDirectionFromDelta(deltaY: Float): HomeHubScrollDirection {
    return when {
        deltaY < -0.5f -> HomeHubScrollDirection.Down
        deltaY > 0.5f -> HomeHubScrollDirection.Up
        else -> HomeHubScrollDirection.Idle
    }
}

internal fun resolveHomeHubHeaderOffset(
    currentOffsetPx: Float,
    deltaY: Float,
    maxOffsetPx: Float,
    isAtTop: Boolean,
): Float {
    if (isAtTop || maxOffsetPx <= 0f) return 0f
    return (currentOffsetPx - deltaY).coerceIn(0f, maxOffsetPx)
}

internal fun resolveHomeHubHeaderVisibility(
    currentlyVisible: Boolean,
    direction: HomeHubScrollDirection,
    isAtTop: Boolean,
): Boolean {
    if (isAtTop) return true
    return when (direction) {
        HomeHubScrollDirection.Down -> false
        HomeHubScrollDirection.Up -> true
        HomeHubScrollDirection.Idle -> currentlyVisible
    }
}

internal fun shouldResetHomeHubScroll(previousPage: Int, currentPage: Int): Boolean {
    return previousPage != currentPage
}

internal fun calculateHomeOpenStreak(
    activities: List<DayActivity>,
    today: LocalDate = LocalDate.now(),
): Int {
    if (activities.isEmpty()) return 0

    val activityByDate = activities.associateBy { it.date }
    val hasActivityToday = (activityByDate[today]?.level ?: 0) > 0
    var checkDate = if (hasActivityToday) today else today.minusDays(1)
    var streak = 0

    while (true) {
        val level = activityByDate[checkDate]?.level ?: 0
        if (level <= 0) break
        streak++
        checkDate = checkDate.minusDays(1)
    }

    return streak
}

internal fun shouldShowNicknameEditHint(
    currentName: String,
    isNameEdited: Boolean,
): Boolean {
    return !isNameEdited && currentName == UserProfilePreferences.DEFAULT_NAME
}

internal fun shouldFillNicknameRowSpace(showNameEditHint: Boolean): Boolean {
    // Keep the edit hint visually attached to the nickname instead of pushing it near the avatar.
    return !showNameEditHint
}

private val greetingDecorators = listOf("✦", "✧", "◆", "◇")

internal enum class GreetingDecorationPreset(val key: String) {
    Auto("auto"),
    None("none"),
    Sparkle("sparkle"),
    Hearts("hearts"),
    Stars("stars"),
    Flowers("flowers"),
    ;

    companion object {
        fun fromKey(key: String): GreetingDecorationPreset {
            return entries.firstOrNull { it.key == key } ?: Auto
        }
    }
}

internal fun decorateGreetingText(
    text: String,
    decoration: GreetingDecorationPreset = GreetingDecorationPreset.Auto,
): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return text
    return when (decoration) {
        GreetingDecorationPreset.Auto -> {
            val marker = greetingDecorators[Math.floorMod(trimmed.hashCode(), greetingDecorators.size)]
            "$marker $trimmed $marker"
        }
        GreetingDecorationPreset.None -> trimmed
        GreetingDecorationPreset.Sparkle -> "✦ $trimmed ✦"
        GreetingDecorationPreset.Hearts -> "♡ $trimmed ♡"
        GreetingDecorationPreset.Stars -> "★ $trimmed ★"
        GreetingDecorationPreset.Flowers -> "✿ $trimmed ✿"
    }
}

private enum class NicknameFontPreset(val key: String, val fontRes: Int?) {
    Default("default", null),
    Montserrat("montserrat", R.font.montserrat_bold),
    Lora("lora", R.font.lora),
    Nunito("nunito", R.font.nunito),
    PtSerif("pt_serif", R.font.pt_serif),
    ;

    companion object {
        fun fromKey(key: String): NicknameFontPreset {
            return entries.firstOrNull { it.key == key } ?: Default
        }
    }
}

private enum class NicknameColorPreset(val key: String) {
    Theme("theme"),
    Accent("accent"),
    Gold("gold"),
    Cyan("cyan"),
    Pink("pink"),
    Custom("custom"),
    ;

    companion object {
        fun fromKey(key: String): NicknameColorPreset {
            return entries.firstOrNull { it.key == key } ?: Theme
        }
    }
}

private enum class NicknameEffectPreset(val key: String) {
    None("none"),
    Sparkle("sparkle"),
    Hearts("hearts"),
    Stars("stars"),
    Flowers("flowers"),
    Kawaii("kawaii"),
    Cat("cat"),
    Moon("moon"),
    Cloud("cloud"),
    Ribbon("ribbon"),
    Sakura("sakura"),
    ;

    companion object {
        fun fromKey(key: String): NicknameEffectPreset {
            return entries.firstOrNull { it.key == key } ?: None
        }
    }
}

private data class NicknameStyle(
    val font: NicknameFontPreset,
    val color: NicknameColorPreset,
    val outline: Boolean,
    val outlineWidth: Int,
    val glow: Boolean,
    val effect: NicknameEffectPreset,
    val customColorHex: String,
)

private data class GreetingStyle(
    val font: NicknameFontPreset,
    val color: NicknameColorPreset,
    val customColorHex: String,
    val fontSize: Int,
    val decoration: GreetingDecorationPreset,
    val italic: Boolean,
)

private data class HomeHubUiState(
    val hero: HomeHubHero? = null,
    val history: List<HomeHubHistory> = emptyList(),
    val recommendations: List<HomeHubRecommendation> = emptyList(),
    val userName: String,
    val userAvatar: String,
    val greeting: dev.icerock.moko.resources.StringResource,
    val showWelcome: Boolean,
)

private data class HomeHubHero(
    val entryId: Long,
    val title: String,
    val progressNumber: Double,
    val coverData: Any?,
)

private data class HomeHubHistory(
    val entryId: Long,
    val title: String,
    val progressNumber: Double,
    val coverData: Any?,
)

private data class HomeHubRecommendation(
    val entryId: Long,
    val title: String,
    val coverData: Any?,
    val subtitle: String? = null,
)

object HomeHubTab : Tab {

    private val uiPreferences: UiPreferences by injectLazy()
    private val activityDataRepository: tachiyomi.domain.achievement.repository.ActivityDataRepository by injectLazy()
    private val userProfilePreferences: UserProfilePreferences by injectLazy()

    override val options: TabOptions
        @Composable
        get() {
            val title = stringResource(AYMR.strings.aurora_home)
            val isSelected = LocalTabNavigator.current.current is HomeHubTab
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_home_enter)
            return TabOptions(
                index = 0u,
                title = title,
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() {
        val showAnimeSection by uiPreferences.showAnimeSection().collectAsState()
        val showMangaSection by uiPreferences.showMangaSection().collectAsState()
        val showNovelSection by uiPreferences.showNovelSection().collectAsState()

        val sections = remember(showAnimeSection, showMangaSection, showNovelSection) {
            buildList {
                if (showAnimeSection) add(HomeHubSection.Anime)
                if (showMangaSection) add(HomeHubSection.Manga)
                if (showNovelSection) add(HomeHubSection.Novel)
            }.ifEmpty { listOf(HomeHubSection.Anime) }
        }

        var selectedSection by rememberSaveable { mutableStateOf(sections.first()) }
        LaunchedEffect(sections) {
            if (selectedSection !in sections) {
                selectedSection = sections.first()
            }
        }

        var animeSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
        var mangaSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
        var novelSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val activityDataFlow = remember(activityDataRepository) { activityDataRepository.getActivityData(days = 365) }
        val activityData by activityDataFlow.collectAsState(initial = emptyList())
        val currentStreak = calculateHomeOpenStreak(activityData)
        val isNameEdited by userProfilePreferences.nameEdited().collectAsState()
        val showHomeGreeting by userProfilePreferences.showHomeGreeting().collectAsState()
        val showHomeStreak by userProfilePreferences.showHomeStreak().collectAsState()
        val nicknameFontKey by userProfilePreferences.nicknameFont().collectAsState()
        val nicknameColorKey by userProfilePreferences.nicknameColor().collectAsState()
        val nicknameCustomColorHex by userProfilePreferences.nicknameCustomColorHex().collectAsState()
        val nicknameOutline by userProfilePreferences.nicknameOutline().collectAsState()
        val nicknameOutlineWidth by userProfilePreferences.nicknameOutlineWidth().collectAsState()
        val nicknameGlow by userProfilePreferences.nicknameGlow().collectAsState()
        val nicknameEffectKey by userProfilePreferences.nicknameEffect().collectAsState()
        val nicknameStyle = NicknameStyle(
            font = NicknameFontPreset.fromKey(nicknameFontKey),
            color = NicknameColorPreset.fromKey(nicknameColorKey),
            outline = nicknameOutline,
            outlineWidth = nicknameOutlineWidth,
            glow = nicknameGlow,
            effect = NicknameEffectPreset.fromKey(nicknameEffectKey),
            customColorHex = nicknameCustomColorHex,
        )
        val greetingFontKey by userProfilePreferences.greetingFont().collectAsState()
        val greetingColorKey by userProfilePreferences.greetingColor().collectAsState()
        val greetingCustomColorHex by userProfilePreferences.greetingCustomColorHex().collectAsState()
        val greetingFontSize by userProfilePreferences.greetingFontSize().collectAsState()
        val greetingDecorationKey by userProfilePreferences.greetingDecoration().collectAsState()
        val greetingItalic by userProfilePreferences.greetingItalic().collectAsState()
        val greetingStyle = GreetingStyle(
            font = NicknameFontPreset.fromKey(greetingFontKey),
            color = NicknameColorPreset.fromKey(greetingColorKey),
            customColorHex = greetingCustomColorHex,
            fontSize = greetingFontSize.coerceIn(10, 26),
            decoration = GreetingDecorationPreset.fromKey(greetingDecorationKey),
            italic = greetingItalic,
        )

        val animeScreenModel = HomeHubTab.rememberScreenModel { HomeHubScreenModel() }
        val mangaScreenModel = HomeHubTab.rememberScreenModel { MangaHomeHubScreenModel() }
        val novelScreenModel = HomeHubTab.rememberScreenModel { NovelHomeHubScreenModel() }
        val animeState by animeScreenModel.state.collectAsState()
        val mangaState by mangaScreenModel.state.collectAsState()
        val novelState by novelScreenModel.state.collectAsState()

        val profileSection = sections.first()
        val (headerUserName, headerUserAvatar, headerGreeting) = when (profileSection) {
            HomeHubSection.Anime -> Triple(animeState.userName, animeState.userAvatar, animeState.greeting)
            HomeHubSection.Manga -> Triple(mangaState.userName, mangaState.userAvatar, mangaState.greeting)
            HomeHubSection.Novel -> Triple(novelState.userName, novelState.userAvatar, novelState.greeting)
        }
        val showNameEditHint = shouldShowNicknameEditHint(
            currentName = headerUserName,
            isNameEdited = isNameEdited,
        )

        val photoPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            uri?.let {
                when (selectedSection) {
                    HomeHubSection.Anime -> animeScreenModel.updateUserAvatar(it.toString())
                    HomeHubSection.Manga -> mangaScreenModel.updateUserAvatar(it.toString())
                    HomeHubSection.Novel -> novelScreenModel.updateUserAvatar(it.toString())
                }
            }
        }

        var showNameDialog by remember { mutableStateOf(false) }
        if (showNameDialog) {
            val currentName = when (selectedSection) {
                HomeHubSection.Anime -> animeState.userName
                HomeHubSection.Manga -> mangaState.userName
                HomeHubSection.Novel -> novelState.userName
            }
            NameDialog(
                currentName = currentName,
                currentStyle = nicknameStyle,
                onDismiss = { showNameDialog = false },
                onConfirm = { newName, newStyle ->
                    if (newName != currentName) {
                        when (selectedSection) {
                            HomeHubSection.Anime -> animeScreenModel.updateUserName(newName)
                            HomeHubSection.Manga -> mangaScreenModel.updateUserName(newName)
                            HomeHubSection.Novel -> novelScreenModel.updateUserName(newName)
                        }
                    }
                    userProfilePreferences.nicknameFont().set(newStyle.font.key)
                    userProfilePreferences.nicknameColor().set(newStyle.color.key)
                    userProfilePreferences.nicknameCustomColorHex().set(newStyle.customColorHex)
                    userProfilePreferences.nicknameOutline().set(newStyle.outline)
                    userProfilePreferences.nicknameOutlineWidth().set(newStyle.outlineWidth.coerceIn(1, 8))
                    userProfilePreferences.nicknameGlow().set(newStyle.glow)
                    userProfilePreferences.nicknameEffect().set(newStyle.effect.key)
                    showNameDialog = false
                },
            )
        }
        var showGreetingDialog by remember { mutableStateOf(false) }
        if (showGreetingDialog) {
            GreetingStyleDialog(
                currentGreeting = stringResource(headerGreeting),
                currentStyle = greetingStyle,
                onDismiss = { showGreetingDialog = false },
                onConfirm = { newStyle ->
                    userProfilePreferences.greetingFont().set(newStyle.font.key)
                    userProfilePreferences.greetingColor().set(newStyle.color.key)
                    userProfilePreferences.greetingCustomColorHex().set(newStyle.customColorHex)
                    userProfilePreferences.greetingFontSize().set(newStyle.fontSize.coerceIn(10, 26))
                    userProfilePreferences.greetingDecoration().set(newStyle.decoration.key)
                    userProfilePreferences.greetingItalic().set(newStyle.italic)
                    showGreetingDialog = false
                },
            )
        }

        var headerOffsetPx by rememberSaveable { mutableStateOf(0f) }
        var headerHeightPx by rememberSaveable { mutableIntStateOf(0) }
        var scrollResetToken by rememberSaveable { mutableIntStateOf(0) }

        val onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit = { section, deltaY, atTop ->
            if (section == selectedSection) {
                headerOffsetPx = resolveHomeHubHeaderOffset(
                    currentOffsetPx = headerOffsetPx,
                    deltaY = deltaY,
                    maxOffsetPx = headerHeightPx.toFloat(),
                    isAtTop = atTop,
                )
            }
        }

        val tabs = sections.map { section ->
            when (section) {
                HomeHubSection.Anime -> TabContent(
                    titleRes = AYMR.strings.label_anime,
                    searchEnabled = true,
                    content = { contentPadding, _ ->
                        AnimeHomeHub(
                            contentPadding = contentPadding,
                            searchQuery = animeSearchQuery,
                            activeSection = selectedSection,
                            scrollResetToken = scrollResetToken,
                            onScrollSignal = onScrollSignal,
                        )
                    },
                )
                HomeHubSection.Manga -> TabContent(
                    titleRes = AYMR.strings.label_manga,
                    searchEnabled = true,
                    content = { contentPadding, _ ->
                        MangaHomeHub(
                            contentPadding = contentPadding,
                            searchQuery = mangaSearchQuery,
                            activeSection = selectedSection,
                            scrollResetToken = scrollResetToken,
                            onScrollSignal = onScrollSignal,
                        )
                    },
                )
                HomeHubSection.Novel -> TabContent(
                    titleRes = AYMR.strings.label_novel,
                    searchEnabled = true,
                    content = { contentPadding, _ ->
                        NovelHomeHub(
                            contentPadding = contentPadding,
                            searchQuery = novelSearchQuery,
                            activeSection = selectedSection,
                            scrollResetToken = scrollResetToken,
                            onScrollSignal = onScrollSignal,
                        )
                    },
                )
            }
        }.toPersistentList()

        val initialIndex = sections.indexOf(selectedSection).coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = initialIndex) { tabs.size }

        LaunchedEffect(sections, pagerState) {
            val targetIndex = sections.indexOf(selectedSection).coerceAtLeast(0)
            if (targetIndex in tabs.indices && pagerState.currentPage != targetIndex) {
                pagerState.scrollToPage(targetIndex)
            }
        }

        var previousPage by rememberSaveable { mutableIntStateOf(initialIndex) }
        LaunchedEffect(pagerState.currentPage, sections) {
            if (sections.isEmpty()) return@LaunchedEffect
            val currentPage = pagerState.currentPage.coerceIn(0, sections.lastIndex)
            if (shouldResetHomeHubScroll(previousPage, currentPage)) {
                scrollResetToken += 1
                headerOffsetPx = 0f
            }
            previousPage = currentPage
            sections.getOrNull(currentPage)?.let { selectedSection = it }
        }

        val onSectionSelected: (Int) -> Unit = { index ->
            if (index in tabs.indices && pagerState.currentPage != index) {
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        }

        TabbedScreenAurora(
            titleRes = null,
            tabs = tabs,
            state = pagerState,
            mangaSearchQuery = mangaSearchQuery,
            onChangeMangaSearchQuery = { mangaSearchQuery = it },
            animeSearchQuery = when (sections.getOrNull(pagerState.currentPage)) {
                HomeHubSection.Novel -> novelSearchQuery
                else -> animeSearchQuery
            },
            onChangeAnimeSearchQuery = {
                when (sections.getOrNull(pagerState.currentPage)) {
                    HomeHubSection.Novel -> novelSearchQuery = it
                    else -> animeSearchQuery = it
                }
            },
            isMangaTab = { index -> sections.getOrNull(index) == HomeHubSection.Manga },
            showCompactHeader = true,
            showTabs = false,
            applyStatusBarsPadding = false,
            instantTabSwitching = false,
            extraHeaderContent = {
                HomeHubPinnedHeader(
                    headerOffsetPx = headerOffsetPx,
                    onHeightMeasured = { measuredHeight ->
                        if (measuredHeight <= 0) return@HomeHubPinnedHeader
                        if (headerHeightPx != measuredHeight) {
                            headerHeightPx = measuredHeight
                            headerOffsetPx = headerOffsetPx.coerceIn(0f, measuredHeight.toFloat())
                        }
                    },
                    greeting = headerGreeting,
                    userName = headerUserName,
                    userAvatar = headerUserAvatar,
                    nicknameStyle = nicknameStyle,
                    greetingStyle = greetingStyle,
                    showGreeting = showHomeGreeting,
                    showNameEditHint = showNameEditHint,
                    currentStreak = currentStreak,
                    showStreak = showHomeStreak,
                    tabs = tabs,
                    selectedIndex = pagerState.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
                    onTabSelected = onSectionSelected,
                    onAvatarClick = { photoPickerLauncher.launch("image/*") },
                    onNameClick = { showNameDialog = true },
                    onGreetingClick = { showGreetingDialog = true },
                )
            },
        )
    }
}

private fun HomeHubScreenModel.State.toUiState(): HomeHubUiState {
    return HomeHubUiState(
        hero = hero?.let {
            HomeHubHero(
                entryId = it.animeId,
                title = it.title,
                progressNumber = it.episodeNumber,
                coverData = it.coverData,
            )
        },
        history = history.map {
            HomeHubHistory(
                entryId = it.animeId,
                title = it.title,
                progressNumber = it.episodeNumber,
                coverData = it.coverData,
            )
        },
        recommendations = recommendations.map {
            HomeHubRecommendation(
                entryId = it.animeId,
                title = it.title,
                coverData = it.coverData,
                subtitle = "${it.seenCount}/${it.totalCount} \u044d\u043f.",
            )
        },
        userName = userName,
        userAvatar = userAvatar,
        greeting = greeting,
        showWelcome = showWelcome,
    )
}

private fun MangaHomeHubScreenModel.State.toUiState(): HomeHubUiState {
    return HomeHubUiState(
        hero = hero?.let {
            HomeHubHero(
                entryId = it.mangaId,
                title = it.title,
                progressNumber = it.chapterNumber,
                coverData = it.coverData,
            )
        },
        history = history.map {
            HomeHubHistory(
                entryId = it.mangaId,
                title = it.title,
                progressNumber = it.chapterNumber,
                coverData = it.coverData,
            )
        },
        recommendations = recommendations.map {
            val readCount = it.totalCount - it.unreadCount
            HomeHubRecommendation(
                entryId = it.mangaId,
                title = it.title,
                coverData = it.coverData,
                subtitle = "$readCount/${it.totalCount} \u0433\u043b.",
            )
        },
        userName = userName,
        userAvatar = userAvatar,
        greeting = greeting,
        showWelcome = showWelcome,
    )
}

private fun NovelHomeHubScreenModel.State.toUiState(): HomeHubUiState {
    return HomeHubUiState(
        hero = hero?.let {
            HomeHubHero(
                entryId = it.novelId,
                title = it.title,
                progressNumber = it.chapterNumber,
                coverData = it.coverData.url ?: it.coverData,
            )
        },
        history = history.map {
            HomeHubHistory(
                entryId = it.novelId,
                title = it.title,
                progressNumber = it.chapterNumber,
                coverData = it.coverData.url ?: it.coverData,
            )
        },
        recommendations = recommendations.map {
            HomeHubRecommendation(
                entryId = it.novelId,
                title = it.title,
                coverData = it.coverData.url ?: it.coverData,
                subtitle = "${it.readCount}/${it.totalCount} \u0433\u043b.",
            )
        },
        userName = userName,
        userAvatar = userAvatar,
        greeting = greeting,
        showWelcome = showWelcome,
    )
}

@Composable
private fun HomeHubPinnedHeader(
    headerOffsetPx: Float,
    onHeightMeasured: (Int) -> Unit,
    greeting: dev.icerock.moko.resources.StringResource,
    userName: String,
    userAvatar: String,
    nicknameStyle: NicknameStyle,
    greetingStyle: GreetingStyle,
    showGreeting: Boolean,
    showNameEditHint: Boolean,
    currentStreak: Int,
    showStreak: Boolean,
    tabs: kotlinx.collections.immutable.ImmutableList<TabContent>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit,
    onGreetingClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val greetingFontFamily = greetingStyle.font.fontRes?.let { FontFamily(Font(it)) }
                    val greetingColor = resolveNicknameColor(greetingStyle.color, greetingStyle.customColorHex, colors)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                    ) {
                        if (showGreeting) {
                            Text(
                                text = decorateGreetingText(stringResource(greeting), greetingStyle.decoration),
                                modifier = Modifier.clickable(onClick = onGreetingClick),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = greetingStyle.fontSize.sp,
                                    lineHeight = (greetingStyle.fontSize + 4).sp,
                                    fontStyle = if (greetingStyle.italic) FontStyle.Italic else FontStyle.Normal,
                                    fontFamily = greetingFontFamily,
                                ),
                                color = greetingColor,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onNameClick),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StyledNicknameText(
                                text = userName,
                                nicknameStyle = nicknameStyle,
                                modifier = Modifier.weight(1f, fill = shouldFillNicknameRowSpace(showNameEditHint)),
                            )
                            if (showNameEditHint) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent.copy(alpha = 0.2f))
                                        .border(
                                            width = 1.dp,
                                            color = colors.accent.copy(alpha = 0.45f),
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.height(if (showStreak) 72.dp else 48.dp),
                    ) {
                        if (showStreak) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.accent.copy(alpha = 0.14f))
                                    .border(
                                        width = 1.dp,
                                        color = colors.accent.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = currentStreak.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Box(
                            Modifier
                                .align(if (showStreak) Alignment.BottomCenter else Alignment.Center)
                                .size(48.dp)
                                .clickable(onClick = onAvatarClick),
                        ) {
                            if (userAvatar.isNotEmpty()) {
                                AsyncImage(
                                    model = userAvatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.AccountCircle,
                                    null,
                                    tint = colors.accent,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            if (userAvatar.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(16.dp)
                                        .background(colors.accent, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.CameraAlt,
                                        null,
                                        tint = colors.textOnAccent,
                                        modifier = Modifier.size(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                if (tabs.size > 1) {
                    AuroraTabRow(
                        tabs = tabs,
                        selectedIndex = selectedIndex,
                        onTabSelected = onTabSelected,
                        scrollable = false,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        },
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(constraints.minWidth, 0) {}
        }
        val placeable = measurables.first().measure(constraints)
        val fullHeight = placeable.height
        if (fullHeight > 0) {
            onHeightMeasured(fullHeight)
        }
        val collapsedHeight = headerOffsetPx.roundToInt().coerceIn(0, fullHeight)
        val visibleHeight = (fullHeight - collapsedHeight).coerceAtLeast(0)
        layout(placeable.width, visibleHeight) {
            placeable.placeRelative(x = 0, y = -collapsedHeight)
        }
    }
}

@Composable
private fun AnimeHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
) {
    val screenModel = HomeHubTab.rememberScreenModel { HomeHubScreenModel() }
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val tabNavigator = LocalTabNavigator.current

    LaunchedEffect(screenModel) {
        HomeHubScreenModel.setInstance(screenModel)
        screenModel.startLiveUpdates()
    }

    val lastSourceName = remember { screenModel.getLastUsedAnimeSourceName() }

    HomeHubScreen(
        section = HomeHubSection.Anime,
        activeSection = activeSection,
        scrollResetToken = scrollResetToken,
        onScrollSignal = onScrollSignal,
        state = state.toUiState(),
        searchQuery = searchQuery,
        lastSourceName = lastSourceName,
        contentPadding = contentPadding,
        heroActionLabelRes = AYMR.strings.aurora_play,
        heroProgressLabelRes = AYMR.strings.aurora_episode_progress,
        onEntryClick = { navigator.push(AnimeScreen(it)) },
        onPlayHero = { screenModel.playHeroEpisode(context) },
        onSourceClick = {
            val sourceId = screenModel.getLastUsedAnimeSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseAnimeSourceScreen(sourceId, null))
            } else {
                tabNavigator.current = BrowseTab
            }
        },
        onBrowseClick = { navigator.push(AnimeExtensionReposScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showAnimeExtension()
        },
        onHistoryClick = { tabNavigator.current = HistoriesTab },
        onLibraryClick = { tabNavigator.current = AnimeLibraryTab },
    )
}

@Composable
private fun MangaHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
) {
    val screenModel = HomeHubTab.rememberScreenModel { MangaHomeHubScreenModel() }
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val tabNavigator = LocalTabNavigator.current

    LaunchedEffect(screenModel) {
        MangaHomeHubScreenModel.setInstance(screenModel)
        screenModel.startLiveUpdates()
    }

    val lastSourceName = remember { screenModel.getLastUsedMangaSourceName() }

    HomeHubScreen(
        section = HomeHubSection.Manga,
        activeSection = activeSection,
        scrollResetToken = scrollResetToken,
        onScrollSignal = onScrollSignal,
        state = state.toUiState(),
        searchQuery = searchQuery,
        lastSourceName = lastSourceName,
        contentPadding = contentPadding,
        heroActionLabelRes = AYMR.strings.aurora_read,
        heroProgressLabelRes = AYMR.strings.aurora_chapter_progress,
        onEntryClick = { navigator.push(MangaScreen(it)) },
        onPlayHero = { screenModel.readHeroChapter(context) },
        onSourceClick = {
            val sourceId = screenModel.getLastUsedMangaSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseMangaSourceScreen(sourceId, null))
            } else {
                tabNavigator.current = BrowseTab
            }
        },
        onBrowseClick = { navigator.push(MangaExtensionReposScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showExtension()
        },
        onHistoryClick = { tabNavigator.current = HistoriesTab },
        onLibraryClick = { tabNavigator.current = MangaLibraryTab },
    )
}

@Composable
private fun NovelHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
) {
    val screenModel = HomeHubTab.rememberScreenModel { NovelHomeHubScreenModel() }
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val tabNavigator = LocalTabNavigator.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(screenModel) {
        screenModel.startLiveUpdates()
    }

    val lastSourceName = remember { screenModel.getLastUsedNovelSourceName() }

    HomeHubScreen(
        section = HomeHubSection.Novel,
        activeSection = activeSection,
        scrollResetToken = scrollResetToken,
        onScrollSignal = onScrollSignal,
        state = state.toUiState(),
        searchQuery = searchQuery,
        lastSourceName = lastSourceName,
        contentPadding = contentPadding,
        heroActionLabelRes = AYMR.strings.aurora_read,
        heroProgressLabelRes = AYMR.strings.aurora_chapter_progress,
        onEntryClick = { navigator.push(NovelScreen(it)) },
        onPlayHero = {
            screenModel.getHeroChapterId()?.let { chapterId ->
                navigator.push(NovelReaderScreen(chapterId))
            }
        },
        onSourceClick = {
            val sourceId = screenModel.getLastUsedNovelSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseNovelSourceScreen(sourceId, null))
            } else {
                tabNavigator.current = BrowseTab
            }
        },
        onBrowseClick = { navigator.push(NovelExtensionReposScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showNovelExtension()
        },
        onHistoryClick = { tabNavigator.current = HistoriesTab },
        onLibraryClick = {
            scope.launch { AnimeLibraryTab.showNovelSection() }
            tabNavigator.current = AnimeLibraryTab
        },
    )
}

@Composable
private fun HomeHubScreen(
    section: HomeHubSection,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
    state: HomeHubUiState,
    searchQuery: String?,
    lastSourceName: String?,
    contentPadding: PaddingValues,
    heroActionLabelRes: dev.icerock.moko.resources.StringResource,
    heroProgressLabelRes: dev.icerock.moko.resources.StringResource,
    onEntryClick: (Long) -> Unit,
    onPlayHero: () -> Unit,
    onSourceClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onExtensionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLibraryClick: () -> Unit,
) {
    val trimmedQuery = searchQuery?.trim().orEmpty()
    val isFiltering = trimmedQuery.isNotEmpty()
    val matchesQuery: (String) -> Boolean = { title ->
        !isFiltering || title.contains(trimmedQuery, ignoreCase = true)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(section, activeSection, scrollResetToken) {
        if (section == activeSection) {
            listState.scrollToItem(0)
        }
    }
    val nestedScrollConnection = remember(section, activeSection, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (section != activeSection) return Offset.Zero
                if (available.y != 0f) {
                    val isAtTop = listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                    onScrollSignal(section, available.y, isAtTop)
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(section, activeSection, listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }.collect { isAtTop ->
            if (section == activeSection && isAtTop) {
                onScrollSignal(section, 0f, true)
            }
        }
    }

    val hero = state.hero?.takeIf { matchesQuery(it.title) }
    val history = state.history.filter { matchesQuery(it.title) }
    val recommendations = state.recommendations.filter { matchesQuery(it.title) }
    val showWelcome = state.showWelcome && !isFiltering

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding = contentPadding,
    ) {
        if (showWelcome) {
            item(key = "welcome") {
                WelcomeSection(onBrowseClick = onBrowseClick, onExtensionClick = onExtensionClick)
            }
        } else {
            hero?.let { heroData ->
                item(key = "hero") {
                    HeroSection(
                        hero = heroData,
                        actionLabelRes = heroActionLabelRes,
                        progressLabelRes = heroProgressLabelRes,
                        onPlayClick = onPlayHero,
                        onEntryClick = { onEntryClick(heroData.entryId) },
                    )
                }
            }

            item(key = "quick_source") {
                QuickSourceButton(sourceName = lastSourceName, onClick = onSourceClick)
            }

            if (history.isNotEmpty()) {
                item(key = "history") {
                    HistoryRow(
                        history = history,
                        onEntryClick = onEntryClick,
                        onViewAllClick = onHistoryClick,
                    )
                }
            }

            if (recommendations.isNotEmpty()) {
                item(key = "recommendations") {
                    RecommendationsGrid(
                        recommendations = recommendations,
                        onEntryClick = onEntryClick,
                        onMoreClick = onLibraryClick,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun WelcomeSection(onBrowseClick: () -> Unit, onExtensionClick: () -> Unit) {
    val colors = AuroraTheme.colors

    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp))
            .background(colors.cardBackground).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.VideoLibrary, null, tint = colors.accent, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(AYMR.strings.aurora_welcome_title),
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(AYMR.strings.aurora_welcome_subtitle),
                color = colors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onBrowseClick,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Search, null, tint = colors.textOnAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(AYMR.strings.aurora_browse_sources),
                    color = colors.textOnAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onExtensionClick,
                colors = ButtonDefaults.buttonColors(containerColor = colors.glass),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Extension, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(AYMR.strings.aurora_add_extension),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun HeroSection(
    hero: HomeHubHero,
    actionLabelRes: dev.icerock.moko.resources.StringResource,
    progressLabelRes: dev.icerock.moko.resources.StringResource,
    onPlayClick: () -> Unit,
    onEntryClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val overlayGradient = remember(colors) {
        Brush.verticalGradient(
            listOf(Color.Transparent, colors.gradientEnd.copy(alpha = 0.8f)),
            startY = 0f,
            endY = 1000f,
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(
            440.dp,
        ).padding(16.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onEntryClick),
    ) {
        AsyncImage(
            model = hero.coverData,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(overlayGradient))

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Badge hidden as per design update
            Text(
                hero.title,
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontFamily = FontFamily(Font(eu.kanade.tachiyomi.R.font.montserrat_bold)),
                lineHeight = 34.sp,
                style = TextStyle(lineBreak = LineBreak.Heading),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(progressLabelRes, (hero.progressNumber % 1000).toInt()),
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(start = 22.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                modifier = Modifier.height(52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = colors.textOnAccent, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(actionLabelRes),
                    color = colors.textOnAccent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun QuickSourceButton(sourceName: String?, onClick: () -> Unit) {
    val colors = AuroraTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = colors.glass),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = sourceName ?: stringResource(AYMR.strings.aurora_open_source),
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HistoryRow(
    history: List<HomeHubHistory>,
    onEntryClick: (Long) -> Unit,
    onViewAllClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Column(modifier = Modifier.padding(top = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(AYMR.strings.aurora_recently_watched),
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                stringResource(AYMR.strings.aurora_more),
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onViewAllClick),
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(history, key = { it.entryId }) { item ->
                AuroraCard(
                    modifier = Modifier.width(128.dp).aspectRatio(0.68f),
                    title = item.title,
                    coverData = item.coverData,
                    subtitle = stringResource(
                        AYMR.strings.aurora_episode_number,
                        (item.progressNumber % 1000).toInt().toString(),
                    ),
                    onClick = { onEntryClick(item.entryId) },
                    imagePadding = 6.dp,
                )
            }
        }
    }
}

@Composable
private fun RecommendationsGrid(
    recommendations: List<HomeHubRecommendation>,
    onEntryClick: (Long) -> Unit,
    onMoreClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Column(modifier = Modifier.padding(top = 32.dp, start = 24.dp, end = 24.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(AYMR.strings.aurora_recently_added),
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                stringResource(AYMR.strings.aurora_more),
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onMoreClick),
            )
        }
        Spacer(Modifier.height(16.dp))

        // Horizontal scrollable row instead of grid
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(recommendations, key = { it.entryId }) { item ->
                AuroraCard(
                    modifier = Modifier.width(128.dp).aspectRatio(0.68f),
                    title = item.title,
                    coverData = item.coverData,
                    subtitle = item.subtitle,
                    onClick = { onEntryClick(item.entryId) },
                    imagePadding = 6.dp,
                )
            }
        }
    }
}

private fun parseNicknameHexColor(rawHex: String): Color? {
    val normalized = rawHex.trim()
    if (normalized.isEmpty()) return null
    val argbInt = runCatching {
        val prefixed = if (normalized.startsWith("#")) normalized else "#$normalized"
        AndroidColor.parseColor(prefixed)
    }.getOrNull() ?: return null
    return Color(argbInt)
}

private fun resolveNicknameColor(
    preset: NicknameColorPreset,
    customHex: String,
    colors: AuroraColors,
): Color {
    return when (preset) {
        NicknameColorPreset.Theme -> colors.textPrimary
        NicknameColorPreset.Accent -> colors.accent
        NicknameColorPreset.Gold -> colors.achievementGold
        NicknameColorPreset.Cyan -> Color(0xFF66D9EF)
        NicknameColorPreset.Pink -> Color(0xFFFF7BC0)
        NicknameColorPreset.Custom -> parseNicknameHexColor(customHex) ?: colors.textPrimary
    }
}

private fun applyNicknameEffect(text: String, effect: NicknameEffectPreset): String {
    return when (effect) {
        NicknameEffectPreset.None -> text
        NicknameEffectPreset.Sparkle -> "✦ $text ✦"
        NicknameEffectPreset.Hearts -> "♡ $text ♡"
        NicknameEffectPreset.Stars -> "★ $text ★"
        NicknameEffectPreset.Flowers -> "✿ $text ✿"
        NicknameEffectPreset.Kawaii -> "(≧◡≦) $text"
        NicknameEffectPreset.Cat -> "ฅ^•ﻌ•^ฅ $text"
        NicknameEffectPreset.Moon -> "☾ $text ☽"
        NicknameEffectPreset.Cloud -> "☁ $text ☁"
        NicknameEffectPreset.Ribbon -> "୨୧ $text ୨୧"
        NicknameEffectPreset.Sakura -> "❀ $text ❀"
    }
}

@Composable
private fun NicknameFontPreset.label(): String {
    return when (this) {
        NicknameFontPreset.Default -> stringResource(AYMR.strings.aurora_nickname_font_default)
        NicknameFontPreset.Montserrat -> stringResource(AYMR.strings.aurora_nickname_font_montserrat)
        NicknameFontPreset.Lora -> stringResource(AYMR.strings.aurora_nickname_font_lora)
        NicknameFontPreset.Nunito -> stringResource(AYMR.strings.aurora_nickname_font_nunito)
        NicknameFontPreset.PtSerif -> stringResource(AYMR.strings.aurora_nickname_font_pt_serif)
    }
}

@Composable
private fun NicknameColorPreset.label(): String {
    return when (this) {
        NicknameColorPreset.Theme -> stringResource(AYMR.strings.aurora_nickname_color_theme)
        NicknameColorPreset.Accent -> stringResource(AYMR.strings.aurora_nickname_color_accent)
        NicknameColorPreset.Gold -> stringResource(AYMR.strings.aurora_nickname_color_gold)
        NicknameColorPreset.Cyan -> stringResource(AYMR.strings.aurora_nickname_color_cyan)
        NicknameColorPreset.Pink -> stringResource(AYMR.strings.aurora_nickname_color_pink)
        NicknameColorPreset.Custom -> stringResource(AYMR.strings.aurora_nickname_color_custom)
    }
}

@Composable
private fun NicknameEffectPreset.label(): String {
    return when (this) {
        NicknameEffectPreset.None -> stringResource(AYMR.strings.aurora_nickname_effect_none)
        NicknameEffectPreset.Sparkle -> stringResource(AYMR.strings.aurora_nickname_effect_sparkle)
        NicknameEffectPreset.Hearts -> stringResource(AYMR.strings.aurora_nickname_effect_hearts)
        NicknameEffectPreset.Stars -> stringResource(AYMR.strings.aurora_nickname_effect_stars)
        NicknameEffectPreset.Flowers -> stringResource(AYMR.strings.aurora_nickname_effect_flowers)
        NicknameEffectPreset.Kawaii -> stringResource(AYMR.strings.aurora_nickname_effect_kawaii)
        NicknameEffectPreset.Cat -> stringResource(AYMR.strings.aurora_nickname_effect_cat)
        NicknameEffectPreset.Moon -> stringResource(AYMR.strings.aurora_nickname_effect_moon)
        NicknameEffectPreset.Cloud -> stringResource(AYMR.strings.aurora_nickname_effect_cloud)
        NicknameEffectPreset.Ribbon -> stringResource(AYMR.strings.aurora_nickname_effect_ribbon)
        NicknameEffectPreset.Sakura -> stringResource(AYMR.strings.aurora_nickname_effect_sakura)
    }
}

@Composable
private fun GreetingDecorationPreset.label(): String {
    return when (this) {
        GreetingDecorationPreset.Auto -> stringResource(AYMR.strings.aurora_greeting_decoration_auto)
        GreetingDecorationPreset.None -> stringResource(AYMR.strings.aurora_greeting_decoration_none)
        GreetingDecorationPreset.Sparkle -> stringResource(AYMR.strings.aurora_greeting_decoration_sparkle)
        GreetingDecorationPreset.Hearts -> stringResource(AYMR.strings.aurora_greeting_decoration_hearts)
        GreetingDecorationPreset.Stars -> stringResource(AYMR.strings.aurora_greeting_decoration_stars)
        GreetingDecorationPreset.Flowers -> stringResource(AYMR.strings.aurora_greeting_decoration_flowers)
    }
}

@Composable
private fun StyledNicknameText(
    text: String,
    nicknameStyle: NicknameStyle,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val displayText = applyNicknameEffect(text, nicknameStyle.effect)
    val textColor = resolveNicknameColor(nicknameStyle.color, nicknameStyle.customColorHex, colors)
    val outlineColor = if (textColor.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.8f)
    }
    val outlineOffset = nicknameStyle.outlineWidth.coerceIn(1, 8).dp
    val fontFamily = nicknameStyle.font.fontRes?.let { FontFamily(Font(it)) }
    val baseStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Black,
    )
    val shadow = if (nicknameStyle.glow) {
        Shadow(
            color = textColor.copy(alpha = 0.85f),
            blurRadius = 20f,
        )
    } else {
        null
    }

    Box(modifier = modifier) {
        if (nicknameStyle.outline) {
            listOf(
                -outlineOffset to 0.dp,
                outlineOffset to 0.dp,
                0.dp to -outlineOffset,
                0.dp to outlineOffset,
                -outlineOffset to -outlineOffset,
                -outlineOffset to outlineOffset,
                outlineOffset to -outlineOffset,
                outlineOffset to outlineOffset,
            ).forEach { (x, y) ->
                Text(
                    text = displayText,
                    modifier = Modifier.offset(x = x, y = y),
                    style = baseStyle.copy(color = outlineColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = displayText,
            style = baseStyle.copy(
                color = textColor,
                shadow = shadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NameStyleChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) colors.accent.copy(alpha = 0.2f) else colors.glass,
                RoundedCornerShape(999.dp),
            )
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.5f) else colors.divider,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            color = if (selected) colors.accent else colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun NameDialog(
    currentName: String,
    currentStyle: NicknameStyle,
    onDismiss: () -> Unit,
    onConfirm: (String, NicknameStyle) -> Unit,
) {
    var text by remember(currentName) { mutableStateOf(currentName) }
    var selectedFont by remember(currentStyle) { mutableStateOf(currentStyle.font) }
    var selectedColor by remember(currentStyle) { mutableStateOf(currentStyle.color) }
    var customColorHex by remember(currentStyle) { mutableStateOf(currentStyle.customColorHex) }
    var outlineEnabled by remember(currentStyle) { mutableStateOf(currentStyle.outline) }
    var outlineWidth by remember(currentStyle) { mutableIntStateOf(currentStyle.outlineWidth.coerceIn(1, 8)) }
    var glowEnabled by remember(currentStyle) { mutableStateOf(currentStyle.glow) }
    var selectedEffect by remember(currentStyle) { mutableStateOf(currentStyle.effect) }
    var isEffectDropdownOpen by remember { mutableStateOf(false) }

    val previewStyle = NicknameStyle(
        font = selectedFont,
        color = selectedColor,
        outline = outlineEnabled,
        outlineWidth = outlineWidth,
        glow = glowEnabled,
        effect = selectedEffect,
        customColorHex = customColorHex,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.aurora_change_nickname)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(AYMR.strings.aurora_nickname_field_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(AYMR.strings.aurora_nickname_preview),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AuroraTheme.colors.glass)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    StyledNicknameText(
                        text = text.trim().ifEmpty { currentName },
                        nicknameStyle = previewStyle,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(AYMR.strings.aurora_nickname_font),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                NicknameFontPreset.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            NameStyleChip(
                                title = preset.label(),
                                selected = selectedFont == preset,
                                onClick = { selectedFont = preset },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = stringResource(AYMR.strings.aurora_nickname_color),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                NicknameColorPreset.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            NameStyleChip(
                                title = preset.label(),
                                selected = selectedColor == preset,
                                onClick = { selectedColor = preset },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (selectedColor == NicknameColorPreset.Custom) {
                    val customColorValid = parseNicknameHexColor(customColorHex) != null
                    OutlinedTextField(
                        value = customColorHex,
                        onValueChange = { value ->
                            val compact = value.replace(" ", "")
                            customColorHex = when {
                                compact.isEmpty() -> "#"
                                compact.startsWith("#") -> compact
                                else -> "#$compact"
                            }
                        },
                        singleLine = true,
                        label = { Text(stringResource(AYMR.strings.aurora_nickname_custom_color)) },
                        supportingText = { Text(stringResource(AYMR.strings.aurora_nickname_custom_color_hint)) },
                        isError = !customColorValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(AYMR.strings.aurora_nickname_outline),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = outlineEnabled, onCheckedChange = { outlineEnabled = it })
                }

                if (outlineEnabled) {
                    Text(
                        stringResource(AYMR.strings.aurora_nickname_outline_thickness, outlineWidth.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = AuroraTheme.colors.textSecondary,
                    )
                    Slider(
                        value = outlineWidth.toFloat(),
                        onValueChange = { outlineWidth = it.roundToInt().coerceIn(1, 8) },
                        valueRange = 1f..8f,
                        steps = 6,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(AYMR.strings.aurora_nickname_glow),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = glowEnabled, onCheckedChange = { glowEnabled = it })
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(AYMR.strings.aurora_nickname_effect),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AuroraTheme.colors.glass)
                            .border(1.dp, AuroraTheme.colors.divider, RoundedCornerShape(12.dp))
                            .clickable { isEffectDropdownOpen = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = selectedEffect.label(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuroraTheme.colors.textPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = isEffectDropdownOpen,
                        onDismissRequest = { isEffectDropdownOpen = false },
                    ) {
                        NicknameEffectPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label()) },
                                onClick = {
                                    selectedEffect = preset
                                    isEffectDropdownOpen = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalizedCustomColor = customColorHex.trim().let { raw ->
                        if (raw.startsWith("#")) raw else "#$raw"
                    }.uppercase()
                    val safeCustomColor = normalizedCustomColor.takeIf {
                        parseNicknameHexColor(it) != null
                    } ?: currentStyle.customColorHex
                    onConfirm(
                        text.trim().ifEmpty { currentName },
                        previewStyle.copy(customColorHex = safeCustomColor),
                    )
                },
            ) {
                Text(stringResource(AYMR.strings.aurora_nickname_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(AYMR.strings.aurora_nickname_cancel))
            }
        },
    )
}

@Composable
private fun GreetingStyleDialog(
    currentGreeting: String,
    currentStyle: GreetingStyle,
    onDismiss: () -> Unit,
    onConfirm: (GreetingStyle) -> Unit,
) {
    var selectedFont by remember(currentStyle) { mutableStateOf(currentStyle.font) }
    var selectedColor by remember(currentStyle) { mutableStateOf(currentStyle.color) }
    var customColorHex by remember(currentStyle) { mutableStateOf(currentStyle.customColorHex) }
    var selectedDecoration by remember(currentStyle) { mutableStateOf(currentStyle.decoration) }
    var italicEnabled by remember(currentStyle) { mutableStateOf(currentStyle.italic) }
    var fontSize by remember(currentStyle) { mutableIntStateOf(currentStyle.fontSize.coerceIn(10, 26)) }

    val previewStyle = GreetingStyle(
        font = selectedFont,
        color = selectedColor,
        customColorHex = customColorHex,
        fontSize = fontSize.coerceIn(10, 26),
        decoration = selectedDecoration,
        italic = italicEnabled,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.aurora_change_greeting_style)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(AYMR.strings.aurora_greeting_preview),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AuroraTheme.colors.glass)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    val greetingFontFamily = previewStyle.font.fontRes?.let { FontFamily(Font(it)) }
                    val greetingColor =
                        resolveNicknameColor(previewStyle.color, previewStyle.customColorHex, AuroraTheme.colors)
                    Text(
                        text = decorateGreetingText(currentGreeting, previewStyle.decoration),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = previewStyle.fontSize.sp,
                            lineHeight = (previewStyle.fontSize + 4).sp,
                            fontStyle = if (previewStyle.italic) FontStyle.Italic else FontStyle.Normal,
                            fontFamily = greetingFontFamily,
                        ),
                        color = greetingColor,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(AYMR.strings.aurora_greeting_font),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                NicknameFontPreset.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            NameStyleChip(
                                title = preset.label(),
                                selected = selectedFont == preset,
                                onClick = { selectedFont = preset },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = stringResource(AYMR.strings.aurora_greeting_font_size, fontSize.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraTheme.colors.textSecondary,
                )
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { fontSize = it.roundToInt().coerceIn(10, 26) },
                    valueRange = 10f..26f,
                    steps = 15,
                )

                Text(
                    text = stringResource(AYMR.strings.aurora_greeting_color),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                NicknameColorPreset.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            NameStyleChip(
                                title = preset.label(),
                                selected = selectedColor == preset,
                                onClick = { selectedColor = preset },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (selectedColor == NicknameColorPreset.Custom) {
                    val customColorValid = parseNicknameHexColor(customColorHex) != null
                    OutlinedTextField(
                        value = customColorHex,
                        onValueChange = { value ->
                            val compact = value.replace(" ", "")
                            customColorHex = when {
                                compact.isEmpty() -> "#"
                                compact.startsWith("#") -> compact
                                else -> "#$compact"
                            }
                        },
                        singleLine = true,
                        label = { Text(stringResource(AYMR.strings.aurora_greeting_custom_color)) },
                        supportingText = { Text(stringResource(AYMR.strings.aurora_greeting_custom_color_hint)) },
                        isError = !customColorValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = stringResource(AYMR.strings.aurora_greeting_decoration),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                GreetingDecorationPreset.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            NameStyleChip(
                                title = preset.label(),
                                selected = selectedDecoration == preset,
                                onClick = { selectedDecoration = preset },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(AYMR.strings.aurora_greeting_italic),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = italicEnabled, onCheckedChange = { italicEnabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalizedCustomColor = customColorHex.trim().let { raw ->
                        if (raw.startsWith("#")) raw else "#$raw"
                    }.uppercase()
                    val safeCustomColor = normalizedCustomColor.takeIf {
                        parseNicknameHexColor(it) != null
                    } ?: currentStyle.customColorHex
                    onConfirm(
                        previewStyle.copy(
                            customColorHex = safeCustomColor,
                            fontSize = previewStyle.fontSize.coerceIn(10, 26),
                        ),
                    )
                },
            ) {
                Text(stringResource(AYMR.strings.aurora_nickname_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(AYMR.strings.aurora_nickname_cancel))
            }
        },
    )
}
