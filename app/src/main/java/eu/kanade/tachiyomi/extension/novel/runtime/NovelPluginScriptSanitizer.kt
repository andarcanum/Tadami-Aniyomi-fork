package eu.kanade.tachiyomi.extension.novel.runtime

object NovelPluginScriptSanitizer {
    fun sanitize(code: String): String {
        var result = code
        if (result.startsWith("\uFEFF")) result = result.substring(1)
        result = result.replace("\u0000", "")
        result = result.replace(Regex("[\u0001-\u0008\u000B\u000C\u000E-\u001F]"), "")
        result = result.replace("\r\n", "\n").replace("\r", "\n")
        result = result.trim()
        return result
    }
}
