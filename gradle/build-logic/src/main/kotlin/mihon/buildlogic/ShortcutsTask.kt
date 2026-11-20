package mihon.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateShortcutsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val templateFile: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val appId = applicationId.get()
        val template = templateFile.get().asFile.readText()
        check(template.contains(APPLICATION_ID_PLACEHOLDER)) {
            "Shortcuts template must contain $APPLICATION_ID_PLACEHOLDER placeholder"
        }
        val outFile = outputDir.file("xml/shortcuts.xml").get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(template.replace(APPLICATION_ID_PLACEHOLDER, appId))
    }

    companion object {
        const val APPLICATION_ID_PLACEHOLDER = "\${applicationId}"
    }
}
