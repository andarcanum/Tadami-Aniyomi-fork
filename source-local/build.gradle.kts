import mihon.buildlogic.AndroidConfig
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.kmp.library")
    kotlin("multiplatform")
}

kotlin {
    android {
        namespace = "tachiyomi.source.local"
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK
        withJava()
        withHostTestBuilder { }
    }

    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(projects.sourceApi)
                api(projects.i18n)
                api(projects.i18nAniyomi)

                implementation(libs.unifile)
                implementation(libs.jetbrains.markdown)
            }
        }
        getByName("androidMain") {
            dependencies {
                implementation(projects.core.archive)
                implementation(projects.core.common)
                implementation(projects.coreMetadata)

                // Move ChapterRecognition to separate module?
                implementation(projects.domain)

                implementation(kotlinx.bundles.serialization)
                // FFmpeg-kit
                implementation(aniyomilibs.ffmpeg.kit)
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.bundles.test)
                runtimeOnly(libs.junitPlatformLauncher)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
