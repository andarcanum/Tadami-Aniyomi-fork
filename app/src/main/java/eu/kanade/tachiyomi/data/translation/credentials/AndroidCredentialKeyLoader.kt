package eu.kanade.tachiyomi.data.translation.credentials

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import tachiyomi.domain.translation.credentials.CredentialStoreException
import tachiyomi.domain.translation.credentials.CredentialStoreFailure
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/** Dedicated translation key. Device/Keystore behavior requires Android integration verification. */
internal class AndroidCredentialKeyLoader : CredentialKeyLoader {
    override fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        val store = openStore()
        existing(store)?.let { return@synchronized it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        generator.generateKey().also { validate(it) }
    }

    override fun getExistingKey(): SecretKey? = synchronized(keyLock) {
        existing(openStore())
    }

    private fun openStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun existing(store: KeyStore): SecretKey? {
        if (!store.containsAlias(ALIAS)) return null
        val key = store.getKey(ALIAS, null) as? SecretKey ?: unavailable()
        validate(key)
        return key
    }

    private fun validate(key: SecretKey) {
        if (key.algorithm != KeyProperties.KEY_ALGORITHM_AES) unavailable()
        val info = SecretKeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (info.keySize != 256 || info.isUserAuthenticationRequired) unavailable()
    }

    private fun unavailable(): Nothing = throw CredentialStoreException(CredentialStoreFailure.UNAVAILABLE)

    private companion object {
        const val ALIAS = "tadami_translation_credentials_v1"
        const val KEYSTORE = "AndroidKeyStore"
        val keyLock = Any()
    }
}
