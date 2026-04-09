package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech

class AndroidNovelTtsEngineInfoSource(
    private val context: Context,
) : NovelTtsEngineInfoSource {
    override fun listInstalledEngines(): List<NovelTtsInstalledEngine> {
        val packageManager = runCatching { context.packageManager }.getOrNull() ?: return emptyList()
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val services = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentServices(
                    intent,
                    android.content.pm.PackageManager.ResolveInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentServices(intent, 0)
            }
        }.getOrElse { return emptyList() }

        return services
            .mapNotNull { resolveInfo ->
                val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager).toString()
                    .takeIf { it.isNotBlank() }
                    ?: serviceInfo.packageName
                NovelTtsInstalledEngine(
                    packageName = serviceInfo.packageName,
                    label = label,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    override fun defaultEnginePackage(): String? {
        return runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.TTS_DEFAULT_SYNTH,
            )?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
