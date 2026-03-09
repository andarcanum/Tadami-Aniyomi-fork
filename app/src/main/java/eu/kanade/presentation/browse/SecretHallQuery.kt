package eu.kanade.presentation.browse

import cafe.adriel.voyager.navigator.Navigator

private const val SECRET_HALL_GATE_CLASS_NAME = "eu.kanade.presentation.browse.local.SecretHallGateImpl"

internal interface SecretHallGate {
    fun isSecretHallQuery(query: String?): Boolean
    fun openSecretHallIfNeeded(navigator: Navigator, query: String?): Boolean
}

private object SecretHallGateFallback : SecretHallGate {
    override fun isSecretHallQuery(query: String?): Boolean = false

    override fun openSecretHallIfNeeded(navigator: Navigator, query: String?): Boolean = false
}

internal fun createSecretHallGate(className: String): SecretHallGate {
    return runCatching {
        val gateClass = Class.forName(className)
        val instance = runCatching { gateClass.getField("INSTANCE").get(null) }
            .getOrElse { gateClass.getDeclaredConstructor().newInstance() }
        instance as SecretHallGate
    }.getOrDefault(SecretHallGateFallback)
}

private val secretHallGate: SecretHallGate by lazy {
    createSecretHallGate(SECRET_HALL_GATE_CLASS_NAME)
}

fun isSecretHallQuery(query: String?): Boolean = secretHallGate.isSecretHallQuery(query)

fun openSecretHallIfNeeded(navigator: Navigator, query: String?): Boolean {
    return secretHallGate.openSecretHallIfNeeded(navigator, query)
}
