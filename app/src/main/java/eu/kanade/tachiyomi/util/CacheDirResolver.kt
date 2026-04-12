package eu.kanade.tachiyomi.util

import android.app.Application
import java.io.File

internal fun Application.safeCacheDir(): File {
    val cacheDirPath = runCatching { cacheDir.path }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

    return if (cacheDirPath != null) {
        File(cacheDirPath).also { if (!it.exists()) it.mkdirs() }
    } else {
        File(System.getProperty("java.io.tmpdir") ?: ".", "aniyomi_test_files_dir").also { it.mkdirs() }
    }
}
