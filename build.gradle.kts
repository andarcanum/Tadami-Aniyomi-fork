buildscript {
    dependencies {
        classpath(libs.android.shortcut.gradle)
    }
}

plugins {
    alias(androidx.plugins.application) apply false
    alias(androidx.plugins.library) apply false
    alias(androidx.plugins.test) apply false
    alias(androidx.plugins.kmp.library) apply false
    alias(kotlinx.plugins.compose.compiler) apply false
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.aboutLibrariesAndroid) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.sqldelight) apply false
    // Required for Spotless 8.x multi-project shared task service (diffplug/spotless#2877).
    alias(libs.plugins.spotless) apply false
}

val buildLogic = gradle.includedBuild("build-logic")

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
    dependsOn(buildLogic.task(":clean"))
}
