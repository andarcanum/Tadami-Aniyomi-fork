package eu.kanade.tachiyomi.ui.reader.novel.translation

internal object GeminiPrivateBridge {
    private const val BRIDGE_CLASS_NAME = "eu.kanade.tachiyomi.privatebridge.GeminiPrivateBridge"

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
        return (callOneArg("unlock", password, String::class.java) as? Boolean) == true
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
        return runCatching {
            target.javaClass.methods.firstOrNull {
                it.name == name &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].isAssignableFrom(parameterType)
            }?.invoke(target, arg)
        }.getOrNull()
    }
}
