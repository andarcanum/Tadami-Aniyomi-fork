# Technology Stack

**Analysis Date:** 2026-03-14

## Languages

**Primary:**
- Kotlin 2.2.0 - Android development, all app modules

**Secondary:**
- Not applicable (pure Kotlin/Android project)

## Runtime

**Environment:**
- Android (minimum SDK not specified in analysis - Android app)
- Java/Gradle via Android Gradle Plugin

**Package Manager:**
- Gradle with Kotlin DSL (build.gradle.kts)
- Version catalogs in `gradle/*.versions.toml`
- Lockfile: Not applicable for Gradle

## Frameworks

**Core:**
- Android SDK - Mobile application framework
- Jetpack Compose 2025.03.00 - Modern declarative UI toolkit
- Material Design 3 - UI components

**Testing:**
- JUnit 5.11.4 - Unit testing framework
- Robolectric 4.13 - Android unit testing
- MockK 1.13.17 - Kotlin mocking library
- Kotest 5.9.1 - Testing assertions
- OkHttp MockWebServer 5.0.0-alpha.14 - HTTP mocking

**Build/Dev:**
- Android Gradle Plugin (AGP) 8.10.0
- Kotlin Gradle Plugin 2.2.0
- Spotless 7.0.2 - Code formatting
- Ktlint 1.5.0 - Linting

## Key Dependencies

**Networking:**
- OkHttp 5.0.0-alpha.14 - HTTP client with logging, brotli, DNS-over-HTTPS
- OkIO 3.10.2 - I/O library
- Jsoup 1.19.1 - HTML parsing

**Database:**
- SqlDelight 2.0.2 - SQL query generation
- SQLite (androidx.sqlite) 2.4.0 - Local database
- Requery SQLite Android 3.45.0 - SQLite wrapper

**Dependency Injection:**
- Injekt (custom fork) - Kotlin dependency injection

**Image Loading:**
- Coil 3.1.0 - Image loading with GIF support, Compose integration

**Media Playback:**
- mpv-android (aniyomi-mpv-lib 1.18.n) - Video player
- FFmpeg-kit 1.18 - Media transcoding

**UI/Compose:**
- Compose BOM 2025.03.00 - Compose version management
- Material3 - Material Design components
- Voyager 1.0.1 - Navigation
- Lottie 6.4.0 - Animations
- RichText 0.20.0 - Rich text rendering

**Data Serialization:**
- Kotlinx Serialization 1.8.1 - JSON, Protobuf, XML
- QuickJS Android 0.9.2 - JavaScript engine
- J2V8 Android 6.3.4 - JavaScript engine (V8)

**Coroutines:**
- Kotlinx Coroutines 1.10.1 - Asynchronous programming

**Other:**
- RxJava 1.3.8 - Reactive programming
- WorkManager 2.10.0 - Background tasks
- Biometric 1.2.0-alpha05 - Authentication
- Shizuku 13.1.0 - Root access
- AboutLibraries 11.6.3 - Open source licenses

## Configuration

**Environment:**
- Build configuration in `gradle.properties`
- Local properties for SDK path
- Private module support via `local.properties`

**Build:**
- Root `build.gradle.kts` - Project-level configuration
- Module-level `build.gradle.kts` files
- Version catalogs in `gradle/*.versions.toml`:
  - `libs.versions.toml` - Main dependencies
  - `kotlinx.versions.toml` - Kotlin ecosystem
  - `compose.versions.toml` - Compose libraries
  - `androidx.versions.toml` - AndroidX libraries
  - `aniyomi.versions.toml` - Aniyomi-specific libs

## Platform Requirements

**Development:**
- Android SDK (via local.properties)
- Java 17+ recommended
- Gradle (handled by wrapper)

**Production:**
- Android device (target varies by module)
- Application ID: `com.tadami.aurora` (configurable)

---

*Stack analysis: 2026-03-14*
