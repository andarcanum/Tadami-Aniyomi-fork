# Aniyomi Project Context

## Overview
**Aniyomi** is an Android application for reading manga and watching anime, based on Mihon (formerly Tachiyomi). It features a clean architecture with multi-module Gradle setup, utilizing Jetpack Compose for the UI and MPV-Android for video playback.

## Key Technologies
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material3), XML (Legacy/Hybrid)
- **Navigation:** Voyager
- **Dependency Injection:** Injekt
- **Asynchronous:** Kotlin Coroutines & Flow, RxJava (Legacy)
- **Database:** SQLDelight
- **Network:** OkHttp 5 (Alpha)
- **Image Loading:** Coil 3
- **Video Player:** MPV-Android, FFmpeg-kit
- **Multiplatform Resources:** Moko Resources

## Project Structure
- **`app/`**: Main Android application module.
- **`core/`**: Core logic and utilities.
- **`data/`**: Data layer implementation (Repositories, Data Sources, Database).
- **`domain/`**: Domain layer (Use Cases, Entities, Repository Interfaces).
- **`presentation-core/`**: Shared UI components and logic.
- **`presentation-widget/`**: Android widgets.
- **`source-api/`**: API for extension sources.
- **`i18n/`**: Internationalization resources.

## Build & Run
The project uses Gradle with Kotlin DSL.

### Prerequisites
- JDK 17 (recommended for Android builds)
- Android SDK

### Common Commands
*   **Build Debug APK:**
    ```bash
    ./gradlew assembleStandardDebug
    ```
*   **Install Debug APK:**
    ```bash
    ./gradlew installStandardDebug
    ```
*   **Run Unit Tests:**
    ```bash
    ./gradlew test
    ```
*   **Check Code Style (Spotless/KtLint):**
    ```bash
    ./gradlew spotlessCheck
    ```
*   **Apply Code Style Fixes:**
    ```bash
    ./gradlew spotlessApply
    ```

## Development Conventions
- **Architecture:** Follow Clean Architecture principles (Presentation -> Domain -> Data).
- **UI:** Prefer Jetpack Compose for new UI.
- **Navigation:** Use Voyager for screen navigation.
- **Code Style:** Strictly enforced via Spotless and KtLint. Run `spotlessApply` before committing.
- **Strings/Resources:** Use Moko Resources for shared strings where applicable.

## Notes
- **Flavors:** The app has `standard` and `dev` flavors (among others like `benchmark`).
- **Signing:** Debug builds use a default debug keystore.
- **Shortcuts:** defined in `app/shortcuts.xml`.
