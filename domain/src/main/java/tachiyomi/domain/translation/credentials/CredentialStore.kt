package tachiyomi.domain.translation.credentials

import java.io.Closeable

/** Device-local secret storage. No legacy preference migration or implicit deletion belongs here. */
interface CredentialStore {
    suspend fun put(id: String, secret: String)
    suspend fun read(id: String): CredentialRead
    suspend fun remove(id: String)
}

sealed interface CredentialRead {
    class Available(val secret: CredentialSecret) : CredentialRead {
        override fun toString(): String = "CredentialRead.Available([REDACTED])"
    }

    data object Missing : CredentialRead
    data object Unavailable : CredentialRead
    data object Corrupt : CredentialRead
}

/**
 * Never log or serialize this carrier or strings returned by [reveal]. Its owned mutable character
 * copy is erased on [close]; JVM Strings, caller copies and runtime/compiler copies cannot be
 * reliably zeroized. This is best-effort lifetime reduction, not a guarantee of plaintext erasure.
 */
class CredentialSecret(secret: String) : Closeable {
    @Transient
    private var characters: CharArray? = secret.toCharArray()

    @Synchronized
    fun reveal(): String = characters?.let { String(it) } ?: error("Credential secret has been released")

    @Synchronized
    override fun close() {
        characters?.fill(0.toChar())
        characters = null
    }

    override fun toString(): String = "CredentialSecret([REDACTED])"
}

enum class CredentialStoreFailure {
    UNAVAILABLE,
    INVALID_VALUE,
}

/** Deliberately carries no raw platform cause, identifier or secret-bearing exception message. */
class CredentialStoreException(val kind: CredentialStoreFailure) : IllegalStateException(
    when (kind) {
        CredentialStoreFailure.UNAVAILABLE -> "Credential storage is unavailable"
        CredentialStoreFailure.INVALID_VALUE -> "Credential value is invalid or exceeds the storage limit"
    },
)

/** Public protocol shared with profile references. IDs are canonical lowercase UUID strings. */
object CredentialIds {
    private val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun isValid(id: String): Boolean = pattern.matches(id)

    fun requireValid(id: String) {
        require(isValid(id)) { "Invalid credential identifier" }
    }
}
