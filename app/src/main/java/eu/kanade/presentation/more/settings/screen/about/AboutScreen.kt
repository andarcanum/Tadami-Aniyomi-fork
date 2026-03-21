package eu.kanade.presentation.more.settings.screen.about

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import eu.kanade.presentation.more.LogoHeader
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_HORIZONTAL_INSET
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_SHAPE
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.settingsAccentColor
import eu.kanade.presentation.more.settings.settingsCardContainerColor
import eu.kanade.presentation.more.settings.settingsTitleColor
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

private val ABOUT_FOOTER_ICON_SLOT_SIZE = 40.dp
private val ABOUT_FOOTER_ICON_GAP = 8.dp

object AboutScreen : Screen() {

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val handleBack = LocalBackPress.current
        val navigator = LocalNavigator.currentOrThrow
        val uiStyle = rememberResolvedSettingsUiStyle()
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
        val itemModifier = if (uiStyle == SettingsUiStyle.Aurora) {
            Modifier.padding(horizontal = AURORA_SETTINGS_CARD_HORIZONTAL_INSET)
        } else {
            Modifier
        }
        val state = rememberLazyListState()

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
            SettingsScaffold(
                title = stringResource(MR.strings.pref_category_about),
                uiStyle = uiStyle,
                onBackPressed = if (handleBack != null) handleBack::invoke else null,
                showTopBar = !isEasterEggVisible,
                topBarCanScroll = { state.canScroll() },
            ) { contentPadding ->
                ScrollbarLazyColumn(
                    state = state,
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
                            modifier = itemModifier,
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
                                modifier = itemModifier,
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

                            Box(modifier = itemModifier) {
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
                    }

                    if (!BuildConfig.DEBUG) {
                        item {
                            TextPreferenceWidget(
                                modifier = itemModifier,
                                title = stringResource(MR.strings.whats_new),
                                onPreferenceClick = { uriHandler.openUri(RELEASE_URL) },
                            )
                        }
                    }

                    item {
                        TextPreferenceWidget(
                            modifier = itemModifier,
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
                            modifier = itemModifier,
                            title = stringResource(MR.strings.licenses),
                            onPreferenceClick = { navigator.push(OpenSourceLicensesScreen()) },
                        )
                    }

                    item {
                        TextPreferenceWidget(
                            modifier = itemModifier,
                            title = stringResource(MR.strings.privacy_policy),
                            onPreferenceClick = { uriHandler.openUri("https://aniyomi.org/privacy/") },
                        )
                    }

                    item {
                        val footerSections = remember { buildAboutFooterSections() }
                        val containerColor = settingsCardContainerColor()
                        val dividerColor = settingsAccentColor().copy(alpha = 0.3f)
                        Card(
                            modifier = Modifier
                                .then(itemModifier)
                                .fillMaxWidth(),
                            shape = AURORA_SETTINGS_CARD_SHAPE,
                            colors = CardDefaults.cardColors(
                                containerColor = containerColor,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AboutFooterLinkSectionContent(section = footerSections.first())
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(40.dp)
                                        .background(dividerColor),
                                )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AboutFooterLinkSectionContent(section = footerSections.last())
                                }
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

@Composable
private fun AboutFooterLinkSectionContent(section: AboutFooterLinkSection) {
    val titleColor = settingsTitleColor()
    val iconTint = settingsAccentColor()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.labelLarge,
            color = titleColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                ABOUT_FOOTER_ICON_GAP,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            section.links.forEach { link ->
                LinkIcon(
                    label = aboutFooterLinkLabel(link.label),
                    icon = aboutFooterLinkIcon(link.icon),
                    url = link.url,
                    modifier = Modifier.size(ABOUT_FOOTER_ICON_SLOT_SIZE),
                    tint = iconTint,
                )
            }
        }
    }
}

@Composable
private fun aboutFooterLinkLabel(label: AboutFooterLinkLabel): String = when (label) {
    AboutFooterLinkLabel.Website -> stringResource(MR.strings.website)
    AboutFooterLinkLabel.Discord -> "Discord"
    AboutFooterLinkLabel.GitHub -> "GitHub"
    AboutFooterLinkLabel.Tadami -> "Tadami"
}

private fun aboutFooterLinkIcon(icon: AboutFooterLinkIcon) = when (icon) {
    AboutFooterLinkIcon.Website -> Icons.Outlined.Public
    AboutFooterLinkIcon.Discord -> CustomIcons.Discord
    AboutFooterLinkIcon.Github -> CustomIcons.Github
}

internal fun buildAboutFooterSections(): List<AboutFooterLinkSection> {
    return listOf(
        AboutFooterLinkSection(
            title = "Aniyomi",
            links = listOf(
                AboutFooterLink(
                    label = AboutFooterLinkLabel.Website,
                    icon = AboutFooterLinkIcon.Website,
                    url = "https://aniyomi.org",
                ),
                AboutFooterLink(
                    label = AboutFooterLinkLabel.Discord,
                    icon = AboutFooterLinkIcon.Discord,
                    url = "https://discord.gg/F32UjdJZrR",
                ),
                AboutFooterLink(
                    label = AboutFooterLinkLabel.GitHub,
                    icon = AboutFooterLinkIcon.Github,
                    url = "https://github.com/aniyomiorg/aniyomi",
                ),
            ),
        ),
        AboutFooterLinkSection(
            title = "Tadami",
            links = listOf(
                AboutFooterLink(
                    label = AboutFooterLinkLabel.Tadami,
                    icon = AboutFooterLinkIcon.Github,
                    url = "https://github.com/andarcanum/Tadami-Aniyomi-fork",
                ),
            ),
        ),
    )
}

internal data class AboutFooterLinkSection(
    val title: String,
    val links: List<AboutFooterLink>,
)

internal data class AboutFooterLink(
    val label: AboutFooterLinkLabel,
    val icon: AboutFooterLinkIcon,
    val url: String,
)

internal enum class AboutFooterLinkLabel {
    Website,
    Discord,
    GitHub,
    Tadami,
}

internal enum class AboutFooterLinkIcon {
    Website,
    Discord,
    Github,
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
