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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import com.tadami.aurora.R
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.domain.ui.model.HomeHeaderLayoutElement
import eu.kanade.domain.ui.model.HomeHeaderLayoutSpec
import eu.kanade.domain.ui.model.HomeHeroCtaMode
import eu.kanade.domain.ui.model.HomeHubRecentCardMode
import eu.kanade.domain.ui.model.HomeStreakCounterStyle
import eu.kanade.presentation.components.AuroraCard
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.components.resolveAuroraCoverModel
import eu.kanade.presentation.components.resolveAuroraCtaLabelShadowSpec
import eu.kanade.presentation.components.resolveAuroraHomeIconShadowSpec
import eu.kanade.presentation.components.toComposeShadow
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroOverlayBrush
import eu.kanade.presentation.more.settings.screen.browse.AnimeExtensionReposScreen
import eu.kanade.presentation.more.settings.screen.browse.MangaExtensionReposScreen
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionReposScreen
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.theme.resolveAuroraBorderColor
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.presentation.util.Tab
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
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.injectLazy
import java.time.LocalDate
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@Composable
internal fun HomeHubPinnedHeader(
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
    streakStyle: HomeStreakCounterStyle,
    greetingAlignRight: Boolean,
    nicknameAlignRight: Boolean,
    homeHeaderLayout: HomeHeaderLayoutSpec,
    tabs: kotlinx.collections.immutable.ImmutableList<TabContent>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit,
    onGreetingClick: () -> Unit,
    onStreakClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val isDarkTheme = colors.background.luminance() < 0.5f
    val headerTintAlpha = if (colors.isEInk) {
        0.03f
    } else {
        resolveHomeHubHeaderTintAlpha(isDarkTheme = isDarkTheme)
    }
    val headerTintSecondaryAlpha = resolveHomeHubHeaderTintSecondaryAlpha(primaryAlpha = headerTintAlpha)
    val headerBackgroundBrush = remember(colors.accent, headerTintAlpha, headerTintSecondaryAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                colors.accent.copy(alpha = headerTintAlpha),
                colors.accent.copy(alpha = headerTintSecondaryAlpha),
                Color.Transparent,
            ),
        )
    }
    Layout(
        content = {
            Column(
                modifier = Modifier
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(3.dp))
                Spacer(Modifier.height(7.dp))
                HomeHubProfileHeaderCanvas(
                    modifier = Modifier.fillMaxWidth(),
                    layoutSpec = homeHeaderLayout,
                    greetingText = stringResource(greeting),
                    userName = userName,
                    userAvatar = userAvatar,
                    nicknameStyle = nicknameStyle,
                    greetingStyle = greetingStyle,
                    showGreeting = showGreeting,
                    showNameEditHint = showNameEditHint,
                    currentStreak = currentStreak,
                    showStreak = showStreak,
                    streakStyle = streakStyle,
                    greetingAlignRight = greetingAlignRight,
                    nicknameAlignRight = nicknameAlignRight,
                    onAvatarClick = onAvatarClick,
                    onNameClick = onNameClick,
                    onGreetingClick = onGreetingClick,
                    onStreakClick = onStreakClick,
                )

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
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = headerBackgroundBrush)
            .clipToBounds(),
        measurePolicy = { measurables, constraints ->
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
        },
    )
}

@Composable
private fun HomeHubProfileHeaderCanvas(
    modifier: Modifier,
    layoutSpec: HomeHeaderLayoutSpec,
    greetingText: String,
    userName: String,
    userAvatar: String,
    nicknameStyle: NicknameStyle,
    greetingStyle: GreetingStyle,
    showGreeting: Boolean,
    showNameEditHint: Boolean,
    currentStreak: Int,
    showStreak: Boolean,
    streakStyle: HomeStreakCounterStyle,
    greetingAlignRight: Boolean,
    nicknameAlignRight: Boolean,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit,
    onGreetingClick: () -> Unit,
    onStreakClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceIn(1f, 1.6f)
    // Use the classic (phone) header layout on tablets too.
    val isTabletHeaderLayout = false
    val elementSizes = remember { defaultHomeHeaderElementPixelSizes() }
    val greetingFontFamily = greetingStyle.font.fontRes?.let { FontFamily(Font(it)) }
    val decoratedGreetingText = remember(greetingText, greetingStyle.decoration) {
        decorateGreetingText(greetingText, greetingStyle.decoration)
    }
    val greetingBaseTextStyle = MaterialTheme.typography.labelMedium
    val greetingTextStyle = remember(
        greetingBaseTextStyle,
        greetingStyle.fontSize,
        greetingStyle.italic,
        greetingFontFamily,
    ) {
        greetingBaseTextStyle.copy(
            fontSize = greetingStyle.fontSize.sp,
            lineHeight = (greetingStyle.fontSize + 3).sp,
            fontStyle = if (greetingStyle.italic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = greetingFontFamily,
            lineBreak = LineBreak.Heading,
        )
    }
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val preMeasureWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val preMeasureScaleX = preMeasureWidthPx / layoutSpec.canvas.width.coerceAtLeast(1f)
        val greetingBaseWidthPx = elementSizes.getValue(HomeHeaderLayoutElement.Greeting).width
        val greetingMeasureWidthPx = (greetingBaseWidthPx * preMeasureScaleX).toInt().coerceAtLeast(1)
        val preMeasuredGreetingLayout = remember(
            decoratedGreetingText,
            greetingTextStyle,
            greetingMeasureWidthPx,
        ) {
            textMeasurer.measure(
                text = AnnotatedString(decoratedGreetingText),
                style = greetingTextStyle,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = greetingMeasureWidthPx),
            )
        }
        val measuredGreetingLineCount = if (showGreeting) {
            if (preMeasuredGreetingLayout.hasVisualOverflow) {
                4
            } else {
                preMeasuredGreetingLayout.lineCount.coerceAtLeast(1)
            }
        } else {
            1
        }
        val greetingLineLimit = resolveGreetingLineLimit(measuredGreetingLineCount)
        val headerCanvasHeightDp = resolveHomeHeaderCanvasHeightDp(greetingLineLimit).dp

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerCanvasHeightDp),
        ) {
            val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val designWidthPx = layoutSpec.canvas.width.coerceAtLeast(1f)
            val designHeightPx = resolveHomeHeaderCanvasHeightDp(greetingLineLimit).toFloat().coerceAtLeast(1f)
            val scaleX = containerWidthPx / designWidthPx
            val scaleY = containerHeightPx / designHeightPx
            val defaultLayoutSpec = remember(layoutSpec.canvas) { HomeHeaderLayoutSpec.default(layoutSpec.canvas) }

            fun elementSizeFor(element: HomeHeaderLayoutElement): HomeHeaderPixelSize {
                val baseSize = elementSizes.getValue(element)
                return if (element == HomeHeaderLayoutElement.Greeting) {
                    baseSize.copy(height = resolveGreetingSlotHeightPx(greetingLineLimit))
                } else {
                    baseSize
                }
            }

            val effectiveElementSizes = HomeHeaderLayoutElement.entries.associateWith(::elementSizeFor)

            fun pointFor(spec: HomeHeaderLayoutSpec, element: HomeHeaderLayoutElement): HomeHeaderPixelPoint {
                val size = effectiveElementSizes.getValue(element)
                return clampHomeHeaderPixelPoint(
                    point = HomeHeaderPixelPoint(
                        x = spec.positionOf(element).x,
                        y = spec.positionOf(element).y,
                    ),
                    elementSize = size,
                    canvasWidth = designWidthPx,
                    canvasHeight = designHeightPx,
                )
            }

            fun tabletCustomDeltaFor(
                element: HomeHeaderLayoutElement,
            ): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
                val current = pointFor(layoutSpec, element)
                val default = pointFor(defaultLayoutSpec, element)
                return (current.x - default.x).dp to (current.y - default.y).dp
            }

            fun clampDpFramePosition(
                x: androidx.compose.ui.unit.Dp,
                y: androidx.compose.ui.unit.Dp,
                width: androidx.compose.ui.unit.Dp,
                height: androidx.compose.ui.unit.Dp,
            ): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
                val maxX = (maxWidth - width).coerceAtLeast(0.dp)
                val maxY = (maxHeight - height).coerceAtLeast(0.dp)
                return x.coerceIn(0.dp, maxX) to y.coerceIn(0.dp, maxY)
            }

            fun frameFor(element: HomeHeaderLayoutElement): Pair<Modifier, Modifier> {
                val size = elementSizeFor(element)
                if (isTabletHeaderLayout) {
                    val greetingSize = elementSizeFor(HomeHeaderLayoutElement.Greeting)
                    val nicknameSize = elementSizeFor(HomeHeaderLayoutElement.Nickname)
                    val avatarSize = elementSizeFor(HomeHeaderLayoutElement.Avatar)
                    val streakSize = elementSizeFor(HomeHeaderLayoutElement.Streak)

                    val horizontalPadding = 8.dp
                    // Tablet preset tuned from the visual editor payload (720x72 baseline),
                    // while still allowing user layout deltas on top.
                    val tabletGreetingRightPadding = 102.5.dp
                    val tabletGreetingHeightBase = 39.5.dp
                    val tabletGreetingYBase = 30.6.dp
                    val tabletNicknameXBase = 0.dp
                    val tabletNicknameYBase = 37.6.dp
                    val tabletNicknameWidthBase = 270.5.dp
                    val tabletStreakXAdjust = (-2).dp
                    val avatarWidthDp = avatarSize.width.dp
                    val avatarHeightDp = avatarSize.height.dp
                    val streakWidthDp = streakSize.width.dp
                    val streakHeightDp = streakSize.height.dp
                    val nicknameHeightDp = if (isTabletHeaderLayout) {
                        (nicknameSize.height.dp * fontScale.coerceIn(1f, 1.25f))
                            .coerceAtLeast(nicknameSize.height.dp)
                            .coerceAtMost(40.dp)
                    } else {
                        nicknameSize.height.dp
                    }
                    val greetingHeightDp = if (isTabletHeaderLayout) {
                        (tabletGreetingHeightBase * fontScale.coerceIn(1f, 1.4f))
                            .coerceAtLeast(tabletGreetingHeightBase)
                            .coerceAtMost(56.dp)
                    } else {
                        greetingSize.height.dp
                    }

                    val baseAvatarXDp = (maxWidth - avatarWidthDp - horizontalPadding).coerceAtLeast(0.dp)
                    // Lift avatar slightly on tablets so it aligns better with nickname/greeting row.
                    val baseAvatarYDp = (maxHeight - avatarHeightDp - 4.dp).coerceAtLeast(0.dp)
                    val baseStreakXDp = ((baseAvatarXDp + (avatarWidthDp - streakWidthDp) / 2f) + tabletStreakXAdjust)
                        .coerceAtLeast(0.dp)
                    // Keep the streak chip as high as possible on tablets to add separation from avatar.
                    val baseStreakYDp = 0.dp

                    val (avatarDeltaX, avatarDeltaY) = tabletCustomDeltaFor(HomeHeaderLayoutElement.Avatar)
                    val (streakDeltaX, streakDeltaY) = tabletCustomDeltaFor(HomeHeaderLayoutElement.Streak)
                    val (nicknameDeltaX, nicknameDeltaY) = tabletCustomDeltaFor(HomeHeaderLayoutElement.Nickname)
                    val (greetingDeltaX, greetingDeltaY) = tabletCustomDeltaFor(HomeHeaderLayoutElement.Greeting)

                    val (avatarXDp, avatarYDp) = clampDpFramePosition(
                        x = baseAvatarXDp + avatarDeltaX,
                        y = baseAvatarYDp + avatarDeltaY,
                        width = avatarWidthDp,
                        height = avatarHeightDp,
                    )
                    val (streakXDp, streakYDp) = clampDpFramePosition(
                        x = baseStreakXDp + streakDeltaX,
                        y = baseStreakYDp + streakDeltaY,
                        width = streakWidthDp,
                        height = streakHeightDp,
                    )

                    val baseNicknameXDp = if (isTabletHeaderLayout) tabletNicknameXBase else horizontalPadding
                    val baseNicknameYDp = if (isTabletHeaderLayout) tabletNicknameYBase else 28.dp
                    val greetingWidthDp = if (isTabletHeaderLayout) {
                        320.dp.coerceAtMost((maxWidth - horizontalPadding).coerceAtLeast(220.dp))
                    } else {
                        (maxWidth - (horizontalPadding * 2)).coerceIn(220.dp, 520.dp)
                    }
                    val nicknameXDpRaw = baseNicknameXDp + nicknameDeltaX
                    val nicknameYDpRaw = baseNicknameYDp + nicknameDeltaY
                    val nicknameXDp = nicknameXDpRaw.coerceAtLeast(0.dp)
                    val nicknameYDp = nicknameYDpRaw.coerceIn(0.dp, (maxHeight - nicknameHeightDp).coerceAtLeast(0.dp))
                    val nicknameWidthDp = if (isTabletHeaderLayout) {
                        tabletNicknameWidthBase
                            .coerceAtMost((maxWidth - nicknameXDp).coerceAtLeast(160.dp))
                            .coerceAtMost((avatarXDp - nicknameXDp - 12.dp).coerceAtLeast(160.dp))
                            .coerceAtMost(greetingWidthDp)
                            .coerceAtLeast(160.dp)
                    } else {
                        (avatarXDp - nicknameXDp - 12.dp)
                            .coerceIn(160.dp, 520.dp)
                            .coerceAtMost((maxWidth - nicknameXDp).coerceAtLeast(160.dp))
                            .coerceAtMost(520.dp)
                    }
                    val baseGreetingXDp = if (isTabletHeaderLayout) {
                        (maxWidth - greetingWidthDp - tabletGreetingRightPadding).coerceAtLeast(0.dp)
                    } else {
                        ((maxWidth - greetingWidthDp) / 2f).coerceAtLeast(0.dp)
                    }
                    val baseGreetingYDp = if (isTabletHeaderLayout) {
                        tabletGreetingYBase.coerceIn(0.dp, (maxHeight - greetingHeightDp).coerceAtLeast(0.dp))
                    } else {
                        (baseNicknameYDp + (nicknameHeightDp - greetingHeightDp) / 2f).coerceAtLeast(0.dp)
                    }
                    val (greetingXDp, greetingYDp) = clampDpFramePosition(
                        x = baseGreetingXDp + greetingDeltaX,
                        y = baseGreetingYDp + greetingDeltaY,
                        width = greetingWidthDp,
                        height = greetingHeightDp,
                    )

                    val xDp: androidx.compose.ui.unit.Dp
                    val yDp: androidx.compose.ui.unit.Dp
                    val widthDp: androidx.compose.ui.unit.Dp
                    val heightDp: androidx.compose.ui.unit.Dp
                    when (element) {
                        HomeHeaderLayoutElement.Greeting -> {
                            xDp = greetingXDp
                            yDp = greetingYDp
                            widthDp = greetingWidthDp
                            heightDp = greetingHeightDp
                        }
                        HomeHeaderLayoutElement.Nickname -> {
                            xDp = nicknameXDp
                            yDp = nicknameYDp
                            widthDp = nicknameWidthDp
                            heightDp = nicknameHeightDp
                        }
                        HomeHeaderLayoutElement.Avatar -> {
                            xDp = avatarXDp
                            yDp = avatarYDp
                            widthDp = avatarWidthDp
                            heightDp = avatarHeightDp
                        }
                        HomeHeaderLayoutElement.Streak -> {
                            xDp = streakXDp
                            yDp = streakYDp
                            widthDp = streakWidthDp
                            heightDp = streakHeightDp
                        }
                    }

                    val slotModifier = Modifier
                        .offset(x = xDp, y = yDp)
                        .width(widthDp)
                        .height(heightDp)
                    val contentModifier = Modifier.fillMaxSize()
                    return slotModifier to contentModifier
                }

                val point = resolveHomeHeaderEffectivePoint(
                    element = element,
                    layoutSpec = layoutSpec,
                    elementSizes = effectiveElementSizes,
                    canvasWidth = designWidthPx,
                    canvasHeight = designHeightPx,
                    showGreeting = showGreeting,
                )
                val xPx = point.x * scaleX
                val yPxRaw = point.y * scaleY
                val widthDp = with(density) { (size.width * scaleX).toDp() }
                val heightDp = with(density) { (size.height * scaleY).toDp() }
                val elementHeightPx = size.height * scaleY
                val yPx = yPxRaw.coerceIn(
                    0f,
                    (containerHeightPx - elementHeightPx).coerceAtLeast(0f),
                )

                val slotModifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .width(widthDp)
                    .height(heightDp)
                val contentModifier = Modifier.fillMaxSize()
                return slotModifier to contentModifier
            }

            if (showGreeting) {
                val (slotModifier, _) = frameFor(HomeHeaderLayoutElement.Greeting)
                val greetingColor = resolveNicknameColor(greetingStyle.color, greetingStyle.customColorHex, colors)
                Box(
                    modifier = slotModifier.clickable(onClick = onGreetingClick),
                    contentAlignment = when {
                        isTabletHeaderLayout -> Alignment.Center
                        else -> Alignment.BottomCenter
                    },
                ) {
                    Text(
                        text = decoratedGreetingText,
                        modifier = Modifier.fillMaxWidth(),
                        style = greetingTextStyle,
                        color = greetingColor.copy(alpha = greetingStyle.alpha.coerceIn(10, 100) / 100f),
                        fontWeight = FontWeight.Medium,
                        maxLines = if (isTabletHeaderLayout) 2 else greetingLineLimit,
                        textAlign = when {
                            isTabletHeaderLayout -> TextAlign.Center
                            greetingAlignRight -> TextAlign.End
                            else -> TextAlign.Start
                        },
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            run {
                val (slotModifier, contentModifier) = frameFor(HomeHeaderLayoutElement.Nickname)
                Box(slotModifier) {
                    Row(
                        modifier = contentModifier.clickable(onClick = onNameClick),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.weight(
                                1f,
                                fill = if (nicknameAlignRight) {
                                    true
                                } else {
                                    shouldFillNicknameRowSpace(showNameEditHint)
                                },
                            ),
                            contentAlignment = if (nicknameAlignRight) {
                                Alignment.CenterEnd
                            } else {
                                Alignment.CenterStart
                            },
                        ) {
                            StyledNicknameText(
                                text = userName,
                                nicknameStyle = nicknameStyle,
                            )
                        }
                        if (showNameEditHint) {
                            if (isTabletHeaderLayout) {
                                Spacer(Modifier.width(0.dp))
                                Box(
                                    modifier = Modifier
                                        .offset(x = 0.dp, y = 9.dp)
                                        .size(18.dp)
                                        .clickable(onClick = onNameClick),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            } else {
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
                }
            }

            val attachStreakToAvatarOnTablet = false

            if (showStreak && !attachStreakToAvatarOnTablet) {
                val (slotModifier, contentModifier) = frameFor(HomeHeaderLayoutElement.Streak)
                Box(slotModifier) {
                    Box(contentModifier, contentAlignment = Alignment.Center) {
                        HomeStreakCounterContent(
                            currentStreak = currentStreak,
                            style = streakStyle,
                            isTabletHeaderLayout = isTabletHeaderLayout,
                            colors = colors,
                            onClick = onStreakClick,
                        )
                    }
                }
            }

            run {
                val (slotModifier, contentModifier) = frameFor(HomeHeaderLayoutElement.Avatar)
                Box(slotModifier) {
                    Box(contentModifier, contentAlignment = Alignment.Center) {
                        if (attachStreakToAvatarOnTablet) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = (-42).dp),
                            ) {
                                HomeStreakCounterContent(
                                    currentStreak = currentStreak,
                                    style = streakStyle,
                                    isTabletHeaderLayout = isTabletHeaderLayout,
                                    colors = colors,
                                    onClick = onStreakClick,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(onClick = onAvatarClick),
                        ) {
                            if (userAvatar.isNotEmpty()) {
                                AsyncImage(
                                    model = userAvatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
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
            }
        }
    }
}

@Composable
private fun HomeStreakCounterContent(
    currentStreak: Int,
    style: HomeStreakCounterStyle,
    isTabletHeaderLayout: Boolean,
    colors: AuroraColors,
    onClick: (() -> Unit)? = null,
) {
    val contentModifier = Modifier.offset(y = if (isTabletHeaderLayout) (-3).dp else 0.dp)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    val labelText = currentStreak.toString()
    when (style) {
        HomeStreakCounterStyle.ClassicBadge -> {
            Row(
                modifier = contentModifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surface.copy(alpha = 0.28f))
                    .border(
                        width = 1.dp,
                        color = colors.accent.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .then(clickModifier)
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
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        HomeStreakCounterStyle.NumberBadgeOnly -> {
            Row(
                modifier = contentModifier.then(clickModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surface.copy(alpha = 0.28f))
                        .border(
                            width = 1.dp,
                            color = colors.accent.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        HomeStreakCounterStyle.NoBadge -> {
            Row(
                modifier = contentModifier.then(clickModifier),
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
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun HomeHeaderLayoutLivePreview(
    modifier: Modifier = Modifier,
    layoutSpec: HomeHeaderLayoutSpec,
    showGreeting: Boolean = true,
    showStreak: Boolean = true,
    streakStyle: HomeStreakCounterStyle = HomeStreakCounterStyle.NoBadge,
    greetingAlignRight: Boolean = false,
    nicknameAlignRight: Boolean = false,
) {
    val nicknameStyle = remember {
        NicknameStyle(
            font = NicknameFontPreset.Default,
            fontSize = 24,
            color = NicknameColorPreset.Theme,
            outline = false,
            outlineWidth = 2,
            glow = false,
            effect = NicknameEffectPreset.None,
            customColorHex = "#FFFFFF",
        )
    }
    val greetingStyle = remember {
        GreetingStyle(
            font = NicknameFontPreset.Default,
            color = NicknameColorPreset.Theme,
            customColorHex = "#FFFFFF",
            fontSize = 12,
            alpha = 60,
            decoration = GreetingDecorationPreset.Auto,
            italic = false,
        )
    }

    HomeHubProfileHeaderCanvas(
        modifier = modifier,
        layoutSpec = layoutSpec,
        greetingText = stringResource(AYMR.strings.home_header_layout_editor_preview_greeting),
        userName = stringResource(AYMR.strings.home_header_layout_editor_preview_nickname),
        userAvatar = "",
        nicknameStyle = nicknameStyle,
        greetingStyle = greetingStyle,
        showGreeting = showGreeting,
        showNameEditHint = false,
        currentStreak = 7,
        showStreak = showStreak,
        streakStyle = streakStyle,
        greetingAlignRight = greetingAlignRight,
        nicknameAlignRight = nicknameAlignRight,
        onAvatarClick = {},
        onNameClick = {},
        onGreetingClick = {},
        onStreakClick = {},
    )
}

@Composable
internal fun homeStreakCounterStyleLabel(style: HomeStreakCounterStyle): String {
    return when (style) {
        HomeStreakCounterStyle.ClassicBadge -> {
            stringResource(AYMR.strings.home_header_layout_editor_streak_style_classic_badge)
        }
        HomeStreakCounterStyle.NumberBadgeOnly -> {
            stringResource(AYMR.strings.home_header_layout_editor_streak_style_number_badge_only)
        }
        HomeStreakCounterStyle.NoBadge -> {
            stringResource(AYMR.strings.home_header_layout_editor_streak_style_no_badge)
        }
    }
}

@Composable
internal fun HomeStreakStyleDialog(
    currentStyle: HomeStreakCounterStyle,
    onDismiss: () -> Unit,
    onConfirm: (HomeStreakCounterStyle) -> Unit,
) {
    var selectedStyle by remember(currentStyle) { mutableStateOf(currentStyle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(AYMR.strings.home_header_layout_editor_streak_style_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                resolveHomeStreakStylePickerOptions().forEach { style ->
                    NameStyleChip(
                        title = homeStreakCounterStyleLabel(style),
                        selected = selectedStyle == style,
                        onClick = { selectedStyle = style },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedStyle) },
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


