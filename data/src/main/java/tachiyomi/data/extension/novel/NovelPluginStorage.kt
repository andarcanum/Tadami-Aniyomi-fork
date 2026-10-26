package tachiyomi.data.extension.novel

import logcat.LogPriority
import logcat.logcat
import java.io.File

class NovelPluginStorage(
    private val baseDir: File,
) {
    private fun pluginDir(): File {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    fun writePluginFiles(
        pluginId: String,
        script: ByteArray,
        customJs: ByteArray?,
        customCss: ByteArray?,
    ) {
        pluginDir()
        val scriptFile = pluginScriptFile(pluginId)
        writeAtomically(scriptFile, script)
        logcat(LogPriority.INFO) {
            "Novel plugin script saved id=$pluginId file=${scriptFile.absolutePath} bytes=${script.size}"
        }

        customJs?.let {
            val file = customJsFile(pluginId)
            writeAtomically(file, it)
            logcat(LogPriority.INFO) {
                "Novel plugin custom JS saved id=$pluginId file=${file.absolutePath} bytes=${it.size}"
            }
        }
        customCss?.let {
            val file = customCssFile(pluginId)
            writeAtomically(file, it)
            logcat(LogPriority.INFO) {
                "Novel plugin custom CSS saved id=$pluginId file=${file.absolutePath} bytes=${it.size}"
            }
        }
    }

    /**
     * Writes via a temp file + rename so a crash mid-write cannot leave a truncated plugin that a
     * later load would silently accept.
     */
    private fun writeAtomically(file: File, bytes: ByteArray) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        if (file.exists() && !file.delete()) {
            tmp.delete()
            throw IllegalStateException("Failed to replace ${file.absolutePath}")
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    fun deletePluginFiles(pluginId: String) {
        pluginScriptFile(pluginId).delete()
        customJsFile(pluginId).delete()
        customCssFile(pluginId).delete()
    }

    fun readPluginScript(pluginId: String): ByteArray? {
        val file = pluginScriptFile(pluginId)
        if (!file.exists()) {
            logcat(LogPriority.WARN) { "Novel plugin script missing id=$pluginId file=${file.absolutePath}" }
            return null
        }
        return readScriptBytes(file, pluginId)
    }

    fun readCustomJs(pluginId: String): ByteArray? {
        val file = customJsFile(pluginId)
        if (!file.exists()) return null
        return readScriptBytes(file, pluginId)
    }

    fun readCustomCss(pluginId: String): ByteArray? {
        val file = customCssFile(pluginId)
        if (!file.exists()) return null
        return readScriptBytes(file, pluginId)
    }

    private fun readScriptBytes(file: File, pluginId: String): ByteArray? {
        // A concurrent uninstall/update can delete the file between exists() and readBytes();
        // treat the read failure as a missing script instead of crashing the caller.
        return runCatching { file.readBytes() }
            .onFailure {
                logcat(LogPriority.WARN) {
                    "Novel plugin script read failed id=$pluginId file=${file.absolutePath}: $it"
                }
            }
            .getOrNull()
    }

    private fun pluginScriptFile(pluginId: String): File {
        return File(pluginDir(), "${sanitizeId(pluginId)}.js")
    }

    private fun customJsFile(pluginId: String): File {
        return File(pluginDir(), "${sanitizeId(pluginId)}.custom.js")
    }

    private fun customCssFile(pluginId: String): File {
        return File(pluginDir(), "${sanitizeId(pluginId)}.custom.css")
    }

    private fun sanitizeId(value: String): String {
        return value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
