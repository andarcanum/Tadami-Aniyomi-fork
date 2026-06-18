package eu.kanade.presentation.browse.local

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val SECRET_HALL_SCENE_ASSET_PATH = "local/secret_hall_scene.json"
private val SECRET_HALL_SCENE_JSON = Json { ignoreUnknownKeys = true }

@Serializable
internal data class SecretHallSceneConfig(
    val content: SecretHallSceneContent,
    val visuals: SecretHallSceneVisuals,
    val timing: SecretHallTimingConfig,
    val names: List<String>,
)

@Serializable
internal data class SecretHallSceneContent(
    val systemLabel: String,
    val title: String,
    val subtitle: String,
    val runeDescription: String,
    val rosterTitle: String = "Все участники Tadami",
    val rosterSubtitle: String = "Летопись тех, кто оставил след.",
    val rosterOpenDescription: String = "Открыть список всех имен.",
    val rosterCloseDescription: String = "Закрыть список всех имен.",
    val systemLabelEn: String? = null,
    val titleEn: String? = null,
    val subtitleEn: String? = null,
    val runeDescriptionEn: String? = null,
    val rosterTitleEn: String? = null,
    val rosterSubtitleEn: String? = null,
    val rosterOpenDescriptionEn: String? = null,
    val rosterCloseDescriptionEn: String? = null,
)

internal data class SecretHallLocalizedContent(
    val systemLabel: String,
    val title: String,
    val subtitle: String,
    val runeDescription: String,
    val rosterTitle: String,
    val rosterSubtitle: String,
    val rosterOpenDescription: String,
    val rosterCloseDescription: String,
)

internal fun SecretHallSceneContent.localizedForLanguage(languageCode: String): SecretHallLocalizedContent {
    val normalizedLanguage = languageCode.trim().lowercase()
    val useEnglish = normalizedLanguage == "en"
    return SecretHallLocalizedContent(
        systemLabel = localizedValue(base = systemLabel, english = systemLabelEn, useEnglish = useEnglish),
        title = localizedValue(base = title, english = titleEn, useEnglish = useEnglish),
        subtitle = localizedValue(base = subtitle, english = subtitleEn, useEnglish = useEnglish),
        runeDescription = localizedValue(base = runeDescription, english = runeDescriptionEn, useEnglish = useEnglish),
        rosterTitle = localizedValue(base = rosterTitle, english = rosterTitleEn, useEnglish = useEnglish),
        rosterSubtitle = localizedValue(base = rosterSubtitle, english = rosterSubtitleEn, useEnglish = useEnglish),
        rosterOpenDescription = localizedValue(
            base = rosterOpenDescription,
            english = rosterOpenDescriptionEn,
            useEnglish = useEnglish,
        ),
        rosterCloseDescription = localizedValue(
            base = rosterCloseDescription,
            english = rosterCloseDescriptionEn,
            useEnglish = useEnglish,
        ),
    )
}

private fun localizedValue(base: String, english: String?, useEnglish: Boolean): String {
    return if (useEnglish && !english.isNullOrBlank()) {
        english
    } else {
        base
    }
}

@Serializable
internal data class SecretHallSceneVisuals(
    val backgroundColor: String,
    val eclipseCoreColor: String,
    val eclipseGlowColor: String,
    val nameColor: String,
    val ashColor: String,
)

@Serializable
internal data class SecretHallTimingConfig(
    val emergeMs: Long,
    val holdMs: Long,
    val burnMs: Long,
    val ashMs: Long,
    val betweenNamesMs: Long,
) {
    val totalCycleDurationMs: Long
        get() = emergeMs + holdMs + burnMs + ashMs + betweenNamesMs
}

internal fun parseSecretHallSceneConfig(json: String): SecretHallSceneConfig {
    return SECRET_HALL_SCENE_JSON.decodeFromString(json)
}

internal fun loadSecretHallSceneConfig(context: Context): SecretHallSceneConfig {
    return runCatching {
        context.assets.open(SECRET_HALL_SCENE_ASSET_PATH).bufferedReader().use { reader ->
            parseSecretHallSceneConfig(reader.readText())
        }
    }.getOrElse { secretHallSceneFallback() }
}

internal fun secretHallSceneFallback(): SecretHallSceneConfig {
    return SecretHallSceneConfig(
        content = SecretHallSceneContent(
            systemLabel = "СИСТЕМА РАЗБЛОКИРОВАНА",
            title = "Зал славы Tadami",
            subtitle =
            "К входу допущены только те, кто выдержал сигнал до конца.",
            runeDescription = "Вернуться в обычный мир.",
            rosterTitle = "Все участники Tadami",
            rosterSubtitle = "Летопись тех, кто оставил след.",
            rosterOpenDescription = "Открыть список всех имен.",
            rosterCloseDescription = "Закрыть список всех имен.",
            systemLabelEn = "SYSTEM UNLOCKED",
            titleEn = "Tadami Hall of Fame",
            subtitleEn = "Only those who endured the signal to the end may enter.",
            runeDescriptionEn = "Return to the ordinary world.",
            rosterTitleEn = "All Tadami Participants",
            rosterSubtitleEn = "A chronicle of those who left a mark.",
            rosterOpenDescriptionEn = "Open the full list of names.",
            rosterCloseDescriptionEn = "Close the full list of names.",
        ),
        visuals = SecretHallSceneVisuals(
            backgroundColor = "#000000",
            eclipseCoreColor = "#120000",
            eclipseGlowColor = "#FFD700",
            nameColor = "#FFE7A8",
            ashColor = "#D7B15A",
        ),
        timing = SecretHallTimingConfig(
            emergeMs = 1_400L,
            holdMs = 1_100L,
            burnMs = 1_700L,
            ashMs = 900L,
            betweenNamesMs = 260L,
        ),
        names = listOf(
            "andarcanum",
            "aniyomiorg",
            "mihonapp",
            "tachiyomi legacy",
            "ranobe pioneers",
            "aurora polish crew",
            "extension maintainers",
            "translation contributors",
            "bug hunters",
            "late-night readers",
            "AnimeDroid_Chan",
            "BlackMods",
        ),
    )
}

internal data class SecretHallOrbitalSpec(
    val launchIndex: Int,
    val baseAngleDegrees: Float,
    val radiusFactor: Float,
)

internal fun buildSecretHallOrbitalSpecs(nameCount: Int): List<SecretHallOrbitalSpec> {
    if (nameCount <= 0) return emptyList()

    val stepDegrees = 360f / nameCount.toFloat()
    val radiusPattern = listOf(0.82f, 1.08f, 0.9f, 1.16f, 0.96f, 1.04f, 0.86f, 1.12f)
    return List(nameCount) { index ->
        val cycleIndex = index % radiusPattern.size
        val cycleOffset = (index / radiusPattern.size) * 0.02f
        SecretHallOrbitalSpec(
            launchIndex = index,
            baseAngleDegrees = stepDegrees * index,
            radiusFactor = radiusPattern[cycleIndex] + cycleOffset,
        )
    }
}

internal fun secretHallVisibleOrbitRadiusFactors(
    orbitalSpecs: List<SecretHallOrbitalSpec>,
    dedupeThreshold: Float = 0.03f,
): List<Float> {
    return orbitalSpecs
        .map { it.radiusFactor }
        .sorted()
        .fold(mutableListOf<Float>()) { acc, value ->
            if (acc.isEmpty() || kotlin.math.abs(acc.last() - value) >= dedupeThreshold) {
                acc += value
            }
            acc
        }
}

internal fun secretHallShouldRenderActiveElectron(phase: SecretHallNamePhase): Boolean {
    return phase == SecretHallNamePhase.Emerge
}

internal enum class SecretHallNamePhase {
    Emerge,
    Hold,
    Burn,
    Ash,
    BetweenNames,
}

internal class SecretHallNameCycle(
    private val timing: SecretHallTimingConfig,
) {
    fun phaseAt(elapsedMs: Long): SecretHallNamePhase {
        val normalizedMs = normalizedElapsedMs(elapsedMs)
        val emergeEnd = timing.emergeMs
        val holdEnd = emergeEnd + timing.holdMs
        val burnEnd = holdEnd + timing.burnMs
        val ashEnd = burnEnd + timing.ashMs

        return when {
            normalizedMs < emergeEnd -> SecretHallNamePhase.Emerge
            normalizedMs < holdEnd -> SecretHallNamePhase.Hold
            normalizedMs < burnEnd -> SecretHallNamePhase.Burn
            normalizedMs < ashEnd -> SecretHallNamePhase.Ash
            else -> SecretHallNamePhase.BetweenNames
        }
    }

    fun phaseProgressAt(elapsedMs: Long): Float {
        val normalizedMs = normalizedElapsedMs(elapsedMs)
        val phase = phaseAt(elapsedMs)
        val segmentStart = when (phase) {
            SecretHallNamePhase.Emerge -> 0L
            SecretHallNamePhase.Hold -> timing.emergeMs
            SecretHallNamePhase.Burn -> timing.emergeMs + timing.holdMs
            SecretHallNamePhase.Ash -> timing.emergeMs + timing.holdMs + timing.burnMs
            SecretHallNamePhase.BetweenNames -> timing.emergeMs + timing.holdMs + timing.burnMs + timing.ashMs
        }
        val segmentDuration = when (phase) {
            SecretHallNamePhase.Emerge -> timing.emergeMs
            SecretHallNamePhase.Hold -> timing.holdMs
            SecretHallNamePhase.Burn -> timing.burnMs
            SecretHallNamePhase.Ash -> timing.ashMs
            SecretHallNamePhase.BetweenNames -> timing.betweenNamesMs
        }.coerceAtLeast(1L)

        return ((normalizedMs - segmentStart).toFloat() / segmentDuration.toFloat()).coerceIn(0f, 1f)
    }

    fun nameIndexAt(elapsedMs: Long, nameCount: Int): Int {
        if (nameCount <= 0) return 0
        val cycleDuration = timing.totalCycleDurationMs.coerceAtLeast(1L)
        return (((elapsedMs.coerceAtLeast(0L)) / cycleDuration) % nameCount).toInt()
    }

    fun launchedNameCountAt(elapsedMs: Long, nameCount: Int): Int {
        if (nameCount <= 0) return 0
        val cycleDuration = timing.totalCycleDurationMs.coerceAtLeast(1L)
        val launched = ((elapsedMs.coerceAtLeast(0L)) / cycleDuration).toInt() + 1
        return launched.coerceIn(0, nameCount)
    }

    private fun normalizedElapsedMs(elapsedMs: Long): Long {
        val cycleDuration = timing.totalCycleDurationMs.coerceAtLeast(1L)
        return elapsedMs.coerceAtLeast(0L) % cycleDuration
    }
}
