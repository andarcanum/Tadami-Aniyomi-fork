package eu.kanade.tachiyomi.ui.webview

import okhttp3.Headers

internal fun resolveWebViewHeaders(
    source: Any?,
): Map<String, String> {
    return source?.let(::extractHeadersViaReflection).orEmpty()
}

private fun extractHeadersViaReflection(source: Any): Map<String, String> {
    return runCatching {
        (source.javaClass.methods.asSequence() + source.javaClass.declaredMethods.asSequence())
            .firstOrNull { it.name == "getHeaders" && it.parameterCount == 0 }
            ?.apply { isAccessible = true }
            ?.invoke(source)
    }.getOrNull()
        ?.let { raw ->
            when (raw) {
                is Headers -> buildMap {
                    for (index in 0 until raw.size) {
                        put(raw.name(index), raw.value(index))
                    }
                }
                is Map<*, *> ->
                    raw.entries
                        .mapNotNull { (name, value) ->
                            val key = name?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            key to (value?.toString() ?: "")
                        }
                        .toMap()
                else -> emptyMap()
            }
        }
        .orEmpty()
}
