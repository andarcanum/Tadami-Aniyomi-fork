package eu.kanade.tachiyomi.data.translation.credentials

import tachiyomi.domain.translation.credentials.CredentialRead
import javax.crypto.SecretKey

internal interface CredentialKeyLoader {
    fun getOrCreateKey(): SecretKey
    fun getExistingKey(): SecretKey?
}

internal interface CredentialCipher {
    fun encrypt(id: String, secret: String): ByteArray
    fun decrypt(id: String, record: ByteArray): CredentialRead
}
