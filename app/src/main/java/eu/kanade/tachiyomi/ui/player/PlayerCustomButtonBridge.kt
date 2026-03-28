package eu.kanade.tachiyomi.ui.player

import `is`.xyz.mpv.MPVLib
import tachiyomi.domain.custombuttons.model.CustomButton
import java.io.File

internal object PlayerCustomButtonBridge {
    private const val MPV_DIR = "mpv"
    private const val MPV_SCRIPTS_DIR = "scripts"

    fun setupCustomButtons(
        filesDir: File,
        buttons: List<CustomButton>,
        primaryButtonId: Long,
    ) {
        val file = writeCustomButtonsScript(
            filesDir = filesDir,
            buttons = buttons,
            primaryButtonId = primaryButtonId,
        )
        MPVLib.command(arrayOf("load-script", file.absolutePath))
    }

    internal fun writeCustomButtonsScript(
        filesDir: File,
        buttons: List<CustomButton>,
        primaryButtonId: Long,
    ): File {
        val scriptsDirectory = File(File(filesDir, MPV_DIR), MPV_SCRIPTS_DIR)
        if (!scriptsDirectory.exists() && !scriptsDirectory.mkdirs()) {
            error("Unable to create MPV scripts directory")
        }

        val customButtonsContent = buildCustomButtonsContent(
            buttons = buttons,
            primaryButtonId = primaryButtonId,
            scriptsDirPath = scriptsDirectory.absolutePath,
        )

        return File(scriptsDirectory, "custombuttons.lua").also { file ->
            file.writeText(customButtonsContent)
        }
    }

    internal fun buildCustomButtonsContent(
        buttons: List<CustomButton>,
        primaryButtonId: Long,
        scriptsDirPath: String,
    ): String {
        return buildString {
            append(
                """
                    local lua_modules = mp.find_config_file('scripts')
                    if lua_modules then
                        package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua;' .. '$scriptsDirPath' .. '/?.lua'
                    end
                    local aniyomi = require 'aniyomi'
                """.trimIndent(),
            )

            buttons.forEach { button ->
                append(
                    """
                        ${button.getButtonOnStartup(primaryButtonId)}
                        function button${button.id}()
                            ${button.getButtonContent(primaryButtonId)}
                        end
                        mp.register_script_message('call_button_${button.id}', button${button.id})
                        function button${button.id}long()
                            ${button.getButtonLongPressContent(primaryButtonId)}
                        end
                        mp.register_script_message('call_button_${button.id}_long', button${button.id}long)
                    """.trimIndent(),
                )
            }
        }
    }
}
