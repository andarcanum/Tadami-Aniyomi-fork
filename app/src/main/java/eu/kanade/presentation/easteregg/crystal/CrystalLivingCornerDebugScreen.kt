package eu.kanade.presentation.easteregg.crystal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.tadami.aurora.BuildConfig
import eu.kanade.domain.easteregg.crystal.CrystalDayPhase
import eu.kanade.domain.easteregg.crystal.CrystalWeather
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

internal fun isCrystalLivingLabAvailable(debug: Boolean, buildType: String): Boolean = debug && buildType == "debug"

/** Debug-only route: overrides never write production observation or reward state. */
internal object CrystalLivingCornerDebugScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        if (!isCrystalLivingLabAvailable(BuildConfig.DEBUG, BuildConfig.BUILD_TYPE)) {
            LaunchedEffect(Unit) { navigator.pop() }
            return
        }
        val colors = AuroraTheme.colors
        var phase by remember { mutableStateOf<CrystalDayPhase?>(null) }
        var weather by remember { mutableStateOf<CrystalWeather?>(null) }
        val scrollState = rememberScrollState()
        SettingsScaffold(
            title = stringResource(AYMR.strings.crystal_living_lab_title),
            uiStyle = rememberResolvedSettingsUiStyle(),
            onBackPressed = { navigator.pop() },
            topBarCanScroll = { scrollState.canScroll() },
        ) { padding ->
            Column(
                Modifier.verticalScroll(scrollState).padding(padding),
                verticalArrangement =
                Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(AYMR.strings.crystal_living_lab_notice),
                    Modifier.padding(16.dp),
                    color = colors
                        .textSecondary,
                )
                CrystalLivingCornerHero(
                    CrystalPalette.Moon,
                    false,
                    labMode = true,
                    forcedPhase = phase,
                    forcedWeather = weather,
                )
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        phase = null
                        weather = null
                    }) {
                        Text(
                            stringResource(
                                AYMR.strings
                                    .crystal_living_auto,
                            ),
                        )
                    }
                    CrystalDayPhase.entries.chunked(2).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { value ->
                                OutlinedButton(
                                    onClick = { phase = value },
                                    modifier = Modifier
                                        .weight(1f),
                                ) { Text(crystalLivingPhaseLabel(value)) }
                            }
                        }
                    }
                    CrystalWeather.entries.forEach { value ->
                        OutlinedButton(
                            onClick = { weather = value },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(crystalLivingWeatherLabel(value)) }
                    }
                }
            }
        }
    }
}
