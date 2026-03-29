package eu.kanade.tachiyomi.ui.reader.novel.translation

import android.util.Log
import java.security.MessageDigest

internal object GeminiPrivateBridge {
    private const val BRIDGE_CLASS_NAME = "eu.kanade.tachiyomi.privatebridge.GeminiPrivateBridge"
    private const val TAG = "GeminiPrivateBridge"

    private val instance: Any? by lazy {
        runCatching {
            Class.forName(BRIDGE_CLASS_NAME).getDeclaredConstructor().newInstance()
        }.getOrNull()
    }

    fun isInstalled(): Boolean = instance != null

    fun providerLabel(): String {
        return callNoArg("providerLabel") as? String ?: "Gemini Private"
    }

    fun isUnlocked(): Boolean {
        return (callNoArg("isUnlocked") as? Boolean) == true
    }

    fun unlock(password: String): Boolean {
        val target = instance ?: run {
            Log.w(TAG, "unlock failed: bridge instance is null")
            return false
        }
        return try {
            val unlocked = (callOneArg(target, "unlock", password, String::class.java) as? Boolean) == true
            if (!unlocked) {
                Log.w(TAG, buildDebugUnlockMessage(target, password))
            }
            unlocked
        } catch (throwable: Throwable) {
            Log.w(TAG, buildDebugUnlockMessage(target, password), throwable)
            false
        }
    }

    fun debugInfo(): String {
        val target = instance ?: return "bridge instance=null"
        return buildDebugInfo(target)
    }

    fun disablePromptModifiers(): Boolean {
        return (callNoArg("disablePromptModifiers") as? Boolean) == true
    }

    fun forceSingleChapterRequest(): Boolean {
        return (callNoArg("forceSingleChapterRequest") as? Boolean) == true
    }

    fun preprocessSegments(segments: List<String>): List<String> {
        @Suppress("UNCHECKED_CAST")
        return (callOneArg("preprocessSegments", segments, List::class.java) as? List<String>) ?: segments
    }

    fun systemPromptOverride(): String? {
        return callNoArg("systemPromptOverride") as? String
    }

    fun requestModelOverride(default: String): String {
        return callNoArg("requestModelOverride") as? String ?: default
    }

    fun requestTemperatureOverride(default: Float): Float {
        return (callNoArg("requestTemperatureOverride") as? Number)?.toFloat() ?: default
    }

    fun requestTopPOverride(default: Float): Float {
        return (callNoArg("requestTopPOverride") as? Number)?.toFloat() ?: default
    }

    fun requestTopKOverride(default: Int): Int {
        return (callNoArg("requestTopKOverride") as? Number)?.toInt() ?: default
    }

    fun requestMaxOutputTokensOverride(default: Int): Int {
        return (callNoArg("requestMaxOutputTokensOverride") as? Number)?.toInt() ?: default
    }

    fun requestFrequencyPenaltyOverride(default: Float): Float {
        return (callNoArg("requestFrequencyPenaltyOverride") as? Number)?.toFloat() ?: default
    }

    fun requestPresencePenaltyOverride(default: Float): Float {
        return (callNoArg("requestPresencePenaltyOverride") as? Number)?.toFloat() ?: default
    }

    fun requestThinkingLevelOverride(default: String): String {
        return callNoArg("requestThinkingLevelOverride") as? String ?: default
    }

    private fun callNoArg(name: String): Any? {
        val target = instance ?: return null
        return callNoArg(target, name)
    }

    private fun callNoArg(target: Any, name: String): Any? {
        return runCatching {
            target.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
                ?.invoke(target)
        }.getOrNull()
    }

    private fun callOneArg(
        name: String,
        arg: Any,
        parameterType: Class<*>,
    ): Any? {
        val target = instance ?: return null
        return callOneArg(target, name, arg, parameterType)
    }

    private fun callOneArg(
        target: Any,
        name: String,
        arg: Any,
        parameterType: Class<*>,
    ): Any? {
        return runCatching {
            target.javaClass.methods.firstOrNull {
                it.name == name &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].isAssignableFrom(parameterType)
            }?.invoke(target, arg)
        }.getOrNull()
    }

    private fun buildDebugUnlockMessage(target: Any, password: String): String {
        return buildString {
            append("unlock rejected; ")
            append(buildDebugInfo(target))
            append("; passwordLen=").append(password.length)
            append("; passwordTrimLen=").append(password.trim().length)
            append("; passwordFp=").append(sha256Hex(password).take(12))
            append("; passwordTrimFp=").append(sha256Hex(password.trim()).take(12))
        }
    }

    private fun buildDebugInfo(target: Any): String {
        return buildString {
            append("class=").append(target.javaClass.name)
            append("; loader=").append(target.javaClass.classLoader?.javaClass?.name ?: "null")
            append(
                "; codeSource=",
            )
            append(
                target.javaClass.protectionDomain?.codeSource?.location?.toExternalForm()
                    ?: "null",
            )
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val hex = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, b ->
            val value = b.toInt() and 0xff
            hex[index * 2] = HEX_CHARS[value ushr 4]
            hex[index * 2 + 1] = HEX_CHARS[value and 0x0f]
        }
        return String(hex)
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
}
