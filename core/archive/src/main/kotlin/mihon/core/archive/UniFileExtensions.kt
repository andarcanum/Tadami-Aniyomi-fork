package mihon.core.archive

import android.content.Context
import android.os.ParcelFileDescriptor
import com.hippo.unifile.UniFile
import java.io.File

fun UniFile.openFileDescriptor(context: Context, mode: String): ParcelFileDescriptor =
    context.contentResolver.openFileDescriptor(uri, mode)
        ?: error("Failed to open file descriptor: ${filePath ?: uri.toString()}")

fun UniFile.archiveReader(context: Context): ArchiveReader {
    val pfd = openFileDescriptor(context, "r")
    return try {
        ArchiveReader(pfd, File(context.cacheDir, "archive_stream_cache"))
    } catch (t: Throwable) {
        runCatching { pfd.close() }
        throw t
    }
}

fun UniFile.epubReader(context: Context) = EpubReader(archiveReader(context))
