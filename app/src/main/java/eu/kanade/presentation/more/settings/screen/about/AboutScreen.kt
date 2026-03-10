package eu.kanade.presentation.more.settings.screen.about

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.LogoHeader
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.updaterEnabled
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.handler.FeatureUsageCollector
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.AppUpdatePreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LinkIcon
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Discord
import tachiyomi.presentation.core.icons.Github
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

private val GlitchMarks = charArrayOf(
    '\u0337',
    '\u0338',
    '\u0336',
)

object AboutScreen : Screen() {

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val handleBack = LocalBackPress.current
        val navigator = LocalNavigator.currentOrThrow
        val achievementHandler = remember { Injekt.get<AchievementHandler>() }
        val featureUsageCollector = remember { Injekt.get<FeatureUsageCollector>() }
        val hiddenFeatureConfig = remember(context) { loadAboutHiddenFeatureConfig(context) }
        val hiddenFeatureContent = remember(hiddenFeatureConfig?.content) {
            hiddenFeatureConfig?.content?.localizedForLanguage(Locale.getDefault().language)
        }
        val easterEggStateMachine = remember(hiddenFeatureConfig) {
            hiddenFeatureConfig?.trigger?.let { trigger ->
                AboutEasterEggStateMachine(
                    requiredPrimarySignals = trigger.requiredPrimarySignals,
                    primedWindowMs = trigger.primedWindowMs,
                    tapStreakWindowMs = trigger.tapStreakWindowMs,
                )
            }
        }
        var isCheckingUpdates by remember { mutableStateOf(false) }
        var easterEggPhase by remember(easterEggStateMachine) {
            mutableStateOf(easterEggStateMachine?.phase ?: AboutEasterEggPhase.Idle)
        }
        val isPrimed = easterEggPhase == AboutEasterEggPhase.Primed
        val isEasterEggVisible = easterEggPhase !in setOf(
            AboutEasterEggPhase.Idle,
            AboutEasterEggPhase.Primed,
        )

        fun syncEasterEggPhase(block: (AboutEasterEggStateMachine) -> Unit) {
            val machine = easterEggStateMachine ?: return
            block(machine)
            easterEggPhase = machine.phase
        }

        LaunchedEffect(easterEggPhase, easterEggStateMachine) {
            if (easterEggStateMachine != null &&
                hiddenFeatureConfig != null &&
                easterEggPhase == AboutEasterEggPhase.Primed
            ) {
                kotlinx.coroutines.delay(hiddenFeatureConfig.trigger.primedWindowMs)
                syncEasterEggPhase { machine ->
                    machine.tick(SystemClock.uptimeMillis())
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = { scrollBehavior ->
                    if (!isEasterEggVisible) {
                        AppBar(
                            title = stringResource(MR.strings.pref_category_about),
                            navigateUp = if (handleBack != null) handleBack::invoke else null,
                            scrollBehavior = scrollBehavior,
                        )
                    }
                },
            ) { contentPadding ->
                ScrollbarLazyColumn(
                    contentPadding = contentPadding,
                ) {
                    item {
                        LogoHeader(
                            onClick = {
                                achievementHandler.trackFeatureUsed(AchievementEvent.Feature.LOGO_CLICK)
                                syncEasterEggPhase { machine ->
                                    when (machine.onPrimarySignal(SystemClock.uptimeMillis())) {
                                        AboutEasterEggTapFeedback.Light -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        AboutEasterEggTapFeedback.Primed -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        AboutEasterEggTapFeedback.None -> Unit
                                    }
                                }
                            },
                        )
                    }

                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.version),
                            subtitle = buildAboutVersionSubtitle(
                                normalVersionName = getVersionName(withBuildDate = true),
                                isPrimed = isPrimed,
                            ),
                            onPreferenceClick = {
                                val deviceInfo = CrashLogUtil(context).getDebugInfo()
                                context.copyToClipboard("Debug information", deviceInfo)
                            },
                            onPreferenceLongClick = {
                                syncEasterEggPhase { machine ->
                                    if (machine.onSecondarySignal(SystemClock.uptimeMillis())) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            },
                        )
                    }

                    if (updaterEnabled) {
                        item {
                            TextPreferenceWidget(
                                title = stringResource(MR.strings.check_for_updates),
                                widget = {
                                    AnimatedVisibility(visible = isCheckingUpdates) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 3.dp,
                                        )
                                    }
                                },
                                onPreferenceClick = {
                                    if (!isCheckingUpdates) {
                                        scope.launch {
                                            isCheckingUpdates = true

                                            checkVersion(
                                                context = context,
                                                onAvailableUpdate = { result ->
                                                    val updateScreen = NewUpdateScreen(
                                                        versionName = result.release.version,
                                                        changelogInfo = result.release.info,
                                                        releaseLink = result.release.releaseLink,
                                                        downloadLink = result.release.downloadLink,
                                                    )
                                                    navigator.push(updateScreen)
                                                },
                                                onFinish = {
                                                    isCheckingUpdates = false
                                                },
                                            )
                                        }
                                    }
                                },
                            )
                        }

                        item {
                            val appUpdatePreferences = remember { Injekt.get<AppUpdatePreferences>() }
                            val updateInterval by appUpdatePreferences.appUpdateInterval().collectAsState()

                            ListPreferenceWidget(
                                value = updateInterval,
                                title = stringResource(MR.strings.pref_app_update_interval),
                                subtitle = null,
                                icon = null,
                                entries = persistentMapOf(
                                    -1 to stringResource(MR.strings.app_update_on_start),
                                    0 to stringResource(MR.strings.update_never),
                                    6 to stringResource(MR.strings.app_update_6h),
                                    12 to stringResource(MR.strings.app_update_12h),
                                    24 to stringResource(MR.strings.app_update_24h),
                                    168 to stringResource(MR.strings.app_update_weekly),
                                ),
                                onValueChange = { newInterval ->
                                    appUpdatePreferences.appUpdateInterval().set(newInterval)
                                    AppUpdateJob.setupTask(context, newInterval)
                                },
                            )
                        }
                    }

                    if (!BuildConfig.DEBUG) {
                        item {
                            TextPreferenceWidget(
                                title = stringResource(MR.strings.whats_new),
                                onPreferenceClick = { uriHandler.openUri(RELEASE_URL) },
                            )
                        }
                    }

                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.help_translate),
                            onPreferenceClick = {
                                uriHandler.openUri(
                                    "https://aniyomi.org/docs/contribute#translation",
                                )
                            },
                        )
                    }

                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.licenses),
                            onPreferenceClick = { navigator.push(OpenSourceLicensesScreen()) },
                        )
                    }

                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.privacy_policy),
                            onPreferenceClick = { uriHandler.openUri("https://aniyomi.org/privacy/") },
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Aniyomi",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row {
                                LinkIcon(
                                    label = stringResource(MR.strings.website),
                                    icon = Icons.Outlined.Public,
                                    url = "https://aniyomi.org",
                                )
                                LinkIcon(
                                    label = "Discord",
                                    icon = CustomIcons.Discord,
                                    url = "https://discord.gg/F32UjdJZrR",
                                )
                                LinkIcon(
                                    label = "GitHub",
                                    icon = CustomIcons.Github,
                                    url = "https://github.com/aniyomiorg/aniyomi",
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .fillMaxWidth(0.5f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            )

                            Text(
                                text = "Tadami",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row {
                                LinkIcon(
                                    label = "Tadami",
                                    icon = CustomIcons.Github,
                                    url = "https://github.com/andarcanum/Tadami-Aniyomi-fork",
                                )
                            }
                        }
                    }
                }
            }

            if (hiddenFeatureConfig != null && hiddenFeatureContent != null) {
                AboutEasterEggOverlay(
                    phase = easterEggPhase,
                    content = hiddenFeatureContent,
                    onGlyphRainFinished = {
                        syncEasterEggPhase { machine ->
                            machine.onGlyphRainFinished()
                        }
                    },
                    onPageMaterialized = {
                        syncEasterEggPhase { machine ->
                            machine.onPageMaterialized()
                        }
                    },
                    onDismissRequest = {
                        syncEasterEggPhase { machine ->
                            machine.dismiss()
                        }
                    },
                    onDismissFinished = {
                        syncEasterEggPhase { machine ->
                            machine.onDismissFinished()
                        }
                    },
                    onRevealComplete = {
                        val missingLogoClicks = (
                            10 -
                                featureUsageCollector.getFeatureCount(AchievementEvent.Feature.LOGO_CLICK)
                            )
                            .coerceAtLeast(0)
                        repeat(missingLogoClicks) {
                            achievementHandler.trackFeatureUsed(AchievementEvent.Feature.LOGO_CLICK)
                        }
                    },
                )
            }
        }
    }

    /**
     * Checks version and shows a user prompt if an update is available.
     */
    private suspend fun checkVersion(
        context: Context,
        onAvailableUpdate: (GetApplicationRelease.Result.NewUpdate) -> Unit,
        onFinish: () -> Unit,
    ) {
        val updateChecker = AppUpdateChecker()
        withUIContext {
            try {
                when (
                    val result = withIOContext {
                        updateChecker.checkForUpdate(
                            context,
                            forceCheck = true,
                        )
                    }
                ) {
                    is GetApplicationRelease.Result.NewUpdate -> {
                        onAvailableUpdate(result)
                    }
                    is GetApplicationRelease.Result.NoNewUpdate -> {
                        context.toast(MR.strings.update_check_no_new_updates)
                    }
                    is GetApplicationRelease.Result.OsTooOld -> {
                        context.toast(MR.strings.update_check_eol)
                    }
                }
            } catch (e: Exception) {
                context.toast(e.message)
                logcat(LogPriority.ERROR, e)
            } finally {
                onFinish()
            }
        }
    }

    fun getVersionName(withBuildDate: Boolean): String {
        return when {
            BuildConfig.DEBUG -> {
                "Debug ${BuildConfig.COMMIT_SHA}".let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime()})"
                    } else {
                        it
                    }
                }
            }
            isPreviewBuildType -> {
                "Preview r${BuildConfig.COMMIT_COUNT}".let {
                    if (withBuildDate) {
                        "$it (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime()})"
                    } else {
                        "$it (${BuildConfig.COMMIT_SHA})"
                    }
                }
            }
            else -> {
                "Stable ${BuildConfig.VERSION_NAME}".let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime()})"
                    } else {
                        it
                    }
                }
            }
        }
    }

    internal fun getFormattedBuildTime(): String {
        return try {
            LocalDateTime.ofInstant(
                Instant.parse(BuildConfig.BUILD_TIME),
                ZoneId.systemDefault(),
            )
                .toDateTimestampString(
                    UiPreferences.dateFormat(
                        Injekt.get<UiPreferences>().dateFormat().get(),
                    ),
                )
        } catch (e: Exception) {
            BuildConfig.BUILD_TIME
        }
    }
}

internal fun buildAboutVersionSubtitle(normalVersionName: String, isPrimed: Boolean): String {
    return if (isPrimed) {
        glitchAboutVersion(normalVersionName)
    } else {
        normalVersionName
    }
}

private fun glitchAboutVersion(versionName: String): String {
    return buildString(versionName.length * 2 + 16) {
        append("V")
        append('\u0338')
        append(' ')
        versionName.forEachIndexed { index, character ->
            append(character)
            if (!character.isWhitespace()) {
                append(GlitchMarks[index % GlitchMarks.size])
            }
        }
    }
}
