package eu.kanade.tachiyomi.ui.player

import com.hippo.unifile.UniFile
import `is`.xyz.mpv.MPVLib
import tachiyomi.domain.storage.service.StorageManager
import java.io.File

internal object PlayerFontBridge {
    private const val MPV_FONTS_DIR = "fonts"

    fun copyFontsDirectory(storageManager: StorageManager, mpvDir: UniFile) {
        // TODO: I think this is a bad hack.
        //  We need to find a way to let MPV directly access our fonts directory.
        val fontsDirectory = File(mpvDir.filePath!!, MPV_FONTS_DIR)
        if (!fontsDirectory.exists() && !fontsDirectory.mkdirs()) {
            error("Unable to create MPV fonts directory")
        }
        copyFontFiles(
            sourceFonts = storageManager.getFontsDirectory()?.listFiles()?.mapNotNull {
                it.filePath?.let(::File)
            }.orEmpty(),
            targetFontsDirectory = fontsDirectory,
        )
        MPVLib.setPropertyString("sub-fonts-dir", fontsDirectory.absolutePath)
        MPVLib.setPropertyString("osd-fonts-dir", fontsDirectory.absolutePath)
    }

    internal fun copyFontFiles(
        sourceFonts: List<File>,
        targetFontsDirectory: File,
    ) {
        if (!targetFontsDirectory.exists()) {
            targetFontsDirectory.mkdirs()
        }
        sourceFonts.forEach { font ->
            val outFile = File(targetFontsDirectory, font.name)
            font.inputStream().use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
