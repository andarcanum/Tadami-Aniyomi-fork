<div align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_foreground.png" alt="Tadami logo" width="160" />
  <h1>Tadami</h1>
  <p><strong>A polished Aniyomi fork for anime, manga, and novels (ranobe).</strong></p>
  <p>
    <a href="https://github.com/andarcanum/Tadami-Aniyomi-fork/releases"><img src="https://img.shields.io/github/v/release/andarcanum/Tadami-Aniyomi-fork?display_name=tag" alt="Latest Release"></a>
    <a href="LICENSE"><img src="https://img.shields.io/github/license/andarcanum/Tadami-Aniyomi-fork" alt="License"></a>
    <a href="https://developer.android.com/about/versions/oreo"><img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen" alt="Android 8+"></a>
  </p>
</div>

## About

Tadami is a community fork of Aniyomi with a stronger focus on UI quality and reading experience, including active novel/ranobe support.

Current source version:
- `versionName`: `0.25`
- `versionCode`: `137`

## What Is Different In This Fork

- Aurora-inspired UI polish and cleaner navigation.
- Full anime, manga, and novel support in one app.
- Novel-oriented development (including compatibility tooling for LNReader plugin ecosystems).

## Features

| Area | Details |
| --- | --- |
| Media types | Anime, manga, and novels in one app |
| Sources and extensions | Separate browsing for anime, manga, and novel sources/extensions |
| Library and updates | Unified library management, updates, history, and download queues |
| Backup and restore | Backup/restore support across media types |
| Customization | Theme and behavior settings for player/reader experience |

## Screenshots

| Home | Library | Update | Browse |
| --- | --- | --- | --- |
| <img src="screenshots/1.jpg" alt="Home" width="240" /> | <img src="screenshots/2.jpg" alt="Library" width="240" /> | <img src="screenshots/3.jpg" alt="Update" width="240" /> | <img src="screenshots/4.jpg" alt="Browse" width="240" /> |

| Title card | Title card 2 | More |
| --- | --- | --- |
| <img src="screenshots/5.jpg" alt="Title card" width="240" /> | <img src="screenshots/6.jpg" alt="Title card 2" width="240" /> | <img src="screenshots/7.jpg" alt="More" width="240" /> |

## Download

Requires Android 8.0+ (API 26+).

- Stable builds and APKs: [Releases](https://github.com/andarcanum/Tadami-Aniyomi-fork/releases)

## Build From Source

Prerequisites:
- JDK 17
- Android SDK (compile SDK 35)
- Android Studio (recommended)

Build commands:

```bash
./gradlew assembleRelease
```

On Windows:

```powershell
.\gradlew.bat assembleRelease
```

APK output:
- `app/build/outputs/apk/release/`

## Contributing

Pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines.

## Disclaimer

This project does not host or distribute copyrighted content. Content availability depends on third-party sources and extensions.

## Credits

- [Mihon](https://github.com/mihonapp/mihon)
- [Aniyomi](https://github.com/aniyomiorg/aniyomi)

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).
