package eu.kanade.tachiyomi.util.system

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The extension installers need the exact APK size for `pm install-create/-write`, and they get
 * a FileProvider URI. Sizes must resolve through the content resolver rather than `UniFile`,
 * which can't map arbitrary content providers to a file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GetUriSizeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `file uri size comes from the file`() {
        val file = File(context.cacheDir, "size-test.apk").apply { writeBytes(ByteArray(2048)) }

        context.getUriSize(Uri.fromFile(file)) shouldBe 2048L
    }

    @Test
    fun `content uri size comes from the asset file descriptor`() {
        File(context.cacheDir, "size-test.apk").writeBytes(ByteArray(4096))
        Robolectric.setupContentProvider(AssetFileProvider::class.java, AUTHORITY)

        context.getUriSize(Uri.parse("content://$AUTHORITY/size-test.apk")) shouldBe 4096L
    }

    @Test
    fun `unresolvable content uri size is null`() {
        context.getUriSize(Uri.parse("content://$AUTHORITY/missing.apk")) shouldBe null
    }

    class AssetFileProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
            val file = File(requireNotNull(context).cacheDir, uri.lastPathSegment!!)
            if (!file.isFile) return null
            return AssetFileDescriptor(
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
                0,
                file.length(),
            )
        }
    }

    private companion object {
        const val AUTHORITY = "eu.kanade.test.size"
    }
}
