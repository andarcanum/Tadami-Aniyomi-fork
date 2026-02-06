package eu.kanade.tachiyomi.ui.reader.novel.setting

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class NovelReaderSettings(
    val fontSize: Int,
    val lineHeight: Float,
    val margin: Int,
    val theme: NovelReaderTheme,
)

enum class NovelReaderTheme {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
data class NovelReaderOverride(
    val fontSize: Int? = null,
    val lineHeight: Float? = null,
    val margin: Int? = null,
    val theme: NovelReaderTheme? = null,
)

class NovelReaderPreferences(
    private val preferenceStore: PreferenceStore,
    private val json: Json = Injekt.get(),
) {
    fun fontSize() = preferenceStore.getInt("novel_reader_font_size", DEFAULT_FONT_SIZE)

    fun lineHeight() = preferenceStore.getFloat("novel_reader_line_height", DEFAULT_LINE_HEIGHT)

    fun margin() = preferenceStore.getInt("novel_reader_margins", DEFAULT_MARGIN)

    fun theme() = preferenceStore.getEnum("novel_reader_theme", NovelReaderTheme.SYSTEM)

    fun sourceOverrides() = preferenceStore.getObject(
        "novel_reader_source_overrides",
        emptyMap(),
        serializer = { json.encodeToString(overrideSerializer, it) },
        deserializer = { json.decodeFromString(overrideSerializer, it) },
    )

    fun getSourceOverride(sourceId: Long): NovelReaderOverride? = sourceOverrides().get()[sourceId]

    fun setSourceOverride(sourceId: Long, override: NovelReaderOverride?) {
        val updated = sourceOverrides().get().toMutableMap()
        if (override == null) {
            updated.remove(sourceId)
        } else {
            updated[sourceId] = override
        }
        sourceOverrides().set(updated)
    }

    fun enableSourceOverride(sourceId: Long) {
        if (getSourceOverride(sourceId) != null) return
        setSourceOverride(
            sourceId,
            NovelReaderOverride(
                fontSize = fontSize().get(),
                lineHeight = lineHeight().get(),
                margin = margin().get(),
                theme = theme().get(),
            ),
        )
    }

    fun updateSourceOverride(
        sourceId: Long,
        update: (NovelReaderOverride) -> NovelReaderOverride,
    ) {
        val current = getSourceOverride(sourceId) ?: NovelReaderOverride()
        setSourceOverride(sourceId, update(current))
    }

    fun resolveSettings(sourceId: Long): NovelReaderSettings {
        val override = getSourceOverride(sourceId)
        return NovelReaderSettings(
            fontSize = override?.fontSize ?: fontSize().get(),
            lineHeight = override?.lineHeight ?: lineHeight().get(),
            margin = override?.margin ?: margin().get(),
            theme = override?.theme ?: theme().get(),
        )
    }

    fun settingsFlow(sourceId: Long): Flow<NovelReaderSettings> {
        return combine(
            fontSize().changes(),
            lineHeight().changes(),
            margin().changes(),
            theme().changes(),
            sourceOverrides().changes(),
        ) { fontSize, lineHeight, margin, theme, overrides ->
            val override = overrides[sourceId]
            NovelReaderSettings(
                fontSize = override?.fontSize ?: fontSize,
                lineHeight = override?.lineHeight ?: lineHeight,
                margin = override?.margin ?: margin,
                theme = override?.theme ?: theme,
            )
        }
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 16
        const val DEFAULT_LINE_HEIGHT = 1.6f
        const val DEFAULT_MARGIN = 16

        private val overrideSerializer = MapSerializer(
            Long.serializer(),
            NovelReaderOverride.serializer(),
        )
    }
}
