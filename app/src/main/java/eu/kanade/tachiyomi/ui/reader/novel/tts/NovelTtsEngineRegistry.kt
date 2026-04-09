package eu.kanade.tachiyomi.ui.reader.novel.tts

interface NovelTtsEngineInfoSource {
    fun listInstalledEngines(): List<NovelTtsInstalledEngine>

    fun defaultEnginePackage(): String?
}

class NovelTtsEngineRegistry(
    private val infoSource: NovelTtsEngineInfoSource,
) {
    fun listEngines(): List<NovelTtsEngineDescriptor> {
        val defaultPackage = infoSource.defaultEnginePackage()
        return infoSource.listInstalledEngines().map { engine ->
            NovelTtsEngineDescriptor(
                packageName = engine.packageName,
                label = engine.label,
                isSystemDefault = engine.packageName == defaultPackage,
            )
        }
    }

    fun resolvePreferredEngine(selectedPackage: String?): NovelTtsEngineDescriptor? {
        val engines = listEngines()
        return when {
            selectedPackage.isNullOrBlank() -> {
                engines.firstOrNull { it.isSystemDefault } ?: engines.firstOrNull()
            }
            else -> {
                engines.firstOrNull { it.packageName == selectedPackage }
                    ?: engines.firstOrNull { it.isSystemDefault }
                    ?: engines.firstOrNull()
            }
        }
    }
}
