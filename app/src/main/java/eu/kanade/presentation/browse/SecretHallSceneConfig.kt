package eu.kanade.presentation.browse

import android.content.Context

internal data class SecretHallSceneConfig(
    val content: SecretHallSceneContent = SecretHallSceneContent(),
    val visuals: SecretHallSceneVisuals = SecretHallSceneVisuals(),
    val timing: SecretHallTimingConfig = SecretHallTimingConfig(),
    val names: List<String> = emptyList(),
    val isEnabled: Boolean = false,
    val isStub: Boolean = true,
)

internal data class SecretHallSceneContent(
    val systemLabel: String = "Unavailable",
    val title: String = secretHallPublicStubTitle(),
    val subtitle: String = secretHallPublicStubMessage(),
    val runeDescription: String = secretHallPublicStubMessage(),
    val rosterTitle: String = secretHallPublicStubTitle(),
    val rosterSubtitle: String = secretHallPublicStubMessage(),
    val rosterOpenDescription: String = secretHallPublicStubMessage(),
    val rosterCloseDescription: String = secretHallPublicStubMessage(),
)

internal data class SecretHallSceneVisuals(
    val backgroundColor: String = "#000000",
    val eclipseCoreColor: String = "#000000",
    val eclipseGlowColor: String = "#000000",
    val nameColor: String = "#FFFFFF",
    val ashColor: String = "#FFFFFF",
)

internal data class SecretHallTimingConfig(
    val emergeMs: Long = 0L,
    val holdMs: Long = 0L,
    val burnMs: Long = 0L,
    val ashMs: Long = 0L,
    val betweenNamesMs: Long = 0L,
) {
    val totalCycleDurationMs: Long
        get() = 0L
}

internal fun parseSecretHallSceneConfig(json: String): SecretHallSceneConfig {
    return secretHallSceneFallback()
}

internal fun loadSecretHallSceneConfig(context: Context): SecretHallSceneConfig {
    return secretHallSceneFallback()
}

internal fun secretHallSceneFallback(): SecretHallSceneConfig {
    return SecretHallSceneConfig()
}

internal data class SecretHallOrbitalSpec(
    val launchIndex: Int,
    val baseAngleDegrees: Float,
    val radiusFactor: Float,
)

internal fun buildSecretHallOrbitalSpecs(nameCount: Int): List<SecretHallOrbitalSpec> {
    return emptyList()
}

internal fun secretHallVisibleOrbitRadiusFactors(
    orbitalSpecs: List<SecretHallOrbitalSpec>,
    dedupeThreshold: Float = 0.03f,
): List<Float> {
    return emptyList()
}

internal fun secretHallShouldRenderActiveElectron(phase: SecretHallNamePhase): Boolean {
    return false
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
    fun phaseAt(elapsedMs: Long): SecretHallNamePhase = SecretHallNamePhase.BetweenNames

    fun nameIndexAt(elapsedMs: Long, nameCount: Int): Int = 0

    fun launchedNameCountAt(elapsedMs: Long, nameCount: Int): Int = 0
}
