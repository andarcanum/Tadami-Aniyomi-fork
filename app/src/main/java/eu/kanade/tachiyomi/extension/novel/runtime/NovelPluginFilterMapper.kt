package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class NovelPluginFilterMapper(
    private val json: Json,
) {
    fun toFilterList(filtersJson: String?): NovelFilterList {
        if (filtersJson.isNullOrBlank()) return NovelFilterList()
        val filters = json.decodeFromString<Map<String, PluginFilterDefinition>>(filtersJson)
        val list = filters.mapNotNull { (key, filter) ->
            when (filter.type) {
                FilterType.TEXT -> PluginTextFilter(
                    key = key,
                    name = filter.label,
                    state = primitiveContent(filter.value).orEmpty(),
                )
                FilterType.SWITCH -> PluginSwitchFilter(
                    key = key,
                    name = filter.label,
                    state = primitiveBoolean(filter.value),
                )
                FilterType.PICKER -> {
                    val options = filter.options.orEmpty()
                    val defaultValue = primitiveContent(filter.value)
                    val index = options.indexOfFirst { it.value == defaultValue }.takeIf { it >= 0 } ?: 0
                    PluginPickerFilter(
                        key = key,
                        name = filter.label,
                        options = options,
                        state = index,
                    )
                }
                FilterType.CHECKBOX -> {
                    val selected = stringValues(filter.value)
                    val options = filter.options.orEmpty()
                    PluginCheckBoxGroup(
                        key = key,
                        name = filter.label,
                        options = options,
                        selected = selected,
                    )
                }
                FilterType.XCHECKBOX -> {
                    val valueObject = filter.value as? JsonObject
                    val include = stringValues(valueObject?.get("include"))
                    val exclude = stringValues(valueObject?.get("exclude"))
                    val options = filter.options.orEmpty()
                    PluginXCheckBoxGroup(
                        key = key,
                        name = filter.label,
                        options = options,
                        include = include,
                        exclude = exclude,
                    )
                }
                else -> null
            }
        }
        return NovelFilterList(list)
    }

    fun toFilterValues(filters: NovelFilterList): JsonObject {
        return buildJsonObject {
            fun putFilterValue(key: String, type: String, value: JsonElement) {
                putJsonObject(key) {
                    put("type", JsonPrimitive(type))
                    put("value", value)
                }
            }
            filters.forEach { filter ->
                when (filter) {
                    is PluginTextFilter -> putFilterValue(filter.key, FilterType.TEXT, JsonPrimitive(filter.state))
                    is PluginSwitchFilter -> putFilterValue(
                        filter.key,
                        FilterType.SWITCH,
                        JsonPrimitive(filter.state),
                    )
                    is PluginPickerFilter -> {
                        val selected = filter.options.getOrNull(filter.state)?.value ?: ""
                        putFilterValue(filter.key, FilterType.PICKER, JsonPrimitive(selected))
                    }
                    is PluginCheckBoxGroup -> {
                        val selected = filter.state.filterIsInstance<PluginCheckBox>()
                            .filter { it.state }
                            .map { it.value }
                        putFilterValue(filter.key, FilterType.CHECKBOX, selected.toJsonArray())
                    }
                    is PluginXCheckBoxGroup -> {
                        val include = filter.state.filterIsInstance<PluginXCheckBox>()
                            .filter { it.state == NovelFilter.XCheckBox.STATE_INCLUDE }
                            .map { it.value }
                        val exclude = filter.state.filterIsInstance<PluginXCheckBox>()
                            .filter { it.state == NovelFilter.XCheckBox.STATE_EXCLUDE }
                            .map { it.value }
                        val value = buildJsonObject {
                            put("include", include.toJsonArray())
                            put("exclude", exclude.toJsonArray())
                        }
                        putFilterValue(filter.key, FilterType.XCHECKBOX, value)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun primitiveContent(value: JsonElement?): String? {
        return (value as? JsonPrimitive)?.contentOrNull
    }

    private fun primitiveBoolean(value: JsonElement?): Boolean {
        val primitive = value as? JsonPrimitive ?: return false
        primitive.booleanOrNull?.let { return it }
        return primitive.contentOrNull?.equals("true", ignoreCase = true) == true
    }

    private fun stringValues(value: JsonElement?): Set<String> {
        return when (value) {
            is JsonArray -> value.asSequence()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            is JsonPrimitive -> delimitedValues(value.contentOrNull).toSet()
            else -> emptySet()
        }
    }

    private fun delimitedValues(value: String?): List<String> {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        if (',' !in raw && ';' !in raw && '|' !in raw) return listOf(raw)
        return raw.split(',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun List<String>.toJsonArray(): JsonArray {
        return JsonArray(map { JsonPrimitive(it) })
    }

    @Serializable
    internal data class PluginFilterDefinition(
        val type: String,
        val label: String,
        val value: JsonElement? = null,
        val options: List<FilterOption>? = null,
    )

    @Serializable
    internal data class FilterOption(
        val label: String,
        val value: String,
    )

    internal object FilterType {
        const val TEXT = "Text"
        const val PICKER = "Picker"
        const val CHECKBOX = "Checkbox"
        const val SWITCH = "Switch"
        const val XCHECKBOX = "XCheckbox"
    }

    internal class PluginTextFilter(
        val key: String,
        name: String,
        state: String,
    ) : NovelFilter.Text(name, state)

    internal class PluginSwitchFilter(
        val key: String,
        name: String,
        state: Boolean,
    ) : NovelFilter.Switch(name, state)

    internal class PluginPickerFilter(
        val key: String,
        name: String,
        val options: List<FilterOption>,
        state: Int,
    ) : NovelFilter.Picker<String>(name, options.map { it.label }.toTypedArray(), state)

    internal class PluginCheckBoxGroup(
        val key: String,
        name: String,
        options: List<FilterOption>,
        selected: Set<String>,
    ) : NovelFilter.Group<NovelFilter.CheckBox>(
        name,
        options.map { PluginCheckBox(it.value, it.label, it.value in selected) },
    )

    internal class PluginCheckBox(
        val value: String,
        name: String,
        state: Boolean,
    ) : NovelFilter.CheckBox(name, state)

    internal class PluginXCheckBoxGroup(
        val key: String,
        name: String,
        options: List<FilterOption>,
        include: Set<String>,
        exclude: Set<String>,
    ) : NovelFilter.Group<NovelFilter.XCheckBox>(
        name,
        options.map { option ->
            val state = when {
                option.value in include -> NovelFilter.XCheckBox.STATE_INCLUDE
                option.value in exclude -> NovelFilter.XCheckBox.STATE_EXCLUDE
                else -> NovelFilter.XCheckBox.STATE_IGNORE
            }
            PluginXCheckBox(option.value, option.label, state)
        },
    )

    internal class PluginXCheckBox(
        val value: String,
        name: String,
        state: Int,
    ) : NovelFilter.XCheckBox(name, state)
}
