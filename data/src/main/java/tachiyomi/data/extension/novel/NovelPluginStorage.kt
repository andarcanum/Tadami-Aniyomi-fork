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
        scriptFile.writeBytes(script)
        logcat(LogPriority.INFO) {
            "Novel plugin script saved id=$pluginId file=${scriptFile.absolutePath} bytes=${script.size}"
        }

        customJs?.let {
            val file = customJsFile(pluginId)
            file.writeBytes(it)
            logcat(LogPriority.INFO) {
                "Novel plugin custom JS saved id=$pluginId file=${file.absolutePath} bytes=${it.size}"
            }
        }
        customCss?.let {
            val file = customCssFile(pluginId)
            file.writeBytes(it)
            logcat(LogPriority.INFO) {
                "Novel plugin custom CSS saved id=$pluginId file=${file.absolutePath} bytes=${it.size}"
            }
        }
    }

    fun deletePluginFiles(pluginId: String) {
        pluginScriptFile(pluginId).delete()
        customJsFile(pluginId).delete()
        customCssFile(pluginId).delete()
    }

    fun readPluginScript(pluginId: String): ByteArray? {
        val file = pluginScriptFile(pluginId)
        return if (file.exists()) {
            file.readBytes()
        } else {
            logcat(LogPriority.WARN) { "Novel plugin script missing id=$pluginId file=${file.absolutePath}" }
            null
        }
    }

    fun readCustomJs(pluginId: String): ByteArray? {
        val file = customJsFile(pluginId)
        return if (file.exists()) file.readBytes() else null
    }

    fun readCustomCss(pluginId: String): ByteArray? {
        val file = customCssFile(pluginId)
        return if (file.exists()) file.readBytes() else null
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
