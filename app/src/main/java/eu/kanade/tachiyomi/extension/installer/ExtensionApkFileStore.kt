package eu.kanade.tachiyomi.extension.installer

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import logcat.LogPriority
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import java.io.File
import tachiyomi.i18n.R as I18nR

/**
 * Per-package index of app-private APKs kept for manual share/install when OEM package
 * installers fail. The index is keyed by package name, so a fresh download never repoints
 * another extension's entry; the APK files themselves live in per-package cache paths owned
 * by their download sites.
 */
class ExtensionApkFileStore(
    private val basePreferences: BasePreferences,
) {
    data class ApkFile(
        val packageName: String,
        val displayName: String,
        val filePath: String,
        val kind: ApkExtensionKind,
    )

    fun save(apkFile: ApkFile) {
        migrateLegacySlotIfNeeded()
        basePreferences.extensionApkFiles().getAndSet { entries ->
            entries.filterNot { it.decode()?.packageName == apkFile.packageName }
                .plus(apkFile.encode())
                .toSet()
        }
    }

    /** Returns the stored APK for [packageName]; pass null only when any entry is acceptable. */
    fun get(packageName: String?): ApkFile? {
        migrateLegacySlotIfNeeded()
        return basePreferences.extensionApkFiles().get()
            .mapNotNull { it.decode() }
            .firstOrNull { packageName == null || it.packageName == packageName }
    }

    fun clear(packageName: String) {
        basePreferences.extensionApkFiles().getAndSet { entries ->
            entries.filterNot { it.decode()?.packageName == packageName }.toSet()
        }
    }

    suspend fun share(context: Context, packageName: String? = null): Boolean {
        val apk = get(packageName) ?: return false
        val file = File(apk.filePath)
        val exists = withIOContext { file.isFile }
        if (!exists) {
            logcat(LogPriority.WARN) {
                "Stored extension APK is missing package=${apk.packageName} path=${apk.filePath}"
            }
            clear(apk.packageName)
            return false
        }

        return runCatching {
            val message = context.getString(I18nR.string.ext_manual_apk_share_message)
            withUIContext {
                context.startActivity(
                    file.getUriCompat(context).toShareIntent(
                        context = context,
                        type = APK_MIME,
                        message = message,
                    ),
                )
            }
            true
        }.getOrElse { error ->
            logcat(LogPriority.ERROR, error) {
                "Failed to share extension APK package=${apk.packageName} path=${apk.filePath}"
            }
            false
        }
    }

    /**
     * One-time port of the pre-index single slot (last_extension_apk_* prefs) into the
     * per-package index, so an already downloaded fallback APK survives the upgrade.
     */
    private fun migrateLegacySlotIfNeeded() {
        val storedPackage = basePreferences.lastExtensionApkPackage().get().takeIf { it.isNotBlank() } ?: return
        val displayName = basePreferences.lastExtensionApkDisplayName().get()
        val filePath =
            basePreferences.lastExtensionApkPath().get().takeIf { it.isNotBlank() } ?: return clearLegacySlot()
        val kind = basePreferences.lastExtensionApkKind().get().toEnumOrNull<ApkExtensionKind>()
            ?: return clearLegacySlot()
        // Clear the legacy slot BEFORE saving: save() re-enters this migration, so a
        // still-populated slot would recurse until the stack overflows.
        clearLegacySlot()
        save(ApkFile(packageName = storedPackage, displayName = displayName, filePath = filePath, kind = kind))
    }

    private fun clearLegacySlot() {
        basePreferences.lastExtensionApkPackage().set("")
        basePreferences.lastExtensionApkDisplayName().set("")
        basePreferences.lastExtensionApkPath().set("")
        basePreferences.lastExtensionApkKind().set("")
    }

    private fun ApkFile.encode(): String {
        return buildJsonObject {
            put("packageName", packageName)
            put("displayName", displayName)
            put("filePath", filePath)
            put("kind", kind.name)
        }.toString()
    }

    private fun String.decode(): ApkFile? {
        return runCatching {
            val obj = Json.parseToJsonElement(this).jsonObject
            ApkFile(
                packageName = obj["packageName"]?.jsonPrimitive?.contentOrNull ?: return null,
                displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                filePath = obj["filePath"]?.jsonPrimitive?.contentOrNull ?: return null,
                kind = obj["kind"]?.jsonPrimitive?.contentOrNull
                    ?.toEnumOrNull<ApkExtensionKind>() ?: return null,
            )
        }.getOrNull()
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? {
        return runCatching { enumValueOf<T>(this) }.getOrNull()
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
