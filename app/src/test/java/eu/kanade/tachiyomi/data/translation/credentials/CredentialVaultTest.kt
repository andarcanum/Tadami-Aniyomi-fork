package eu.kanade.tachiyomi.data.translation.credentials

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.credentials.CredentialIds
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.credentials.CredentialStoreException
import tachiyomi.domain.translation.credentials.CredentialStoreFailure
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.NotSerializableException
import java.io.ObjectOutputStream
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class CredentialVaultTest {

    @Test
    fun `credential references accept only canonical lowercase UUID strings`() {
        CredentialIds.isValid(ID_A) shouldBe true
        CredentialIds.isValid("123e4567-e89b-12d3-a456-426614174000") shouldBe true
        val rejected =
            listOf(
                "",
                "1-1-1-1-1",
                "123E4567-E89B-12D3-A456-426614174000",
                "../$ID_A",
                " $ID_A",
                "$ID_A ",
                "connection:$ID_A",
            )
        rejected.forEach { CredentialIds.isValid(it) shouldBe false }
    }

    @Test
    fun `secret carrier and available result never reveal contents through diagnostics or serialization`() {
        val secret = CredentialSecret("synthetic-value-not-a-real-key")
        secret.reveal() shouldBe "synthetic-value-not-a-real-key"
        secret.toString().contains("synthetic-value") shouldBe false
        CredentialRead.Available(secret).toString().contains("synthetic-value") shouldBe false
        shouldThrow<NotSerializableException> {
            ObjectOutputStream(ByteArrayOutputStream()).use { it.writeObject(secret) }
        }
        secret.close()
        shouldThrow<IllegalStateException> { secret.reveal() }.message shouldBe "Credential secret has been released"
        secret.toString().contains("synthetic-value") shouldBe false
    }

    @Test
    fun `real AES round trip uses a versioned encrypted envelope`() {
        val fixture = Fixture()
        val record = fixture.cipher.encrypt(ID_A, "synthetic-key-value")
        val text = record.toString(Charsets.US_ASCII)
        text.startsWith("tadami-credential:1:") shouldBe true
        text.contains("synthetic-key-value") shouldBe false
        fixture.cipher.decrypt(ID_A, record).revealed() shouldBe "synthetic-key-value"
    }

    @Test
    fun `each encryption produces a fresh 12 byte IV`() {
        val fixture = Fixture()
        val records = (1..8).map { fixture.cipher.encrypt(ID_A, "fixture") }
        val ivs = records.map { it.toString(Charsets.US_ASCII).split(':')[2] }
        ivs.distinct().size shouldBe 8
        ivs.forEach { Base64.getDecoder().decode(it).size shouldBe 12 }
    }

    @Test
    fun `swapped IDs and validly framed tampered ciphertext are corrupt`() {
        val fixture = Fixture()
        val record = fixture.cipher.encrypt(ID_A, "fixture")
        fixture.cipher.decrypt(ID_B, record) shouldBe CredentialRead.Corrupt
        val parts = record.toString(Charsets.US_ASCII).split(':').toMutableList()
        val bytes = Base64.getDecoder().decode(parts[3])
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        parts[3] = Base64.getEncoder().encodeToString(bytes)
        fixture.cipher.decrypt(ID_A, parts.joinToString(":").toByteArray(Charsets.US_ASCII)) shouldBe
            CredentialRead.Corrupt
    }

    @Test
    fun `malformed unsupported and unmarked records never fall back to plaintext`() {
        val fixture = Fixture()
        val record = fixture.cipher.encrypt(ID_A, "fixture")
        val parts = record.toString(Charsets.US_ASCII).split(':')
        val rejected = listOf(
            "fixture-plaintext",
            "enc:v1:fixture-plaintext",
            "tadami-credential:2:" + parts.drop(2).joinToString(":"),
            "tadami-credential:1:%%:%%",
            "tadami-credential:1:" + Base64.getEncoder().encodeToString(ByteArray(11)) + ":" + parts[3],
            "tadami-credential:1:" + parts[2] + ":" + Base64.getEncoder().encodeToString(ByteArray(15)),
            record.toString(Charsets.US_ASCII) + ":extra",
        )
        val reads = fixture.keys.existingCalls
        rejected.forEach {
            fixture.cipher.decrypt(ID_A, it.toByteArray(Charsets.US_ASCII)) shouldBe
                CredentialRead.Corrupt
        }
        fixture.keys.existingCalls shouldBe reads
    }

    @Test
    fun `missing decryption key is unavailable and never creates a replacement key`() {
        val fixture = Fixture()
        val record = fixture.cipher.encrypt(ID_A, "fixture")
        fixture.keys.key = null
        val creations = fixture.keys.createCalls
        fixture.cipher.decrypt(ID_A, record) shouldBe CredentialRead.Unavailable
        fixture.keys.createCalls shouldBe creations
        fixture.keys.key shouldBe null
    }

    @Test
    fun `wrong existing AES key returns corrupt without plaintext or raw cause`() {
        val fixture = Fixture()
        val record = fixture.cipher.encrypt(ID_A, "fixture")
        fixture.keys.key = newKey()
        fixture.cipher.decrypt(ID_A, record) shouldBe CredentialRead.Corrupt
    }

    @Test
    fun `put read and explicit remove acknowledge stored records only`() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.store.read(ID_A) shouldBe CredentialRead.Missing
        fixture.keys.existingCalls shouldBe 0
        fixture.store.put(ID_A, "first")
        fixture.store.put(ID_B, "second")
        fixture.store.read(ID_A).revealed() shouldBe "first"
        fixture.store.read(ID_B).revealed() shouldBe "second"
        fixture.store.remove(ID_A)
        fixture.store.read(ID_A) shouldBe CredentialRead.Missing
        fixture.store.read(ID_B).revealed() shouldBe "second"
        fixture.store.remove(ID_A)
    }

    @Test
    fun `opaque empty and unicode values round trip without value normalization`() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.store.put(ID_A, "")
        fixture.store.read(ID_A).revealed() shouldBe ""
        fixture.store.put(ID_A, "fixture-ключ-🔑")
        fixture.store.read(ID_A).revealed() shouldBe "fixture-ключ-🔑"
    }

    @Test
    fun `failed put preserves the old record and does not acknowledge success`() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.store.put(ID_A, "old-value")
        val old = fixture.records.values[ID_A]!!.copyOf()
        fixture.records.writeFailure = IOException("synthetic-secret-bearing-platform-message")
        var error: CredentialStoreException? = null
        try {
            fixture.store.put(ID_A, "new-value")
        } catch (failure: CredentialStoreException) {
            error = failure
        }
        error.shouldBeInstanceOf<CredentialStoreException>().kind shouldBe CredentialStoreFailure.UNAVAILABLE
        error?.cause shouldBe null
        error?.message?.contains("synthetic-secret") shouldBe false
        fixture.records.values[ID_A]!!.contentEquals(old) shouldBe true
        fixture.records.writeFailure = null
        fixture.store.read(ID_A).revealed() shouldBe "old-value"
    }

    @Test
    fun `unavailable key or filesystem gives typed safe failure without writes or raw exception text`() =
        runBlocking<Unit> {
            val fixture = Fixture()
            fixture.keys.failure = IOException("synthetic-sensitive-key-loader-message")
            var error: CredentialStoreException? = null
            try {
                fixture.store.put(ID_A, "fixture")
            } catch (failure: CredentialStoreException) {
                error = failure
            }
            error.shouldBeInstanceOf<CredentialStoreException>().kind shouldBe CredentialStoreFailure.UNAVAILABLE
            error?.message?.contains("synthetic-sensitive") shouldBe false
            error?.cause shouldBe null
            fixture.records.values.isEmpty() shouldBe true
            fixture.keys.failure = null
            fixture.store.put(ID_A, "fixture")
            fixture.records.readFailure = IOException("synthetic-sensitive-file-message")
            fixture.store.read(ID_A) shouldBe CredentialRead.Unavailable
        }

    @Test
    fun `bounded secret validation happens before key creation and record writes`() = runBlocking<Unit> {
        val fixture = Fixture()
        val before = fixture.keys.createCalls
        var failure: CredentialStoreException? = null
        try {
            fixture.store.put(ID_A, "x".repeat(16_385))
        } catch (error: CredentialStoreException) {
            failure = error
        }
        failure.shouldBeInstanceOf<CredentialStoreException>().kind shouldBe CredentialStoreFailure.INVALID_VALUE
        fixture.keys.createCalls shouldBe before
        fixture.records.values.isEmpty() shouldBe true
        fixture.store.put(ID_A, "x".repeat(16_384))
        fixture.store.read(ID_A).revealed().length shouldBe 16_384
    }

    @Test
    fun `UTF8 byte limits and malformed unicode are rejected without changing the stored credential`() =
        runBlocking<Unit> {
            val fixture = Fixture()
            fixture.store.put(ID_A, "old")
            for (value in listOf("界".repeat(6_000), String(charArrayOf(0xD800.toChar())))) {
                var failure: CredentialStoreException? = null
                try {
                    fixture.store.put(ID_A, value)
                } catch (error: CredentialStoreException) {
                    failure = error
                }
                failure.shouldBeInstanceOf<CredentialStoreException>().kind shouldBe
                    CredentialStoreFailure.INVALID_VALUE
                fixture.store.read(ID_A).revealed() shouldBe "old"
            }
        }

    @Test
    fun `oversized stored record is corrupt before loading any key`() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.records.values[ID_A] = ByteArray(32_769) { 65 }
        fixture.store.read(ID_A) shouldBe CredentialRead.Corrupt
        fixture.keys.existingCalls shouldBe 0
        fixture.keys.createCalls shouldBe 0
    }

    @Test
    fun `invalid credential identifiers never reach storage or keyloader`() = runBlocking<Unit> {
        val fixture = Fixture()
        for (invalid in listOf("../escape", "123E4567-E89B-12D3-A456-426614174000", " bad ")) {
            var error: IllegalArgumentException? = null
            try {
                fixture.store.put(invalid, "fixture")
            } catch (failure: IllegalArgumentException) {
                error = failure
            }
            error.shouldBeInstanceOf<IllegalArgumentException>().message shouldBe "Invalid credential identifier"
        }
        fixture.records.values.isEmpty() shouldBe true
        fixture.keys.createCalls shouldBe 0
    }

    private class Fixture {
        val keys = Keys()
        val cipher = AesGcmCredentialCipher(keys)
        val records = Records()
        val store = EncryptedCredentialStore(cipher, records)
    }

    private class Keys : CredentialKeyLoader {
        var key: SecretKey? = newKey()
        var createCalls = 0
        var existingCalls = 0
        var failure: Exception? = null
        override fun getOrCreateKey(): SecretKey {
            createCalls++
            failure?.let { throw it }
            return key ?: newKey().also { key = it }
        }
        override fun getExistingKey(): SecretKey? {
            existingCalls++
            failure?.let { throw it }
            return key
        }
    }

    private class Records : CredentialRecordStore {
        val values = mutableMapOf<String, ByteArray>()
        var writeFailure: Exception? = null
        var readFailure: Exception? = null
        override fun read(id: String): ByteArray? {
            readFailure?.let { throw it }
            return values[id]?.copyOf()
        }
        override fun writeAtomic(id: String, record: ByteArray) {
            writeFailure?.let { throw it }
            values[id] =
                record.copyOf()
        }
        override fun remove(id: String) {
            values.remove(id)
        }
    }

    private fun CredentialRead.revealed(): String = shouldBeInstanceOf<CredentialRead.Available>().secret.use {
        it.reveal()
    }

    companion object {
        private const val ID_A = "00000000-0000-0000-0000-000000000001"
        private const val ID_B = "00000000-0000-0000-0000-000000000002"
        private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}
