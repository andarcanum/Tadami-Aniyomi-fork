import mihon.buildlogic.AndroidConfig
import mihon.buildlogic.generatedBuildDir
import mihon.buildlogic.tasks.getLocalesConfigTask
import dev.icerock.gradle.tasks.GenerateMultiplatformResourcesTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.kmp.library")
    kotlin("multiplatform")
    alias(libs.plugins.moko)
}

kotlin {
    android {
        namespace = "tachiyomi.i18n.aniyomi"
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK
        withJava()
        withHostTestBuilder { }

        androidResources {
            enable = true
        }

        lint {
            disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.core)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

val generatedAndroidResourceDir = generatedBuildDir.resolve("android/res")

multiplatformResources {
    resourcesClassName.set("AYMR")
    resourcesPackage.set("tachiyomi.i18n.aniyomi")
    resourcesSourceSets {
        getByName("commonMain").srcDirs("src/commonMain/resources")
        getByName("androidMain").srcDirs(generatedAndroidResourceDir)
    }
}

tasks {
    withType<GenerateMultiplatformResourcesTask>().configureEach {
        if (name.contains("android", ignoreCase = true)) {
            androidSourceSetName.set("androidMain")
        }
    }

    val localesConfigTask = project.getLocalesConfigTask(generatedAndroidResourceDir)
    matching {
        it.name == "preBuild" || it.name == "preDebugBuild" || it.name == "preReleaseBuild"
    }.configureEach {
        dependsOn(localesConfigTask)
    }
    matching { it.name == "assemble" }.configureEach {
        dependsOn(localesConfigTask)
    }
}
