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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.tadami.aurora.R
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.domain.ui.model.HomeHeaderLayoutElement
import eu.kanade.domain.ui.model.HomeHeaderLayoutSpec
import eu.kanade.domain.ui.model.HomeHeroCtaMode
import eu.kanade.domain.ui.model.HomeHubRecentCardMode
import eu.kanade.domain.ui.model.HomeStreakCounterStyle
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.util.Tab
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.injectLazy
import java.time.LocalDate
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

internal enum class HomeHubSection {
    Anime,
    Manga,
    Novel,
    ;

    val key: String
        get() = name.lowercase()

    companion object {
        fun fromKey(key: String): HomeHubSection {
            return entries.firstOrNull { it.key == key } ?: Anime
        }
    }
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

internal enum class HomeHubHeroActionIcon {
    Play,
}

internal data class HomeHubHeroActionSpec(
    val labelRes: dev.icerock.moko.resources.StringResource,
    val progressLabelRes: dev.icerock.moko.resources.StringResource,
    val icon: HomeHubHeroActionIcon,
)

internal enum class HomeHubHeroButtonVisualMode {
    AuroraGlass,
    ClassicSolid,
}

internal data class HomeHubHeroButtonSurfaceSpec(
    val containerAlpha: Float,
    val usesGradient: Boolean,
    val borderAlpha: Float,
    val innerGlowAlpha: Float,
    val highlightAlpha: Float,
)

internal fun resolveHomeHubHeroActionSpec(
    section: HomeHubSection,
    progressNumber: Double,
    mode: HomeHeroCtaMode,
): HomeHubHeroActionSpec {
    val progressLabelRes = when (section) {
        HomeHubSection.Anime -> AYMR.strings.aurora_episode_progress
        HomeHubSection.Manga, HomeHubSection.Novel -> AYMR.strings.aurora_chapter_progress
    }
    val hasProgress = progressNumber > 0.0

    return when (mode) {
        HomeHeroCtaMode.Classic -> {
            val labelRes = if (hasProgress) MR.strings.action_resume else MR.strings.action_start
            HomeHubHeroActionSpec(
                labelRes = labelRes,
                progressLabelRes = progressLabelRes,
                icon = HomeHubHeroActionIcon.Play,
            )
        }
        HomeHeroCtaMode.Aurora -> {
            val labelRes = if (hasProgress) {
                MR.strings.action_resume
            } else {
                when (section) {
                    HomeHubSection.Anime -> AYMR.strings.aurora_play
                    HomeHubSection.Manga, HomeHubSection.Novel -> AYMR.strings.aurora_read
                }
            }
            HomeHubHeroActionSpec(
                labelRes = labelRes,
                progressLabelRes = progressLabelRes,
                icon = HomeHubHeroActionIcon.Play,
            )
        }
    }
}

internal fun resolveHomeHubHeroButtonVisualMode(mode: HomeHeroCtaMode): HomeHubHeroButtonVisualMode {
    return when (mode) {
        HomeHeroCtaMode.Aurora -> HomeHubHeroButtonVisualMode.AuroraGlass
        HomeHeroCtaMode.Classic -> HomeHubHeroButtonVisualMode.ClassicSolid
    }
}

internal fun resolveHomeHubHeroButtonSurfaceSpec(
    mode: HomeHeroCtaMode,
    isDark: Boolean,
): HomeHubHeroButtonSurfaceSpec {
    return when (mode) {
        HomeHeroCtaMode.Aurora -> HomeHubHeroButtonSurfaceSpec(
            containerAlpha = if (isDark) 0.50f else 0.78f,
            usesGradient = false,
            borderAlpha = if (isDark) 0.12f else 0.18f,
            innerGlowAlpha = if (isDark) 0.55f else 0.10f,
            highlightAlpha = if (isDark) 0f else 0.12f,
        )
        HomeHeroCtaMode.Classic -> HomeHubHeroButtonSurfaceSpec(
            containerAlpha = 1f,
            usesGradient = true,
            borderAlpha = 0.12f,
            innerGlowAlpha = 0f,
            highlightAlpha = 0f,
        )
    }
}

internal fun resolveHomeHubHeroButtonGlowEnabled(
    mode: HomeHeroCtaMode,
): Boolean {
    return when (mode) {
        HomeHeroCtaMode.Aurora -> false
        HomeHeroCtaMode.Classic -> false
    }
}

internal enum class HomeHubRecentCardRenderMode {
    AuroraPoster,
    ClassicAuroraCard,
}

internal data class HomeHubRecentPosterCardSpec(
    val posterAspectRatio: Float,
    val titleMaxLines: Int,
    val textHorizontalPaddingDp: Int,
    val textTopSpacingDp: Int,
    val textBlockMinHeightDp: Int,
)

internal data class HomeHubRecentPosterSurfaceSpec(
    val containerAlpha: Float,
    val posterAlpha: Float,
)

internal fun resolveHomeHubRecentCardRenderMode(mode: HomeHubRecentCardMode): HomeHubRecentCardRenderMode {
    return when (mode) {
        HomeHubRecentCardMode.Aurora -> HomeHubRecentCardRenderMode.AuroraPoster
        HomeHubRecentCardMode.Classic -> HomeHubRecentCardRenderMode.ClassicAuroraCard
    }
}

internal fun resolveHomeHubRecentPosterCardSpec(deviceClass: AuroraDeviceClass): HomeHubRecentPosterCardSpec {
    return when (deviceClass) {
        AuroraDeviceClass.Phone -> HomeHubRecentPosterCardSpec(
            posterAspectRatio = 0.9f,
            titleMaxLines = 2,
            textHorizontalPaddingDp = 2,
            textTopSpacingDp = 8,
            textBlockMinHeightDp = 58,
        )
        AuroraDeviceClass.TabletCompact,
        AuroraDeviceClass.TabletExpanded,
        -> HomeHubRecentPosterCardSpec(
            posterAspectRatio = 0.9f,
            titleMaxLines = 2,
            textHorizontalPaddingDp = 2,
            textTopSpacingDp = 8,
            textBlockMinHeightDp = 54,
        )
    }
}

internal fun resolveHomeHubRecentPosterSurfaceSpec(isDark: Boolean): HomeHubRecentPosterSurfaceSpec {
    return if (isDark) {
        HomeHubRecentPosterSurfaceSpec(
            containerAlpha = 0.06f,
            posterAlpha = 0.10f,
        )
    } else {
        HomeHubRecentPosterSurfaceSpec(
            containerAlpha = 0.12f,
            posterAlpha = 0.18f,
        )
    }
}

internal fun shouldResetHomeHubScroll(previousPage: Int, currentPage: Int): Boolean {
    return previousPage != currentPage
}

internal fun shouldUseHomeHubWrappedSections(deviceClass: AuroraDeviceClass): Boolean {
    return deviceClass != AuroraDeviceClass.Phone
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

internal fun resolveHomeStreakStylePickerOptions(): List<HomeStreakCounterStyle> {
    return listOf(
        HomeStreakCounterStyle.ClassicBadge,
        HomeStreakCounterStyle.NumberBadgeOnly,
        HomeStreakCounterStyle.NoBadge,
    )
}

internal fun shouldFillNicknameRowSpace(showNameEditHint: Boolean): Boolean {
    // Keep the edit hint visually attached to the nickname instead of pushing it near the avatar.
    return !showNameEditHint
}

private const val HOME_HEADER_CANVAS_HEIGHT_ONE_LINE_DP = 72
private const val HOME_HEADER_CANVAS_HEIGHT_TWO_LINES_DP = 76
private const val HOME_HEADER_CANVAS_HEIGHT_THREE_LINES_DP = 80
private const val HOME_HEADER_CANVAS_HEIGHT_FOUR_LINES_DP = 88

internal fun resolveGreetingLineLimit(measuredLineCount: Int): Int {
    return measuredLineCount.coerceIn(1, 4)
}

internal fun resolveHomeHeaderCanvasHeightDp(lineLimit: Int): Int {
    return when (lineLimit.coerceIn(1, 4)) {
        1 -> HOME_HEADER_CANVAS_HEIGHT_ONE_LINE_DP
        2 -> HOME_HEADER_CANVAS_HEIGHT_TWO_LINES_DP
        3 -> HOME_HEADER_CANVAS_HEIGHT_THREE_LINES_DP
        else -> HOME_HEADER_CANVAS_HEIGHT_FOUR_LINES_DP
    }
}

internal fun resolveGreetingSlotHeightPx(lineLimit: Int): Float {
    return when (lineLimit.coerceIn(1, 4)) {
        1 -> 24f
        2 -> 36f
        3 -> 48f
        else -> 56f
    }
}

private const val HOME_HEADER_NICKNAME_MIN_GAP_PX = 2f
private const val HOME_HEADER_DEFAULT_NICKNAME_Y_PX = 26f
private const val HOME_HEADER_NICKNAME_ATTACH_TOLERANCE_PX = 6f

internal fun resolveNicknameYForGreetingOverlap(
    nicknameY: Float,
    greetingY: Float,
    greetingHeight: Float,
    minGap: Float = HOME_HEADER_NICKNAME_MIN_GAP_PX,
    nicknameX: Float = 0f,
    nicknameWidth: Float = Float.POSITIVE_INFINITY,
    greetingX: Float = 0f,
    greetingWidth: Float = Float.POSITIVE_INFINITY,
    defaultNicknameY: Float = HOME_HEADER_DEFAULT_NICKNAME_Y_PX,
    attachTolerance: Float = HOME_HEADER_NICKNAME_ATTACH_TOLERANCE_PX,
): Float {
    if (!rangesOverlap(nicknameX, nicknameWidth, greetingX, greetingWidth)) {
        return nicknameY
    }

    val minimumSafeNicknameY = greetingY + greetingHeight + minGap
    val attachThreshold = defaultNicknameY + attachTolerance
    return if (nicknameY <= attachThreshold) {
        minimumSafeNicknameY
    } else {
        maxOf(nicknameY, minimumSafeNicknameY)
    }
}

internal fun resolveHomeHeaderEffectivePoint(
    element: HomeHeaderLayoutElement,
    layoutSpec: HomeHeaderLayoutSpec,
    elementSizes: Map<HomeHeaderLayoutElement, HomeHeaderPixelSize>,
    canvasWidth: Float,
    canvasHeight: Float,
    showGreeting: Boolean,
): HomeHeaderPixelPoint {
    fun pointFor(target: HomeHeaderLayoutElement): HomeHeaderPixelPoint {
        val size = elementSizes.getValue(target)
        return clampHomeHeaderPixelPoint(
            point = HomeHeaderPixelPoint(
                x = layoutSpec.positionOf(target).x,
                y = layoutSpec.positionOf(target).y,
            ),
            elementSize = size,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
        )
    }

    val point = pointFor(element)
    if (element != HomeHeaderLayoutElement.Nickname || !showGreeting) {
        return point
    }

    val nicknameSize = elementSizes.getValue(HomeHeaderLayoutElement.Nickname)
    val greetingPoint = pointFor(HomeHeaderLayoutElement.Greeting)
    val greetingSize = elementSizes.getValue(HomeHeaderLayoutElement.Greeting)
    return point.copy(
        y = resolveNicknameYForGreetingOverlap(
            nicknameY = point.y,
            greetingY = greetingPoint.y,
            greetingHeight = greetingSize.height,
            nicknameX = point.x,
            nicknameWidth = nicknameSize.width,
            greetingX = greetingPoint.x,
            greetingWidth = greetingSize.width,
        ),
    )
}

private fun rangesOverlap(
    firstX: Float,
    firstWidth: Float,
    secondX: Float,
    secondWidth: Float,
): Boolean {
    if (!firstWidth.isFinite() || !secondWidth.isFinite()) {
        return true
    }

    val safeFirstWidth = firstWidth.coerceAtLeast(0f)
    val safeSecondWidth = secondWidth.coerceAtLeast(0f)
    if (safeFirstWidth == 0f || safeSecondWidth == 0f) {
        return false
    }

    return firstX < secondX + safeSecondWidth &&
        firstX + safeFirstWidth > secondX
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

internal enum class NicknameFontPreset(val key: String, val fontRes: Int?) {
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

internal enum class NicknameColorPreset(val key: String) {
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

internal enum class NicknameEffectPreset(val key: String) {
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

internal data class NicknameStyle(
    val font: NicknameFontPreset,
    val fontSize: Int,
    val color: NicknameColorPreset,
    val outline: Boolean,
    val outlineWidth: Int,
    val glow: Boolean,
    val effect: NicknameEffectPreset,
    val customColorHex: String,
)

internal data class GreetingStyle(
    val font: NicknameFontPreset,
    val color: NicknameColorPreset,
    val customColorHex: String,
    val fontSize: Int,
    val alpha: Int,
    val decoration: GreetingDecorationPreset,
    val italic: Boolean,
)

internal data class HomeHubUiState(
    val hero: HomeHubHero? = null,
    val history: List<HomeHubHistory> = emptyList(),
    val recommendations: List<HomeHubRecommendation> = emptyList(),
    val userName: String,
    val userAvatar: String,
    val greeting: dev.icerock.moko.resources.StringResource,
    val greetingReady: Boolean,
    val isLoading: Boolean,
    val showWelcome: Boolean,
)

internal data class HomeHubHero(
    val entryId: Long,
    val title: String,
    val progressNumber: Double,
    val coverData: Any?,
)

internal data class HomeHubHistory(
    val entryId: Long,
    val title: String,
    val progressNumber: Double,
    val coverData: Any?,
)

internal data class HomeHubRecommendation(
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
        LaunchedEffect(Unit) {
            userProfilePreferences.migrateGreetingDefaultsV026IfNeeded()
        }

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

        val homeHubLastSectionPreference = remember { userProfilePreferences.homeHubLastSection() }
        val initialSelectedSection = remember(sections) {
            HomeHubSection.fromKey(homeHubLastSectionPreference.get())
                .takeIf { it in sections }
                ?: sections.first()
        }
        var selectedSection by rememberSaveable { mutableStateOf(initialSelectedSection) }
        LaunchedEffect(sections) {
            if (selectedSection !in sections) {
                selectedSection = HomeHubSection.fromKey(homeHubLastSectionPreference.get())
                    .takeIf { it in sections }
                    ?: sections.first()
            }
        }
        LaunchedEffect(selectedSection) {
            val key = selectedSection.key
            if (homeHubLastSectionPreference.get() != key) {
                homeHubLastSectionPreference.set(key)
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
        val homeStreakCounterStyleKey by userProfilePreferences.homeStreakCounterStyle().collectAsState()
        val homeStreakCounterStyle = remember(homeStreakCounterStyleKey) {
            HomeStreakCounterStyle.fromKey(homeStreakCounterStyleKey)
        }
        val homeHeroCtaModeKey by userProfilePreferences.homeHeroCtaMode().collectAsState()
        val homeHeroCtaMode = remember(homeHeroCtaModeKey) {
            HomeHeroCtaMode.fromKey(homeHeroCtaModeKey)
        }
        val homeHubRecentCardModeKey by userProfilePreferences.homeHubRecentCardMode().collectAsState()
        val homeHubRecentCardMode = remember(homeHubRecentCardModeKey) {
            HomeHubRecentCardMode.fromKey(homeHubRecentCardModeKey)
        }
        val homeHeaderGreetingAlignRight by userProfilePreferences.homeHeaderGreetingAlignRight().collectAsState()
        val homeHeaderNicknameAlignRight by userProfilePreferences.homeHeaderNicknameAlignRight().collectAsState()
        val homeHeaderLayoutJson by userProfilePreferences.homeHeaderLayoutJson().collectAsState()
        val homeHeaderLayout = remember(homeHeaderLayoutJson) {
            userProfilePreferences.getHomeHeaderLayoutOrDefault()
        }
        val nicknameFontKey by userProfilePreferences.nicknameFont().collectAsState()
        val nicknameFontSize by userProfilePreferences.nicknameFontSize().collectAsState()
        val nicknameColorKey by userProfilePreferences.nicknameColor().collectAsState()
        val nicknameCustomColorHex by userProfilePreferences.nicknameCustomColorHex().collectAsState()
        val nicknameOutline by userProfilePreferences.nicknameOutline().collectAsState()
        val nicknameOutlineWidth by userProfilePreferences.nicknameOutlineWidth().collectAsState()
        val nicknameGlow by userProfilePreferences.nicknameGlow().collectAsState()
        val nicknameEffectKey by userProfilePreferences.nicknameEffect().collectAsState()
        val nicknameStyle = NicknameStyle(
            font = NicknameFontPreset.fromKey(nicknameFontKey),
            fontSize = nicknameFontSize.coerceIn(14, 36),
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
        val greetingAlpha by userProfilePreferences.greetingAlpha().collectAsState()
        val greetingDecorationKey by userProfilePreferences.greetingDecoration().collectAsState()
        val greetingItalic by userProfilePreferences.greetingItalic().collectAsState()
        val greetingStyle = GreetingStyle(
            font = NicknameFontPreset.fromKey(greetingFontKey),
            color = NicknameColorPreset.fromKey(greetingColorKey),
            customColorHex = greetingCustomColorHex,
            fontSize = greetingFontSize.coerceIn(10, 26),
            alpha = greetingAlpha.coerceIn(10, 100),
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
        val headerGreetingReady = when (profileSection) {
            HomeHubSection.Anime -> animeState.greetingReady
            HomeHubSection.Manga -> mangaState.greetingReady
            HomeHubSection.Novel -> novelState.greetingReady
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
                    userProfilePreferences.nicknameFontSize().set(newStyle.fontSize.coerceIn(14, 36))
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
                    userProfilePreferences.greetingAlpha().set(newStyle.alpha.coerceIn(10, 100))
                    userProfilePreferences.greetingDecoration().set(newStyle.decoration.key)
                    userProfilePreferences.greetingItalic().set(newStyle.italic)
                    showGreetingDialog = false
                },
            )
        }
        var showStreakStyleDialog by remember { mutableStateOf(false) }
        if (showStreakStyleDialog) {
            HomeStreakStyleDialog(
                currentStyle = homeStreakCounterStyle,
                onDismiss = { showStreakStyleDialog = false },
                onConfirm = { selectedStyle ->
                    userProfilePreferences.homeStreakCounterStyle().set(selectedStyle.key)
                    showStreakStyleDialog = false
                },
            )
        }

        // Do not persist collapsed header position across app relaunches.
        var headerOffsetPx by remember { mutableStateOf(0f) }
        var headerHeightPx by remember { mutableIntStateOf(0) }
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
                            heroCtaMode = homeHeroCtaMode,
                            recentCardMode = homeHubRecentCardMode,
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
                            heroCtaMode = homeHeroCtaMode,
                            recentCardMode = homeHubRecentCardMode,
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
                            heroCtaMode = homeHeroCtaMode,
                            recentCardMode = homeHubRecentCardMode,
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
                    showGreeting = showHomeGreeting && headerGreetingReady,
                    showNameEditHint = showNameEditHint,
                    currentStreak = currentStreak,
                    showStreak = showHomeStreak,
                    streakStyle = homeStreakCounterStyle,
                    greetingAlignRight = homeHeaderGreetingAlignRight,
                    nicknameAlignRight = homeHeaderNicknameAlignRight,
                    homeHeaderLayout = homeHeaderLayout,
                    tabs = tabs,
                    selectedIndex = pagerState.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
                    onTabSelected = onSectionSelected,
                    onAvatarClick = { photoPickerLauncher.launch("image/*") },
                    onNameClick = { showNameDialog = true },
                    onGreetingClick = { showGreetingDialog = true },
                    onStreakClick = { showStreakStyleDialog = true },
                )
            },
        )
    }
}

internal fun HomeHubScreenModel.State.toUiState(): HomeHubUiState {
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
        greetingReady = greetingReady,
        isLoading = isLoading,
        showWelcome = showWelcome,
    )
}

internal fun MangaHomeHubScreenModel.State.toUiState(): HomeHubUiState {
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
        greetingReady = greetingReady,
        isLoading = isLoading,
        showWelcome = showWelcome,
    )
}

internal fun NovelHomeHubScreenModel.State.toUiState(): HomeHubUiState {
    return HomeHubUiState(
        hero = hero?.let {
            HomeHubHero(
                entryId = it.novelId,
                title = it.title,
                progressNumber = it.chapterNumber,
                coverData = mapNovelHomeHubCoverData(it.coverData),
            )
        },
        history = history.map {
            HomeHubHistory(
                entryId = it.novelId,
                title = it.title,
                progressNumber = it.chapterNumber,
                coverData = mapNovelHomeHubCoverData(it.coverData),
            )
        },
        recommendations = recommendations.map {
            HomeHubRecommendation(
                entryId = it.novelId,
                title = it.title,
                coverData = mapNovelHomeHubCoverData(it.coverData),
                subtitle = "${it.readCount}/${it.totalCount} \u0433\u043b.",
            )
        },
        userName = userName,
        userAvatar = userAvatar,
        greeting = greeting,
        greetingReady = greetingReady,
        isLoading = isLoading,
        showWelcome = showWelcome,
    )
}

internal fun mapNovelHomeHubCoverData(coverData: tachiyomi.domain.entries.novel.model.NovelCover): Any? {
    return coverData
}

internal fun resolveHomeHubHeaderTintAlpha(isDarkTheme: Boolean): Float {
    return if (isDarkTheme) 0.12f else 0.06f
}

internal fun resolveHomeHubHeaderTintSecondaryAlpha(primaryAlpha: Float): Float {
    return (primaryAlpha * 0.5f).coerceIn(0f, 1f)
}

internal fun homeHubRimLightAlphaStops(): List<Pair<Float, Float>> {
    return listOf(
        0.00f to 0.15f,
        0.28f to 0.05f,
        0.62f to 0.00f,
        1.00f to 0.00f,
    )
}

internal fun homeHubRimLightBrush(colors: AuroraColors): Brush {
    val stops = homeHubRimLightAlphaStops()
        .map { (stop, alpha) ->
            stop to if (colors.isDark) {
                Color.White.copy(alpha = alpha)
            } else {
                colors.accent.copy(alpha = alpha * 0.5f)
            }
        }
        .toTypedArray()
    return Brush.verticalGradient(colorStops = stops)
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

internal fun resolveNicknameColor(
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
internal fun StyledNicknameText(
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
        fontSize = nicknameStyle.fontSize.coerceIn(14, 36).sp,
        lineHeight = (nicknameStyle.fontSize.coerceIn(14, 36) + 2).sp,
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
internal fun NameStyleChip(
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
    var fontSize by remember(currentStyle) { mutableIntStateOf(currentStyle.fontSize.coerceIn(14, 36)) }
    var selectedColor by remember(currentStyle) { mutableStateOf(currentStyle.color) }
    var customColorHex by remember(currentStyle) { mutableStateOf(currentStyle.customColorHex) }
    var outlineEnabled by remember(currentStyle) { mutableStateOf(currentStyle.outline) }
    var outlineWidth by remember(currentStyle) { mutableIntStateOf(currentStyle.outlineWidth.coerceIn(1, 8)) }
    var glowEnabled by remember(currentStyle) { mutableStateOf(currentStyle.glow) }
    var selectedEffect by remember(currentStyle) { mutableStateOf(currentStyle.effect) }
    var isEffectDropdownOpen by remember { mutableStateOf(false) }

    val previewStyle = NicknameStyle(
        font = selectedFont,
        fontSize = fontSize.coerceIn(14, 36),
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
                    .heightIn(max = 520.dp)
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
                    text = stringResource(AYMR.strings.aurora_nickname_font_size, fontSize.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraTheme.colors.textSecondary,
                )
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { fontSize = it.roundToInt().coerceIn(14, 36) },
                    valueRange = 14f..36f,
                    steps = 21,
                )

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
    var alpha by remember(currentStyle) { mutableIntStateOf(currentStyle.alpha.coerceIn(10, 100)) }

    val previewStyle = GreetingStyle(
        font = selectedFont,
        color = selectedColor,
        customColorHex = customColorHex,
        fontSize = fontSize.coerceIn(10, 26),
        alpha = alpha.coerceIn(10, 100),
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
                    .heightIn(max = 520.dp)
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
                        color = greetingColor.copy(alpha = previewStyle.alpha.coerceIn(10, 100) / 100f),
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
                    text = stringResource(AYMR.strings.aurora_greeting_alpha, "$alpha%"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraTheme.colors.textSecondary,
                )
                Slider(
                    value = alpha.toFloat(),
                    onValueChange = { alpha = it.roundToInt().coerceIn(10, 100) },
                    valueRange = 10f..100f,
                    steps = 89,
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
