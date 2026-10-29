package eu.kanade.tachiyomi.data.backup.create

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.BackupContentSummary
import eu.kanade.tachiyomi.data.backup.BackupOrigin
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Round-trip guard for [BackupWriter.writeStreamed] itself (not just verifyStagedBackup):
 * the staged gzip artifact must decompress back to exactly the emitted payload and the
 * destination must hold those staged bytes. BackupWriterVerifyTest covers the verification
 * function with its own gzip helper and never exercises the writer; this test does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BackupWriterWriteStreamedTest {

    @Test
    fun `writeStreamed output decompresses to exactly the emitted bytes`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val writer = BackupWriter(context)
        val file = File(context.cacheDir, "write-streamed-test.tachibk")
        file.delete()
        val destination = UniFile.fromFile(file)!!
        val payload = nativePayload(manga = 2, categories = 1)

        val receipt = runBlocking {
            writer.writeStreamed(
                destination = destination,
                expected = BackupContentSummary(
                    mangaCount = 2,
                    categoriesCount = 1,
                ),
                expectedOrigin = BackupOrigin.TADAMI,
                emit = { out -> out.write(payload) },
            )
        }

        val restored = file.inputStream().use { input ->
            GZIPInputStream(input).use { gzip -> gzip.readBytes() }
        }
        restored shouldBe payload
        receipt.byteLength shouldBe file.length()
    }

    private fun nativePayload(manga: Int, categories: Int): ByteArray {
        val backup = Backup(
            backupManga = (1..manga).map { BackupManga(source = it.toLong(), url = "u$it", title = "t$it") },
            backupCategories = (1..categories).map { BackupCategory(name = "c$it") },
            isLegacy = false,
        )
        return ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
    }
}
