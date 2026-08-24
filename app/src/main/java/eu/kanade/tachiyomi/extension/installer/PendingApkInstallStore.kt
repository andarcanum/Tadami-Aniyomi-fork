package eu.kanade.tachiyomi.extension.installer

import android.content.Context
import android.content.Intent
import android.os.Build
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.util.storage.getUriCompat
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

/** Pending install requests older than this are dropped instead of replayed on startup. */
internal const val PENDING_INSTALL_TTL_MS = 7L * 24 * 60 * 60 * 1000

internal fun isPendingInstallStale(createdAtMs: Long, nowMs: Long): Boolean =
    nowMs - createdAtMs > PENDING_INSTALL_TTL_MS

/**
 * Persisted pending APK install requests used when Android requires the user to grant
 * "Install unknown apps" permission before the install intent can be launched.
 *
 * Multiple requests can accumulate while the user is away in the system settings; the queue is
 * replayed in order once the permission is granted.
 */
class PendingApkInstallStore(
    private val basePreferences: BasePreferences,
) {
    data class PendingInstall(
        val packageName: String,
        val displayName: String,
        val filePath: String,
        val kind: ApkExtensionKind,
        val backend: ApkInstallBackend,
        val createdAtMs: Long = System.currentTimeMillis(),
    )

    fun save(request: PendingInstall) {
        basePreferences.pendingApkInstallQueue().getAndSet { queue ->
            (queue - request.packageName + request.encode()).toSet()
        }
    }

    fun get(): PendingInstall? = getAll().firstOrNull()

    fun getAll(): List<PendingInstall> {
        migrateLegacyPendingIfNeeded()
        return basePreferences.pendingApkInstallQueue().get().mapNotNull { it.decode() }
    }

    fun clear() {
        basePreferences.pendingApkInstallQueue().set(emptySet())
    }

    /**
     * One-time port of the pre-queue single-slot pending install (separate prefs) into the queue,
     * so an in-flight permission wait survives the app upgrade.
     */
    private fun migrateLegacyPendingIfNeeded() {
        val packageName = basePreferences.pendingApkInstallPackage().get().takeIf { it.isNotBlank() } ?: return
        val displayName = basePreferences.pendingApkInstallDisplayName().get()
        val filePath = basePreferences.pendingApkInstallPath().get().takeIf { it.isNotBlank() }
        val kind = basePreferences.pendingApkInstallKind().get().toEnumOrNull<ApkExtensionKind>()
        val backend = basePreferences.pendingApkInstallBackend().get().toEnumOrNull<ApkInstallBackend>()
        if (filePath != null && kind != null && backend != null) {
            save(PendingInstall(packageName, displayName, filePath, kind, backend))
        }
        clearLegacy()
    }

    private fun clearLegacy() {
        basePreferences.pendingApkInstallPackage().set("")
        basePreferences.pendingApkInstallDisplayName().set("")
        basePreferences.pendingApkInstallPath().set("")
        basePreferences.pendingApkInstallKind().set("")
        basePreferences.pendingApkInstallBackend().set("")
    }

    @Suppress("DEPRECATION")
    suspend fun resumeIfPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            return false
        }

        var resumedAny = false
        val nowMs = System.currentTimeMillis()
        for (pending in getAll()) {
            if (isPendingInstallStale(pending.createdAtMs, nowMs)) {
                logcat(LogPriority.INFO) {
                    "Dropping stale pending APK install package=${pending.packageName} " +
                        "ageDays=${(nowMs - pending.createdAtMs) / PENDING_INSTALL_TTL_MS}"
                }
                remove(pending.packageName)
                continue
            }
            val apkFile = File(pending.filePath)
            val exists = withIOContext { apkFile.isFile }
            if (!exists) {
                logcat(LogPriority.WARN) {
                    "Pending APK install file is missing package=${pending.packageName} path=${pending.filePath}"
                }
                remove(pending.packageName)
                continue
            }

            // Only the legacy system-intent installer can be replayed blindly here: launching
            // ACTION_INSTALL_PACKAGE IS its native mechanism. Every other backend (including
            // PackageInstaller's session flow) would bypass its own logic and never report a
            // result back; keep the entry so the normal extensions-screen flow can retry it.
            if (pending.backend != ApkInstallBackend.LEGACY) {
                logcat(LogPriority.WARN) {
                    "Skipping auto-resume of ${pending.backend} install " +
                        "package=${pending.packageName}; retry from the extensions screen"
                }
                continue
            }

            val launched = runCatching {
                withUIContext {
                    Intent(Intent.ACTION_INSTALL_PACKAGE)
                        .setDataAndType(apkFile.getUriCompat(context), APK_MIME)
                        .putExtra(Intent.EXTRA_RETURN_RESULT, false)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .let(context::startActivity)
                }
                logcat(LogPriority.INFO) {
                    "Resumed pending APK install package=${pending.packageName} kind=${pending.kind} backend=${pending.backend}"
                }
                true
            }.getOrElse { error ->
                logcat(LogPriority.ERROR, error) {
                    "Failed to resume pending APK install package=${pending.packageName} path=${pending.filePath}"
                }
                false
            }
            if (launched) {
                resumedAny = true
                remove(pending.packageName)
            }
        }
        return resumedAny
    }

    /** Removes only the entry for [packageName]: a successful install of one extension must
     *  never wipe pending entries of other extensions (cross-media queue loss). */
    fun remove(packageName: String) {
        basePreferences.pendingApkInstallQueue().getAndSet { queue ->
            queue.filterNot { it.decode()?.packageName == packageName }.toSet()
        }
    }

    private fun PendingInstall.encode(): String {
        return buildJsonObject {
            put("packageName", packageName)
            put("displayName", displayName)
            put("filePath", filePath)
            put("kind", kind.name)
            put("backend", backend.name)
            put("createdAtMs", createdAtMs)
        }.toString()
    }

    private fun String.decode(): PendingInstall? {
        return runCatching {
            val obj = Json.parseToJsonElement(this).jsonObject
            PendingInstall(
                packageName = obj["packageName"]?.jsonPrimitive?.contentOrNull ?: return null,
                displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                filePath = obj["filePath"]?.jsonPrimitive?.contentOrNull ?: return null,
                kind = obj["kind"]?.jsonPrimitive?.contentOrNull
                    ?.toEnumOrNull<ApkExtensionKind>() ?: return null,
                backend = obj["backend"]?.jsonPrimitive?.contentOrNull
                    ?.toEnumOrNull<ApkInstallBackend>() ?: return null,
                createdAtMs = obj["createdAtMs"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: System.currentTimeMillis(),
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
