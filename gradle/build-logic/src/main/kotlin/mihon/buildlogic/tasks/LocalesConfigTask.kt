package mihon.buildlogic.tasks

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.io.File

private val emptyResourcesElement = "<resources>\\s*</resources>|<resources\\s*/>".toRegex()

fun Project.getLocalesConfigTask(
    outputResourceDir: File,
    sourceRoots: Iterable<File> = listOf(file("src/commonMain/moko-resources")),
): TaskProvider<Task> {
    val stringsFiles = sourceRoots
        .flatMap { sourceRoot ->
            sourceRoot.walkTopDown()
                .filter { it.isFile && it.name == "strings.xml" }
                .toList()
        }
    val outputFile = outputResourceDir.resolve("xml/locales_config.xml")

    return tasks.register("generateLocalesConfig") {
        inputs.files(stringsFiles)
        outputs.file(outputFile)

        doLast {
            val locales = stringsFiles
                .filterNot { it.readText().contains(emptyResourcesElement) }
                .map {
                    it.parentFile.name
                        .replace("base", "en")
                        .replace("-r", "-")
                        .replace("+", "-")
                }
                .distinct()
                .sorted()
                .joinToString("\n") { "|   <locale android:name=\"$it\"/>" }

            val content = """
            |<?xml version="1.0" encoding="utf-8"?>
            |<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
            $locales
            |</locale-config>
            """.trimMargin()

            outputFile.parentFile.mkdirs()
            outputFile.writeText(content)
        }
    }
}
