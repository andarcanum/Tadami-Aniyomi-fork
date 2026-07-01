plugins {
    id("mihon.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "mihon.core.archive"
}

dependencies {
    implementation(libs.jsoup)
    compileOnly(libs.jspecify)
    implementation(libs.libarchive)
    implementation(libs.unifile)
}
