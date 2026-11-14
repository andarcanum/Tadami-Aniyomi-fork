plugins {
    id("mihon.library")
}

android {
    namespace = "com.davemorrissey.labs.subscaleview"
}

dependencies {
    implementation(projects.core.common)
    implementation(androidx.annotation)

    // Tests
    testImplementation(libs.bundles.test)
    testImplementation(libs.robolectric)
    testImplementation(androidx.test.core)
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly(libs.junitVintageEngine)
}
