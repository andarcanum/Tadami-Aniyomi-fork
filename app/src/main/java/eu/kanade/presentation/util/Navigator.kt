package eu.kanade.presentation.util

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import eu.kanade.presentation.reader.novel.NovelReaderSystemUiSession
import eu.kanade.presentation.theme.LocalIsEInkMode
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
private val AURORA_EASING = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
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
            if (
                shouldSuppressTransitionForInternalChapterReplace(
                    initialScreen = initialState,
                    targetScreen = targetState,
                    internalChapterReplacePending = NovelReaderSystemUiSession.isInternalChapterReplacePending(),
                )
            ) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                when (resolvedMode) {
                    ResolvedNavigationTransitionMode.NONE -> EnterTransition.None togetherWith ExitTransition.None
                    ResolvedNavigationTransitionMode.LEGACY -> {
                        materialSharedAxisX(
                            forward = navigator.lastEvent != StackEvent.Pop,
                            slideDistance = slideDistance,
                        )
                    }
                    ResolvedNavigationTransitionMode.MODERN -> {
                        modernSharedAxisX(
                            forward = navigator.lastEvent != StackEvent.Pop,
                            slideDistance = modernSlideDistance,
                        )
                    }
                }
            }
        },
        modifier = modifier,
    )
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
