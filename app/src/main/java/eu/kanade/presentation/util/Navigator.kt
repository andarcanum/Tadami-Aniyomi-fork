package eu.kanade.presentation.util

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.ScreenModelStore
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.presentation.easteregg.crystal.CrystalCollectionScreen
import eu.kanade.presentation.easteregg.crystal.CrystalLunarIris
import eu.kanade.presentation.easteregg.crystal.CrystalLunarVeil
import eu.kanade.presentation.easteregg.crystal.CrystalMemorialScreen
import eu.kanade.presentation.easteregg.crystal.CrystalPortalTransition
import eu.kanade.presentation.reader.novel.NovelReaderSystemUiSession
import eu.kanade.presentation.theme.LocalIsEInkMode
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.powerManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * For invoking back press to the parent activity
 */
val LocalBackPress: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

private val uiPreferences: UiPreferences = Injekt.get()
private const val MODERN_ENTER_DURATION = 300
private const val MODERN_EXIT_DURATION = 300
private const val MODERN_ENTER_DELAY = 0
private const val TITLE_ZOOM_MS = 750
private const val TITLE_ZOOM_FADE_IN_MS = 550
private const val TITLE_ZOOM_EXIT_MS = 280
private val AURORA_EASING = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
private val MODERN_SLIDE_DISTANCE = 30.dp

interface Tab : cafe.adriel.voyager.navigator.tab.Tab {
    suspend fun onReselect(navigator: Navigator) {}

    @Composable
    fun currentNavigationStyle(): NavStyle = uiPreferences.navStyle().collectAsState().value
}

abstract class Screen : Screen {

    override val key: ScreenKey = uniqueScreenKey
}

/**
 * A variant of ScreenModel.coroutineScope except with the IO dispatcher instead of the
 * main dispatcher.
 */
val ScreenModel.ioCoroutineScope: CoroutineScope
    get() = ScreenModelStore.getOrPutDependency(
        screenModel = this,
        name = "ScreenModelIoCoroutineScope",
        factory = { key -> CoroutineScope(Dispatchers.IO + SupervisorJob()) + CoroutineName(key) },
        onDispose = { scope -> scope.cancel() },
    )

interface AssistContentScreen {
    fun onProvideAssistUrl(): String?
}

@Composable
fun DefaultNavigatorScreenTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val slideDistance = rememberSlideDistance()
    val density = LocalDensity.current
    val modernSlideDistance = with(density) { MODERN_SLIDE_DISTANCE.roundToPx() }
    val selectedMode = uiPreferences.navigationTransitionMode().collectAsState().value
    val titleScreenAnimationEnabled = uiPreferences.titleScreenAnimation().collectAsState().value
    val isEInkMode = LocalIsEInkMode.current
    val resolvedMode = resolveNavigationTransitionMode(
        selectedMode = selectedMode,
        animatorDurationScale = context.animatorDurationScale,
        isPowerSaveMode = context.powerManager.isPowerSaveMode,
        isEInkMode = isEInkMode,
    )
    ScreenTransition(
        navigator = navigator,
        transition = {
            val isForward = navigator.lastEvent != StackEvent.Pop
            if (
                shouldSuppressTransitionForInternalChapterReplace(
                    initialScreen = initialState,
                    targetScreen = targetState,
                    internalChapterReplacePending = NovelReaderSystemUiSession.isInternalChapterReplacePending(),
                )
            ) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                when {
                    // System (or user) has disabled animations altogether.
                    resolvedMode == ResolvedNavigationTransitionMode.NONE -> {
                        CrystalPortalTransition.clear()
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                    // Cat-face Lunar Iris diaphragm portal from 1D Ceremony -> Crystal's Living Corner.
                    initialState is CrystalMemorialScreen &&
                        targetState is CrystalCollectionScreen &&
                        CrystalPortalTransition.consumeNavigatorPortal() -> crystalIrisPortalTransition()
                    // Slow cinematic twilight entrance into Crystal Memorial.
                    isForward && targetState is CrystalMemorialScreen -> crystalMemorialEnter()
                    // Velvety entrance into Crystal's Living Corner from Treasury.
                    isForward && targetState.isCrystalScreen() -> crystalScreenEnter()
                    !isForward && initialState.isCrystalScreen() -> crystalScreenExit()
                    // Dedicated title-screen entrance: Deep Zoom (scale + fade),
                    // only when pushing an entry screen forward and the option
                    // is enabled. Everything else keeps the configured mode.
                    titleScreenAnimationEnabled &&
                        isForward &&
                        targetState.isTitleScreen() -> titleScreenDeepZoom()
                    // Reverse of the entrance when leaving a title screen:
                    // the title zooms back out while the origin fades in.
                    titleScreenAnimationEnabled &&
                        !isForward &&
                        initialState.isTitleScreen() -> titleScreenZoomOut()
                    resolvedMode == ResolvedNavigationTransitionMode.LEGACY -> {
                        materialSharedAxisX(
                            forward = isForward,
                            slideDistance = slideDistance,
                        )
                    }
                    else -> {
                        modernSharedAxisX(
                            forward = isForward,
                            slideDistance = modernSlideDistance,
                        )
                    }
                }
            }
        },
        modifier = modifier,
    )
}

private fun Screen.isTitleScreen(): Boolean =
    this is MangaScreen || this is AnimeScreen || this is NovelScreen

private fun Screen.isCrystalScreen(): Boolean =
    this is CrystalCollectionScreen || this is CrystalMemorialScreen

private fun AnimatedContentTransitionScope<Screen>.crystalMemorialEnter(): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = CrystalLunarVeil.NAV_ENTER_MS,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    )
    val exit = fadeOut(
        animationSpec = tween(
            durationMillis = CrystalLunarVeil.NAV_EXIT_MS,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    ) + scaleOut(
        targetScale = CrystalLunarVeil.NAV_EXIT_SCALE,
        animationSpec = tween(
            durationMillis = CrystalLunarVeil.NAV_ENTER_MS,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    )
    return (enter togetherWith exit).apply {
        targetContentZIndex = 1f
    }
}

private fun AnimatedContentTransitionScope<Screen>.crystalIrisPortalTransition(): ContentTransform {
    // CrystalCollectionScreen draws its own circular iris clip mask starting at radius 0px;
    // keep the container opaque immediately while the live constellation stays visible underneath.
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = 1,
            easing = LinearEasing,
        ),
    )
    val exit = fadeOut(
        animationSpec = tween(
            durationMillis = CrystalLunarIris.EXIT_FADE_MS,
            delayMillis = CrystalLunarIris.EXIT_DELAY_MS,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    ) + scaleOut(
        targetScale = CrystalLunarIris.EXIT_SCALE,
        animationSpec = tween(
            durationMillis = CrystalLunarIris.DURATION_MS,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    )
    return (enter togetherWith exit).apply {
        targetContentZIndex = 1f
    }
}

private fun AnimatedContentTransitionScope<Screen>.crystalScreenEnter(): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = 480,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    ) + scaleIn(
        initialScale = 0.96f,
        animationSpec = tween(
            durationMillis = 480,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    )
    val exit = fadeOut(
        animationSpec = tween(
            durationMillis = 360,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    )
    return (enter togetherWith exit).apply {
        targetContentZIndex = 1f
    }
}

private fun AnimatedContentTransitionScope<Screen>.crystalScreenExit(): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = 380,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    )
    val exit = fadeOut(
        animationSpec = tween(
            durationMillis = 420,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    ) + scaleOut(
        targetScale = 0.975f,
        animationSpec = tween(
            durationMillis = 420,
            easing = CrystalLunarVeil.CINEMATIC_EASING,
        ),
    )
    return (enter togetherWith exit).apply {
        targetContentZIndex = 1f
    }
}

private fun AnimatedContentTransitionScope<Screen>.titleScreenDeepZoom(): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = TITLE_ZOOM_FADE_IN_MS,
            easing = AURORA_EASING,
        ),
    ) + scaleIn(
        initialScale = 0.90f,
        animationSpec = tween(
            durationMillis = TITLE_ZOOM_MS,
            easing = AURORA_EASING,
        ),
    )
    val exit = fadeOut(
        animationSpec = tween(
            durationMillis = TITLE_ZOOM_EXIT_MS,
            easing = AURORA_EASING,
        ),
    )
    return (enter togetherWith exit).apply {
        targetContentZIndex = 1f
    }
}

private fun AnimatedContentTransitionScope<Screen>.titleScreenZoomOut(): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = TITLE_ZOOM_EXIT_MS,
            easing = AURORA_EASING,
        ),
    )
    val exit = fadeOut(
        animationSpec = tween(
            durationMillis = TITLE_ZOOM_EXIT_MS,
            easing = AURORA_EASING,
        ),
    ) + scaleOut(
        targetScale = 0.94f,
        animationSpec = tween(
            durationMillis = TITLE_ZOOM_EXIT_MS,
            easing = AURORA_EASING,
        ),
    )
    return (enter togetherWith exit).apply {
        targetContentZIndex = 1f
    }
}

@Composable
fun ScreenTransition(
    navigator: Navigator,
    transition: AnimatedContentTransitionScope<Screen>.() -> ContentTransform,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() },
) {
    AnimatedContent(
        targetState = navigator.lastItem,
        transitionSpec = transition,
        modifier = modifier,
        label = "transition",
    ) { screen ->
        navigator.saveableState("transition", screen) {
            content(screen)
        }
    }
}

private fun AnimatedContentTransitionScope<Screen>.modernSharedAxisX(
    forward: Boolean,
    slideDistance: Int,
): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = MODERN_ENTER_DURATION,
            delayMillis = MODERN_ENTER_DELAY,
            easing = AURORA_EASING,
        ),
    ) + slideInHorizontally(
        initialOffsetX = { if (forward) slideDistance else -slideDistance },
        animationSpec = tween(
            durationMillis = MODERN_ENTER_DURATION,
            delayMillis = MODERN_ENTER_DELAY,
            easing = AURORA_EASING,
        ),
    )
    val exit = fadeOut(
        animationSpec = tween(
            durationMillis = MODERN_EXIT_DURATION,
            easing = AURORA_EASING,
        ),
    ) + slideOutHorizontally(
        targetOffsetX = { if (forward) -slideDistance else slideDistance },
        animationSpec = tween(
            durationMillis = MODERN_EXIT_DURATION,
            easing = AURORA_EASING,
        ),
    )
    return (enter togetherWith exit).apply {
        targetContentZIndex = 1f
    }
}
