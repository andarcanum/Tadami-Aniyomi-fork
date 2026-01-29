# Technology Stack

## Core
- **Language:** [Kotlin](https://kotlinlang.org/) - Primary programming language.
- **Platform:** Android (Min SDK 24, Target SDK 34/35)
- **Architecture:** Multimodule Domain-Driven Design (DDD).

## Frontend / UI
- **Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern toolkit for building native UI.
- **Design System:** Material Design 3 (M3).
- **Navigation:** [Voyager](https://voyager.adriel.cafe/) - A multiplatform navigation library.
- **Image Loading:** [Coil 3](https://coil-kt.github.io/coil/) - Kotlin-first image loading library.

## Data & Networking
- **Database:** [SQLDelight](https://cashapp.github.io/sqldelight/) - Generates typesafe Kotlin APIs from SQL.
- **Networking:** [OkHttp 5](https://square.github.io/okhttp/) - HTTP client for Android and Java.
- **Parsing:** [Jsoup](https://jsoup.org/) - Java library for working with real-world HTML.
- **Concurrency:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & Flow.
- **Legacy Reactive:** RxJava 1.3.8.

## Dependency Injection
- **Framework:** [Injekt](https://github.com/mihonapp/injekt) - Dependency injection library used in the Tachiyomi/Aniyomi ecosystem.

## Build & Tooling
- **Build System:** Gradle (Kotlin DSL).
- **Dependency Management:** Gradle Version Catalogs (`libs.versions.toml`).
- **Code Quality:** Spotless & Ktlint.
