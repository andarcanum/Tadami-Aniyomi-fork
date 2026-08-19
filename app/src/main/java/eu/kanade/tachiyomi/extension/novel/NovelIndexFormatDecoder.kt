package eu.kanade.tachiyomi.extension.novel

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.model.NetworkExtensionStore
import okio.Buffer
import okio.GzipSource
import okio.buffer

/**
 * Normalizes any novel plugin repo index payload into the app's plugin JSON array.
 *
 * Supported inputs:
 * - plain JSON array (LNReader/Tachiyomi plugin repos, plus the legacy keiyoushi index.min.json);
 * - the new-format proto3-JSON store index (index.json with extensionList);
 * - the gzip-compressed protobuf store index (index.pb).
 *
 * Output is the plugin JSON array string consumed by [NovelPluginIndexParser] and
 * [NovelPluginRepoParser], so both novel listing paths stay JSON-only downstream.
 */
internal object NovelIndexFormatDecoder {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Returns the plugin JSON array string, or null when the payload is not a
     * supported novel plugin index.
     */
    fun decodeToPluginJson(bytes: ByteArray): String? {
        val raw = gunzipIfNeeded(bytes)
        if (raw.isEmpty()) return null
        return when (raw[0].toInt() and 0xff) {
            '['.code -> raw.toString(Charsets.UTF_8)
            '{'.code -> decodeJsonObjectToPluginJson(raw.toString(Charsets.UTF_8))
            else -> decodeProtoToPluginJson(raw)
        }
    }

    private fun decodeJsonObjectToPluginJson(text: String): String? {
        val store = try {
            json.decodeFromString<NetworkExtensionStore>(text)
        } catch (_: Exception) {
            return null
        }
        return store.extensionList?.toPluginJson()
    }

    private fun decodeProtoToPluginJson(raw: ByteArray): String? {
        val store = try {
            ProtoBuf.decodeFromByteArray<NetworkExtensionStore>(raw)
        } catch (_: Exception) {
            null
        }
        val list = store?.extensionList
            ?: try {
                ProtoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(raw)
            } catch (_: Exception) {
                null
            }
        return list?.toPluginJson()
    }

    private fun NetworkExtensionStore.ExtensionList.toPluginJson(): String = buildJsonArray {
        extensions.forEach { extension -> add(extension.toPluginJsonObject()) }
    }.toString()

    private fun NetworkExtensionStore.Extension.toPluginJsonObject(): JsonObject = buildJsonObject {
        put("isNovel", true)
        put("id", packageName)
        put("pkg", packageName)
        put("name", name)
        val source = sources.firstOrNull()
        put("lang", source?.language ?: "all")
        put("site", source?.homeUrl ?: "")
        put("version", versionCode)
        put("url", resources.apkUrl)
        put("apk", resources.apkUrl)
        put("iconUrl", resources.iconUrl)
        put(
            "sources",
            buildJsonArray {
                sources.forEach { s ->
                    add(
                        buildJsonObject {
                            put("id", s.id)
                            put("name", s.name)
                            put("lang", s.language)
                            put("baseUrl", s.homeUrl)
                        },
                    )
                }
            },
        )
        put("nsfw", if (contentWarning >= NetworkExtensionStore.ContentWarning.MIXED) 1 else 0)
        put("customJS", null as String?)
        put("customCSS", null as String?)
        put("hasSettings", false)
        put("sha256", "")
    }

    private fun gunzipIfNeeded(bytes: ByteArray): ByteArray {
        if (bytes.size < 2 || (bytes[0].toInt() and 0xff) != 0x1f || (bytes[1].toInt() and 0xff) != 0x8b) {
            return bytes
        }
        return try {
            GzipSource(Buffer().write(bytes)).buffer().use { it.readByteArray() }
        } catch (_: Exception) {
            bytes
        }
    }
}
