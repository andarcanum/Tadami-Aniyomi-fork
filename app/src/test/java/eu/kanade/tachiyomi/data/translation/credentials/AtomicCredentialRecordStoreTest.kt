package eu.kanade.tachiyomi.data.translation.credentials

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialStoreException
import tachiyomi.domain.translation.credentials.CredentialStoreFailure
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AtomicCredentialRecordStoreTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun `atomic put stores only encrypted records under opaque credential IDs`() = runBlocking<Unit> {
        val root = temporary.resolve("vault").toFile()
        val cipher = cipher()
        val records = FileCredentialRecordStore(root)
        val store = EncryptedCredentialStore(cipher, records)
        store.put(ID_A, "synthetic-provider-value")
        store.read(ID_A).revealed() shouldBe "synthetic-provider-value"
        root.listFiles()!!.map { it.name } shouldBe listOf("$ID_A.credential")
        File(
            root,
            "$ID_A.credential",
        ).readBytes().toString(Charsets.US_ASCII).contains("synthetic-provider-value") shouldBe
            false
    }

    @Test
    fun `partial temporary write failure preserves the old decryptable record`() = runBlocking<Unit> {
        val root = temporary.resolve("vault").toFile()
        val cipher = cipher()
        val original = EncryptedCredentialStore(cipher, FileCredentialRecordStore(root))
        original.put(ID_A, "old-value")
        val before = File(root, "$ID_A.credential").readBytes()
        val failing = EncryptedCredentialStore(
            cipher,
            FileCredentialRecordStore(root, writeAndSync = { file, bytes ->
                FileOutputStream(file).use { output ->
                    output.write(bytes, 0, 1)
                    throw IOException("synthetic-sensitive-platform-message")
                }
            }),
        )
        var failure: CredentialStoreException? = null
        try {
            failing.put(ID_A, "new-value")
        } catch (error: CredentialStoreException) {
            failure = error
        }
        failure.shouldBeInstanceOf<CredentialStoreException>().kind shouldBe CredentialStoreFailure.UNAVAILABLE
        failure?.cause shouldBe null
        failure?.message?.contains("synthetic-sensitive") shouldBe false
        File(root, "$ID_A.credential").readBytes().contentEquals(before) shouldBe true
        original.read(ID_A).revealed() shouldBe "old-value"
        root.listFiles()!!.map { it.name } shouldBe listOf("$ID_A.credential")
    }

    @Test
    fun `atomic replacement failure preserves the old decryptable record`() = runBlocking<Unit> {
        val root = temporary.resolve("vault").toFile()
        val cipher = cipher()
        val original = EncryptedCredentialStore(cipher, FileCredentialRecordStore(root))
        original.put(ID_A, "old-value")
        val failing = EncryptedCredentialStore(
            cipher,
            FileCredentialRecordStore(root, replaceAtomic = { _, _ ->
                throw IOException("fixture-replacement-failure")
            }),
        )
        var failure: CredentialStoreException? = null
        try {
            failing.put(ID_A, "new-value")
        } catch (error: CredentialStoreException) {
            failure = error
        }
        failure.shouldBeInstanceOf<CredentialStoreException>().kind shouldBe CredentialStoreFailure.UNAVAILABLE
        original.read(ID_A).revealed() shouldBe "old-value"
    }

    @Test
    fun `read and remove of a missing record do not create the vault directory`() = runBlocking<Unit> {
        val root = temporary.resolve("absent").toFile()
        val store = EncryptedCredentialStore(cipher(), FileCredentialRecordStore(root))
        store.read(ID_A) shouldBe CredentialRead.Missing
        store.remove(ID_A)
        root.exists() shouldBe false
    }

    @Test
    fun `explicit remove affects only the requested credential`() = runBlocking<Unit> {
        val root = temporary.resolve("vault").toFile()
        val store = EncryptedCredentialStore(cipher(), FileCredentialRecordStore(root))
        store.put(ID_A, "first")
        store.put(ID_B, "second")
        store.remove(ID_A)
        store.read(ID_A) shouldBe CredentialRead.Missing
        store.read(ID_B).revealed() shouldBe "second"
    }

    @Test
    fun `oversized file fails closed before any decryption key lookup`() = runBlocking<Unit> {
        val root = temporary.resolve("vault").toFile().apply { mkdirs() }
        File(root, "$ID_A.credential").writeBytes(ByteArray(32_769) { 65 })
        var keyReads = 0
        val keys = object : CredentialKeyLoader {
            override fun getOrCreateKey(): SecretKey = error("Must not create a key")
            override fun getExistingKey(): SecretKey? {
                keyReads++
                return null
            }
        }
        val store = EncryptedCredentialStore(AesGcmCredentialCipher(keys), FileCredentialRecordStore(root))
        store.read(ID_A) shouldBe CredentialRead.Corrupt
        keyReads shouldBe 0
    }

    @Test
    fun `readers see the old record until synced temporary data is atomically replaced`() = runBlocking<Unit> {
        val root = temporary.resolve("vault").toFile()
        val cipher = cipher()
        val original = EncryptedCredentialStore(cipher, FileCredentialRecordStore(root))
        original.put(ID_A, "old-value")
        val prepared = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val pending = EncryptedCredentialStore(
            cipher,
            FileCredentialRecordStore(root, writeAndSync = { file, bytes ->
                FileOutputStream(file).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                prepared.complete(Unit)
                release.await()
            }),
        )
        val write = async { pending.put(ID_A, "new-value") }
        try {
            withTimeout(5_000) { prepared.await() }
            original.read(ID_A).revealed() shouldBe "old-value"
            release.countDown()
            write.await()
            original.read(ID_A).revealed() shouldBe "new-value"
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `concurrent first writes into non-existent vault directory succeed reliably`() = runBlocking<Unit> {
        val root = temporary.resolve("fresh-concurrent-vault").toFile()
        val records = FileCredentialRecordStore(root)
        val barrier = CountDownLatch(1)
        val writers = (1..8).map { index ->
            async(Dispatchers.IO) {
                val id = "00000000-0000-0000-0000-00000000000$index"
                val payload = "record-$index".toByteArray()
                barrier.await()
                records.writeAtomic(id, payload)
                records.read(id)?.decodeToString()
            }
        }
        barrier.countDown()
        val results = writers.awaitAll()
        results shouldBe (1..8).map { "record-$it" }
    }

    private fun cipher(): AesGcmCredentialCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return AesGcmCredentialCipher(object : CredentialKeyLoader {
            override fun getOrCreateKey(): SecretKey = key
            override fun getExistingKey(): SecretKey = key
        })
    }

    private fun CredentialRead.revealed(): String = shouldBeInstanceOf<CredentialRead.Available>().secret.use {
        it.reveal()
    }

    private companion object {
        const val ID_A = "00000000-0000-0000-0000-000000000001"
        const val ID_B = "00000000-0000-0000-0000-000000000002"
    }
}
