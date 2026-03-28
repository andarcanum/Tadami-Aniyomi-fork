package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
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
            sourceFonts = listOf(sourceFont),
            targetFontsDirectory = targetDir.resolve("fonts"),
        )

        val copiedFiles = targetDir.resolve("fonts").listFiles()?.map { it.name }?.sorted()
        copiedFiles shouldBe listOf("TestFont.ttf")
        targetDir.resolve("fonts").resolve("TestFont.ttf").readText() shouldBe "font-bytes"
    }
}
