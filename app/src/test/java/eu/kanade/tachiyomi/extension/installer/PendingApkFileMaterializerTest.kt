package eu.kanade.tachiyomi.extension.installer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.test.assertFailsWith

/**
 * DownloadManager installs now materialize the APK into the app cache before installing or
 * sharing it, because the app-specific external dir file is not always openable by our own
 * process (EACCES), while the cache copy always is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingApkFileMaterializerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val materializer = PendingApkFileMaterializer()

    @Test
    fun `content uri is copied into the pending install cache`() {
        runBlocking {
            val uri = Uri.parse("content://test.provider/downloads/extension.apk")
            val payload = ByteArray(1024) { (it % 251).toByte() }
            shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(payload))

            val file = materializer.materialize(context, uri, "eu.test.extension")

            file.absolutePath shouldBe
                File(context.cacheDir, "pending_apk_installs/eu.test.extension.apk").absolutePath
            (file.readBytes() contentEquals payload) shouldBe true
        }
    }

    @Test
    fun `unreadable uri leaves no installable apk behind`() {
        runBlocking {
            val uri = Uri.parse("content://test.provider/downloads/unreadable.apk")
            val unreadable = object : InputStream() {
                override fun read(): Int = throw IOException("open failed: EACCES (Permission denied)")
            }
            shadowOf(context.contentResolver).registerInputStream(uri, unreadable)
            val apkDir = File(context.cacheDir, "pending_apk_installs")

            assertFailsWith<IOException> {
                materializer.materialize(context, uri, "eu.test.unreadable")
            }

            File(apkDir, "eu.test.unreadable.apk").exists() shouldBe false
        }
    }
}
