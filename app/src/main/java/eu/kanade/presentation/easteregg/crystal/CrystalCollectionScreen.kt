package eu.kanade.presentation.easteregg.crystal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.tadami.aurora.BuildConfig
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.SettingsTreasuryScreen
import eu.kanade.presentation.more.settings.screen.resolveSearchableSettingsBackPress
import eu.kanade.presentation.util.LocalBackPress
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

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
        BackHandler(onBack = back)
        CrystalCollectionRoomTheme { scene ->
            val topWallBackdropHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                (220.dp.toPx() - scroll.value).coerceAtLeast(0f).toDp()
            }
            Box(Modifier.fillMaxSize().background(Color(scene.floor))) {
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
                    Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scroll),
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
