package mihon.core.archive

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipCentralDirectoryReaderTest {

    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads stored and deflated entries in reverse and concurrent order`() {
        val cbz = File(tempDir.toFile(), "chapter.cbz")
        val payloads = (0 until 20).associate { idx ->
            val name = "page_%03d.png".format(idx)
            val data = ByteArray(4096) { ((idx * 31 + it) and 0xFF).toByte() }
            name to data
        }

        ZipOutputStream(cbz.outputStream()).use { zos ->
            payloads.entries.forEachIndexed { index, (name, bytes) ->
                val entry = ZipEntry(name)
                if (index % 2 == 0) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                } else {
                    entry.method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }

        FileInputStream(cbz).channel.use { channel ->
            val reader = ZipCentralDirectoryReader.tryOpen(channel, cbz.length())!!
            reader.entries.map { it.name }.toList() shouldBe payloads.keys.toList()

            // Reverse order access
            payloads.keys.reversed().forEach { name ->
                val actual = reader.openInputStream(name)!!.use { it.readBytes() }
                actual shouldBe payloads[name]
            }

            // Concurrent positional reads
            val pool = Executors.newFixedThreadPool(4)
            try {
                val tasks = payloads.keys.map { name ->
                    Callable {
                        val actual = reader.openInputStream(name)!!.use { it.readBytes() }
                        actual shouldBe payloads[name]
                    }
                }
                pool.invokeAll(tasks).forEach { it.get() }
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun `returns null for non-zip data so caller falls back to libarchive`() {
        val notZip = File(tempDir.toFile(), "fake.cbr")
        notZip.writeBytes(ByteArray(128) { 0x42 })
        FileInputStream(notZip).channel.use { channel ->
            ZipCentralDirectoryReader.tryOpen(channel, notZip.length()) shouldBe null
        }
    }
}
