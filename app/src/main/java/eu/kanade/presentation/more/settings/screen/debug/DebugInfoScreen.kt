package eu.kanade.presentation.more.settings.screen.debug

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.profileinstaller.ProfileVerifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScaffold
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.guava.await
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

class DebugInfoScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val uiStyle = rememberResolvedSettingsUiStyle()
        PreferenceScaffold(
            titleRes = MR.strings.pref_debug_info,
            uiStyle = uiStyle,
            onBackPressed = navigator::pop,
            itemsProvider = {
                listOf(
                    Preference.PreferenceItem.TextPreference(
                        title = context.stringResource(MR.strings.debug_worker_info_title),
                        onClick = { navigator.push(WorkerInfoScreen()) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = context.stringResource(MR.strings.debug_backup_schema_title),
                        onClick = { navigator.push(BackupSchemaScreen()) },
                    ),
                    getAppInfoGroup(),
                    getDeviceInfoGroup(),
                )
            },
        )
    }

    @Composable
    private fun getAppInfoGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        return Preference.PreferenceGroup(
            title = context.stringResource(AYMR.strings.debug_info_app),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = context.stringResource(AYMR.strings.debug_info_version),
                    subtitle = AboutScreen.getVersionName(false),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = context.stringResource(AYMR.strings.debug_info_build_time),
                    subtitle = AboutScreen.getFormattedBuildTime(),
                ),
                getProfileVerifierPreference(),
                Preference.PreferenceItem.TextPreference(
                    title = context.stringResource(AYMR.strings.debug_info_webview_version),
                    subtitle = getWebViewVersion(),
                ),
            ),
        )
    }

    @Composable
    @ReadOnlyComposable
    private fun getWebViewVersion(): String {
        return WebViewUtil.getVersion(LocalContext.current)
    }

    @Composable
    private fun getProfileVerifierPreference(): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val status by produceState(initialValue = "-", key1 = context, key2 = configuration) {
            val result = ProfileVerifier.getCompilationStatusAsync().await().profileInstallResultCode
            value = when (result) {
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_NO_PROFILE_INSTALLED,
                -> context.stringResource(AYMR.strings.debug_info_profile_not_installed)
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_COMPILED_WITH_PROFILE,
                -> context.stringResource(AYMR.strings.debug_info_profile_compiled)
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_COMPILED_WITH_PROFILE_NON_MATCHING,
                -> context.stringResource(AYMR.strings.debug_info_profile_non_matching)
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_ERROR_CACHE_FILE_EXISTS_BUT_CANNOT_BE_READ,
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_ERROR_CANT_WRITE_PROFILE_VERIFICATION_RESULT_CACHE_FILE,
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_ERROR_PACKAGE_NAME_DOES_NOT_EXIST,
                -> context.stringResource(AYMR.strings.debug_info_profile_error, result)
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_ERROR_UNSUPPORTED_API_VERSION,
                -> context.stringResource(AYMR.strings.debug_info_profile_unsupported)
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_PROFILE_ENQUEUED_FOR_COMPILATION,
                -> context.stringResource(AYMR.strings.debug_info_profile_pending)
                ProfileVerifier.CompilationStatus.RESULT_CODE_ERROR_NO_PROFILE_EMBEDDED -> context.stringResource(
                    AYMR.strings.debug_info_profile_not_embedded,
                )
                else -> context.stringResource(AYMR.strings.debug_info_profile_unknown, result)
            }
        }
        return Preference.PreferenceItem.TextPreference(
            title = context.stringResource(AYMR.strings.debug_info_profile_status),
            subtitle = status,
        )
    }

    @Composable
    private fun getDeviceInfoGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val items = persistentListOf<Preference.PreferenceItem<out Any>>().mutate {
            it.add(
                Preference.PreferenceItem.TextPreference(
                    title = context.stringResource(AYMR.strings.debug_info_model),
                    subtitle = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
                ),
            )

            if (DeviceUtil.oneUiVersion != null) {
                it.add(
                    Preference.PreferenceItem.TextPreference(
                        title = context.stringResource(AYMR.strings.debug_info_oneui_version),
                        subtitle = "${DeviceUtil.oneUiVersion}",
                    ),
                )
            } else if (DeviceUtil.miuiMajorVersion != null) {
                it.add(
                    Preference.PreferenceItem.TextPreference(
                        title = context.stringResource(AYMR.strings.debug_info_miui_version),
                        subtitle = "${DeviceUtil.miuiMajorVersion}",
                    ),
                )
            }

            val androidVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Build.VERSION.RELEASE_OR_CODENAME
            } else {
                Build.VERSION.RELEASE
            }
            it.add(
                Preference.PreferenceItem.TextPreference(
                    title = context.stringResource(AYMR.strings.debug_info_android_version),
                    subtitle = "$androidVersion (${Build.DISPLAY})",
                ),
            )
        }

        return Preference.PreferenceGroup(
            title = context.stringResource(AYMR.strings.debug_info_device),
            preferenceItems = items,
        )
    }
}
