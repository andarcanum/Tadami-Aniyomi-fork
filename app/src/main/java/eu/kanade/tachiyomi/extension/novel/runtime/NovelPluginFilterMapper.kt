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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NovelPluginFilterMapper(
    private val json: Json,
) {
    fun toFilterList(filtersJson: String?): NovelFilterList {
        if (filtersJson.isNullOrBlank()) return NovelFilterList()
        val filters = json.decodeFromString<Map<String, PluginFilterDefinition>>(filtersJson)
        val list = filters.mapNotNull { (key, filter) ->
            when (filter.type) {
                FilterType.Text -> PluginTextFilter(
                    key = key,
                    name = filter.label,
                    state = filter.value?.jsonPrimitive?.content ?: "",
                )
                FilterType.Switch -> PluginSwitchFilter(
                    key = key,
                    name = filter.label,
                    state = filter.value?.jsonPrimitive?.content?.toBoolean() ?: false,
                )
                FilterType.Picker -> {
                    val options = filter.options.orEmpty()
                    val defaultValue = filter.value?.jsonPrimitive?.content
                    val index = options.indexOfFirst { it.value == defaultValue }.takeIf { it >= 0 } ?: 0
                    PluginPickerFilter(
                        key = key,
                        name = filter.label,
                        options = options,
                        state = index,
                    )
                }
                FilterType.Checkbox -> {
                    val selected = filter.value
                        ?.jsonArray
                        ?.map { it.jsonPrimitive.content }
                        ?.toSet()
                        .orEmpty()
                    val options = filter.options.orEmpty()
                    PluginCheckBoxGroup(
                        key = key,
                        name = filter.label,
                        options = options,
                        selected = selected,
                    )
                }
                FilterType.XCheckbox -> {
                    val valueObject = filter.value?.jsonObject
                    val include = valueObject?.get("include")
                        ?.jsonArray
                        ?.map { it.jsonPrimitive.content }
                        ?.toSet()
                        .orEmpty()
                    val exclude = valueObject?.get("exclude")
                        ?.jsonArray
                        ?.map { it.jsonPrimitive.content }
                        ?.toSet()
                        .orEmpty()
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
                    is PluginTextFilter -> putFilterValue(filter.key, FilterType.Text, JsonPrimitive(filter.state))
                    is PluginSwitchFilter -> putFilterValue(
                        filter.key,
                        FilterType.Switch,
                        JsonPrimitive(filter.state),
                    )
                    is PluginPickerFilter -> {
                        val selected = filter.options.getOrNull(filter.state)?.value ?: ""
                        putFilterValue(filter.key, FilterType.Picker, JsonPrimitive(selected))
                    }
                    is PluginCheckBoxGroup -> {
                        val selected = filter.state.filterIsInstance<PluginCheckBox>()
                            .filter { it.state }
                            .map { it.value }
                        putFilterValue(filter.key, FilterType.Checkbox, selected.toJsonArray())
                    }
                    is PluginXCheckBoxGroup -> {
                        val include = filter.state.filterIsInstance<PluginXCheckBox>()
                            .filter { it.state == NovelFilter.XCheckBox.STATE_INCLUDE }
                            .map { it.value }
                        val exclude = filter.state.filterIsInstance<PluginXCheckBox>()
                            .filter { it.state == NovelFilter.XCheckBox.STATE_EXCLUDE }
                            .map { it.value }
                        val value = buildJsonObject {
                            if (include.isNotEmpty()) put("include", include.toJsonArray())
                            if (exclude.isNotEmpty()) put("exclude", exclude.toJsonArray())
                        }
                        putFilterValue(filter.key, FilterType.XCheckbox, value)
                    }
                    else -> Unit
                }
            }
        }
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
        const val Text = "Text"
        const val Picker = "Picker"
        const val Checkbox = "Checkbox"
        const val Switch = "Switch"
        const val XCheckbox = "XCheckbox"
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
