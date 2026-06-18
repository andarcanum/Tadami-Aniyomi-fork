package eu.kanade.tachiyomi.extension.novel.api

import eu.kanade.tachiyomi.extension.novel.normalizeNovelLang
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelPluginIndexParser(
    private val json: Json,
) {
    fun parse(payload: String, repoUrl: String): List<NovelPlugin.Available> {
        val entries = json.parseToJsonElement(payload).jsonArray
        return entries.mapNotNull { element ->
            element.toPlugin(repoUrl)
        }
    }
}

private fun JsonElement.toPlugin(repoUrl: String): NovelPlugin.Available? {
    val obj = jsonObject

    obj.toKotlinExtensionPlugin(repoUrl)?.let { return it }

    val id = obj["id"]?.stringValue() ?: return null
    val name = obj["name"]?.stringValue() ?: return null
    val site = obj["site"]?.stringValue() ?: return null
    val lang = normalizeNovelLang(obj["lang"]?.stringValue())
    val url = obj["url"]?.stringValue()?.resolveAgainstRepo(repoUrl) ?: return null
    val iconUrl = obj["iconUrl"]?.stringValue()?.resolveAgainstRepo(repoUrl)
    val customJs = obj["customJS"]?.stringValue()?.resolveAgainstRepo(repoUrl)
    val customCss = obj["customCSS"]?.stringValue()?.resolveAgainstRepo(repoUrl)
    val hasSettings = obj["hasSettings"]?.jsonPrimitive?.booleanOrNull ?: false
    val sha256 = obj["sha256"]?.stringValue().orEmpty()
    val versionCode = parseVersion(obj["version"])
    val rawVersion = obj["version"]?.stringValue()
    val versionName = rawVersion?.takeIf { it.isNotBlank() } ?: versionCode.toString()

    return NovelPlugin.Available(
        id = id,
        name = name,
        site = site,
        lang = lang,
        versionCode = versionCode,
        versionName = versionName,
        url = url,
        iconUrl = iconUrl,
        customJs = customJs,
        customCss = customCss,
        hasSettings = hasSettings,
        sha256 = sha256,
        repoUrl = repoUrl,
    )
}

private fun JsonObject.toKotlinExtensionPlugin(repoUrl: String): NovelPlugin.Available? {
    if (this["isNovel"]?.jsonPrimitive?.booleanOrNull != true) return null
    val pkgName = this["pkg"]?.stringValue() ?: return null
    val apkUrl = this["apk"]?.stringValue()?.resolveApkAgainstRepo(repoUrl) ?: return null
    val rawName = this["name"]?.stringValue() ?: pkgName
    val name = rawName
        .substringAfter("Tsundoku: ")
        .substringAfter("NovelApp: ")
        .ifBlank { rawName }
    val lang = normalizeNovelLang(this["lang"]?.stringValue())
    val versionCode = parseVersion(this["code"] ?: this["version"])
    val versionName = this["version"]?.stringValue()?.takeIf { it.isNotBlank() } ?: versionCode.toString()
    val site = this["sources"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("baseUrl")
        ?.stringValue()
        .orEmpty()

    return NovelPlugin.Available(
        id = pkgName,
        name = name,
        site = site,
        lang = lang,
        versionCode = versionCode,
        versionName = versionName,
        url = apkUrl,
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = this["sha256"]?.stringValue().orEmpty(),
        repoUrl = repoUrl,
        pkgName = pkgName,
        apkUrl = apkUrl,
        isKotlinExtension = true,
    )
}

private fun String.resolveApkAgainstRepo(repoUrl: String): String? {
    val raw = trim()
    if (raw.isBlank()) return null
    raw.toHttpUrlOrNull()?.let { return it.toString() }

    val normalizedRepoUrl = repoUrl.trim()
    val repoRoot = if (normalizedRepoUrl.endsWith(".json", ignoreCase = true)) {
        normalizedRepoUrl.substringBeforeLast('/')
    } else {
        normalizedRepoUrl.trimEnd('/')
    }
    val base = "$repoRoot/".toHttpUrlOrNull() ?: return raw
    val apkPath = if (raw.startsWith("apk/")) raw else "apk/$raw"
    return base.resolve(apkPath)?.toString() ?: raw
}

private fun parseVersion(element: JsonElement?): Int {
    val primitive = element?.jsonPrimitive ?: return 0
    if (!primitive.isString) {
        return primitive.content.toIntOrNull() ?: 0
    }
    val raw = primitive.content.trim()
    if (raw.isEmpty()) return 0
    return if (raw.contains('.')) {
        val parts = raw.split('.', '-', '_')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        major * 1_000_000 + minor * 1_000 + patch
    } else {
        raw.toIntOrNull() ?: 0
    }
}

private fun JsonObject.stringValue(key: String): String? = this[key]?.stringValue()

private fun JsonElement.stringValue(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content
}

private fun String.resolveAgainstRepo(repoUrl: String): String? {
    val raw = trim()
    if (raw.isBlank()) return null
    raw.toHttpUrlOrNull()?.let { return it.toString() }

    val normalizedRepoUrl = repoUrl.trim()
    val baseUrl = if (normalizedRepoUrl.endsWith(".json", ignoreCase = true)) {
        normalizedRepoUrl
    } else {
        normalizedRepoUrl.trimEnd('/') + "/"
    }
    val base = baseUrl.toHttpUrlOrNull() ?: return raw
    return base.resolve(raw)?.toString() ?: raw
}
