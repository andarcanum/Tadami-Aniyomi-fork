package eu.kanade.presentation.more.settings.screen

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.GlitchPalette
import eu.kanade.presentation.components.RiftBreachDirective
import eu.kanade.presentation.components.RiftCorruptTerminal
import eu.kanade.presentation.components.heartbeat
import eu.kanade.presentation.components.quakeOffset
import eu.kanade.presentation.components.rememberGlitchTime
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.advanced.ClearAnimeDatabaseScreen
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.presentation.more.settings.screen.advanced.ClearNovelDatabaseScreen
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoScreen
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.anime.AnimeMetadataUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaMetadataUpdateJob
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_CONTROLD
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_LIBREDNS
import eu.kanade.tachiyomi.network.PREF_DOH_MULLVAD
import eu.kanade.tachiyomi.network.PREF_DOH_NJALLA
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.network.PREF_DOH_SHECAN
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.isDhizukuInstalled
import eu.kanade.tachiyomi.util.system.isShizukuInstalled
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
import tachiyomi.domain.entries.manga.interactor.ResetMangaViewerFlags
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.HapticFeedbackMode
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.math.roundToInt

object SettingsAdvancedScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val networkPreferences = remember { Injekt.get<NetworkPreferences>() }
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        val meltdownStage by uiPreferences.meltdownStage().collectAsState()

        val list = mutableListOf<Preference>(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_dump_crash_logs),
                subtitle = stringResource(MR.strings.pref_dump_crash_logs_summary),
                onClick = {
                    scope.launch {
                        CrashLogUtil(context).dumpLogs()
                    }
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = networkPreferences.verboseLogging(),
                title = stringResource(MR.strings.pref_verbose_logging),
                subtitle = stringResource(MR.strings.pref_verbose_logging_summary),
                onValueChanged = {
                    context.toast(MR.strings.requires_app_restart)
                    true
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_debug_info),
                onClick = { navigator.push(DebugInfoScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_onboarding_guide),
                onClick = { navigator.push(OnboardingScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_manage_notifications),
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
            ),
            getBackgroundActivityGroup(),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.label_haptic_feedback),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = uiPreferences.hapticFeedbackMode(),
                        entries = HapticFeedbackMode.entries
                            .associateWith { stringResource(it.titleRes) }
                            .toImmutableMap(),
                        title = stringResource(MR.strings.pref_haptic_feedback),
                        subtitle = stringResource(MR.strings.pref_haptic_feedback_summary),
                    ),
                ),
            ),
            getDataGroup(),
            getNetworkGroup(networkPreferences = networkPreferences),
            getLibraryGroup(),
            getReaderGroup(basePreferences = basePreferences),
            getExtensionsGroup(basePreferences = basePreferences),
            // SY -->
            getDataSaverGroup(),
            // SY <--
        )

        if (meltdownStage == 1) {
            list.add(
                Preference.PreferenceItem.CustomPreference(
                    title = "Glitch Rift",
                    content = {
                        GlitchRiftWidget(
                            onTap = {
                                scope.launch {
                                    try {
                                        val vibrator = if (android.os.Build.VERSION.SDK_INT >=
                                            android.os.Build.VERSION_CODES.S
                                        ) {
                                            val manager = context.getSystemService(
                                                android.os.VibratorManager::class.java,
                                            )
                                            manager?.defaultVibrator
                                        } else {
                                            @Suppress("DEPRECATION")
                                            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                        }
                                        if (vibrator != null && vibrator.hasVibrator()) {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                vibrator.vibrate(
                                                    android.os.VibrationEffect.createOneShot(
                                                        800,
                                                        android.os.VibrationEffect.DEFAULT_AMPLITUDE,
                                                    ),
                                                )
                                            } else {
                                                @Suppress("DEPRECATION")
                                                vibrator.vibrate(800)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Ignore vibration SecurityException or other issues
                                    }
                                    uiPreferences.meltdownStage().set(2)
                                }
                            },
                        )
                    },
                ),
            )
        }

        return list
    }

    @Composable
    private fun getBackgroundActivityGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_background_activity),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_disable_battery_optimization),
                    subtitle = stringResource(MR.strings.pref_disable_battery_optimization_summary),
                    onClick = {
                        val packageName: String = context.packageName
                        if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                            try {
                                @SuppressLint("BatteryLife")
                                val intent = Intent().apply {
                                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    data = "package:$packageName".toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                context.toast(MR.strings.battery_optimization_setting_activity_not_found)
                            }
                        } else {
                            context.toast(MR.strings.battery_optimization_disabled)
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Don't kill my app!",
                    subtitle = stringResource(MR.strings.about_dont_kill_my_app),
                    onClick = { uriHandler.openUri("https://dontkillmyapp.com/") },
                ),
            ),
        )
    }

    @Composable
    private fun getDataGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_data),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_invalidate_download_cache),
                    subtitle = stringResource(AYMR.strings.pref_invalidate_download_cache_summary),
                    onClick = {
                        Injekt.get<MangaDownloadCache>().invalidateCache()
                        Injekt.get<AnimeDownloadCache>().invalidateCache()
                        Injekt.get<NovelDownloadCache>().invalidateAll()
                        context.toast(MR.strings.download_cache_invalidated)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.pref_clear_manga_database),
                    subtitle = stringResource(AYMR.strings.pref_clear_manga_database_summary),
                    onClick = { navigator.push(ClearDatabaseScreen()) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.pref_clear_anime_database),
                    subtitle = stringResource(AYMR.strings.pref_clear_anime_database_summary),
                    onClick = { navigator.push(ClearAnimeDatabaseScreen()) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.pref_clear_novel_database),
                    subtitle = stringResource(AYMR.strings.pref_clear_novel_database_summary),
                    onClick = { navigator.push(ClearNovelDatabaseScreen()) },
                ),
            ),
        )
    }

    @Composable
    private fun getNetworkGroup(
        networkPreferences: NetworkPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val networkHelper = remember { Injekt.get<NetworkHelper>() }

        val userAgentPref = networkPreferences.defaultUserAgent()
        val userAgent by userAgentPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_network),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_cookies),
                    onClick = {
                        networkHelper.cookieJar.removeAll()
                        context.toast(MR.strings.cookies_cleared)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_webview_data),
                    onClick = {
                        try {
                            WebView(context).run {
                                setDefaultSettings()
                                clearCache(true)
                                clearFormData()
                                clearHistory()
                                clearSslPreferences()
                            }
                            WebStorage.getInstance().deleteAllData()
                            context.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
                            context.toast(MR.strings.webview_data_deleted)
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            context.toast(MR.strings.cache_delete_error)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = networkPreferences.networkCacheSize(),
                    entries = persistentMapOf(
                        32 to "32 MiB",
                        64 to "64 MiB",
                        128 to "128 MiB",
                        256 to "256 MiB",
                    ),
                    title = stringResource(MR.strings.pref_network_cache_size),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = networkPreferences.dohProvider(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        PREF_DOH_CLOUDFLARE to "Cloudflare",
                        PREF_DOH_GOOGLE to "Google",
                        PREF_DOH_ADGUARD to "AdGuard",
                        PREF_DOH_QUAD9 to "Quad9",
                        PREF_DOH_ALIDNS to "AliDNS",
                        PREF_DOH_DNSPOD to "DNSPod",
                        PREF_DOH_360 to "360",
                        PREF_DOH_QUAD101 to "Quad 101",
                        PREF_DOH_MULLVAD to "Mullvad",
                        PREF_DOH_CONTROLD to "Control D",
                        PREF_DOH_NJALLA to "Njalla",
                        PREF_DOH_SHECAN to "Shecan",
                        PREF_DOH_LIBREDNS to "LibreDNS",
                    ),
                    title = stringResource(MR.strings.pref_dns_over_https),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = userAgentPref,
                    title = stringResource(MR.strings.pref_user_agent_string),
                    onValueChanged = {
                        try {
                            // OkHttp checks for valid values internally
                            Headers.Builder().add("User-Agent", it)
                            context.toast(MR.strings.requires_app_restart)
                        } catch (_: IllegalArgumentException) {
                            context.toast(MR.strings.error_user_agent_string_invalid)
                            return@EditTextPreference false
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_user_agent_string),
                    enabled = remember(userAgent) { userAgent != userAgentPref.defaultValue() },
                    onClick = {
                        userAgentPref.delete()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getLibraryGroup(): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_library),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_refresh_library_covers),
                    onClick = {
                        AnimeLibraryUpdateJob.startNow(context)
                        MangaLibraryUpdateJob.startNow(context)
                        NovelLibraryUpdateJob.startNow(context)
                        AnimeMetadataUpdateJob.startNow(context)
                        MangaMetadataUpdateJob.startNow(context)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_viewer_flags),
                    subtitle = stringResource(MR.strings.pref_reset_viewer_flags_summary),
                    onClick = {
                        scope.launchNonCancellable {
                            val success = Injekt.get<ResetMangaViewerFlags>().await()
                            withUIContext {
                                val message = if (success) {
                                    MR.strings.pref_reset_viewer_flags_success
                                } else {
                                    MR.strings.pref_reset_viewer_flags_error
                                }
                                context.toast(message)
                            }
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getReaderGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val chooseColorProfile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                basePreferences.displayProfile().set(uri.toString())
            }
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reader),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = basePreferences.hardwareBitmapThreshold(),
                    entries = GLUtil.CUSTOM_TEXTURE_LIMIT_OPTIONS
                        .mapIndexed { index, option ->
                            val display = if (index == 0) {
                                stringResource(MR.strings.pref_hardware_bitmap_threshold_default, option)
                            } else {
                                option.toString()
                            }
                            option to display
                        }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_hardware_bitmap_threshold),
                    subtitleProvider = { value, options ->
                        stringResource(MR.strings.pref_hardware_bitmap_threshold_summary, options[value].orEmpty())
                    },
                    enabled = !ImageUtil.HARDWARE_BITMAP_UNSUPPORTED &&
                        GLUtil.DEVICE_TEXTURE_LIMIT > GLUtil.SAFE_TEXTURE_LIMIT,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = basePreferences.alwaysDecodeLongStripWithSSIV(),
                    title = stringResource(MR.strings.pref_always_decode_long_strip_with_ssiv_2),
                    subtitle = stringResource(MR.strings.pref_always_decode_long_strip_with_ssiv_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_display_profile),
                    subtitle = basePreferences.displayProfile().get(),
                    onClick = {
                        chooseColorProfile.launch(arrayOf("*/*"))
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getExtensionsGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val extensionInstallerPref = basePreferences.extensionInstaller()
        var shizukuMissing by remember { mutableStateOf(false) }
        var dhizukuMissing by remember { mutableStateOf(false) }
        val trustAnimeExtension = remember { Injekt.get<TrustAnimeExtension>() }
        val trustMangaExtension = remember { Injekt.get<TrustMangaExtension>() }
        val novelPluginKeyValueStore = remember { Injekt.get<NovelPluginKeyValueStore>() }
        val novelPluginSourceFactory = remember { Injekt.get<NovelPluginSourceFactory>() }

        if (shizukuMissing) {
            val dismiss = { shizukuMissing = false }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(text = stringResource(MR.strings.ext_installer_shizuku)) },
                text = {
                    Text(
                        text = stringResource(MR.strings.ext_installer_shizuku_unavailable_dialog),
                    )
                },
                dismissButton = {
                    TextButton(onClick = dismiss) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dismiss()
                            uriHandler.openUri("https://shizuku.rikka.app/download")
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
            )
        }
        if (dhizukuMissing) {
            val dismiss = { dhizukuMissing = false }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(text = stringResource(MR.strings.ext_installer_dhizuku)) },
                text = {
                    Text(
                        text = stringResource(MR.strings.ext_installer_dhizuku_unavailable_dialog),
                    )
                },
                dismissButton = {
                    TextButton(onClick = dismiss) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dismiss()
                            uriHandler.openUri("https://github.com/iamr0s/Dhizuku")
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_extensions),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = extensionInstallerPref,
                    entries = extensionInstallerPref.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.ext_installer_pref),
                    onValueChanged = {
                        if (it == BasePreferences.ExtensionInstaller.SHIZUKU &&
                            !context.isShizukuInstalled
                        ) {
                            shizukuMissing = true
                            false
                        } else if (it == BasePreferences.ExtensionInstaller.DHIZUKU &&
                            !context.isDhizukuInstalled
                        ) {
                            dhizukuMissing = true
                            false
                        } else {
                            true
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.ext_revoke_trust),
                    onClick = {
                        trustMangaExtension.revokeAll()
                        trustAnimeExtension.revokeAll()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.pref_clear_novel_plugin_cache),
                    subtitle = stringResource(AYMR.strings.pref_clear_novel_plugin_cache_summary),
                    onClick = {
                        scope.launchNonCancellable {
                            val success = runCatching {
                                novelPluginKeyValueStore.clearAll()
                                novelPluginSourceFactory.clearRuntimeCaches()
                            }.isSuccess
                            withUIContext {
                                context.toast(
                                    if (success) {
                                        AYMR.strings.novel_plugin_cache_cleared
                                    } else {
                                        MR.strings.cache_delete_error
                                    },
                                )
                            }
                        }
                    },
                ),
            ),
        )
    }

    // SY -->
    @Composable
    private fun getDataSaverGroup(): Preference.PreferenceGroup {
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val dataSaver by sourcePreferences.dataSaver().collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.data_saver),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = sourcePreferences.dataSaver(),
                    entries = persistentMapOf(
                        DataSaver.NONE to stringResource(MR.strings.disabled),
                        DataSaver.BANDWIDTH_HERO to stringResource(AYMR.strings.bandwidth_hero),
                        DataSaver.WSRV_NL to stringResource(AYMR.strings.wsrv),
                        DataSaver.RESMUSH_IT to stringResource(AYMR.strings.resmush),
                    ),
                    title = stringResource(AYMR.strings.data_saver),
                    subtitle = stringResource(AYMR.strings.data_saver_summary),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = sourcePreferences.dataSaverServer(),
                    title = stringResource(AYMR.strings.bandwidth_data_saver_server),
                    subtitle = stringResource(AYMR.strings.data_saver_server_summary),
                    enabled = dataSaver == DataSaver.BANDWIDTH_HERO,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverDownloader(),
                    title = stringResource(AYMR.strings.data_saver_downloader),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverIgnoreJpeg(),
                    title = stringResource(AYMR.strings.data_saver_ignore_jpeg),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverIgnoreGif(),
                    title = stringResource(AYMR.strings.data_saver_ignore_gif),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = sourcePreferences.dataSaverImageQuality(),
                    entries = listOf(
                        "10%",
                        "20%",
                        "40%",
                        "50%",
                        "70%",
                        "80%",
                        "90%",
                        "95%",
                    ).associateBy { it.trimEnd('%').toInt() }.toPersistentMap(),
                    title = stringResource(AYMR.strings.data_saver_image_quality),
                    subtitle = stringResource(AYMR.strings.data_saver_image_quality_summary),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                kotlin.run {
                    val dataSaverImageFormatJpeg by sourcePreferences.dataSaverImageFormatJpeg().collectAsState()
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.dataSaverImageFormatJpeg(),
                        title = stringResource(AYMR.strings.data_saver_image_format),
                        subtitle = if (dataSaverImageFormatJpeg) {
                            stringResource(AYMR.strings.data_saver_image_format_summary_on)
                        } else {
                            stringResource(AYMR.strings.data_saver_image_format_summary_off)
                        },
                        enabled = dataSaver != DataSaver.NONE && dataSaver != DataSaver.RESMUSH_IT,
                    )
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverColorBW(),
                    title = stringResource(AYMR.strings.data_saver_color_bw),
                    enabled = dataSaver == DataSaver.BANDWIDTH_HERO,
                ),
            ),
        )
    }
    // SY <--

    @Composable
    private fun GlitchRiftWidget(onTap: () -> Unit) {
        val time by rememberGlitchTime()
        var breaching by remember { mutableStateOf(false) }
        var showDirective by remember { mutableStateOf(false) }
        // Раскрытие разлома при УДЕРЖАНИИ: заряд 0 -> 1 плавно раскрывает
        // разлом; полный заряд = брешь и переход на brutalist (Шаг 3).
        val holdCharge = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        // Покой: медленное "дыхание" разлома, чтобы шов и датамош были живыми.
        val idle = rememberInfiniteTransition(label = "rift_idle")
        val breathe by idle.animateFloat(
            initialValue = 0.16f,
            targetValue = 0.34f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "rift_breathe",
        )
        // Итоговое раскрытие: дыхание + заряд удержания; после бреши — плавно разрывается до пика.
        val breachOpenProg = androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (breaching) 1f else 0f,
            animationSpec = tween(3000, easing = androidx.compose.animation.core.LinearEasing),
            label = "breach_open",
        )
        val openValue = when {
            breaching -> breathe + breachOpenProg.value * (0.97f - breathe)
            else -> (breathe + holdCharge.value * (0.94f - breathe)).coerceIn(0f, 0.97f)
        }

        // После бреши — медленная пауза/анимация разрыва, затем полноэкранная директива (как дойти до Шага 3).
        LaunchedEffect(breaching) {
            if (breaching) {
                kotlinx.coroutines.delay(3500)
                showDirective = true
            }
        }

        // Пульс интенсивности глитча в такт "сердцебиения" движка.
        val hb = heartbeat(time)
        val baseIntensity = (0.34f + (hb - 1f) * 1.1f).coerceIn(0.22f, 0.62f)
        // Во время бреши глитч раскручивается вслед за раскрытием разлома.
        val intensity = (baseIntensity + openValue * 0.9f).coerceIn(0.22f, 1.0f)
        val flicker = kotlin.math.sin(time * 24f) * 0.5f + 0.5f

        // SYNC RATE: держится у красной черты ~400% и дёргается за неё.
        val syncRate = (
            376f +
                kotlin.math.sin(time * 5.5f) * 34f +
                kotlin.math.sin(time * 57f) * 9f
            ).coerceIn(0f, 420f)
        val syncPct = syncRate.toInt()
        val redline = syncRate >= 400f

        val shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)

        // Экран директивы после бреши: объясняет, как попасть на Шаг 3.
        // Кнопка «ВОЙТИ В ПУСТОТУ» → onTap() переводит meltdownStage в 2.
        if (showDirective) {
            Dialog(
                onDismissRequest = { onTap() },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                RiftBreachDirective(onEnter = { onTap() })
            }
        }

        val shakeIntensity = when {
            breaching -> 3f * breachOpenProg.value
            holdCharge.value > 0.001f -> holdCharge.value * 0.7f
            else -> 0f
        }
        val q = quakeOffset(time, shakeIntensity)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(150.dp)
                .offset { IntOffset(q.x.roundToInt(), q.y.roundToInt()) }
                .clip(shape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (breaching) return@detectTapGestures
                            // удержание заряжает раскрытие; полный заряд = брешь (4.5 секунды)
                            val job = scope.launch {
                                holdCharge.animateTo(
                                    1f,
                                    animationSpec = tween(4500, easing = FastOutSlowInEasing),
                                )
                                breaching = true
                            }
                            tryAwaitRelease()
                            if (!breaching) {
                                job.cancel()
                                scope.launch {
                                    holdCharge.animateTo(
                                        0f,
                                        animationSpec = tween(700, easing = FastOutSlowInEasing),
                                    )
                                }
                            }
                        },
                    )
                },
        ) {
            // Разлом + приглушённый глитч: тяжёлые Canvas-фолбэки (datamosh/scan/static)
            // отключены, а текст вынесен НАД оверлеями, чтобы его снова было видно.
            // Весь арт (рамка-терминал, RGB-датамош, шов, сфера-глобус, трещина)
            // рисуется одним AGSL-шейдером; на API<33 — фолбэк RiftDatamoshBackground.
            RiftCorruptTerminal(
                time = time,
                charge = holdCharge.value,
                breach = if (breaching) breachOpenProg.value else 0f,
                open = openValue,
                modifier = Modifier.matchParentSize(),
            )

            // Текст и шкала — в левой части блока (сфера шейдера справа).
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 18.dp, end = 8.dp, top = 24.dp, bottom = 12.dp)
                    .fillMaxWidth(0.60f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(AYMR.strings.meltdown_system_compromised),
                    color = GlitchPalette.HazardRed.copy(alpha = 0.75f + 0.25f * flicker),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Счётчик SYNC RATE + блочная шкала
                val filled = ((syncRate / 400f) * 16f).toInt().coerceIn(0, 16)
                val bar = buildString {
                    repeat(16) { i -> append(if (i < filled) '\u2588' else '\u2591') }
                }
                Text(
                    text = stringResource(AYMR.strings.meltdown_sync_rate, syncPct),
                    color = if (redline) GlitchPalette.SignalRed else GlitchPalette.Phosphor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = bar,
                    color = GlitchPalette.HazardRed.copy(alpha = if (redline) flicker else 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(6.dp))

                val charging = holdCharge.value > 0.001f
                Text(
                    text = when {
                        breaching -> stringResource(AYMR.strings.meltdown_inhibitors_disengaged)
                        charging -> stringResource(AYMR.strings.meltdown_breaching, (holdCharge.value * 100f).toInt())
                        else -> stringResource(AYMR.strings.meltdown_hold_rift)
                    },
                    color = if (breaching || charging) {
                        GlitchPalette.SignalRed.copy(alpha = 0.6f + 0.4f * flicker)
                    } else {
                        Color.LightGray.copy(alpha = 0.55f + 0.35f * flicker)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
