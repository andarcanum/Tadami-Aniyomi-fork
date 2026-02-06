package eu.kanade.tachiyomi.extension.novel.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.domain.extension.novel.model.NovelPlugin
import eu.kanade.tachiyomi.extension.novel.normalizeNovelLang

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
    val id = obj["id"]?.stringValue() ?: return null
    val name = obj["name"]?.stringValue() ?: return null
    val site = obj["site"]?.stringValue() ?: return null
    val lang = normalizeNovelLang(obj["lang"]?.stringValue())
    val url = obj["url"]?.stringValue() ?: return null
    val iconUrl = obj["iconUrl"]?.stringValue()
    val customJs = obj["customJS"]?.stringValue()
    val customCss = obj["customCSS"]?.stringValue()
    val hasSettings = obj["hasSettings"]?.jsonPrimitive?.booleanOrNull ?: false
    val sha256 = obj["sha256"]?.stringValue().orEmpty()
    val version = parseVersion(obj["version"])

    return NovelPlugin.Available(
        id = id,
        name = name,
        site = site,
        lang = lang,
        version = version,
        url = url,
        iconUrl = iconUrl,
        customJs = customJs,
        customCss = customCss,
        hasSettings = hasSettings,
        sha256 = sha256,
        repoUrl = repoUrl,
    )
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
