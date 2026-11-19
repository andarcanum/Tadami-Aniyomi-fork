package eu.kanade.tachiyomi.ui.player

import com.hippo.unifile.UniFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.custombuttons.model.CustomButton
import java.io.File

class PlayerBridgeBehaviorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `write custom buttons script creates the expected lua file`() {
        val scriptFile = PlayerCustomButtonBridge.writeCustomButtonsScript(
            filesDir = tempDir,
            buttons = listOf(
                CustomButton(
                    id = 7L,
                    name = "Open",
                    isFavorite = true,
                    sortIndex = 0L,
                    content = "return ${'$'}id",
                    longPressContent = "return ${'$'}isPrimary",
                    onStartup = "startup ${'$'}id",
                ),
            ),
            primaryButtonId = 7L,
        )

        scriptFile.exists() shouldBe true
        scriptFile.absolutePath.contains(tempDir.absolutePath) shouldBe true
        val content = scriptFile.readText()
        content shouldContain "startup 7"
        content shouldContain "return true"
        content shouldContain "call_button_7"
    }

    @Test
    fun `copy fonts helper copies files into the target fonts directory`() {
        val sourceFonts = tempDir.resolve("source-fonts").apply { mkdirs() }
        val sourceFont = sourceFonts.resolve("TestFont.ttf").apply {
            writeText("font-bytes")
        }
        val targetDir = tempDir.resolve("mpv-fonts")
        PlayerFontBridge.copyFontFiles(
            sourceFonts = listOfNotNull(UniFile.fromFile(sourceFont)),
            targetFontsDirectory = targetDir.resolve("fonts"),
        )

        val copiedFiles = targetDir.resolve("fonts").listFiles()?.map { it.name }?.sorted()
        copiedFiles shouldBe listOf("TestFont.ttf")
        targetDir.resolve("fonts").resolve("TestFont.ttf").readText() shouldBe "font-bytes"
    }

    @Test
    fun `copy fonts helper skips unreadable fonts and keeps copying the rest`() {
        val sourceFonts = tempDir.resolve("source-fonts").apply { mkdirs() }
        val missingFont = sourceFonts.resolve("MissingFont.ttf")
        val readableFont = sourceFonts.resolve("ReadableFont.ttf").apply {
            writeText("readable-font")
        }
        val targetDir = tempDir.resolve("mpv-fonts")

        PlayerFontBridge.copyFontFiles(
            sourceFonts = listOfNotNull(
                UniFile.fromFile(missingFont),
                UniFile.fromFile(readableFont),
            ),
            targetFontsDirectory = targetDir.resolve("fonts"),
        )

        val copiedFiles = targetDir.resolve("fonts").listFiles()?.map { it.name }?.sorted()
        copiedFiles shouldBe listOf("ReadableFont.ttf")
        targetDir.resolve("fonts").resolve("ReadableFont.ttf").readText() shouldBe "readable-font"
    }

    @Test
    fun `copy fonts helper skips unchanged fonts and prunes removed fonts`() {
        val sourceFonts = tempDir.resolve("source-fonts").apply { mkdirs() }
        val keptFont = sourceFonts.resolve("KeptFont.ttf").apply {
            writeText("font-v1")
            setLastModified(1_700_000_000_000L)
        }
        val targetFonts = tempDir.resolve("mpv-fonts").resolve("fonts").apply { mkdirs() }
        val staleFont = targetFonts.resolve("RemovedFont.ttf").apply {
            writeText("stale")
        }

        PlayerFontBridge.copyFontFiles(
            sourceFonts = listOfNotNull(UniFile.fromFile(keptFont)),
            targetFontsDirectory = targetFonts,
        )

        staleFont.exists() shouldBe false
        val stagedKept = targetFonts.resolve("KeptFont.ttf")
        stagedKept.readText() shouldBe "font-v1"

        // Second pass with identical length & timestamp should not rewrite the staged file.
        stagedKept.setLastModified(1_800_000_000_000L)
        PlayerFontBridge.copyFontFiles(
            sourceFonts = listOfNotNull(UniFile.fromFile(keptFont)),
            targetFontsDirectory = targetFonts,
        )
        stagedKept.lastModified() shouldBe 1_800_000_000_000L
    }

    @Test
    fun `setup custom buttons returns null when disabled`() {
        val result = PlayerCustomButtonBridge::class.java
            .getMethod(
                "setupCustomButtons",
                File::class.java,
                List::class.java,
                Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            .invoke(
                PlayerCustomButtonBridge,
                tempDir,
                emptyList<CustomButton>(),
                0L,
                false,
            )

        result shouldBe null
    }
}
