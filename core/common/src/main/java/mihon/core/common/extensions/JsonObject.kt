package mihon.core.common.extensions

import kotlinx.serialization.json.JsonObject

private val JsonObjectEmpty = JsonObject(emptyMap())

val JsonObject.Companion.EMPTY: JsonObject get() = JsonObjectEmpty
