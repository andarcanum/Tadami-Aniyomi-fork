plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(androidx.gradle)
    compileOnly(kotlinx.gradle)
    compileOnly(kotlinx.compose.compiler.gradle)
    implementation(libs.spotless.gradle)
    compileOnly(gradleApi())

    compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    compileOnly(files(androidx.javaClass.superclass.protectionDomain.codeSource.location))
    compileOnly(files(compose.javaClass.superclass.protectionDomain.codeSource.location))
    compileOnly(files(kotlinx.javaClass.superclass.protectionDomain.codeSource.location))
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
