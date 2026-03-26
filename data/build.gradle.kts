plugins {
    id("mihon.library")
    kotlin("plugin.serialization")
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "tachiyomi.data"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets {
        named("debug") {
            java.directories += listOf(
                "build/generated/sqldelight/code/Database/debug",
                "build/generated/sqldelight/code/AnimeDatabase/debug",
                "build/generated/sqldelight/code/NovelDatabase/debug",
                "build/generated/sqldelight/code/AchievementsDatabase/debug",
            )
        }
        named("release") {
            java.directories += listOf(
                "build/generated/sqldelight/code/Database/release",
                "build/generated/sqldelight/code/AnimeDatabase/release",
                "build/generated/sqldelight/code/NovelDatabase/release",
                "build/generated/sqldelight/code/AchievementsDatabase/release",
            )
        }
    }

    sqldelight {
        databases {
            create("Database") {
                packageName.set("tachiyomi.data")
                dialect(libs.sqldelight.dialects.sql)
                schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
                srcDirs.from(project.file("./src/main/sqldelight"))
            }
            create("AnimeDatabase") {
                packageName.set("tachiyomi.mi.data")
                dialect(libs.sqldelight.dialects.sql)
                schemaOutputDirectory.set(project.file("./src/main/sqldelightanime"))
                srcDirs.from(project.file("./src/main/sqldelightanime"))
            }
            create("NovelDatabase") {
                packageName.set("tachiyomi.novel.data")
                dialect(libs.sqldelight.dialects.sql)
                schemaOutputDirectory.set(project.file("./src/main/sqldelightnovel"))
                srcDirs.from(project.file("./src/main/sqldelightnovel"))
            }
            create("AchievementsDatabase") {
                packageName.set("tachiyomi.db.achievement")
                dialect(libs.sqldelight.dialects.sql)
                schemaOutputDirectory.set(project.file("./src/main/sqldelightachievements"))
                srcDirs.from(project.file("./src/main/sqldelightachievements"))
            }
        }
    }
}

tasks.matching {
    it.name == "extractDebugAnnotations" || it.name == "extractReleaseAnnotations"
}.configureEach {
    val variant = name.removePrefix("extract").removeSuffix("Annotations")
    dependsOn(
        "generate${variant}DatabaseInterface",
        "generate${variant}AnimeDatabaseInterface",
        "generate${variant}NovelDatabaseInterface",
        "generate${variant}AchievementsDatabaseInterface",
    )
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)

    implementation(libs.bundles.sqldelight)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.okhttp.mockwebserver)
}
