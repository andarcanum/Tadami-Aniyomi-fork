package eu.kanade.presentation.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Lightweight OEM/battery policy advisor for long-running downloads.
 *
 * This does not force any permission. It only surfaces a targeted, dismiss-free
 * reminder when the device is likely to throttle background work (Xiaomi/MIUI/
 * HyperOS family) or when Android battery optimizations are still enabled.
 */
@Composable
fun DownloadPowerPolicyBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val advisor = remember(context) { DownloadPowerPolicyAdvisor(context) }
    val state = remember { advisor.currentState() }

    if (!state.shouldShow) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.BatteryAlert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(AYMR.strings.download_power_policy_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.isXiaomiFamily) {
                        stringResource(AYMR.strings.download_power_policy_xiaomi_summary)
                    } else {
                        stringResource(AYMR.strings.download_power_policy_android_summary)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = advisor::openBatteryOptimizationSettings) {
                        Text(stringResource(AYMR.strings.download_power_policy_battery_action))
                    }
                    if (state.isXiaomiFamily) {
                        TextButton(onClick = advisor::openXiaomiAutostartSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                            )
                            Text(stringResource(AYMR.strings.download_power_policy_autostart_action))
                        }
                    }
                }
            }
        }
    }
}

private class DownloadPowerPolicyAdvisor(
    private val context: Context,
) {
    fun currentState(): State {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val isXiaomiFamily = listOf("xiaomi", "redmi", "poco").any { token ->
            manufacturer.contains(token) || brand.contains(token)
        }
        val ignoringBatteryOptimizations = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            runCatching {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }.getOrDefault(false)

        return State(
            isXiaomiFamily = isXiaomiFamily,
            ignoringBatteryOptimizations = ignoringBatteryOptimizations,
        )
    }

    fun openBatteryOptimizationSettings() {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intents = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        } else {
            listOf(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            )
        }
        openFirstAvailable(intents)
    }

    fun openXiaomiAutostartSettings() {
        val intents = listOf(
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings",
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
        openFirstAvailable(intents)
    }

    private fun openFirstAvailable(intents: List<Intent>) {
        intents.forEach { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next OEM/system fallback.
            } catch (_: SecurityException) {
                // Some ROMs block direct settings panels; try the next fallback.
            }
        }
    }

    data class State(
        val isXiaomiFamily: Boolean,
        val ignoringBatteryOptimizations: Boolean,
    ) {
        val shouldShow: Boolean
            get() = isXiaomiFamily || !ignoringBatteryOptimizations
    }
}
