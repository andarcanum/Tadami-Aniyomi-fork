package eu.kanade.tachiyomi.data.translation.credentials

import tachiyomi.domain.translation.credentials.CredentialIds
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption

/** Atomic encrypted-record replacement in one private directory. No plaintext or history cleanup. */
internal class FileCredentialRecordStore(
    directory: File,
    private val writeAndSync: (File, ByteArray) -> Unit = { file, record ->
        FileOutputStream(file).use { output ->
            output.write(record)
            output.fd.sync()
        }
    },
    private val replaceAtomic: (File, File) -> Unit = { pending, target ->
        Files.move(
            pending.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        Unit
    },
) : CredentialRecordStore {
    private val directory = directory.absoluteFile.normalize()

    override fun read(id: String): ByteArray? {
        val target = recordFile(id)
        return try {
            Files.newInputStream(target.toPath()).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > AesGcmCredentialCipher.MAX_RECORD_BYTES) throw CorruptCredentialRecord()
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } catch (_: NoSuchFileException) {
            null
        }
    }

    override fun writeAtomic(id: String, record: ByteArray) {
        if (record.size > AesGcmCredentialCipher.MAX_RECORD_BYTES) throw CorruptCredentialRecord()
        val target = recordFile(id)
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Credential directory is unavailable")
        }
        val pending = Files.createTempFile(directory.toPath(), "pending-", ".tmp").toFile()
        try {
            writeAndSync(pending, record)
            // Never degrade to a non-atomic overwrite if the filesystem refuses atomic replacement.
            replaceAtomic(pending, target)
        } finally {
            // Only this operation's owned temporary ciphertext is removed, never directory history.
            pending.delete()
        }
    }

    override fun remove(id: String) {
        Files.deleteIfExists(recordFile(id).toPath())
    }

    private fun recordFile(id: String): File {
        CredentialIds.requireValid(id)
        val target = File(directory, "$id.credential")
        if (Files.isSymbolicLink(directory.toPath()) || Files.isSymbolicLink(target.toPath()) ||
            target.canonicalFile.parentFile != directory.canonicalFile
        ) {
            throw IOException("Credential record path is unavailable")
        }
        return target
    }
}
