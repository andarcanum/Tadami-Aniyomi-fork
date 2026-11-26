package eu.kanade.presentation.easteregg.crystal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.tadami.aurora.BuildConfig
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.SettingsTreasuryScreen
import eu.kanade.presentation.more.settings.screen.resolveSearchableSettingsBackPress
import eu.kanade.presentation.theme.LocalIsEInkMode
import eu.kanade.presentation.util.LocalBackPress
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A normal nested Treasury screen, keeping the complete keepsake set together. */
internal object CrystalCollectionScreen : SearchableSettings {
    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = AYMR.strings.crystal_section_title

    @Composable
    override fun getPreferences(): List<Preference> = listOf(
        Preference.PreferenceItem.CustomPreference(title = stringResource(AYMR.strings.crystal_collection_title)) {
            CrystalCollectionSection()
        },
    )

    @Composable
    override fun RowScope.AppBarAction() {
        if (isCrystalLivingLabAvailable(BuildConfig.DEBUG, BuildConfig.BUILD_TYPE)) {
            val navigator = LocalNavigator.currentOrThrow
            IconButton(onClick = { navigator.push(CrystalLivingCornerDebugScreen) }) {
                Icon(Icons.Outlined.BugReport, stringResource(AYMR.strings.crystal_living_lab_title))
            }
        }
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scroll = rememberScrollState()
        val back = resolveSearchableSettingsBackPress(LocalBackPress.current, { navigator.pop() })
        val appearance = rememberCrystalAppearance()
        val policy = rememberCrystalMotionPolicy()
        val isEInk = LocalIsEInkMode.current
        val playIris = remember { CrystalPortalTransition.consumeIrisReveal() }
        val animateIris = playIris && !appearance.reduced && !isEInk && policy.systemAnimations
        val iris = remember(animateIris) { Animatable(if (animateIris) 0f else 1f) }
        val irisSettled by remember(iris) { derivedStateOf { iris.value >= 0.999f } }
        LaunchedEffect(animateIris) {
            if (animateIris) {
                // Let the first frame lay out before starting the iris clock so frame 0 is never skipped.
                withFrameNanos { }
                iris.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = CrystalLunarIris.DURATION_MS,
                        easing = CrystalLunarIris.EASING,
                    ),
                )
            }
        }
        val irisPath = remember { Path() }
        val goldColor = Color(appearance.palette.gold)
        val roseColor = Color(appearance.palette.rose)
        BackHandler(onBack = back)
        CrystalCollectionRoomTheme { scene ->
            val topWallBackdropHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                (220.dp.toPx() - scroll.value).coerceAtLeast(0f).toDp()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val p = iris.value
                        if (p >= 0.999f) {
                            drawContent()
                        } else {
                            val (cx, cy) = CrystalSceneGeometry.constellationIrisCenter(
                                width = size.width,
                                height = size.height,
                                ceremonyBlend = 1f,
                            )
                            val maxRadius = CrystalLunarIris.maxCornerRadius(cx, cy, size.width, size.height)
                            val radius = p * maxRadius
                            if (radius > 0.5f) {
                                val roomScale = CrystalLunarIris.roomScale(p)
                                irisPath.reset()
                                irisPath.addOval(
                                    Rect(
                                        left = cx - radius,
                                        top = cy - radius,
                                        right = cx + radius,
                                        bottom = cy + radius,
                                    ),
                                )
                                clipPath(irisPath) {
                                    scale(roomScale, roomScale, pivot = Offset(cx, cy)) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                            }
                            drawCatFaceLunarIrisOverlay(
                                cx = cx,
                                cy = cy,
                                radius = radius,
                                progress = p,
                                gold = goldColor,
                                rose = roseColor,
                            )
                        }
                    }
                    .background(Color(scene.floor)),
            ) {
                if (topWallBackdropHeight > 0.dp) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(topWallBackdropHeight)
                            .background(Color(scene.wall)),
                    )
                }
                CrystalCollectionSection(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scroll),
                    ambientPaused = !irisSettled,
                )
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().height(60.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = Color(scene.wall).copy(alpha = .18f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color(scene.trim).copy(alpha = .35f)),
                    ) {
                        IconButton(onClick = back) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(AYMR.strings.crystal_back),
                                tint = Color(scene.heading),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    AppBarAction()
                }
            }
        }
    }
}

/** A saved but unacknowledged receipt resumes its ceremony; only acknowledged gifts use replay. */
internal fun CrystalMemorialState.replayFromCollection(): Boolean = isGranted && !hasPendingCeremony

internal enum class CrystalCollectionDestination { EXISTING_COLLECTION, EXISTING_TREASURY, NEW_TREASURY }

internal fun resolveCrystalCollectionDestination(stack: List<Screen>): CrystalCollectionDestination = when {
    stack.any { it == CrystalCollectionScreen } -> CrystalCollectionDestination.EXISTING_COLLECTION
    stack.any { it == SettingsTreasuryScreen } -> CrystalCollectionDestination.EXISTING_TREASURY
    else -> CrystalCollectionDestination.NEW_TREASURY
}

/** The collection always has the main Treasury behind it, including after a release ceremony. */
internal fun Navigator.openCrystalCollection() {
    when (resolveCrystalCollectionDestination(items)) {
        CrystalCollectionDestination.EXISTING_COLLECTION -> popUntil { it == CrystalCollectionScreen }
        CrystalCollectionDestination.EXISTING_TREASURY -> {
            popUntil { it == SettingsTreasuryScreen }
            push(CrystalCollectionScreen)
        }
        CrystalCollectionDestination.NEW_TREASURY -> {
            replace(SettingsTreasuryScreen)
            push(CrystalCollectionScreen)
        }
    }
}

private fun DrawScope.drawCatFaceLunarIrisOverlay(
    cx: Float,
    cy: Float,
    radius: Float,
    progress: Float,
    gold: Color,
    rose: Color,
) {
    val center = Offset(cx, cy)
    // Brief starlight ignition bloom right on Crystal's nose star as the diaphragm opens.
    val noseBloom = CrystalLunarIris.noseBloomAlpha(progress)
    if (noseBloom > 0.01f) {
        val bloomR = (28.dp.toPx() + progress * 92.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFFFFAF0).copy(alpha = 0.68f * noseBloom),
                0.42f to gold.copy(alpha = 0.34f * noseBloom),
                1f to Color.Transparent,
                center = center,
                radius = bloomR,
            ),
            radius = bloomR,
            center = center,
        )
    }

    val rimAlpha = CrystalLunarIris.rimAlpha(progress)
    if (rimAlpha <= 0.005f || radius <= 1f) return

    // Soft outer moonlit bloom behind the expanding diaphragm ring.
    drawCircle(
        color = gold.copy(alpha = 0.28f * rimAlpha),
        radius = radius,
        center = center,
        style = Stroke(width = (10.dp.toPx() * (1f - progress * 0.35f)).coerceAtLeast(2f)),
    )
    // Crisp luminous golden-pearl aperture edge.
    drawCircle(
        color = Color(0xFFFFF4DA).copy(alpha = 0.88f * rimAlpha),
        radius = radius,
        center = center,
        style = Stroke(width = (2.8.dp.toPx() * (1f - progress * 0.42f)).coerceAtLeast(1f)),
    )

    // Sixteen delicate 4-point sparkles rotating along the expanding lunar diaphragm.
    val sparkleLen = (6.8.dp.toPx() * rimAlpha).coerceAtLeast(1.5f)
    val sparkleStroke = (1.25.dp.toPx() * rimAlpha).coerceAtLeast(0.75f)
    val twoPi = (2.0 * PI).toFloat()
    for (i in 0 until CrystalLunarIris.SPARKLE_COUNT) {
        val angle = (i.toFloat() / CrystalLunarIris.SPARKLE_COUNT) * twoPi + progress * 1.35f
        val sx = cx + cos(angle) * radius
        val sy = cy + sin(angle) * radius
        val tint = if (i % 2 == 0) Color(0xFFFFF8E8) else rose
        val alpha = rimAlpha * if (i % 2 == 0) 0.94f else 0.82f
        drawLine(
            color = tint.copy(alpha = alpha),
            start = Offset(sx - sparkleLen, sy),
            end = Offset(sx + sparkleLen, sy),
            strokeWidth = sparkleStroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint.copy(alpha = alpha),
            start = Offset(sx, sy - sparkleLen),
            end = Offset(sx, sy + sparkleLen),
            strokeWidth = sparkleStroke,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color(0xFFFFFCF4).copy(alpha = alpha),
            radius = sparkleStroke * 1.15f,
            center = Offset(sx, sy),
        )
    }
}
