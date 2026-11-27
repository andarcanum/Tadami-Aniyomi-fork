package eu.kanade.tachiyomi.data.translation.credentials

import tachiyomi.domain.translation.credentials.CredentialIds
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.credentials.CredentialStoreException
import tachiyomi.domain.translation.credentials.CredentialStoreFailure
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Pure JVM-testable cipher. Platform key access is the only external cryptographic dependency. */
internal class AesGcmCredentialCipher(private val keys: CredentialKeyLoader) : CredentialCipher {
    override fun encrypt(id: String, secret: String): ByteArray {
        CredentialIds.requireValid(id)
        val plaintext = encodeSecret(secret)
        try {
            val key = try {
                keys.getOrCreateKey()
            } catch (_: Exception) {
                unavailable()
            }
            validateKey(key)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // Keystore requires provider-generated randomized IVs. Never supply a caller IV here.
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val parameters = cipher.parameters.getParameterSpec(GCMParameterSpec::class.java)
            if (iv.size != IV_BYTES || parameters.tLen != TAG_BITS) unavailable()
            cipher.updateAAD(aad(id))
            val encrypted = cipher.doFinal(plaintext)
            return listOf(
                PREFIX,
                VERSION,
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(encrypted),
            )
                .joinToString(":").toByteArray(Charsets.US_ASCII)
        } catch (error: CredentialStoreException) {
            throw error
        } catch (_: Exception) {
            unavailable()
        } finally {
            plaintext.fill(0)
        }
    }

    override fun decrypt(id: String, record: ByteArray): CredentialRead {
        CredentialIds.requireValid(id)
        val envelope = try {
            decodeEnvelope(record)
        } catch (
            _: CorruptCredentialRecord,
        ) {
            return CredentialRead.Corrupt
        }
        val key = try {
            keys.getExistingKey()
        } catch (_: Exception) {
            return CredentialRead.Unavailable
        }
            ?: return CredentialRead.Unavailable
        return try {
            validateKey(key)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, envelope.iv))
            cipher.updateAAD(aad(id))
            val plaintext = cipher.doFinal(envelope.ciphertext)
            try {
                val characters = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(plaintext))
                try {
                    CredentialRead.Available(CredentialSecret(characters.toString()))
                } finally {
                    if (characters.hasArray()) characters.array().fill(0.toChar())
                }
            } finally {
                plaintext.fill(0)
            }
        } catch (_: AEADBadTagException) {
            CredentialRead.Corrupt
        } catch (_: BadPaddingException) {
            CredentialRead.Corrupt
        } catch (_: CharacterCodingException) {
            CredentialRead.Corrupt
        } catch (_: Exception) {
            CredentialRead.Unavailable
        }
    }

    private fun encodeSecret(secret: String): ByteArray {
        if (secret.length > MAX_SECRET_BYTES) invalidValue()
        val bytes = try {
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(secret))
        } catch (_: CharacterCodingException) {
            invalidValue()
        }
        try {
            if (bytes.remaining() > MAX_SECRET_BYTES) invalidValue()
            return ByteArray(bytes.remaining()).also { bytes.get(it) }
        } finally {
            if (bytes.hasArray()) bytes.array().fill(0)
        }
    }

    private fun decodeEnvelope(record: ByteArray): Envelope {
        if (record.isEmpty() || record.size > MAX_RECORD_BYTES ||
            record.any { it.toInt() !in 33..126 }
        ) {
            throw CorruptCredentialRecord()
        }
        val parts = record.toString(Charsets.US_ASCII).split(':', limit = 5)
        if (parts.size != 4 || parts[0] != PREFIX || parts[1] != VERSION) throw CorruptCredentialRecord()
        val iv = decodeBase64(parts[2])
        val ciphertext = decodeBase64(parts[3])
        if (iv.size != IV_BYTES ||
            ciphertext.size !in TAG_BYTES..(MAX_SECRET_BYTES + TAG_BYTES)
        ) {
            throw CorruptCredentialRecord()
        }
        return Envelope(iv, ciphertext)
    }

    private fun decodeBase64(encoded: String): ByteArray {
        val decoded = try {
            Base64.getDecoder().decode(encoded)
        } catch (
            _: IllegalArgumentException,
        ) {
            throw CorruptCredentialRecord()
        }
        if (Base64.getEncoder().encodeToString(decoded) != encoded) throw CorruptCredentialRecord()
        return decoded
    }

    private fun validateKey(key: SecretKey) {
        if (!key.algorithm.equals("AES", ignoreCase = true)) unavailable()
        // Hardware/Keystore keys are not exportable; the platform loader checks their KeyInfo.
        // Do not mutate a key provider's encoding array: this cipher does not own that buffer.
        if (key.encoded?.size?.let { it != 32 } == true) unavailable()
    }

    private fun aad(id: String): ByteArray = "$PREFIX:$VERSION:$id".toByteArray(Charsets.UTF_8)
    private fun unavailable(): Nothing = throw CredentialStoreException(CredentialStoreFailure.UNAVAILABLE)
    private fun invalidValue(): Nothing = throw CredentialStoreException(CredentialStoreFailure.INVALID_VALUE)
    private class Envelope(val iv: ByteArray, val ciphertext: ByteArray)

    companion object {
        const val MAX_SECRET_BYTES = 16_384
        const val MAX_RECORD_BYTES = 32_768
        private const val PREFIX = "tadami-credential"
        private const val VERSION = "1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = 16
    }
}
