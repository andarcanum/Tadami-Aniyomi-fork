package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.BackupContentSummary
import eu.kanade.tachiyomi.data.backup.BackupDetector
import eu.kanade.tachiyomi.data.backup.BackupDiagnosticLog
import eu.kanade.tachiyomi.data.backup.BackupOrigin
import eu.kanade.tachiyomi.data.backup.BackupWriteReceipt
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Writes a backup so that a half-written or silently truncated file can never replace a good one.
 *
 * The bytes are compressed in the cache directory first and verified there at the wire level: the
 * staged artifact must decompress back to exactly the serialized payload (streaming digest), and
 * the payload must still declare the expected origin and content counts (top level protobuf field
 * scan, no objects materialized). A full decode is deliberately not used: a second object graph
 * does not fit in RAM next to the payload and the creator's own graph, and it exhausted the 256 MB
 * heap on small-heap devices (OutOfMemoryError). The destination is then compared against the
 * staged bytes with a streaming digest, because a SAF provider is free to accept a write and store
 * something else (or nothing at all). Everything streams: no second compressed copy, no full
 * payload copy and no decoded object graph ever sit in RAM next to the payload.
 */
class BackupWriter(
    private val context: Context,
) {

    /**
     * Streaming write: [emit] pushes the top level protobuf fields straight into the staged gzip
     * stream, so the uncompressed payload never exists as a single array in RAM. On large
     * libraries that array (tens of megabytes) sat next to the creator's own object graph and
     * pushed small-heap devices into OutOfMemoryError.
     *
     * @param destination file chosen by the user or created by the auto backup job.
     * @param expected content the caller believes it serialized.
     * @param expectedOrigin format the caller believes it wrote.
     * @param emit writes the uncompressed payload bytes; every byte is digested and counted.
     * @return a receipt describing what is actually stored on disk.
     */
    suspend fun writeStreamed(
        destination: UniFile,
        expected: BackupContentSummary,
        expectedOrigin: BackupOrigin,
        emit: (OutputStream) -> Unit,
    ): BackupWriteReceipt {
        val staging = File.createTempFile("backup-staging", ".tachibk", context.cacheDir)
        try {
            var plainBytes = 0L
            val payloadDigest = BackupDiagnosticLog.measure(context, "stage_gzip") {
                // Level 1 + 64 KiB buffering: the staging file is transient and rebuilt on every
                // backup; on low-end devices CPU time and syscall count dominate file size
                // (17.6 MB raw, level 6 = 3-24 s measured; the default GZIPOutputStream buffer
                // is 512 bytes).
                BufferedOutputStream(FileOutputStream(staging), 64 * 1024).use { bufferedOut ->
                    object : GZIPOutputStream(bufferedOut, 64 * 1024) {
                        init {
                            def.setLevel(Deflater.BEST_SPEED)
                        }
                    }.use { gzipOut ->
                        val digestOut = DigestOutputStream(gzipOut, MessageDigest.getInstance("SHA-256"))
                        val counting = object : OutputStream() {
                            override fun write(b: Int) {
                                digestOut.write(b)
                                plainBytes++
                            }

                            override fun write(b: ByteArray, off: Int, len: Int) {
                                digestOut.write(b, off, len)
                                plainBytes += len
                            }
                        }
                        emit(counting)
                        counting.flush()
                        digestOut.messageDigest.digest()
                    }
                }
            }
            BackupDiagnosticLog.log(context, "serialize_size", "bytes=$plainBytes")
            if (plainBytes == 0L) {
                throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
            }

            // Verify the staged bytes before touching the user's existing backup.
            BackupDiagnosticLog.measure(context, "verify_staged") {
                verifyStagedStream(staging, payloadDigest, expected, expectedOrigin)
            }

            BackupDiagnosticLog.measure(context, "write_destination") {
                replaceDestination(destination, staging)
            }

            // A SAF provider is free to accept a write and store something else (or nothing at
            // all). Byte equality is proven with streaming digests instead of reading the whole
            // destination back into RAM: the staged content is already verified above, so a
            // matching destination digest verifies the destination too.
            val checksum = sha256(staging)
            BackupDiagnosticLog.measure(context, "verify_destination") {
                val writtenDigest = destination.openInputStream().use { sha256(it) }
                if (writtenDigest != checksum) {
                    throw IOException(
                        "Backup destination does not contain the bytes that were just written " +
                            "(sha256 $writtenDigest instead of $checksum)",
                    )
                }
            }

            val byteLength = staging.length()
            // Counts only: never titles, urls or any quest payload.
            BackupDiagnosticLog.log(
                context,
                "write_receipt",
                "bytes=$byteLength sha256=$checksum origin=$expectedOrigin " +
                    "manga=${expected.mangaCount} anime=${expected.animeCount} " +
                    "novel=${expected.novelCount} categories=${expected.categoriesCount}",
            )

            return BackupWriteReceipt(
                byteLength = byteLength,
                sha256 = checksum,
                origin = expectedOrigin,
                summary = expected,
            )
        } finally {
            staging.delete()
        }
    }

    /**
     * Overwrite [destination] in place, truncating any previous, longer content.
     *
     * Opening a SAF document in "rwt" mode is the only reliable way to shorten it. Some providers
     * ignore the truncate flag, so the size is checked and, as a last resort, the document is
     * deleted and recreated rather than left with trailing bytes of an older backup.
     */
    private fun replaceDestination(destination: UniFile, staged: File) {
        val uri = destination.uri
        val resolver = context.contentResolver
        val size = staged.length()

        val wroteInPlace = try {
            resolver.openFileDescriptor(uri, "rwt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    out.channel.truncate(0)
                    FileInputStream(staged).use { it.copyTo(out) }
                    out.flush()
                    pfd.fileDescriptor.sync()
                    if (out.channel.size() != size) {
                        throw IOException(
                            "Backup file could not be truncated to the new size " +
                                "(${out.channel.size()} instead of $size)",
                        )
                    }
                }
                true
            } ?: false
        } catch (e: IOException) {
            throw e
        } catch (_: Exception) {
            false
        }

        if (wroteInPlace) return

        // Fallback for providers that refuse random access: recreate the document.
        val parent = destination.parentFile
            ?: throw IOException("Backup destination cannot be safely replaced")
        val name = destination.name
            ?: throw IOException("Backup destination cannot be safely replaced")
        if (!destination.delete()) {
            throw IOException("Backup destination could not be replaced")
        }
        val recreated = parent.createFile(name)
            ?: throw IOException("Backup destination could not be recreated")
        recreated.openOutputStream().use { out -> FileInputStream(staged).use { it.copyTo(out) } }
        if (recreated.length() != size) {
            throw IOException("Backup destination has an unexpected size after writing")
        }
    }

    private fun sha256(file: File): String = FileInputStream(file).use { sha256(it) }
}

/**
 * Proof that [staged] holds exactly the bytes that were serialized, and that those bytes still
 * declare the origin and content counts the caller meant to store.
 *
 * Wire level on purpose. The previous implementation decompressed the staged file into a second
 * full payload copy and decoded it into a complete object graph; on a 256 MB heap that did not fit
 * next to the payload and the creator's own graph, and backup creation died with
 * OutOfMemoryError (verify_staged stage) exactly on large libraries. Byte identity is now proven
 * with streaming digests, origin and counts are read from the top level protobuf fields without
 * materializing anything.
 */
internal fun verifyStagedBackup(
    staged: File,
    payload: ByteArray,
    expected: BackupContentSummary,
    expectedOrigin: BackupOrigin,
) {
    verifyStagedStream(staged, sha256Bytes(payload), expected, expectedOrigin)
}

/**
 * Streaming variant: the payload digest was computed while emitting, and origin plus content
 * counts are scanned off the decompressed staged stream without materializing the payload.
 */
internal fun verifyStagedStream(
    staged: File,
    payloadDigest: ByteArray,
    expected: BackupContentSummary,
    expectedOrigin: BackupOrigin,
) {
    // The staged artifact must decompress to exactly the serialized payload: this is the link that
    // ties the destination digest check (destination == staged) back to what was serialized.
    val stagedDigest = GZIPInputStream(FileInputStream(staged)).use { sha256(it) }
    if (stagedDigest != payloadDigest.toHexString()) {
        throw IOException(
            "Backup staged file does not contain the bytes that were serialized " +
                "(sha256 $stagedDigest instead of ${payloadDigest.toHexString()})",
        )
    }

    GZIPInputStream(FileInputStream(staged)).use { stream ->
        val scan = BackupDetector.scanStream(stream)
        val actualOrigin = BackupDetector.originFromScan(scan)
        if (actualOrigin != expectedOrigin) {
            throw IOException(
                "Backup staged file was written as $expectedOrigin but reads back as $actualOrigin",
            )
        }

        val actual = BackupDetector.summaryFromScan(scan)
        if (actual != expected) {
            throw IOException(
                "Backup staged file is incomplete: expected $expected but it contains $actual",
            )
        }
    }
}

private const val DIGEST_CHUNK = 64 * 1024

private fun sha256Bytes(bytes: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    var offset = 0
    while (offset < bytes.size) {
        val length = minOf(DIGEST_CHUNK, bytes.size - offset)
        digest.update(bytes, offset, length)
        offset += length
    }
    return digest.digest()
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

private fun sha256(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DIGEST_CHUNK)
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
