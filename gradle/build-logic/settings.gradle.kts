dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../libs.versions.toml"))
        }
        create("androidx") {
            from(files("../androidx.versions.toml"))
        }
        create("compose") {
            from(files("../compose.versions.toml"))
        }
        create("kotlinx") {
            from(files("../kotlinx.versions.toml"))
        }
        create("aniyomilibs") {
            from(files("../aniyomi.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
